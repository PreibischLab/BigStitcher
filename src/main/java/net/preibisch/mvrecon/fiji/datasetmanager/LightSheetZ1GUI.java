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
package net.preibisch.mvrecon.fiji.datasetmanager;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;

import bdv.BigDataViewer;
import bdv.viewer.ViewerOptions;
import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.headless.definedataset.LightSheetZ1MetaData;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;

public class LightSheetZ1GUI extends LightSheetZ1 implements MultiViewDatasetDefinition {

	private boolean fixBioformats = false;
	
	public static String[] rotAxes = new String[] { "X-Axis", "Y-Axis", "Z-Axis" };

	public static String defaultFirstFile = "";
	public static boolean defaultModifyCal = false;
	public static boolean defaultRotAxis = false;
	public static boolean defaultApplyRotAxis = true;
	public static boolean defaultFixBioformats = false;
	
	@SuppressWarnings("deprecation")
	public static void main( String[] args )
	{
		//defaultFirstFile = "/Volumes/My Passport/worm7/Track1(3).czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/130706_Aiptasia8.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/abe_Arabidopsis1.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/multiview.czi";
		defaultFirstFile = "/Users/david/Desktop/_twotiles.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
		SpimData2 sd = new LightSheetZ1GUI().createDataset();
		
		BigDataViewer.open(  sd, "", null, ViewerOptions.options() );
	}
	
	@Override
	public LightSheetZ1GUI newInstance() {
		return new LightSheetZ1GUI();
	}
	
	@Override
	public SpimData2 createDataset()
	{
		final File cziFile = queryCZIFile();

		if ( cziFile == null )
			return null;

		final LightSheetZ1MetaData meta = new LightSheetZ1MetaData();

		if ( !meta.loadMetaData( cziFile ) )
		{
			IOFunctions.println( "Failed to analyze file." );
			return null;
		}

		if ( !showDialogs( meta ) )
			return null;

		final String directory = cziFile.getParent();
		final ImgFactory< ? extends NativeType< ? > > imgFactory = selectImgFactory( meta );

		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints timepoints = this.createTimePoints( meta );
		final ArrayList< ViewSetup > setups = this.createViewSetups( meta );
		final MissingViews missingViews = null;

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, missingViews );
		final ImgLoader imgLoader = new LightSheetZ1ImgLoader( cziFile, imgFactory, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( meta.calX(), meta.calY() ), meta.calZ() );

		IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );
		
		// create calibration + translation view registrations
		final ViewRegistrations viewRegistrations = DatasetCreationUtils.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );
		
		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults(), new IntensityAdjustments() );

		if ( meta.applyAxis() )
			TransformationTools.applyAxis( spimData );

		// TODO: Remove BIOFORMATS bug workaround
		if ( fixBioformats )
			LightSheetZ1MetaData.fixBioformats( spimData, cziFile, meta );

		return spimData;
	}

	@Override
	public String getTitle() { return "Zeiss Lightsheet Z.1 Dataset Loader (Bioformats)"; }

	@Override
	public String getExtendedDescription()
	{
		return "This datset definition supports files saved by the Zeiss Lightsheet Z.1\n" +
				"microscope. By default, one file per time-point is saved by Zen, which includes\n" +
				"all angles, channels and illumination directions. We support this format and\n" +
				"most other combinations that can be saved.\n" +
				"\n" +
				"Note: if you want to process multiple CZI datasets that are actually one experi-\n" +
				"ment (e.g. two channels individually acquired), please re-save them in Zen as\n" +
				"CZI files containing only one 3d stack per file and use the dataset definition\n" +
				"'3d Image Stacks (LOCI Bioformats)'";
	}


	
	
	protected boolean showDialogs( final LightSheetZ1MetaData meta )
	{
		GenericDialog gd = new GenericDialog( "Lightsheet Z.1 Properties" );

		gd.addMessage( "Angles (" + meta.numAngles() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int a = 0; a < meta.numAngles(); ++a )
			gd.addStringField( "Angle_" + (a+1) + ":", meta.angles()[ a ] );
		
		gd.addMessage( "Tiles (" + meta.numTiles() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int t = 0; t < meta.numTiles(); ++t )
			gd.addStringField( "Tile_" + (t+1) + ":", meta.tiles()[ t ] );

		gd.addMessage( "Channels (" + meta.numChannels() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int c = 0; c < meta.numChannels(); ++c )
			gd.addStringField( "Channel_" + (c+1) + ":", meta.channels()[ c ] );

		gd.addMessage( "Illumination Directions (" + meta.numIlluminations() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );

		for ( int i = 0; i < meta.numIlluminations(); ++i )
			gd.addStringField( "_______Illumination_" + (i+1) + ":", meta.illuminations()[ i ] );

		gd.addMessage( "Timepoints (" + meta.numTimepoints() + " present)", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );

		gd.addMessage( "Calibration", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addCheckbox( "Modify_calibration", defaultModifyCal );
		gd.addMessage( "Pixel Distance X: " + meta.calX() + " " + meta.calUnit() );
		gd.addMessage( "Pixel Distance Y: " + meta.calY() + " " + meta.calUnit() );
		gd.addMessage( "Pixel Distance Z: " + meta.calZ() + " " + meta.calUnit() );

		gd.addMessage( "Additional Meta Data", new Font( Font.SANS_SERIF, Font.BOLD, 13 ) );
		gd.addMessage( "" );
		gd.addCheckbox( "Modify_rotation_axis", defaultRotAxis );
		gd.addCheckbox( "Apply_rotation_to_dataset", defaultApplyRotAxis );

		// TODO: Remove BIOFORMATS bug workaround
		if ( meta.allImageSizesEqual() )
		{
			gd.addMessage( "WARNING: All image stacks have the same size, this could be the Bioformats bug.", new Font( Font.SANS_SERIF, Font.BOLD, 13 ), Color.red );
			defaultFixBioformats = true;
		}
		gd.addCheckbox( "Fix_Bioformats image stack size bug", defaultFixBioformats );

		gd.addMessage( "Acquisition Objective: " + meta.objective(), new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		gd.addMessage( "Rotation axis: " + meta.rotationAxisName() + " axis", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		gd.addMessage( (meta.lightsheetThickness() < 0 ? "" : "Lighsheet thickness: " + meta.lightsheetThickness() + " um"), new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		gd.addMessage( "Pixel type: " + meta.pixelTypeString() + " (" + meta.bytesPerPixel() + " byte per pixel)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );

		IOFunctions.println( "Dataset directory: " + new File( meta.files()[ 0 ] ).getParent() );
		IOFunctions.println( "Dataset files:" );

		for ( int i = 0; i < meta.files().length; ++i )
			IOFunctions.println( new File( meta.files()[ i ] ).getName() );

		IOFunctions.println( "Image sizes:" );
		for ( int a = 0; a < meta.numAngles(); ++a )
			IOFunctions.println( "Angle " + meta.angles()[ a ] + ": " + Util.printCoordinates( meta.imageSizes().get( a ) ) + " px." );

		GUIHelper.addScrollBars( gd );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		for ( int a = 0; a < meta.numAngles(); ++a )
			meta.angles()[ a ] = gd.getNextString();
		
		for ( int t = 0; t < meta.numTiles(); ++t )
			meta.tiles()[ t ] = gd.getNextString();

		for ( int c = 0; c < meta.numChannels(); ++c )
			meta.channels()[ c ] = gd.getNextString();

		for ( int i = 0; i < meta.numIlluminations(); ++i )
			meta.illuminations()[ i ] = gd.getNextString();

		final boolean modifyCal = defaultModifyCal = gd.getNextBoolean();
		final boolean modifyAxis = defaultRotAxis = gd.getNextBoolean();
		meta.setApplyAxis( defaultApplyRotAxis = gd.getNextBoolean() );

		// TODO: Remove BIOFORMATS bug workaround
		fixBioformats = defaultFixBioformats = gd.getNextBoolean();

		if ( modifyAxis || modifyCal )
		{
			gd = new GenericDialog( "Modify Meta Data" );

			if ( modifyCal )
			{
				gd.addNumericField( "Pixel_distance_x", meta.calX(), 5 );
				gd.addNumericField( "Pixel_distance_y", meta.calY(), 5 );
				gd.addNumericField( "Pixel_distance_z", meta.calZ(), 5 );
				gd.addStringField( "Pixel_unit", meta.calUnit() );
			}

			if ( modifyAxis )
			{
				if ( meta.rotationAxis() < 0 )
					meta.setRotationAxis( 0 );

				gd.addChoice( "Rotation_around", rotAxes, rotAxes[ meta.rotationAxis() ] );
			}

			gd.showDialog();

			if ( gd.wasCanceled() )
				return false;

			if ( modifyCal )
			{
				meta.setCalX( gd.getNextNumber() );
				meta.setCalY( gd.getNextNumber() );
				meta.setCalZ( gd.getNextNumber() );
				meta.setCalUnit( gd.getNextString() );
			}

			if ( modifyAxis )
				meta.setRotationAxis( gd.getNextChoiceIndex() );
		}

		return true;
	}

	protected File queryCZIFile()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Define Lightsheet Z.1 Dataset" );
	
		gd.addFileField( "First_CZI file of the dataset", defaultFirstFile, 50 );
	
		gd.showDialog();
	
		if ( gd.wasCanceled() )
			return null;
	
		final File firstFile = new File( defaultFirstFile = gd.getNextString() );
	
		if ( !firstFile.exists() )
		{
			IOFunctions.println( "File '" + firstFile.getAbsolutePath() + "' does not exist. Stopping" );
			return null;
		}
		else
		{
			IOFunctions.println( "Investigating file '" + firstFile.getAbsolutePath() + "'." );
			return firstFile;
		}
	}

}
