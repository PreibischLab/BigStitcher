package spim.fiji.datasetmanager;


import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;

import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import bdv.BigDataViewer;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.fiji.plugin.Apply_Transformation;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.LightSheetZ1ImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;

public class LightSheetZ1 implements MultiViewDatasetDefinition
{
	public static String[] rotAxes = new String[] { "X-Axis", "Y-Axis", "Z-Axis" };

	public static String defaultFirstFile = "";
	public static boolean defaultModifyCal = false;
	public static boolean defaultRotAxis = false;
	public static boolean defaultApplyRotAxis = true;

	@Override
	public String getTitle() { return "Zeiss Lightsheet Z.1 Dataset (LOCI Bioformats)"; }

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
		
		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );
		
		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( new File( directory ), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		if ( meta.applyAxis() )
			Apply_Transformation.applyAxis( spimData );

		return spimData;
	}
	
	/**
	 * Assembles the {@link ViewRegistration} object consisting of a list of {@link ViewRegistration}s for all {@link ViewDescription}s that are present
	 * 
	 * @param viewDescriptionList
	 * @param minResolution - the smallest resolution in any dimension (distance between two pixels in the output image will be that wide)
	 * @return
	 */
	protected static ViewRegistrations createViewRegistrations( final Map< ViewId, ViewDescription > viewDescriptionList, final double minResolution )
	{
		final HashMap< ViewId, ViewRegistration > viewRegistrationList = new HashMap< ViewId, ViewRegistration >();
		
		for ( final ViewDescription viewDescription : viewDescriptionList.values() )
			if ( viewDescription.isPresent() )
			{
				final ViewRegistration viewRegistration = new ViewRegistration( viewDescription.getTimePointId(), viewDescription.getViewSetupId() );
				
				final VoxelDimensions voxelSize = viewDescription.getViewSetup().getVoxelSize(); 

				final double calX = voxelSize.dimension( 0 ) / minResolution;
				final double calY = voxelSize.dimension( 1 ) / minResolution;
				final double calZ = voxelSize.dimension( 2 ) / minResolution;
				
				final AffineTransform3D m = new AffineTransform3D();
				m.set( calX, 0.0f, 0.0f, 0.0f, 
					   0.0f, calY, 0.0f, 0.0f,
					   0.0f, 0.0f, calZ, 0.0f );
				final ViewTransform vt = new ViewTransformAffine( "calibration", m );
				viewRegistration.preconcatenateTransform( vt );
				
				final Tile tile = viewDescription.getViewSetup().getAttribute( Tile.class );

				if (tile.hasLocation()){
					final double shiftX = tile.getLocation()[0] / voxelSize.dimension( 0 );
					final double shiftY = tile.getLocation()[1] / voxelSize.dimension( 1 );
					final double shiftZ = tile.getLocation()[2] / voxelSize.dimension( 2 );
					
					final AffineTransform3D m2 = new AffineTransform3D();
					m2.set( 1.0f, 0.0f, 0.0f, shiftX, 
						   0.0f, 1.0f, 0.0f, shiftY,
						   0.0f, 0.0f, 1.0f, shiftZ );
					final ViewTransform vt2 = new ViewTransformAffine( "Translation", m2 );
					viewRegistration.concatenateTransform( vt2 );
				}
				
				viewRegistrationList.put( viewRegistration, viewRegistration );
			}
		
		return new ViewRegistrations( viewRegistrationList );
	}

	/**
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups( final LightSheetZ1MetaData meta )
	{
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		for ( int c = 0; c < meta.numChannels(); ++c )
			channels.add( new Channel( c, meta.channels()[ c ] ) );
		
		final ArrayList< Tile > tiles = new ArrayList<>();
		for (int i = 0; i < meta.numTiles(); i++)
		{
			Tile t = new Tile( i, meta.tiles()[i], meta.tileLocations().get(i) );
			tiles.add( t );
		}
		

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		for ( int i = 0; i < meta.numIlluminations(); ++i )
			illuminations.add( new Illumination( i, meta.illuminations()[ i ] ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		for ( int a = 0; a < meta.numAngles(); ++a )
		{
			final Angle angle = new Angle( a, meta.angles()[ a ] );
			
			try
			{
				final double degrees = Double.parseDouble( meta.angles()[ a ] );
				double[] axis = null;

				if ( meta.rotationAxis() == 0 )
					axis = new double[]{ 1, 0, 0 };
				else if ( meta.rotationAxis() == 1 )
					axis = new double[]{ 0, 1, 0 };
				else if ( meta.rotationAxis() == 2 )
					axis = new double[]{ 0, 0, 1 };

				if ( axis != null && !Double.isNaN( degrees ) &&  !Double.isInfinite( degrees ) )
					angle.setRotation( axis, degrees );
			}
			catch ( Exception e ) {};

			angles.add( angle );
		}

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( int it = 0; it < tiles.size(); ++it )
					{
						final Tile t = tiles.get( it );
						final VoxelDimensions voxelSize = new FinalVoxelDimensions( meta.calUnit(), meta.calX(), meta.calY(), meta.calZ() );
						final Dimensions dim = new FinalDimensions( meta.imageSizes().get( t.getId() ) );
						final Angle theAngle = angles.get( meta.getAngleMap().get( it ));
						viewSetups.add( new ViewSetup( viewSetups.size(), null, dim, voxelSize, t, c, theAngle, i ) );
					}
	
				

		return viewSetups;
	}

	/**
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints( final LightSheetZ1MetaData meta )
	{
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.numTimepoints(); ++t )
			timepoints.add( new TimePoint( t ) );

		return new TimePoints( timepoints );
	}

	protected ImgFactory< ? extends NativeType< ? > > selectImgFactory( final LightSheetZ1MetaData meta )
	{
		long maxNumPixels = 0;

		for ( int a = 0; a < meta.numAngles(); ++a )
		{
			final int[] dim = meta.imageSizes().get( a );

			long n = 1;

			for ( int d = 0; d < dim.length; ++d )
				n *= (long)dim[ d ];

			maxNumPixels = Math.max( n, maxNumPixels );
		}

		int smallerLog2 = (int)Math.ceil( Math.log( maxNumPixels ) / Math.log( 2 ) );

		String s = "Maximum number of pixels in any view: n=" + maxNumPixels + 
				" (2^" + (smallerLog2-1) + " < n < 2^" + smallerLog2 + " px), ";

		if ( smallerLog2 <= 31 )
		{
			IOFunctions.println( s + "using ArrayImg." );
			return new ArrayImgFactory< FloatType >();
		}
		else
		{
			IOFunctions.println( s + "using CellImg(256)." );
			return new CellImgFactory< FloatType >( 256 );
		}
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

	@Override
	public LightSheetZ1 newInstance() { return new LightSheetZ1(); }

	public static void main( String[] args )
	{
		//defaultFirstFile = "/Volumes/My Passport/worm7/Track1(3).czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/130706_Aiptasia8.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/abe_Arabidopsis1.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/multiview.czi";
		defaultFirstFile = "/Users/david/Desktop/_twotiles.czi";
		//defaultFirstFile = "/Volumes/My Passport/Zeiss Olaf Lightsheet Z.1/worm7/Track1.czi";
		SpimData2 sd = new LightSheetZ1().createDataset();
		
		new BigDataViewer( sd, "", null );
	}
}
