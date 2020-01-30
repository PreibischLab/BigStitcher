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
import net.preibisch.mvrecon.process.fusion.transformed.TransformVirtual;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class QualityGUI implements FusionExportInterface
{
	public static double defaultDownsampling = 8.0;
	public static int defaultBB = 0;

	public static boolean defaultUseRelativeFRC = true;
	public static boolean defaultSmoothLocalFRC = false;
	public static int defaultFFTSize = 512;
	public static int defaultFRCStepSize = 20;

	public static String[] splittingTypes = new String[]{
			"Each timepoint & channel",
			"Each timepoint, channel & illumination",
			"All views together",
			"Each view" };

	public static int defaultSplittingType = 0;
	public static boolean defaultPreserveAnisotropy = false;

	public final static ArrayList< ImgExportGui > staticImgExportAlgorithms = new ArrayList< ImgExportGui >();
	public final static String[] imgExportDescriptions;
	public static int defaultImgExportAlgorithm = 0;

	protected int boundingBox = defaultBB;
	protected int splittingType = defaultSplittingType;
	protected double downsampling = defaultDownsampling;
	protected boolean preserveAnisotropy = defaultPreserveAnisotropy;
	protected double avgAnisoF;
	protected boolean useRelativeFRC = defaultUseRelativeFRC;
	protected boolean useSmoothLocalFRC = defaultSmoothLocalFRC;
	protected int fftSize = defaultFFTSize;
	protected int frcStepSize = defaultFRCStepSize;
	protected int imgExport = defaultImgExportAlgorithm;

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

	public QualityGUI( final SpimData2 spimData, final List< ViewId > views )
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

	@Override
	public int getPixelType() { return 0; }

	@Override
	public double getDownsampling(){ return downsampling; }

	@Override
	public double getAnisotropyFactor() { return avgAnisoF; }

	public boolean getUseRelativeFRC() { return useRelativeFRC; }
	public boolean getUseSmoothLocalFRC() { return useSmoothLocalFRC; }
	public int getFFTSize() { return fftSize; }
	public int getFRCStepSize() { return frcStepSize; }

	@Override
	public int getSplittingType() { return splittingType; }

	@Override
	public ImgExportGui getNewExporterInstance() { return staticImgExportAlgorithms.get( imgExport ).newInstance(); }

	public boolean queryDetails()
	{
		final String[] choices = FusionGUI.getBoundingBoxChoices( allBoxes );
		final String[] choicesForMacro = FusionGUI.getBoundingBoxChoices( allBoxes, false );

		if ( defaultBB >= choices.length )
			defaultBB = 0;

		final GenericDialog gd = new GenericDialog( "Image Quality Estimation" );
		Label label1 = null, label2 = null;

		// use macro-compatible choice names in headless mode
		if ( !PluginHelper.isHeadless() )
			gd.addChoice( "Bounding_Box", choices, choices[ defaultBB ] );
		else
			gd.addChoice( "Bounding_Box", choicesForMacro, choicesForMacro[ defaultBB ] );

		gd.addMessage( "" );

		gd.addSlider( "Downsampling", 1.0, 16.0, defaultDownsampling );

		gd.addMessage( "" );

		if ( avgAnisoF > 1.01 ) // for numerical instabilities (computed upon instantiation)
		{
			gd.addCheckbox( "Preserve_original data anisotropy (shrink image " + TransformationTools.f.format( avgAnisoF ) + " times in z) ", defaultPreserveAnisotropy );
			gd.addMessage(
					"WARNING: Enabling this means to 'shrink' the dataset in z the same way the input\n" +
					"images were scaled. Only use this if this is not a multiview dataset.", GUIHelper.smallStatusFont, GUIHelper.warning );
		}

		gd.addMessage( "" );

		gd.addCheckbox( "Relative_FRC", defaultUseRelativeFRC );
		gd.addCheckbox( "Smooth_Local_FRC", defaultSmoothLocalFRC );
		gd.addNumericField( "FRC_FFT_Size", defaultFFTSize, 0 );
		gd.addNumericField( "FRC_Stepsize (z)", defaultFRCStepSize, 0 );
		gd.addMessage( "" );

		gd.addMessage( "" );

		gd.addChoice( "Produce one quality image for", splittingTypes, splittingTypes[ defaultSplittingType ] );
		gd.addChoice( "Quality_image", imgExportDescriptions, imgExportDescriptions[ defaultImgExportAlgorithm ] );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label2 = (Label)gd.getMessage();

		if ( !PluginHelper.isHeadless() )
		{
			final ManageQualityDialogListeners m = new ManageQualityDialogListeners(
					gd,
					(Choice)gd.getChoices().get( 0 ),
					(TextField)gd.getNumericFields().get( 0 ),
					avgAnisoF > 1.01 ? (Checkbox)gd.getCheckboxes().lastElement() : null,
					(Choice)gd.getChoices().get( 1 ),
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

		if ( avgAnisoF > 1.01 )
			preserveAnisotropy = defaultPreserveAnisotropy = gd.getNextBoolean();
		else
			preserveAnisotropy = defaultPreserveAnisotropy = false;

		if ( !preserveAnisotropy )
			avgAnisoF = Double.NaN;

		useRelativeFRC = defaultUseRelativeFRC = gd.getNextBoolean();
		useSmoothLocalFRC =defaultSmoothLocalFRC = gd.getNextBoolean();
		fftSize = defaultFFTSize = Math.max( 16, (int)Math.round( gd.getNextNumber() ) );
		frcStepSize = defaultFRCStepSize = Math.max( 1, (int)Math.round( gd.getNextNumber() ) );

		splittingType = defaultSplittingType = gd.getNextChoiceIndex();
		imgExport = defaultImgExportAlgorithm = gd.getNextChoiceIndex();

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Selected Quality Parameters: " );
		IOFunctions.println( "Downsampling: " + DownsampleTools.printDownsampling( getDownsampling() ) );
		IOFunctions.println( "BoundingBox: " + getBoundingBox() );
		IOFunctions.println( "DownsampledBoundingBox: " + getDownsampledBoundingBox() );
		IOFunctions.println( "AnisotropyFactor: " + avgAnisoF );
		IOFunctions.println( "Relative FRC: " + useRelativeFRC );
		IOFunctions.println( "Smooth Local FRC: " + useSmoothLocalFRC );
		IOFunctions.println( "FRC FFT Size: " + fftSize );
		IOFunctions.println( "FRC Step Size (z): " + frcStepSize );
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

}
