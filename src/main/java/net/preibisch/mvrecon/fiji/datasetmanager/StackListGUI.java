package net.preibisch.mvrecon.fiji.datasetmanager;

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.IntegerPattern;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Dimensions;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.NamePattern;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public abstract class StackListGUI extends StackList implements MultiViewDatasetDefinition{
	
	protected String[] dimensionChoiceTimePointsTrue = new String[] { "NO (one time-point)", "YES (one file per time-point)", "YES (all time-points in one file)" }; 
	protected String[] dimensionChoiceTimePointsFalse = new String[] { dimensionChoiceTimePointsTrue[ 0 ], dimensionChoiceTimePointsTrue[ 1 ] }; 

	protected String[] dimensionChoiceChannelsTrue = new String[] { "NO (one channel)", "YES (one file per channel)", "YES (all channels in one file)" }; 
	protected String[] dimensionChoiceChannelsFalse = new String[] { dimensionChoiceChannelsTrue[ 0 ], dimensionChoiceChannelsTrue[ 1 ] }; 

	protected String[] dimensionChoiceIlluminationsTrue = new String[] { "NO (one illumination direction)", "YES (one file per illumination direction)", "YES (all illumination directions in one file)" }; 
	protected String[] dimensionChoiceIlluminationsFalse = new String[] { dimensionChoiceIlluminationsTrue[ 0 ], dimensionChoiceIlluminationsTrue[ 1 ] }; 

	protected String[] dimensionChoiceAnglesTrue = new String[] { "NO (one angle)", "YES (one file per angle)", "YES (all angles in one file)" }; 
	protected String[] dimensionChoiceAnglesFalse = new String[] { dimensionChoiceAnglesTrue[ 0 ], dimensionChoiceAnglesTrue[ 1 ] }; 
	
	protected String[] dimensionChoiceTilesTrue = new String[] { "NO (one tile)", "YES (one file per tile)", "YES (all tiles in one file)" }; 
	protected String[] dimensionChoiceTilesFalse = new String[] { dimensionChoiceTilesTrue[ 0 ], dimensionChoiceTilesTrue[ 1 ] }; 

	
	protected String[] calibrationChoice1 = new String[]{
			"Same voxel-size for all views",
			"Different voxel-sizes for each view" };
	protected String[] calibrationChoice2 = new String[]{
			"Load voxel-size(s) from file(s)",
			"Load voxel-size(s) from file(s) and display for verification",
			"User define voxel-size(s)" };
	public static String[] imglib2Container = new String[]{ "ArrayImg (faster)", "CellImg (slower, larger files supported)" };

	@Override
	public SpimData2 createDataset()
	{
		// collect all the information
		if ( !queryInformation() )
			return null;
		
		// load locations if we can
		if (canLoadTileLocationFromMeta())
			if( !loadAllTileLocations() )
				return null;
		
		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints timepoints = this.createTimePoints();
		final ArrayList< ViewSetup > setups = this.createViewSetups();
		final MissingViews missingViews = this.createMissingViews();
		
		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription = new SequenceDescription( timepoints, setups, null, missingViews );
		final ImgLoader imgLoader = createAndInitImgLoader( ".", new File( directory ), imgFactory, sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );
		
		// FIXME: this is probably very inefficenient
		for (TimePoint tp : timepoints.getTimePointsOrdered())
			for (ViewSetup setup : setups)
			{
				Dimensions siz = imgLoader.getSetupImgLoader( setup.getId() ).getImageSize( tp.getId() );
				setup.setSize( siz );
			}

		// get the minimal resolution of all calibrations
		final double minResolution = DatasetCreationUtils.minResolution(
				sequenceDescription,
				sequenceDescription.getViewDescriptions().values() );

		IOFunctions.println( "Minimal resolution in all dimensions over all views is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );

		// create calibration + translation view registrations
		final ViewRegistrations viewRegistrations = DatasetCreationUtils.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );
		
		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults(), new IntensityAdjustments() );

		return spimData;
	}
	
	protected boolean queryInformation()
	{		
		try 
		{
			if ( !queryGeneralInformation() )
				return false;

			if ( defaultFileNamePattern == null )
				defaultFileNamePattern = assembleDefaultPattern();

			if ( !queryNames() )
				return false;

			if ( showDebugFileNames && !debugShowFiles() )
				return false;

			if ( ( calibration1 == 0 && calibration2 < 2 && !loadFirstCalibration() ) || ( calibration1 == 1  && calibration2 < 2 && !loadAllCalibrations() ) )
				return false;

			if ( !queryDetails() )
				return false;
		} 
		catch ( ParseException e )
		{
			IOFunctions.println( e.toString() );
			return false;
		}

		return true;
	}
	

	protected boolean queryDetails()
	{
		final GenericDialog gd;

		if ( calibration2 == 0 )
			gd = null;
		else
			gd = new GenericDialog( "Define dataset (3/3)" );

		if ( calibration1 == 0 ) // same voxel-size for all views
		{
			Calibration cal = null;
			
			if ( calibrations.values().size() != 1 )
				cal = new Calibration();
			else
				for ( final Calibration c : calibrations.values() )
					cal = c;

			if ( calibration2 > 0 ) // user define or verify the values
			{
				gd.addMessage( "Calibration (voxel size)", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );
				if ( calibration2 == 1 )
					gd.addMessage( "(read from file)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
				gd.addMessage( "" );
				
				gd.addNumericField( "Pixel_distance_x", cal.calX, 5 );
				gd.addNumericField( "Pixel_distance_y", cal.calY, 5 );
				gd.addNumericField( "Pixel_distance_z", cal.calZ, 5 );
				gd.addStringField( "Pixel_unit", cal.calUnit );
			
				gd.showDialog();
		
				if ( gd.wasCanceled() )
					return false;
	
				cal.calX = gd.getNextNumber();
				cal.calY = gd.getNextNumber();
				cal.calZ = gd.getNextNumber();
				
				cal.calUnit = gd.getNextString();
			}
			else
			{
				IOFunctions.println( "Calibration (voxel-size) read from file: x:" + cal.calX + " y:" + cal.calY + " z:" + cal.calZ + " " + cal.calUnit );
			}

			// same calibrations for all views
			calibrations.clear();			
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int t = 0; t < tileNameList.size(); ++t )
							calibrations.put( new ViewSetupPrecursor( c, i, a, t ),  cal );
		}
		else // different voxel-size for all views
		{
			if ( calibration2 > 0 ) // user define or verify the values
			{
				gd.addMessage( "Calibrations", new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );
				if ( calibration2 == 1 )
					gd.addMessage( "(read from file)", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
				gd.addMessage( "" );
			}

			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int t = 0; t < tileNameList.size(); ++t )
						{
							ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a, t );
							Calibration cal = calibrations.get( vsp );
	
							if ( cal == null )
							{
								if ( calibration2 < 2 ) // load from file
								{
									IOFunctions.println( "Could not read calibration for view: " + vsp );
									IOFunctions.println( "Replacing with uniform calibration." );
								}
							
								cal = new Calibration();
								calibrations.put( vsp, cal );
							}
	
							if ( calibration2 > 0 ) // user define or verify the values
							{
								gd.addMessage( "View [" + vsp + "]" );
	
								gd.addNumericField( "Pixel_distance_x", cal.calX, 5 );
								gd.addNumericField( "Pixel_distance_y", cal.calY, 5 );
								gd.addNumericField( "Pixel_distance_z", cal.calZ, 5 );
								gd.addStringField( "Pixel_unit", cal.calUnit );
		
								gd.addMessage( "" );
							}
							else
							{
								IOFunctions.println( "Calibration (voxel-size) read from file x:" + cal.calX + " y:" + cal.calY + " z:" + cal.calZ + " " + cal.calUnit + " for view " + vsp );
							}
						}

			if ( calibration2 > 0 ) // user define or verify the values
			{
				GUIHelper.addScrollBars( gd );
	
				gd.showDialog();
	
				if ( gd.wasCanceled() )
					return false;

				for ( int c = 0; c < channelNameList.size(); ++c )
					for ( int i = 0; i < illuminationsNameList.size(); ++i )
						for ( int a = 0; a < angleNameList.size(); ++a )
							for ( int t = 0; t < tileNameList.size(); ++t )
							{
								final ViewSetupPrecursor vsp = new ViewSetupPrecursor( c, i, a, t );
								final Calibration cal = calibrations.get( vsp );
								
								cal.calX = gd.getNextNumber();
								cal.calY = gd.getNextNumber();
								cal.calZ = gd.getNextNumber();
								
								cal.calUnit = gd.getNextString();
							}
			}
		}
		
		return true;
	}
	
	
	protected boolean debugShowFiles()
	{
		final GenericDialog gd = new GenericDialog( "3d image stacks files" );

		gd.addMessage( "" );
		gd.addMessage( "Path: " + directory + "   " );
		gd.addMessage( "Note: Not selected files will be treated as missing views (e.g. missing files).", GUIHelper.smallStatusFont );

		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int ti = 0; ti < tileNameList.size(); ++ti )
						{
							String fileName = getFileNameFor( t, c, i, a, ti );
							
							final boolean fileExisits = new File( directory, fileName ).exists();
							
							String ext = "";
							
							if ( hasMultipleChannels > 0 && numDigitsChannels == 0 )
								ext +=  "c = " + channelNameList.get( c );
	
							if ( hasMultipleTimePoints > 0 && numDigitsTimepoints == 0 )
								if ( ext.length() > 0 )
									ext += ", t = " + timepointNameList.get( t );
								else
									ext += "t = " + timepointNameList.get( t );
	
							if ( hasMultipleIlluminations > 0 && numDigitsIlluminations == 0 )
								if ( ext.length() > 0 )
									ext += ", i = " + illuminationsNameList.get( i );
								else
									ext += "i = " + illuminationsNameList.get( i );
	
							if ( hasMultipleAngles > 0 && numDigitsAngles == 0 )
								if ( ext.length() > 0 )
									ext += ", a = " + angleNameList.get( a );
								else
									ext += "a = " + angleNameList.get( a );
							
							
							if ( hasMultipleTiles > 0 && numDigitsTiles == 0 )
								if ( ext.length() > 0 )
									ext += ", x = " + tileNameList.get( ti );
								else
									ext += "x = " + tileNameList.get( ti );
	
							if ( ext.length() > 1 )
								fileName += "   >> [" + ext + "]";
	
							final boolean select;
	
							if ( fileExisits )
							{
								fileName += " (file found)";
								select = true;
							}
							else
							{
								select = false;
								fileName += " (file NOT found)";
							}
							
							gd.addCheckbox( fileName, select );
							
							// otherwise underscores are gone ...
							((Checkbox)gd.getCheckboxes().lastElement()).setLabel( fileName );
							if ( !fileExisits )
								((Checkbox)gd.getCheckboxes().lastElement()).setBackground( GUIHelper.error );
						}
				
		GUIHelper.addScrollBars( gd );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		exceptionIds = new ArrayList<int[]>();

		// collect exceptions to the definitions
		for ( int t = 0; t < timepointNameList.size(); ++t )
			for ( int c = 0; c < channelNameList.size(); ++c )
				for ( int i = 0; i < illuminationsNameList.size(); ++i )
					for ( int a = 0; a < angleNameList.size(); ++a )
						for ( int ti = 0; ti < tileNameList.size(); ++ti )
							if ( gd.getNextBoolean() == false )
							{
								// FIXME: handle Tiles here
								exceptionIds.add( new int[]{ t, c, i, a } );
								System.out.println( "adding missing views t:" + t + " c:" + c + " i:" + i + " a:" + a );
							}

		return true;
	}
	
	protected boolean queryNames() throws ParseException
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (2/3)" );
		
		gd.addDirectoryOrFileField( "Image_File_directory", defaultDirectory, 40 );
		gd.addStringField( "Image_File_Pattern", defaultFileNamePattern, 40 );

		if ( hasMultipleTimePoints > 0 )
			gd.addStringField( "Timepoints_", defaultTimepoints, 15 );
		
		if ( hasMultipleChannels > 0 )
			gd.addStringField( "Channels_", defaultChannels, 15 );

		if ( hasMultipleIlluminations > 0 )
			gd.addStringField( "Illumination_directions_", defaultIlluminations, 15 );
		
		if ( hasMultipleAngles > 0 )
			gd.addStringField( "Acquisition_angles_", defaultAngles, 15 );
		
		if ( hasMultipleTiles > 0 )
			gd.addStringField( "Tiles_", defaultTiles, 15 );

		gd.addChoice( "Calibration_Type", calibrationChoice1, calibrationChoice1[ defaultCalibration1 ] );
		gd.addChoice( "Calibration_Definition", calibrationChoice2, calibrationChoice2[ defaultCalibration2 ] );

		gd.addChoice( "ImgLib2_data_container", imglib2Container, imglib2Container[ defaultContainer ] );
		gd.addMessage(
				"Use ArrayImg if -ALL- input views are smaller than ~2048x2048x500 px (2^31 px), or if the\n" +
				"program throws an OutOfMemory exception while processing.  CellImg is slower, but more\n" +
				"memory efficient and supports much larger file sizes only limited by the RAM of the machine.", 
				new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		
		gd.addCheckbox( "Show_list of filenames (to debug and it allows to deselect individual files)", showDebugFileNames );
		gd.addMessage( "Note: this might take a few seconds if thousands of files are present", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ) );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		defaultDirectory = directory = gd.getNextString();
		defaultFileNamePattern = fileNamePattern = gd.getNextString();

		timepoints = channels = illuminations = angles = tiles = null;
		replaceTimepoints = replaceChannels = replaceIlluminations = replaceAngles = replaceTiles = null;
		
		// get the String patterns and verify that the corresponding pattern, 
		// e.g. {t} or {tt} exists in the pattern
		if ( hasMultipleTimePoints > 0 )
		{
			defaultTimepoints = timepoints = gd.getNextString();
			
			if ( hasMultipleTimePoints == 1 )
			{
				replaceTimepoints = IntegerPattern.getReplaceString( fileNamePattern, TIMEPOINT_PATTERN );
				
				if ( replaceTimepoints == null )
					throw new ParseException( "Pattern {" + TIMEPOINT_PATTERN + "} not present in " + fileNamePattern + 
							" although you indicated there would be several timepoints. Stopping.", 0 );					
				else
					numDigitsTimepoints = replaceTimepoints.length() - 2;
			}
			else 
			{
				replaceTimepoints = null;
				numDigitsTimepoints = 0;
			}
		}

		if ( hasMultipleChannels > 0 )
		{
			defaultChannels = channels = gd.getNextString();
			
			if ( hasMultipleChannels == 1 )
			{			
				replaceChannels = IntegerPattern.getReplaceString( fileNamePattern, CHANNEL_PATTERN );
				if ( replaceChannels == null )
						throw new ParseException( "Pattern {" + CHANNEL_PATTERN + "} not present in " + fileNamePattern + 
								" although you indicated there would be several channels. Stopping.", 0 );					
				else
					numDigitsChannels = replaceChannels.length() - 2;
			}
			else
			{
				replaceChannels = null;
				numDigitsChannels = 0;
			}
		}

		if ( hasMultipleIlluminations > 0 )
		{
			defaultIlluminations = illuminations = gd.getNextString();
			
			if ( hasMultipleIlluminations == 1 )
			{
				replaceIlluminations = IntegerPattern.getReplaceString( fileNamePattern, ILLUMINATION_PATTERN );
				
				if ( replaceIlluminations == null )
					throw new ParseException( "Pattern {" + ILLUMINATION_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several illumination directions. Stopping.", 0 );
				else
					numDigitsIlluminations = replaceIlluminations.length() - 2;
			}
			else
			{
				replaceIlluminations = null;
				numDigitsIlluminations = 0;
			}
		}

		if ( hasMultipleAngles > 0 )
		{
			defaultAngles = angles = gd.getNextString();
			
			if ( hasMultipleAngles == 1 )
			{
				replaceAngles = IntegerPattern.getReplaceString( fileNamePattern, ANGLE_PATTERN );

				if ( replaceAngles == null )
					throw new ParseException( "Pattern {" + ANGLE_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several angles.", 0 );
				else
					numDigitsAngles = replaceAngles.length() - 2;
			}
			else
			{
				replaceAngles = null;
				numDigitsAngles = 0;
			}
		}
		
		if ( hasMultipleTiles > 0 )
		{
			defaultTiles = tiles = gd.getNextString();
			
			if ( hasMultipleTiles == 1 )
			{
				replaceTiles = IntegerPattern.getReplaceString( fileNamePattern, TILE_PATTERN );

				if ( replaceTiles == null )
					throw new ParseException( "Pattern {" + TILE_PATTERN + "} not present in " + fileNamePattern + 
						" although you indicated there would be several tiles.", 0 );
				else
					numDigitsTiles = replaceTiles.length() - 2;
			}
			else
			{
				replaceTiles = null;
				numDigitsTiles = 0;
			}
		}

		// get the list of integers
		timepointNameList = ( NamePattern.parseNameString( timepoints, false ) );
		channelNameList = ( NamePattern.parseNameString( channels, true ) );
		illuminationsNameList = ( NamePattern.parseNameString( illuminations, true ) );
		angleNameList = ( NamePattern.parseNameString( angles, true ) );
		tileNameList = ( NamePattern.parseNameString( tiles, true ) );

		exceptionIds = new ArrayList< int[] >();

		defaultCalibration1 = calibration1 = gd.getNextChoiceIndex();
		defaultCalibration2 = calibration2 = gd.getNextChoiceIndex();

		defaultContainer = gd.getNextChoiceIndex();
		
		if ( defaultContainer == 0 )
			imgFactory = new ArrayImgFactory< FloatType >();
		else
			imgFactory = new CellImgFactory< FloatType >( 256 );
		
		showDebugFileNames = gd.getNextBoolean();
		
		return true;		
	}
	
	
	protected boolean queryGeneralInformation()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Define dataset (1/3)" );
		
		final Color green = new Color( 0, 139, 14 );
		final Color red = Color.RED;
		
		gd.addMessage( "File reader: " + getTitle(), new Font( Font.SANS_SERIF, Font.BOLD, 14 ) );

		gd.addMessage( "" );		

		if ( supportsMultipleTimepointsPerFile() )
		{
			if ( getDefaultMultipleTimepoints() >= dimensionChoiceTimePointsTrue.length )
				setDefaultMultipleTimepoints( 0 );

			gd.addMessage( "Supports multiple timepoints per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_timepoints", dimensionChoiceTimePointsTrue, dimensionChoiceTimePointsTrue[ getDefaultMultipleTimepoints() ] );
		}
		else
		{
			if ( getDefaultMultipleTimepoints() >= dimensionChoiceTimePointsFalse.length )
				setDefaultMultipleTimepoints( 0 );

			gd.addMessage( "NO support for multiple timepoints per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_timepoints", dimensionChoiceTimePointsFalse, dimensionChoiceTimePointsFalse[ getDefaultMultipleTimepoints() ] );
		}

		gd.addMessage( "" );

		if ( supportsMultipleChannelsPerFile() )
		{
			if ( getDefaultMultipleChannels() >= dimensionChoiceChannelsTrue.length )
				setDefaultMultipleChannels( 0 );

			gd.addMessage( "Supports multiple channels per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_channels", dimensionChoiceChannelsTrue, dimensionChoiceChannelsTrue[ getDefaultMultipleChannels() ] );
		}
		else
		{
			if ( getDefaultMultipleChannels() >= dimensionChoiceChannelsFalse.length )
				setDefaultMultipleChannels( 0 );

			gd.addMessage( "NO support for multiple channels per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_channels", dimensionChoiceChannelsFalse, dimensionChoiceChannelsFalse[ getDefaultMultipleChannels() ] );
		}

		gd.addMessage( "" );

		if ( supportsMultipleIlluminationsPerFile() )
		{
			if ( getDefaultMultipleIlluminations() >= dimensionChoiceIlluminationsTrue.length )
				setDefaultMultipleIlluminations( 0 );

			gd.addMessage( "Supports multiple illumination directions per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "_____Multiple_illumination_directions", dimensionChoiceIlluminationsTrue, dimensionChoiceIlluminationsTrue[ getDefaultMultipleIlluminations() ] );
		}
		else
		{
			if ( getDefaultMultipleIlluminations() >= dimensionChoiceIlluminationsFalse.length )
				setDefaultMultipleIlluminations( 0 );

			gd.addMessage( "NO support for multiple illumination directions per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "_____Multiple_illumination_directions", dimensionChoiceIlluminationsFalse, dimensionChoiceIlluminationsFalse[ getDefaultMultipleIlluminations() ] );
		}

		gd.addMessage( "" );

		if ( supportsMultipleAnglesPerFile() )
		{
			if ( getDefaultMultipleAngles() >= dimensionChoiceAnglesTrue.length )
				setDefaultMultipleAngles( 0 );

			gd.addMessage( "Supports multiple angles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_angles", dimensionChoiceAnglesTrue, dimensionChoiceAnglesTrue[ getDefaultMultipleAngles() ] );
		}
		else
		{
			if ( getDefaultMultipleAngles() >= dimensionChoiceAnglesFalse.length )
				setDefaultMultipleAngles( 0 );

			gd.addMessage( "NO support for multiple angles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_angles", dimensionChoiceAnglesFalse, dimensionChoiceAnglesFalse[ getDefaultMultipleAngles() ] );
		}
		
		
		gd.addMessage( "" );

		if ( supportsMultipleTilesPerFile() )
		{
			if ( getDefaultMultipleTiles() >= dimensionChoiceTilesTrue.length )
				setDefaultMultipleTiles( 0 );

			gd.addMessage( "Supports multiple tiles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), green );
			gd.addChoice( "Multiple_tiles", dimensionChoiceTilesTrue, dimensionChoiceTilesTrue[ getDefaultMultipleTiles() ] );
		}
		else
		{
			if ( getDefaultMultipleTiles() >= dimensionChoiceTilesFalse.length )
				setDefaultMultipleTiles( 0 );

			gd.addMessage( "NO support for multiple tiles per file", new Font( Font.SANS_SERIF, Font.ITALIC, 11 ), red );
			gd.addChoice( "Multiple_tiles", dimensionChoiceTilesFalse, dimensionChoiceTilesFalse[ getDefaultMultipleTiles() ] );
		}

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		hasMultipleTimePoints = gd.getNextChoiceIndex();
		hasMultipleChannels = gd.getNextChoiceIndex();
		hasMultipleIlluminations = gd.getNextChoiceIndex();
		hasMultipleAngles = gd.getNextChoiceIndex();
		hasMultipleTiles = gd.getNextChoiceIndex();

		setDefaultMultipleTimepoints( hasMultipleTimePoints );
		setDefaultMultipleChannels( hasMultipleChannels );
		setDefaultMultipleIlluminations( hasMultipleIlluminations );
		setDefaultMultipleAngles( hasMultipleAngles );
		setDefaultMultipleTiles( hasMultipleTiles );

		return true;
	}
	
}
