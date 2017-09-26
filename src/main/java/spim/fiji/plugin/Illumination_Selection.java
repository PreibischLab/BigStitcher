package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.stream.Collectors;

import gui.popup.SelectIlluminationPopup;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class Illumination_Selection implements PlugIn
{

	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "for illumination Selection", false, false, false, false, false ) )
			return;

		final SpimData2 data = result.getData();
		ArrayList< ViewId > selectedViews = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		SpimData2 filteredSpimData = SelectIlluminationPopup.processIlluminationSelection( 
				data, 
				selectedViews.stream().map( vid -> data.getSequenceDescription().getViewDescription( vid ) ).collect( Collectors.toList() ),
				false,
				false,
				null );

		if (filteredSpimData != null)
		{
			SpimData2.saveXML( filteredSpimData, result.getXMLFileName(), result.getClusterExtension() );
		}

	}


	public static void main(String[] args)
	{
		new Illumination_Selection().run( "" );
	}

}
