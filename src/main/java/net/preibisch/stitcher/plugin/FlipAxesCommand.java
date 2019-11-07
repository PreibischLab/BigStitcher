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
import net.preibisch.stitcher.arrangement.FlipAxes;

@Plugin(type = Command.class, menuPath = "Plugins>BigStitcher>Batch Processing>Tools>Flip Axes")
public class FlipAxesCommand implements Command
{

	@Override
	public void run()
	{
		// load SpimData
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "to load a TileConfiguration for", false, false, false, false, false ) )
			return;
		final SpimData2 data = result.getData();
		final ArrayList< ViewId > views = SpimData2.getAllViewIdsSorted( result.getData(),
				result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final Map< ViewId, Dimensions > dims = new HashMap<>();
		views.forEach( v -> dims.put( v,
				data.getSequenceDescription().getViewDescriptions().get( v ).getViewSetup().getSize() ) );
		final boolean[] flipAxes = new boolean[3];
		GenericDialog gd = new GenericDialog( "Flip Parameters" );
		gd.addCheckbox( "Flip_X", true );
		gd.addCheckbox( "Flip_Y", false );
		gd.addCheckbox( "Flip_Z", false );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return;

		flipAxes[0] = gd.getNextBoolean();
		flipAxes[1] = gd.getNextBoolean();
		flipAxes[2] = gd.getNextBoolean();

		FlipAxes.applyFlipToData( data.getViewRegistrations(), dims, views, flipAxes );

		// save result
		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );

	}

}
