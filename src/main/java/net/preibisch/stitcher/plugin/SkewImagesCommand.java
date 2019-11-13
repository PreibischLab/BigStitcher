package net.preibisch.stitcher.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.stitcher.arrangement.SkewImages;

@Plugin(type = Command.class, menuPath = "Plugins>BigStitcher>Batch Processing>Tools>(De-)Skew Images")
public class SkewImagesCommand implements Command
{
	private static String[] axesChoice = new String[] {"X", "Y", "Z"};

	@Override
	public void run()
	{
		// load SpimData
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "to load a TileConfiguration for", false, false, false, false, false ) )
			return;
		final SpimData2 data = result.getData();
		// get views to process
		final ArrayList< ViewId > views = SpimData2.getAllViewIdsSorted( result.getData(),
				result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		// get dimensions for selected views
		final Map< ViewId, Dimensions > dims = new HashMap<>();
		views.forEach( v -> dims.put( v,
				data.getSequenceDescription().getViewDescriptions().get( v ).getViewSetup().getSize() ) );

		// query parameters in dialog
		GenericDialog gd = new GenericDialog( "(De)Skew Parameters" );
		gd.addChoice( "Skew_Direction", axesChoice, axesChoice[0] );
		gd.addChoice( "Skew_Along_Which_Axis", axesChoice, axesChoice[2] );
		gd.addSlider( "Angle", -90, 90, 45 );
		gd.showDialog();

		if (gd.wasCanceled())
			return;

		// get parameters from dialog
		final int direction = gd.getNextChoiceIndex();
		final int skewAxis = gd.getNextChoiceIndex();
		final double angle = gd.getNextNumber() / 180 * Math.PI;

		SkewImages.applySkewToData( data.getViewRegistrations(), dims, views, direction, skewAxis, angle );

		// save result
		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );
	}

}
