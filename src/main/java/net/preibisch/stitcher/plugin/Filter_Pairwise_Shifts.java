package net.preibisch.stitcher.plugin;

import ij.plugin.PlugIn;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.stitcher.gui.popup.SimpleRemoveLinkPopup;

public class Filter_Pairwise_Shifts implements PlugIn
{

	@Override
	public void run(String arg)
	{
		// TODO: should we ask for grouping and then apply filter only to subset?
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "for link filtering", false, false, false, false, false ) )
			return;
		final SpimData2 data = result.getData();

		SimpleRemoveLinkPopup.filterPairwiseShifts( data, false, null );
		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );
	}

	public static void main(String[] args)
	{
		new Filter_Pairwise_Shifts().run("");
	}

}
