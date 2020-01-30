/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.plugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.DeconvolutionGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.deconvolution.DeconView;
import net.preibisch.mvrecon.process.deconvolution.DeconViewPSF.PSFTYPE;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.deconvolution.MultiViewDeconvolution;
import net.preibisch.mvrecon.process.deconvolution.MultiViewDeconvolutionMul;
import net.preibisch.mvrecon.process.deconvolution.MultiViewDeconvolutionSeq;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInitFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.ComputeBlockThreadFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.mul.ComputeBlockMulThreadCPUFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.sequential.ComputeBlockSeqThread;
import net.preibisch.mvrecon.process.deconvolution.util.PSFPreparation;
import net.preibisch.mvrecon.process.deconvolution.util.ProcessInputImages;
import net.preibisch.mvrecon.process.export.Calibrateable;
import net.preibisch.mvrecon.process.export.ImgExport;
import net.preibisch.mvrecon.process.export.ImgExportGui;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.FusionTools.ImgDataType;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Deconvolution implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset (Multiview) Deconvolution", true, true, true, true, true ) )
			return;

		deconvolve( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean deconvolve(
			final SpimData2 spimData,
			final List< ViewId > views )
	{
		return deconvolve( spimData, views, DeconViews.createExecutorService() );
	}

	/**
	 * 
	 * @param spimData
	 * @param viewList
	 * @param service Will be shutdown.
	 * @return
	 */
	public static boolean deconvolve(
			final SpimData2 spimData,
			final List< ViewId > viewList,
			final ExecutorService service )
	{
		final DeconvolutionGUI decon = new DeconvolutionGUI( spimData, viewList, service );

		if ( !decon.queryDetails() )
			return false;

		// query exporter parameters
		final ImgExportGui exporter = decon.getNewExporterInstance();

		// query exporter parameters
		if ( !exporter.queryParameters( decon ) )
			return false;

		final List< Group< ViewDescription > > deconGroupBatches = decon.getFusionGroups();
		int i = 0;

		for ( final Group< ViewDescription > deconGroup : Group.getGroupsSorted( deconGroupBatches ) )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Deconvolving group " + (++i) + "/" + deconGroupBatches.size() + " (group=" + deconGroup + ")" );

			final Pair< Double, String > transformedCal = TransformationTools.computeAverageCalibration( deconGroup, spimData.getViewRegistrations() );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Approximate pixel size of fused image (without downsampling): " + transformedCal.getA() + " " + transformedCal.getB() );

			if ( Calibrateable.class.isInstance( exporter ) )
				((Calibrateable)exporter).setCalibration( transformedCal.getA(), transformedCal.getB() );

			final List< Group< ViewDescription > > deconVirtualViews = Group.getGroupsSorted( decon.getDeconvolutionGrouping( deconGroup ) );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): This group contains the following 'virtual views':" );

			for ( final Group< ViewDescription > virtualView : deconVirtualViews )
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): " + Group.gvids( Group.getViewsSorted( virtualView.getViews() ) ) );

			final Interval bb = decon.getBoundingBox();
			final double downsampling = decon.getDownsampling();

			final ProcessInputImages< ViewDescription > fusion = new ProcessInputImages<>(
					spimData,
					deconVirtualViews,
					service,
					bb,
					downsampling,
					true,
					FusionTools.defaultBlendingRange,
					FusionTools.defaultBlendingBorder,
					true,
					decon.getBlendingRange(),
					decon.getBlendingBorder() / ( Double.isNaN( downsampling ) ? 1.0f : (float)downsampling ),
					decon.adjustIntensities() ? spimData.getIntensityAdjustments().getIntensityAdjustments() : null );

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Fusion of 'virtual views' " );
			fusion.fuseGroups();

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Normalizing weights ... " );
			fusion.normalizeWeights( decon.getOSEMSpeedUp(), decon.getAdditionalSmoothBlending(), 0.1f, 0.05f );

			if ( decon.getInputImgCacheType() == ImgDataType.CACHED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Caching fused input images ... " );
				fusion.cacheImages();
			}
			else if ( decon.getInputImgCacheType() == ImgDataType.PRECOMPUTED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Precomputing fused input images ... " );
				fusion.copyImages( decon.getCopyFactory() );
			}

			if ( decon.getWeightCacheType() == ImgDataType.CACHED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Caching weight images ... " );
				fusion.cacheUnnormalizedWeights();
				fusion.cacheNormalizedWeights();
			}
			if ( decon.getWeightCacheType() == ImgDataType.PRECOMPUTED )
			{
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Precomputing weight images ... " );
				// we cache the unnormalized ones so the copying is efficient
				fusion.cacheUnnormalizedWeights();
				fusion.copyNormalizedWeights( decon.getCopyFactory() );
			}

			final ImgFactory< FloatType > psiFactory = decon.getPsiFactory();
			final int[] blockSize = decon.getComputeBlockSize();
			final int numIterations = decon.getNumIterations();
			final PSFTYPE psfType = decon.getPSFType();
			final boolean filterBlocksForContent = decon.testEmptyBlocks();
			final boolean debug = decon.getDebugMode();
			final int debugInterval = decon.getDebugInterval();
			final ComputeBlockThreadFactory< ? > cptf = decon.getComputeBlockThreadFactory();
			final boolean mul = decon.isMultiplicative();
			final PsiInitFactory psiInitFactory = decon.getPsiInitFactory();

			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Grouping, and transforming PSF's " );

			if ( mul )
				IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Making all PSF's the same size to support multiplicative updates." );

			final HashMap< Group< ViewDescription >, ArrayImg< FloatType, ? > > psfs =
					PSFPreparation.loadGroupTransformPSFs( spimData.getPointSpreadFunctions(), fusion, mul );

			try
			{
				if ( filterBlocksForContent )
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution and testing for empty ones that can be dropped." );
				else
					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Setting up blocks for deconvolution." );

				final ArrayList< DeconView > deconViews = new ArrayList<>();

				for ( final Group< ViewDescription > virtualView : Group.getGroupsSorted( fusion.getGroups() ) )
				{
					final DeconView view = new DeconView(
							service,
							fusion.getImages().get( virtualView ),
							fusion.getNormalizedWeights().get( virtualView ),
							psfs.get( virtualView ),
							psfType,
							blockSize,
							cptf.numParallelBlocks(),
							filterBlocksForContent );

					if ( view.getNumBlocks() <= 0 )
						return false;

					view.setTitle( Group.gvids( virtualView ) );
					deconViews.add( view );

					IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Added " + view );
				}

				final DeconViews views = new DeconViews( deconViews, service );

				final MultiViewDeconvolution< ? > mvDecon;

				if ( mul )
				{
					if ( !ComputeBlockMulThreadCPUFactory.class.isInstance( cptf ) )
					{
						IOFunctions.println( "For multiplicative deconvolution only CPU is supported so far, sorry. Please open a github issue and I'll implement it." );
						IOFunctions.println( "GITHUB page: https://github.com/PreibischLab/multiview-reconstruction/issues" );
						return false;
					}

					((ComputeBlockMulThreadCPUFactory)cptf).setNumViews( deconVirtualViews.size() );
					mvDecon = new MultiViewDeconvolutionMul( views, numIterations, psiInitFactory, (ComputeBlockMulThreadCPUFactory)cptf, psiFactory );
				}
				else
				{
					mvDecon = new MultiViewDeconvolutionSeq( views, numIterations, psiInitFactory, (ComputeBlockThreadFactory< ComputeBlockSeqThread >)cptf, psiFactory );
				}

				if ( !mvDecon.initWasSuccessful() )
					return false;
				mvDecon.setDebug( debug );
				mvDecon.setDebugInterval( debugInterval );
				mvDecon.runIterations();

				if ( !export( mvDecon.getPSI(), decon, exporter, deconGroup ) )
				{
					IOFunctions.println( "ERROR exporting the image using '" + exporter.getClass().getSimpleName() + "'" );
					return false;
				}
			}
			catch ( OutOfMemoryError oome )
			{
				oome.printStackTrace();

				IOFunctions.println( "Out of memory.  Use smaller blocks, virtual/cached inputs, and check \"Edit > Options > Memory & Threads\"" );
				IOFunctions.println( "Your java instance has access to a total amount of RAM of: " + Runtime.getRuntime().maxMemory() / (1024*1024) );

				service.shutdown();

				return false;
			}
		}

		service.shutdown();

		exporter.finish();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): DONE." );

		return true;
	}

	protected static boolean export(
			final RandomAccessibleInterval< FloatType > output,
			final DeconvolutionGUI fusion,
			final ImgExport exporter,
			final Group< ViewDescription > group )
	{
		final String title = Image_Fusion.getTitle( fusion.getSplittingType(), group );

		return exporter.exportImage( output, fusion.getBoundingBox(), fusion.getDownsampling(), Double.NaN, title, group );
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Deconvolution().run( null );
	}
}
