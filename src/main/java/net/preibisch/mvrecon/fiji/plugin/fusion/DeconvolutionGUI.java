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
package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.cuda.CUDADevice;
import net.preibisch.mvrecon.process.cuda.CUDAFourierConvolution;
import net.preibisch.mvrecon.process.cuda.CUDATools;
import net.preibisch.mvrecon.process.cuda.NativeLibraryTools;
import net.preibisch.mvrecon.process.deconvolution.DeconViewPSF.PSFTYPE;
import net.preibisch.mvrecon.process.deconvolution.MultiViewDeconvolution;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInit.PsiInitType;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInitAvgApproxFactory;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInitAvgPreciseFactory;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInitBlurredFusedFactory;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInitFactory;
import net.preibisch.mvrecon.process.deconvolution.init.PsiInitFromFileFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.ComputeBlockThreadFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.mul.ComputeBlockMulThreadCPUFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.sequential.ComputeBlockSeqThreadCPUFactory;
import net.preibisch.mvrecon.process.deconvolution.iteration.sequential.ComputeBlockSeqThreadCUDAFactory;
import net.preibisch.mvrecon.process.export.AppendSpimData2HDF5Gui;
import net.preibisch.mvrecon.process.export.DisplayImageGui;
import net.preibisch.mvrecon.process.export.ExportSpimData2HDF5Gui;
import net.preibisch.mvrecon.process.export.ExportSpimData2TIFFGui;
import net.preibisch.mvrecon.process.export.ImgExportGui;
import net.preibisch.mvrecon.process.export.Save3dTIFFGui;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.FusionTools.ImgDataType;
import net.preibisch.mvrecon.process.fusion.intensityadjust.IntensityAdjustmentTools;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class DeconvolutionGUI extends DeconvolutionUtils implements FusionExportInterface
{
	public final static ArrayList< ImgExportGui > staticImgExportAlgorithms = new ArrayList< ImgExportGui >();
	public final static String[] imgExportDescriptions;

	static
	{
		IOFunctions.printIJLog = true;

		staticImgExportAlgorithms.add( new DisplayImageGui() );
		staticImgExportAlgorithms.add( new Save3dTIFFGui( null ) );
		staticImgExportAlgorithms.add( new ExportSpimData2TIFFGui() );
		staticImgExportAlgorithms.add( new ExportSpimData2HDF5Gui() );
		staticImgExportAlgorithms.add( new AppendSpimData2HDF5Gui() );

		imgExportDescriptions = new String[ staticImgExportAlgorithms.size() ];

		for ( int i = 0; i < staticImgExportAlgorithms.size(); ++i )
			imgExportDescriptions[ i ] = staticImgExportAlgorithms.get( i ).getDescription();
	}

	public static String[] computationOnChoice = new String[]{
			"CPU (Java)",
			"GPU (Nvidia CUDA via JNA)" };

	public static String[] osemspeedupChoice = new String[]{
			"1 (balanced)",
			"minimal number of overlapping views",
			"average number of overlapping views",
			"specify manually" };

	public static String[] blocksChoice = new String[]{
			"in 256x256x256 blocks", 
			"in 512x512x512 blocks",
			"in 768x768x768 blocks",
			"in 1024x1024x1024 blocks",
			"specify maximal blocksize manually",
			"one block (??????x??????x??????) for the entire image" };

	public static String[] psfTypeChoice = new String[]{
		"Efficient Bayesian - Optimization II (very fast, imprecise)", 
		"Efficient Bayesian - Optimization I (fast, precise)", 
		"Efficient Bayesian (less fast, more precise)", 
		"Independent (slow, very precise)" };

	public static String[] psiInitChoice = new String[]{
			"Blurred, fused image (suggested, higher compute effort)",
			"Average intensity (higer compute effort)",
			"Approximated average intensity (fast option)",
			"From TIFF file (dimensions must match bounding box)" };

	public static String[] splittingTypes = new String[]{
			"Each timepoint & channel",
			"Each timepoint, channel & illumination",
			"All views together",
			"Each view" };

	public static int defaultBB = 0;
	public static int defaultInputImgCacheType = 1;
	public static int defaultWeightCacheType = 1;
	public static double defaultDownsampling = 1.0;
	public static boolean defaultAdjustIntensities = false;
	public static boolean defaultMul = false;
	public static int defaultPSFType = 1;
	public static int defaultPsiInit = 0;
	public static double defaultOsemSpeedup = 1;
	public static int defaultNumIterations = 10;
	public static boolean defaultDebugMode = false;
	public static int defaultDebugInterval = 1;
	public static boolean defaultUseTikhonovRegularization = true;
	public static double defaultLambda = 0.006;
	public static int defaultBlockSizeIndex = 1;
	public static int defaultBlockSizeX = 384, defaultBlockSizeY = 384, defaultBlockSizeZ = 384;
	public static boolean defaultTestEmptyBlocks = true;
	public static int defaultCacheBlockSize = MultiViewDeconvolution.cellDim;
	public static int defaultCacheMaxNumBlocks = MultiViewDeconvolution.maxCacheSize;
	public static int defaultPsiCopyBlockSize = MultiViewDeconvolution.cellDim * 2;
	public static int defaultComputeOnIndex = 0;
	public static boolean defaultAdjustBlending = false;
	public static float defaultBlendingRange = MultiViewDeconvolution.defaultBlendingRange;
	public static float defaultBlendingBorder = MultiViewDeconvolution.defaultBlendingBorder;
	public static boolean defaultAdditionalSmoothBlending = MultiViewDeconvolution.additionalSmoothBlending;
	public static boolean defaultGroupTiles = true;
	public static boolean defaultGroupIllums = true;
	public static int defaultSplittingType = 0;
	public static int defaultImgExportAlgorithm = 0;
	public static String defaultPsiStartFile = "";
	public static boolean defaultPreciseAvgMax = true;


	protected int boundingBox = defaultBB;
	protected double downsampling = defaultDownsampling;
	protected boolean adjustIntensities = defaultAdjustIntensities;
	protected boolean mul = defaultMul;
	protected int cacheTypeInputImg = defaultInputImgCacheType;
	protected int cacheTypeWeights = defaultWeightCacheType;
	protected int psfType = defaultPSFType;
	protected int psiInit = defaultPsiInit;
	protected double osemSpeedup = defaultOsemSpeedup;
	protected int numIterations = defaultNumIterations;
	protected boolean debugMode = defaultDebugMode;
	protected int debugInterval = defaultDebugInterval;
	protected boolean useTikhonov = defaultUseTikhonovRegularization;
	protected double lambda = defaultLambda;
	protected int blockSizeIndex = defaultBlockSizeIndex;
	protected int[] blockSize = new int[]{ defaultBlockSizeX, defaultBlockSizeY, defaultBlockSizeZ };
	protected boolean testEmptyBlocks = defaultTestEmptyBlocks;
	protected int cacheBlockSize = defaultCacheBlockSize;
	protected int cacheMaxNumBlocks = defaultCacheMaxNumBlocks;
	protected int psiCopyBlockSize = defaultPsiCopyBlockSize;
	protected int computeOnIndex = defaultComputeOnIndex;
	protected ImgFactory< FloatType > psiFactory = null;
	protected ImgFactory< FloatType > copyFactory = null;
	protected ImgFactory< FloatType > blockFactory = new ArrayImgFactory<>();
	protected ComputeBlockThreadFactory< ? > computeFactory = null;
	protected boolean adjustBlending = defaultAdjustBlending;
	protected float blendingRange = defaultBlendingRange;
	protected float blendingBorder = defaultBlendingBorder;
	protected boolean additionalSmoothBlending = defaultAdditionalSmoothBlending;
	protected boolean groupTiles = defaultGroupTiles;
	protected boolean groupIllums = defaultGroupIllums;
	protected int splittingType = defaultSplittingType;
	protected int imgExport = defaultImgExportAlgorithm;
	protected long[] maxBlock = null;
	protected String psiStartFile = "";
	protected boolean preciseAvgMax = true;

	protected NonRigidParametersGUI nrgui;

	final protected SpimData2 spimData;
	final List< ViewId > views;
	final List< BoundingBox > allBoxes;
	final ExecutorService service;
	final HashMap< ViewId, PointSpreadFunction > psfs;
	final long[] maxDimPSF;

	public DeconvolutionGUI( final SpimData2 spimData, final List< ViewId > views, final ExecutorService service )
	{
		this.spimData = spimData;
		this.views = new ArrayList<>();
		this.views.addAll( views );
		this.service = service;

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, views );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// get all bounding boxes and two extra ones
		this.allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );

		// check that all psfs are there, make a local lookup
		psfs = getPSFs();

		if ( psfs != null )
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Computing maximal PSF size ... " );

			maxDimPSF = maxTransformedKernel( psfs, spimData.getViewRegistrations() );

			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Maximal transformed PSF size = " + Util.printCoordinates( maxDimPSF ) );
		}
		else
		{
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Error, not all views have PSF's assigned, please do that first." );

			maxDimPSF = null;
		}
	}

	@Override
	public SpimData2 getSpimData() { return spimData; }

	@Override
	public List< ViewId > getViews() { return views; }

	public BoundingBox getBoundingBox() { return allBoxes.get( boundingBox ); }

	@Override
	public Interval getDownsampledBoundingBox()
	{
		if ( !Double.isNaN( downsampling ) )
			return TransformVirtual.scaleBoundingBox( getBoundingBox(), 1.0 / downsampling );
		else
			return getBoundingBox();
	}

	@Override
	public int getPixelType() { return 0; }

	@Override
	public double getDownsampling(){ return downsampling; }

	@Override
	public double getAnisotropyFactor() { return Double.NaN; }

	public boolean adjustIntensities() { return adjustIntensities; }

	@Override
	public int getSplittingType() { return splittingType; }

	public ImgDataType getInputImgCacheType() { return ImgDataType.values()[ cacheTypeInputImg ]; }
	public ImgDataType getWeightCacheType() { return ImgDataType.values()[ cacheTypeWeights ]; }
	public PSFTYPE getPSFType() { return PSFTYPE.values()[ psfType ]; }
	public double getOSEMSpeedUp() { return osemSpeedup; }
	public int getNumIterations() { return numIterations; }
	public boolean getDebugMode() { return debugMode; }
	public int getDebugInterval() { return debugInterval; }
	public boolean getUseTikhonov() { return useTikhonov; }
	public float getLambda() { return useTikhonov ? (float)lambda : 0.0f; }
	public int[] getComputeBlockSize() { return blockSize; }
	public boolean testEmptyBlocks() { return testEmptyBlocks; }
	public int getCacheBlockSize() { return cacheBlockSize; }
	public int getCacheMaxNumBlocks(){ return cacheMaxNumBlocks; }
	public int getPsiCopyBlockSize() { return psiCopyBlockSize; }
	public ImgFactory< FloatType > getBlockFactory() { return blockFactory; }
	public ImgFactory< FloatType > getPsiFactory() { return psiFactory; }
	public ImgFactory< FloatType > getCopyFactory() { return copyFactory; }
	public ComputeBlockThreadFactory< ? > getComputeBlockThreadFactory() { return computeFactory; }
	public boolean isMultiplicative() { return mul; } //TODO: maybe this actually multiplicative (cannot remove remove blocks, psf must be the same size)
	public float getBlendingRange() { return blendingRange; }
	public float getBlendingBorder() { return blendingBorder; }
	public boolean getAdditionalSmoothBlending() { return additionalSmoothBlending; }
	public boolean groupTiles() { return groupTiles; }
	public boolean groupIllums() { return groupIllums; }
	public NonRigidParametersGUI getNonRigidParameters() { return nrgui; }
	public PsiInitFactory getPsiInitFactory()
	{
		final PsiInitType psiInitType = PsiInitType.values()[ psiInit ];

		if ( psiInitType == PsiInitType.FUSED_BLURRED )
			return new PsiInitBlurredFusedFactory();
		else if ( psiInitType == PsiInitType.AVG )
			return new PsiInitAvgPreciseFactory();
		else if ( psiInitType == PsiInitType.APPROX_AVG )
			return new PsiInitAvgApproxFactory();
		else
			return new PsiInitFromFileFactory( new File( psiStartFile ), preciseAvgMax );
	}

	@Override
	public ImgExportGui getNewExporterInstance() { return staticImgExportAlgorithms.get( imgExport ).newInstance(); }

	public boolean queryDetails()
	{
		if ( maxDimPSF == null )
			return false;

		final boolean hasIntensityAdjustments = IntensityAdjustmentTools.containsAdjustments( spimData.getIntensityAdjustments(), views );
		final boolean enableNonRigid = false; //NonRigidParametersGUI.enableNonRigid;
		this.nrgui = new NonRigidParametersGUI( spimData, views );

		final String[] choices = FusionGUI.getBoundingBoxChoices( allBoxes );

		if ( defaultBB >= choices.length )
			defaultBB = 0;

		Choice boundingBoxChoice = null, inputCacheChoice = null, nonrigidChoice = null, weightCacheChoice = null, blockChoice = null, computeOnChoice = null, splitChoice = null;
		TextField downsampleField = null;
		Label label1 = null, label2 = null;

		final GenericDialog gd = new GenericDialog( "Image Fusion" );

		gd.addChoice( "Bounding_Box", choices, choices[ defaultBB ] );
		if ( !PluginHelper.isHeadless() ) boundingBoxChoice = (Choice)gd.getChoices().lastElement();
		gd.addMessage( "" );

		gd.addSlider( "Downsampling", 1.0, 16.0, defaultDownsampling );
		if ( !PluginHelper.isHeadless() ) downsampleField = (TextField)gd.getNumericFields().lastElement();
		gd.addChoice( "Input image(s)", FusionTools.imgDataTypeChoice, FusionTools.imgDataTypeChoice[ defaultInputImgCacheType ] );
		if ( !PluginHelper.isHeadless() ) inputCacheChoice = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Weight image(s)", FusionTools.imgDataTypeChoice, FusionTools.imgDataTypeChoice[ defaultWeightCacheType ] );
		if ( !PluginHelper.isHeadless() ) weightCacheChoice = (Choice)gd.getChoices().lastElement();

		if ( enableNonRigid )
		{
			this.nrgui.addQuery( gd );
			nonrigidChoice = (Choice)gd.getChoices().lastElement();
		}
		else
		{
			this.nrgui.isActive = false;
			nonrigidChoice = null;
		}

		if ( hasIntensityAdjustments )
			gd.addCheckbox( "Adjust_image_intensities", defaultAdjustIntensities );

		gd.addMessage( "" );

		gd.addChoice( "Initialize_with", psiInitChoice, psiInitChoice[ defaultPsiInit ] );
		gd.addChoice( "Type_of_iteration", psfTypeChoice, psfTypeChoice[ defaultPSFType ] );
		gd.addCheckbox( "Fast_sequential_iterations (OSEM)", !defaultMul );
		gd.addNumericField( "OSEM_acceleration", defaultOsemSpeedup, 1 );
		gd.addNumericField( "Number_of_iterations", defaultNumIterations, 0 );
		gd.addCheckbox( "Debug_mode", defaultDebugMode );
		gd.addCheckbox( "Use_Tikhonov_regularization", defaultUseTikhonovRegularization );
		gd.addNumericField( "Tikhonov_parameter", defaultLambda, 4 );

		gd.addMessage( "" );

		gd.addChoice( "Compute", blocksChoice, blocksChoice[ defaultBlockSizeIndex ] );
		if ( !PluginHelper.isHeadless() ) blockChoice = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Compute_on", computationOnChoice, computationOnChoice[ defaultComputeOnIndex ] );
		if ( !PluginHelper.isHeadless() ) computeOnChoice = (Choice)gd.getChoices().lastElement();
		gd.addCheckbox( "Adjust_blending & grouping parameters", defaultAdjustBlending );

		gd.addMessage( "" );

		gd.addChoice( "Produce one fused image for", splittingTypes, splittingTypes[ defaultSplittingType ] );
		if ( !PluginHelper.isHeadless() ) splitChoice = (Choice)gd.getChoices().lastElement();
		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label2 = (Label)gd.getMessage();

		if ( !PluginHelper.isHeadless() )
		{
			final ManageDeconvolutionDialogListeners m = new ManageDeconvolutionDialogListeners(
					gd,
					boundingBoxChoice,
					downsampleField,
					inputCacheChoice,
					nonrigidChoice,
					weightCacheChoice,
					blockChoice,
					computeOnChoice,
					splitChoice,
					label1,
					label2,
					this );
	
			m.update();
		}

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		boundingBox = defaultBB = gd.getNextChoiceIndex();
		downsampling = defaultDownsampling = gd.getNextNumber();

		if ( downsampling == 1.0 )
			downsampling = Double.NaN;

		cacheTypeInputImg = defaultInputImgCacheType = gd.getNextChoiceIndex();
		cacheTypeWeights = defaultWeightCacheType = gd.getNextChoiceIndex();

		if ( hasIntensityAdjustments )
			adjustIntensities = defaultAdjustIntensities = gd.getNextBoolean();
		else
			adjustIntensities = false;

		psiInit = defaultPsiInit = gd.getNextChoiceIndex();
		psfType = defaultPSFType = gd.getNextChoiceIndex();
		mul = defaultMul = !gd.getNextBoolean();
		osemSpeedup = defaultOsemSpeedup = gd.getNextNumber();
		numIterations = defaultNumIterations = (int)Math.round( gd.getNextNumber() );
		debugMode = defaultDebugMode = gd.getNextBoolean();
		useTikhonov = defaultUseTikhonovRegularization = gd.getNextBoolean();
		lambda = defaultLambda = gd.getNextNumber();
		blockSizeIndex = defaultBlockSizeIndex = gd.getNextChoiceIndex();
		computeOnIndex = defaultComputeOnIndex = gd.getNextChoiceIndex();
		adjustBlending = defaultAdjustBlending = gd.getNextBoolean();
		splittingType = defaultSplittingType = gd.getNextChoiceIndex();
		imgExport = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		if ( mul )
		{
			testEmptyBlocks = false;
			osemSpeedup = 1.0;
		}
		else
		{
			testEmptyBlocks = defaultTestEmptyBlocks;
		}

		if ( PsiInitType.values()[ psiInit ] == PsiInitType.FROM_FILE )
		{
			if ( !getPsiFile() )
				return false;
		}

		if ( !getDebug() )
			return false;

		if ( !getBlocks() )
			return false;

		psiFactory = new CellImgFactory<>( psiCopyBlockSize );
		copyFactory = new CellImgFactory<>( psiCopyBlockSize );

		if ( !getBlendingAndGrouping() )
			return false;

		if ( !getComputeDevice() )
			return false;

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Selected (MultiView)Deconvolution Parameters: " );
		IOFunctions.println( "Bounding Box: " + getBoundingBox() );
		IOFunctions.println( "Downsampling: " + DownsampleTools.printDownsampling( getDownsampling() ) );
		IOFunctions.println( "Downsampled Bounding Box: " + getDownsampledBoundingBox() );
		IOFunctions.println( "Input Image Cache Type: " + FusionTools.imgDataTypeChoice[ getInputImgCacheType().ordinal() ] );
		IOFunctions.println( "Weight Cache Type: " + FusionTools.imgDataTypeChoice[ getWeightCacheType().ordinal() ] );
		IOFunctions.println( "Adjust intensities: " + adjustIntensities );
		IOFunctions.println( "Multiplicative iterations: " + mul );
		IOFunctions.println( "PSF Type: " + psfTypeChoice[ getPSFType().ordinal() ] );
		IOFunctions.println( "Psi Init: " + psiInitChoice[ psiInit ] );
		IOFunctions.println( "OSEMSpeedup: " + osemSpeedup );
		IOFunctions.println( "Num Iterations: " + numIterations );
		IOFunctions.println( "Debug Mode: " + debugMode );
		if ( debugMode ) IOFunctions.println( "DebugInterval: " + debugInterval );
		IOFunctions.println( "use Tikhonov: " + useTikhonov );
		if ( useTikhonov ) IOFunctions.println( "Tikhonov Lambda: " + lambda );
		IOFunctions.println( "Compute block size: " + Util.printCoordinates( blockSize ) );
		IOFunctions.println( "Test for empty blocks: " + testEmptyBlocks );
		IOFunctions.println( "Cache block size: " + cacheBlockSize );
		IOFunctions.println( "Cache max num blocks: " + cacheMaxNumBlocks );
		IOFunctions.println( "Deconvolved/Copy block size: " + psiCopyBlockSize );
		IOFunctions.println( "Compute on: " + computationOnChoice[ computeOnIndex ] );
		IOFunctions.println( "ComputeBlockThread Factory: " + computeFactory.getClass().getSimpleName() + ": " + computeFactory );
		IOFunctions.println( "Blending range: " + blendingRange );
		IOFunctions.println( "Blending border: " + blendingBorder );
		IOFunctions.println( "Additional smooth blending: " + additionalSmoothBlending );
		IOFunctions.println( "Group tiles: " + groupTiles );
		IOFunctions.println( "Group illums: " + groupIllums );
		IOFunctions.println( "Split by: " + splittingTypes[ getSplittingType() ] );
		IOFunctions.println( "Image Export: " + imgExportDescriptions[ imgExport ] );
		IOFunctions.println( "ImgLoader.isVirtual(): " + isImgLoaderVirtual() );
		IOFunctions.println( "ImgLoader.isMultiResolution(): " + isMultiResolution() );

		return true;
	}

	public boolean isImgLoaderVirtual() { return FusionGUI.isImgLoaderVirtual( spimData ); }
	public boolean isMultiResolution() { return FusionGUI.isMultiResolution( spimData ); }

	public List< Group< ViewDescription > > getFusionGroups()
	{
		return FusionGUI.getFusionGroups( getSpimData(), getViews(), getSplittingType() );
	}

	public List< Group< ViewDescription > > getDeconvolutionGrouping( final Group< ViewDescription > group )
	{
		final HashSet< Class< ? extends Entity > > groupingFactors = new HashSet<>();

		if ( groupTiles )
			groupingFactors.add( Tile.class );

		if ( groupIllums )
			groupingFactors.add( Illumination.class );

		final ArrayList< ViewDescription > list = new ArrayList<>();
		list.addAll( group.getViews() );

		return Group.combineBy( list, groupingFactors );
	}

	public HashMap< ViewId, PointSpreadFunction > getPSFs()
	{
		final HashMap< ViewId, PointSpreadFunction > psfs = new HashMap<>();

		for ( final ViewId view : views )
		{
			if ( !spimData.getPointSpreadFunctions().getPointSpreadFunctions().containsKey( view ) )
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": ERROR - No PSF assigned for view " + Group.pvid( view ) );
				return null;
			}
			else
			{
				psfs.put( view, spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( view ) );
			}
		}

		return psfs;
	}

	protected boolean getPsiFile()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Select PSI init file" );
		gd.addFileField( "PSI_file", defaultPsiStartFile, 80 );
		gd.addCheckbox( "Precise avg & max computation from input", defaultPreciseAvgMax );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		defaultPsiStartFile = psiStartFile = gd.getNextString();
		defaultPreciseAvgMax = preciseAvgMax = gd.getNextBoolean();

		return true;
	}

	protected boolean getDebug()
	{
		if ( debugMode )
		{
			GenericDialog gdDebug = new GenericDialog( "Debug options" );
			gdDebug.addNumericField( "Show debug output every n'th frame, n = ", defaultDebugInterval, 0 );
			gdDebug.showDialog();

			if ( gdDebug.wasCanceled() )
				return false;

			defaultDebugInterval = debugInterval = (int)Math.round( gdDebug.getNextNumber() );
		}

		return true;
	}

	protected boolean getBlocks()
	{
		if ( blockSizeIndex == 0 )
		{
			this.blockSize = new int[]{ 256, 256, 256 };
		}
		else if ( blockSizeIndex == 1 )
		{
			this.blockSize = new int[]{ 512, 512, 512 };
		}
		else if ( blockSizeIndex == 2 )
		{
			this.blockSize = new int[]{ 768, 768, 768 };
		}
		else if ( blockSizeIndex == 3 )
		{
			this.blockSize = new int[]{ 1024, 1024, 1024 };
		}
		else if ( blockSizeIndex == 4 )
		{
			GenericDialog gd = new GenericDialog( "Define block sizes" );

			gd.addNumericField( "Compute_blocksize_x", defaultBlockSizeX, 0 );
			gd.addNumericField( "Compute_blocksize_y", defaultBlockSizeY, 0 );
			gd.addNumericField( "Compute_blocksize_z", defaultBlockSizeZ, 0 );

			gd.addMessage( "Note: block sizes shouldn't be smaller than 128 pixels since it might result in a\n"
					+ "too small or even negative effective blocksize (blocksize-2*kernelsize)", GUIHelper.smallStatusFont );
			gd.addMessage( "" );

			if ( !mul )
			{
				gd.addCheckbox( "Remove_empty_blocks", defaultTestEmptyBlocks );
				gd.addMessage( "Note: if selected, all blocks of each virtual input view are scanned to test\n"
						+ "if some of them are entirely empty. This takes some time, but if some are, it saves a lot.", GUIHelper.smallStatusFont );
				gd.addMessage( "" );
			}

			gd.addNumericField( "Deconvolved_image_block_size", defaultPsiCopyBlockSize, 0 );
			gd.addMessage( "Note: this values defines the block size for the deconvolved & copied images", GUIHelper.smallStatusFont );
			gd.addMessage( "" );

			if ( cacheTypeInputImg == 1 || cacheTypeWeights == 1 )
			{
				gd.addNumericField( "Cache_block_size", defaultCacheBlockSize, 0 );
				gd.addNumericField( "Cache_max num blocks", defaultCacheMaxNumBlocks, 0 );
				gd.addMessage( "Note: these values define the cache parameters for input images & weights", GUIHelper.smallStatusFont );
			}

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			this.blockSize = new int[]{
					defaultBlockSizeX = Math.max( 1, (int)Math.round( gd.getNextNumber() ) ),
					defaultBlockSizeY = Math.max( 1, (int)Math.round( gd.getNextNumber() ) ),
					defaultBlockSizeZ = Math.max( 1, (int)Math.round( gd.getNextNumber() ) ) };

			if ( !mul )
				this.testEmptyBlocks = defaultTestEmptyBlocks = gd.getNextBoolean();

			this.psiCopyBlockSize = defaultPsiCopyBlockSize = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );

			if ( cacheTypeInputImg == 1 || cacheTypeWeights == 1 )
			{
				this.cacheBlockSize = defaultCacheBlockSize = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
				this.cacheMaxNumBlocks = defaultCacheMaxNumBlocks = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );
			}
		}
		else
		{
			this.blockSize = new int[]{ (int)maxBlock[ 0 ], (int)maxBlock[ 1 ], (int)maxBlock[ 2 ] };
		}

		if ( computeOnIndex == 1 && !isPowerOfTwo( this.blockSize ) )
		{
			IOFunctions.println( "ERROR: Manually block sizes that are not power-of-2 (e.g. 256, 512, 1024 are not supported with GPU.");
			return false;
		}

		return true;
	}

	public static boolean isPowerOfTwo( final int[] values )
	{
		if ( values == null || values.length == 0 )
			return false;

		for ( int d = 0; d < values.length; ++d )
			if ( !isPowerOfTwo( values[ d ] ) )
				return false;

		return true;
	}

	public static boolean isPowerOfTwo( int value )
	{
		if ( value < 1 )
			return false;

		while( value % 2 == 0 || value == 1 )
		{
			value /= 2;
			if ( value == 0 )
				return true;
		}

		return false;
	}

	protected boolean getComputeDevice()
	{
		if ( mul )
		{
			// numViews is set later in Image_Deconvolution
			this.computeFactory = new ComputeBlockMulThreadCPUFactory( service, -1, MultiViewDeconvolution.minValue, getLambda(), blockSize, blockFactory );
		}
		else if ( computeOnIndex == 0 )
		{
			this.computeFactory = new ComputeBlockSeqThreadCPUFactory( service, MultiViewDeconvolution.minValue, getLambda(), blockSize, blockFactory );
		}
		else if ( computeOnIndex == 1 )
		{
			final ArrayList< String > potentialNames = new ArrayList< String >();
			potentialNames.add( "fftCUDA" );
			potentialNames.add( "FourierConvolutionCUDA" );

			final CUDAFourierConvolution cuda = NativeLibraryTools.loadNativeLibrary( potentialNames, CUDAFourierConvolution.class );

			if ( cuda == null )
			{
				IOFunctions.println( "Cannot load CUDA JNA library." );
				return false;
			}

			final ArrayList< CUDADevice > selectedDevices = CUDATools.queryCUDADetails( cuda, true );

			if ( selectedDevices == null || selectedDevices.size() == 0 )
				return false;

			final HashMap< Integer, CUDADevice > idToCudaDevice = new HashMap<>();

			for ( int devId = 0; devId < selectedDevices.size(); ++devId )
				idToCudaDevice.put( devId, selectedDevices.get( devId ) );

			this.computeFactory = new ComputeBlockSeqThreadCUDAFactory( service, MultiViewDeconvolution.minValue, getLambda(), blockSize, cuda, idToCudaDevice );
		}
		else
		{
			throw new RuntimeException( "Unknown compute device index: " + computeOnIndex );
		}

		return true;
	}

	protected boolean getBlendingAndGrouping()
	{
		if ( adjustBlending )
		{
			final GenericDialog gd = new GenericDialog( "Adjust blending & grouping parameters" );

			gd.addSlider( "Blending_boundary (pixels)", -50, 50, defaultBlendingBorder );
			gd.addSlider( "Blending_range (pixels)", 0, 100, defaultBlendingRange );
			gd.addCheckbox( "Additional_smooth_blending", defaultAdditionalSmoothBlending );

			gd.addMessage( "" );

			gd.addMessage( "Note: both sizes are in coordinates of the deconvolved image. If you experience stripy artifacts, try to\n" +
						   " - increase the blending boundary\n" +
						   " - increase the blending range\n" +
						   " - make sure you use \"Blurred, fused image\" as initialization for the deconvolved image (main dialog)\n" +
						   " - enable \"Additional smooth blending\" \n" +
						   "The blending range is independent of the downsampling. The blending boundary will be adjusted for downsampling,\n" +
						   "please choose it relative to the PSF size (<50%). The boundary pixels describe a range of pixels at the edge of\n" +
						   "each input that are discarded. A negative blending boundary value means that the deconvolution will make an\n" + 
						   "educated guess even for pixels that were not imaged, based on the fact that parts of their signal is visible due\n" +
						   "to the PSF", GUIHelper.smallStatusFont );

			gd.addMessage( "" );

			if ( splittingType <= 2 )
				gd.addCheckbox( "Group_tiles into one view", defaultGroupTiles );
			if ( splittingType == 0 || splittingType == 2 )
				gd.addCheckbox( "Group_illuminations into one view", defaultGroupIllums );

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			blendingBorder = defaultBlendingBorder = (float)gd.getNextNumber();
			blendingRange = defaultBlendingRange = (float)gd.getNextNumber();
			additionalSmoothBlending = defaultAdditionalSmoothBlending = gd.getNextBoolean();

			if ( splittingType <= 2 )
				groupTiles = defaultGroupTiles = gd.getNextBoolean();
			else
				groupTiles = false;

			if ( splittingType == 0 || splittingType == 2 )
				groupIllums = defaultGroupIllums = gd.getNextBoolean();
			else
				groupIllums = false;
		}

		return true;
	}

}
