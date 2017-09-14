package spim.fiji.plugin;


import java.awt.Button;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import gui.StitchingExplorer;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;

public class Stitching_Explorer implements PlugIn
{
	boolean newDataset = false;

	@Override
	public void run( String arg )
	{
		final LoadParseQueryXML result = new EasterEggLoadParseQueryXML();

		result.addButton( "Define a new dataset", new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				((TextField)result.getGenericDialog().getStringFields().firstElement()).setText( "define" );
				Button ok = result.getGenericDialog().getButtons()[ 0 ];

				ActionEvent ae =  new ActionEvent( ok, ActionEvent.ACTION_PERFORMED, "");
				Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ae);
			}
		});

		if ( !result.queryXML( "Stitching Explorer", "", false, false, false, false, false ) && !newDataset )
			return;

		final SpimData2 data = result.getData();
		final String xml = result.getXMLFileName();
		final XmlIoSpimData2 io = result.getIO();;

		final StitchingExplorer< SpimData2, XmlIoSpimData2 > explorer =
				new StitchingExplorer< SpimData2, XmlIoSpimData2 >( data, xml, io );

		explorer.getFrame().toFront();
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( System.getProperty("os.name").toLowerCase().contains( "win" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "Z:\\Data\\Expansion Microscopy/dataset.xml";
		else if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Grants and CV/BIMSB/Projects/CLARITY/Big Data Sticher/Dros_converted/dataset.xml";

		new Stitching_Explorer().run( null );
	}
}
