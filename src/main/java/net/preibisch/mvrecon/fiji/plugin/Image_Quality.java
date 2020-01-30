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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.fusion.QualityGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.export.Calibrateable;
import net.preibisch.mvrecon.process.export.ImgExport;
import net.preibisch.mvrecon.process.export.ImgExportGui;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.quality.FRCRealRandomAccessible;
import net.preibisch.mvrecon.process.quality.FRCTools;

/**
 * Plugin to fuse images using transformations from the SpimData object
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Image_Quality implements PlugIn
{
	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Quality Estimation", true, true, true, true, true ) )
			return;

		estimateFRC( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean estimateFRC(
			final SpimData2 spimData,
			final List< ViewId > viewsToProcess )
	{
		final QualityGUI quality = new QualityGUI( spimData, viewsToProcess );

		if ( !quality.queryDetails() )
			return false;

		final List< Group< ViewDescription > > groups = quality.getFusionGroups();
		int i = 0;

		if ( !Double.isNaN( quality.getAnisotropyFactor() ) ) // flatten the fused image
		{
			final double anisoF = quality.getAnisotropyFactor();

			Interval bb = quality.getBoundingBox();
			final long[] min = new long[ 3 ];
			final long[] max = new long[ 3 ];

			bb.min( min );
			bb.max( max );

			min[ 2 ] = Math.round( Math.floor( min[ 2 ] / anisoF ) );
			max[ 2 ] = Math.round( Math.ceil( max[ 2 ] / anisoF ) );

			final Interval boundingBox = new FinalInterval( min, max );

			// we need to update the bounding box here
			quality.setBoundingBox( boundingBox );
		}

		// query exporter parameters
		final ImgExportGui exporter = quality.getNewExporterInstance();

		// query exporter parameters
		if ( !exporter.queryParameters( quality ) )
			return false;

		// one common executerservice
		final ExecutorService taskExecutor = Executors.newFixedThreadPool( Threads.numThreads() );

		for ( final Group< ViewDescription > group : Group.getGroupsSorted( groups ) )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): FRC-Quality estimating group " + (++i) + "/" + groups.size() + " (group=" + group + ")" );

			final Pair< Double, String > transformedCal = TransformationTools.computeAverageCalibration( group, spimData.getViewRegistrations() );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Approximate pixel size of quality image (without downsampling): " + transformedCal.getA() + " " + transformedCal.getB() );

			if ( Calibrateable.class.isInstance( exporter ) )
				((Calibrateable)exporter).setCalibration( transformedCal.getA(), transformedCal.getB() );

			// img loading and registrations
			final ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();
			final ViewRegistrations registrations = spimData.getViewRegistrations();

			final ArrayList< Pair< RandomAccessibleInterval< FloatType >, AffineTransform3D > > data = new ArrayList<>();

			for ( final ViewId viewId : group )
			{
				final AffineTransform3D transform;

				final ViewRegistration vr = registrations.getViewRegistration( viewId );
				vr.updateModel();

				if ( Double.isNaN( quality.getAnisotropyFactor() ) ) // no flattening of the quality image
				{
					transform = vr.getModel();
				}
				else
				{
					transform = vr.getModel().copy();
					final AffineTransform3D aniso = new AffineTransform3D();
					aniso.set(
							1.0, 0.0, 0.0, 0.0,
							0.0, 1.0, 0.0, 0.0,
							0.0, 0.0, 1.0/quality.getAnisotropyFactor(), 0.0 );
					transform.preConcatenate( aniso );
				}

				final FRCRealRandomAccessible< FloatType > frc =
						FRCTools.computeFRC( viewId, imgLoader, quality.getFRCStepSize(), quality.getFFTSize(), quality.getUseRelativeFRC(), quality.getUseSmoothLocalFRC() );

				data.add( new ValuePair<>( frc.getRandomAccessibleInterval(), vr.getModel() ) );
			}

			final RandomAccessibleInterval< FloatType > virtualQuality = FRCTools.fuseRAIs( data, quality.getDownsampling(), quality.getBoundingBox(), 1 );

			if ( !export( virtualQuality, taskExecutor, new FloatType(), quality, exporter, group, null ) )
				return false;
		}

		exporter.finish();
		
		taskExecutor.shutdown();

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): DONE." );

		return true;
	}

	protected static < T extends RealType< T > & NativeType< T > > boolean export(
			final RandomAccessibleInterval< T > output,
			final ExecutorService taskExecutor,
			final T type,
			final QualityGUI quality,
			final ImgExport exporter,
			final Group< ViewDescription > group,
			final double[] minmax )
	{
		final RandomAccessibleInterval< T > processedOutput = FusionTools.copyImg( output, new ImagePlusImgFactory< T >( type ), type, taskExecutor, true );

		final String title = Image_Fusion.getTitle( quality.getSplittingType(), group );

		if ( minmax == null )
			return exporter.exportImage( processedOutput, quality.getBoundingBox(), quality.getDownsampling(), quality.getAnisotropyFactor(), title, group );
		else
			return exporter.exportImage( processedOutput, quality.getBoundingBox(), quality.getDownsampling(), quality.getAnisotropyFactor(), title, group, minmax[ 0 ], minmax[ 1 ] );
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Image_Quality().run( null );
	}
}
