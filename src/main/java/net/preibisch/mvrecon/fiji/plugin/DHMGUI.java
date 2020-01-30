package net.preibisch.mvrecon.fiji.plugin;

import java.io.File;
import java.util.ArrayList;

import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.datasetmanager.DHM;
import net.preibisch.mvrecon.fiji.datasetmanager.DHMMetaData;
import net.preibisch.mvrecon.fiji.datasetmanager.DatasetCreationUtils;
import net.preibisch.mvrecon.fiji.datasetmanager.MultiViewDatasetDefinition;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.DHMImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.intensityadjust.IntensityAdjustments;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public class DHMGUI extends DHM implements MultiViewDatasetDefinition {
	
	@Override
	public String getTitle()
	{
		return "Holographic Imaging Dataset";
	}

	@Override
	public String getExtendedDescription()
	{
		return
			"This dataset definition supports data as created by a holographic microscope\n" +
			"(Amplitude & Phase stacks in 3d over time)";
	}

	@Override
	public SpimData2 createDataset()
	{
		final DHMMetaData meta = queryDirectoryAndRatio();

		if ( meta == null )
			return null;

		if ( !meta.loadMetaData() )
			return null;

		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints timepoints = this.createTimePoints( meta );
		final ArrayList< ViewSetup > setups = this.createViewSetups( meta );
		final MissingViews missingViews = null;

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription =
				new SequenceDescription( timepoints, setups, null, missingViews );
		final ImgLoader imgLoader =
				new DHMImgLoader(
						meta.getDir(),
						meta.getStackDir(),
						meta.getAmplitudeDir(),
						meta.getPhaseDir(),
						meta.getTimepoints(),
						meta.getZPlanes(),
						meta.getExt(),
						meta.getAmpChannelId(),
						meta.getPhaseChannelId(),
						sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( meta.calX, meta.calY ), meta.calZ );

		IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );
		
		// create calibration + translation view registrations
		final ViewRegistrations viewRegistrations = DatasetCreationUtils.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );
		
		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( meta.getDir(), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes(), new PointSpreadFunctions(), new StitchingResults(), new IntensityAdjustments() );

		return spimData;
	}
	
	protected DHMMetaData queryDirectoryAndRatio()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Specify Holographic Acquistion Directory" );

		gd.addDirectoryField( "Holographic_Acquisition main directory", defaultDir, 50 );

		gd.addMessage( "" );
		gd.addMessage( "Camera pixel size (e.g. 3.45um) / Magnification (e.g. 20):" );
		gd.addNumericField( "Pixel_distance_x", defaulCalX, 5 );
		gd.addNumericField( "Pixel_distance_y", defaulCalY, 5 );
		gd.addMessage( "Depth between planes (e.g. 0.5mm) / Magnification^2 (e.g. 20^2) * 1000 (mm to um):" );
		gd.addNumericField( "Pixel_distance_z", defaulCalZ, 5 );
		gd.addStringField( "Pixel_unit", defaulCalUnit );
		gd.addMessage( "" );
		gd.addCheckbox( "Open_all planes to ensure they have the same dimensions (takes time!)", defaultOpenAll );
		gd.showDialog();
	
		if ( gd.wasCanceled() )
			return null;

		return new DHMMetaData(
				new File( defaultDir = gd.getNextString() ),
				defaulCalX = gd.getNextNumber(),
				defaulCalY = gd.getNextNumber(),
				defaulCalZ = gd.getNextNumber(),
				defaulCalUnit = gd.getNextString(),
				defaultOpenAll = gd.getNextBoolean() );
	}

	@Override
	public DHMGUI newInstance()
	{
		return new DHMGUI();
	}

	public static void main( String[] args )
	{
		new DHMGUI().createDataset();
	}
}
