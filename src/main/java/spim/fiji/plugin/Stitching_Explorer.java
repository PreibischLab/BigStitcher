package spim.fiji.plugin;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import gui.StitchingExplorer;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.ImgLib2Temp.Pair;
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
				result.setReturnFalse( true );
				result.getGenericDialog().dispose();
				setDefineNewDataset();
			}
		});

		if ( !result.queryXML( "Stitching Explorer", "", false, false, false, false, false ) && !newDataset )
			return;

		final SpimData2 data;
		final String xml;
		final XmlIoSpimData2 io;

		if ( newDataset )
		{
			final Pair< SpimData2, String > dataset = new Define_Multi_View_Dataset().defineDataset( true );

			if ( dataset == null )
				return;

			data = dataset.getA();
			xml = dataset.getB();
			io = new XmlIoSpimData2( "" );
		}
		else
		{
			data = result.getData();
			xml = result.getXMLFileName();
			io = result.getIO();
		}

		final StitchingExplorer< SpimData2, XmlIoSpimData2 > explorer =
				new StitchingExplorer< SpimData2, XmlIoSpimData2 >( data, xml, io );

		explorer.getFrame().toFront();
	}

	protected void setDefineNewDataset()
	{
		this.newDataset = true;
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( System.getProperty("os.name").toLowerCase().contains( "win" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "C:/Users/preibisch/Downloads/StageVIIprimordium/dataset.xml";
		else if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Grants and CV/BIMSB/Projects/Big Data Sticher/Dros_converted/dataset.xml";

		new Stitching_Explorer().run( null );
	}
}
