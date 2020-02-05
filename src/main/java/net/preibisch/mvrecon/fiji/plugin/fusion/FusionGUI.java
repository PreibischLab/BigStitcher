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

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.export.AppendSpimData2HDF5Gui;
import net.preibisch.mvrecon.process.export.DisplayImageGui;
import net.preibisch.mvrecon.process.export.ExportSpimData2HDF5Gui;
import net.preibisch.mvrecon.process.export.ExportSpimData2TIFFGui;
import net.preibisch.mvrecon.process.export.ImgExportGui;
import net.preibisch.mvrecon.process.export.Save3dTIFFGui;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.FusionUtils;
import net.preibisch.mvrecon.process.fusion.intensityadjust.IntensityAdjustmentTools;
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class FusionGUI extends FusionUtils implements FusionExportInterface
{
	public static String[] interpolationTypes = new String[]{ "Nearest Neighbor", "Linear Interpolation" };
	public static int defaultInterpolation = 1;

	public static String[] pixelTypes = new String[]{ "32-bit floating point", "16-bit unsigned integer" };
	public static int defaultPixelType = 0;

	public static String[] splittingTypes = new String[]{
			"Each timepoint & channel",
			"Each timepoint, channel & illumination",
			"All views together",
			"Each view" };

	public static int defaultSplittingType = 0;

	public static boolean defaultUseBlending = true;
	public static boolean defaultUseContentBased = false;
	public static boolean defaultAdjustIntensities = false;
	public static boolean defaultPreserveAnisotropy = false;
	public static boolean defaultClusterProcessing = false;

	public final static ArrayList< ImgExportGui > staticImgExportAlgorithms = new ArrayList< ImgExportGui >();
	public final static String[] imgExportDescriptions;
	public static int defaultImgExportAlgorithm = 0;

	protected int interpolation = defaultInterpolation;
	protected int boundingBox = defaultBB;
	protected int pixelType = defaultPixelType;
	protected int cacheType = defaultCache;
	protected int splittingType = defaultSplittingType;
	protected double downsampling = defaultDownsampling;
	protected boolean useBlending = defaultUseBlending;
	protected boolean useContentBased = defaultUseContentBased;
	protected boolean adjustIntensities = defaultAdjustIntensities;
	protected boolean preserveAnisotropy = defaultPreserveAnisotropy;
	protected double avgAnisoF;
	protected int imgExport = defaultImgExportAlgorithm;
	protected boolean processOnCluster = defaultClusterProcessing;

	protected NonRigidParametersGUI nrgui;

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

	final protected SpimData2 spimData;
	final List< ViewId > views;
	final List< BoundingBox > allBoxes;

	public FusionGUI( final SpimData2 spimData, final List< ViewId > views )
	{
		this.spimData = spimData;
		this.views = new ArrayList<>();
		this.views.addAll( views );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, views );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// get all bounding boxes and two extra ones
		this.allBoxes = BoundingBoxTools.getAllBoundingBoxes( spimData, views, true );

		// average anisotropy of input views
		this.avgAnisoF = TransformationTools.getAverageAnisotropyFactor( spimData, views );
	}

	@Override
	public SpimData2 getSpimData() { return spimData; }

	@Override
	public List< ViewId > getViews() { return views; }

	public Interval getBoundingBox() { return allBoxes.get( boundingBox ); }
	public void setBoundingBox( final Interval bb ) { this.allBoxes.set( boundingBox, new BoundingBox( bb ) ); }

	@Override
	public Interval getDownsampledBoundingBox()
	{
		if ( !Double.isNaN( downsampling ) )
			return TransformVirtual.scaleBoundingBox( getBoundingBox(), 1.0 / downsampling );
		else
			return getBoundingBox();
	}
	public int getInterpolation() { return interpolation; }

	@Override
	public int getPixelType() { return pixelType; }

	public int getCacheType() { return cacheType; }

	public NonRigidParametersGUI getNonRigidParameters() { return nrgui; }

	@Override
	public double getDownsampling(){ return downsampling; }

	public boolean useBlending() { return useBlending; }

	public boolean useContentBased() { return useContentBased; }

	public boolean adjustIntensities() { return adjustIntensities; }

	@Override
	public double getAnisotropyFactor() { return avgAnisoF; }

	@Override
	public int getSplittingType() { return splittingType; }

	@Override
	public ImgExportGui getNewExporterInstance() { return staticImgExportAlgorithms.get( imgExport ).newInstance(); }

	public boolean queryDetails()
	{
		final boolean enableNonRigid = NonRigidParametersGUI.enableNonRigid;
		final Choice boundingBoxChoice, pixelTypeChoice, cachingChoice, nonrigidChoice, splitChoice;
		final TextField downsampleField;
		final Checkbox contentbasedCheckbox, anisoCheckbox;

		final String[] choices = FusionGUI.getBoundingBoxChoices( allBoxes );
		final String[] choicesForMacro = FusionGUI.getBoundingBoxChoices( allBoxes, false );

		if ( defaultBB >= choices.length )
			defaultBB = 0;

		final boolean hasIntensityAdjustments = IntensityAdjustmentTools.containsAdjustments( spimData.getIntensityAdjustments(), views );

		final GenericDialog gd = new GenericDialog( "Image Fusion" );
		Label label1 = null, label2 = null;

		// use macro-compatible choice names in headless mode
		if ( !PluginHelper.isHeadless() )
			gd.addChoice( "Bounding_Box", choices, choices[ defaultBB ] );
		else
			gd.addChoice( "Bounding_Box", choicesForMacro, choicesForMacro[ defaultBB ] );
		boundingBoxChoice = PluginHelper.isHeadless() ? null : (Choice)gd.getChoices().lastElement();

		gd.addSlider( "Downsampling", 1.0, 16.0, defaultDownsampling );
		downsampleField = PluginHelper.isHeadless() ? null : (TextField)gd.getNumericFields().lastElement();

		gd.addChoice( "Pixel_type", pixelTypes, pixelTypes[ defaultPixelType ] );
		pixelTypeChoice = PluginHelper.isHeadless() ? null : (Choice)gd.getChoices().lastElement();

		gd.addChoice( "Interpolation", interpolationTypes, interpolationTypes[ defaultInterpolation ] );
		gd.addChoice( "Image ", FusionTools.imgDataTypeChoice, FusionTools.imgDataTypeChoice[ defaultCache ] );
		cachingChoice = PluginHelper.isHeadless() ? null : (Choice)gd.getChoices().lastElement();

		gd.addMessage( "We advise using VIRTUAL for saving at TIFF, and CACHED for saving as HDF5 if memory is low", GUIHelper.smallStatusFont, GUIHelper.neutral );

		this.nrgui = new NonRigidParametersGUI( spimData, views );
		if ( enableNonRigid )
		{
			this.nrgui.addQuery( gd );
			nonrigidChoice = PluginHelper.isHeadless() ? null : (Choice)gd.getChoices().lastElement();
		}
		else
		{
			this.nrgui.isActive = false;
			nonrigidChoice = null;
		}

		gd.addCheckbox( "Blend images smoothly", defaultUseBlending );
		gd.addCheckbox( "Use content based fusion (warning, huge memory requirements)", defaultUseContentBased );
		contentbasedCheckbox = PluginHelper.isHeadless() ? null : (Checkbox)gd.getCheckboxes().lastElement();

		if ( hasIntensityAdjustments )
			gd.addCheckbox( "Adjust_image_intensities (only use with 32-bit output)", defaultAdjustIntensities );

		if ( avgAnisoF > 1.01 ) // for numerical instabilities (computed upon instantiation)
		{
			gd.addCheckbox( "Preserve_original data anisotropy (shrink image " + TransformationTools.f.format( avgAnisoF ) + " times in z) ", defaultPreserveAnisotropy );
			anisoCheckbox = PluginHelper.isHeadless() ? null : (Checkbox)gd.getCheckboxes().lastElement();
			gd.addMessage(
					"WARNING: Enabling this means to 'shrink' the dataset in z the same way the input\n" +
					"images were scaled. Only use this if this is not a multiview dataset.", GUIHelper.smallStatusFont, GUIHelper.warning );
		}
		else
		{
			anisoCheckbox = null;
		}

		gd.addChoice( "Produce one fused image for", splittingTypes, splittingTypes[ defaultSplittingType ] );
		splitChoice = PluginHelper.isHeadless() ? null : (Choice)gd.getChoices().lastElement();

		gd.addChoice( "Fused_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );

		gd.addCheckbox( "Cluster Processing", defaultClusterProcessing );
		
		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label2 = (Label)gd.getMessage();

		if ( !PluginHelper.isHeadless() )
		{
			final ManageFusionDialogListeners m = new ManageFusionDialogListeners(
					gd,
					boundingBoxChoice,
					downsampleField,
					pixelTypeChoice,
					cachingChoice,
					nonrigidChoice,
					contentbasedCheckbox,
					anisoCheckbox,
					splitChoice,
					label1,
					label2,
					this );
	
			m.update();
		}

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		// Hacky: replace the bounding box choices with a macro-compatible version
		// i.e. choices that do not contain the dimensions (AxBxC px), which we do not know in advance
		if ( !PluginHelper.isHeadless() )
		{
			final Choice bboxChoice = (Choice) gd.getChoices().get( 0 );
			final int selectedBbox = bboxChoice.getSelectedIndex();
			for (int i = 0; i<choices.length; i++)
			{
				bboxChoice.remove( 0 );
				bboxChoice.addItem( choicesForMacro[i] );
			}
			bboxChoice.select( selectedBbox );
		}

		boundingBox = defaultBB = gd.getNextChoiceIndex();
		downsampling = defaultDownsampling = gd.getNextNumber();

		if ( downsampling == 1.0 )
			downsampling = Double.NaN;

		pixelType = defaultPixelType = gd.getNextChoiceIndex();
		interpolation = defaultInterpolation = gd.getNextChoiceIndex();
		cacheType = defaultCache = gd.getNextChoiceIndex();

		if ( enableNonRigid )
		{
			if ( !this.nrgui.parseQuery( gd, false ) )
				return false;
		}

		useBlending = defaultUseBlending = gd.getNextBoolean();
		useContentBased = defaultUseContentBased = gd.getNextBoolean();
		if ( hasIntensityAdjustments )
			adjustIntensities = defaultAdjustIntensities = gd.getNextBoolean();
		else
			adjustIntensities = false;
		if ( avgAnisoF > 1.01 )
			preserveAnisotropy = defaultPreserveAnisotropy = gd.getNextBoolean();
		else
			preserveAnisotropy = defaultPreserveAnisotropy = false;

		if ( !preserveAnisotropy )
			avgAnisoF = Double.NaN;

		splittingType = defaultSplittingType = gd.getNextChoiceIndex();
		imgExport = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		processOnCluster = defaultClusterProcessing = gd.getNextBoolean();
		
		if ( this.nrgui.isActive() && this.nrgui.userSelectedAdvancedParameters() )
			if ( !this.nrgui.advancedParameters() )
				return false;

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Selected Fusion Parameters: " );
		IOFunctions.println( "Downsampling: " + DownsampleTools.printDownsampling( getDownsampling() ) );
		IOFunctions.println( "BoundingBox: " + getBoundingBox() );
		IOFunctions.println( "DownsampledBoundingBox: " + getDownsampledBoundingBox() );
		IOFunctions.println( "PixelType: " + pixelTypes[ getPixelType() ] );
		IOFunctions.println( "Interpolation: " + interpolationTypes[ getInterpolation() ] );
		IOFunctions.println( "CacheType: " + FusionTools.imgDataTypeChoice[ getCacheType() ] );
		IOFunctions.println( "Blending: " + useBlending );
		IOFunctions.println( "Adjust intensities: " + adjustIntensities );
		IOFunctions.println( "Content-based: " + useContentBased );
		IOFunctions.println( "AnisotropyFactor: " + avgAnisoF );
		IOFunctions.println( "Split by: " + splittingTypes[ getSplittingType() ] );
		IOFunctions.println( "Image Export: " + imgExportDescriptions[ imgExport ] );
		IOFunctions.println( "ImgLoader.isVirtual(): " + isImgLoaderVirtual() );
		IOFunctions.println( "ImgLoader.isMultiResolution(): " + isMultiResolution() );
		IOFunctions.println( "Cluster processing: " + clusterProcessing() );
		
		IOFunctions.println( "Non-Rigid active: " + this.nrgui.isActive() );
		if ( this.nrgui.isActive() )
		{
			IOFunctions.println( "Non-Rigid alpha: " + this.nrgui.getAlpha() );
			IOFunctions.println( "Non-Rigid cpd: " + this.nrgui.getControlPointDistance() );
			IOFunctions.println( "Non-Rigid showDistanceMap: " + this.nrgui.showDistanceMap() );
			IOFunctions.println( "Non-Rigid nonRigidAcrossTime: " + this.nrgui.nonRigidAcrossTime() );
			for ( final String label : this.nrgui.getLabels() )
				IOFunctions.println( "Non-Rigid Label: " + label );
		}

		return true;
	}

	public boolean isImgLoaderVirtual() { return isImgLoaderVirtual( spimData ); }
	
	public boolean clusterProcessing() { return processOnCluster; }

	public boolean isMultiResolution() { return isMultiResolution( spimData ); }

	public List< Group< ViewDescription > > getFusionGroups()
	{
		return getFusionGroups( getSpimData(), getViews(), getSplittingType() );
	}

}
