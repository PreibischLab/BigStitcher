package spim.fiji.plugin;


import ij.ImageJ;
import ij.plugin.PlugIn;
import input.FractalSpimDataGenerator;
import mpicbg.spim.io.IOFunctions;
import simulation.imgloader.SimulatedBeadsImgLoader2;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import gui.StitchingExplorer;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.XmlIoSpimData2;

public class Stitching_Explorer implements PlugIn
{
	boolean newDataset = false;
	boolean useFractal = false;
	boolean useSimulatedBeads = false;

	@Override
	public void run( String arg )
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();

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

		result.addButton( "Simulated Fractal Example", new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				result.setReturnFalse( true );
				result.getGenericDialog().dispose();
				setUseFractal();
			}
		});
		
		result.addButton( "Simulated Beads Example", new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				result.setReturnFalse( true );
				result.getGenericDialog().dispose();
				useSimulatedBeads = true;
			}
		});

		if ( !result.queryXML( "Stitching Explorer", "", false, false, false, false, false ) && !newDataset && !useFractal && !useSimulatedBeads )
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
		else if ( useFractal )
		{
			data = FractalSpimDataGenerator.createVirtualSpimData();
			xml = null;
			io = null;
		}
		
		else if (useSimulatedBeads)
		{
			data = SpimData2.convert( SimulatedBeadsImgLoader2.createSpimDataFromUserInput());
			xml = null;
			io = null;
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

	protected void setUseFractal()
	{
		this.useFractal = true;
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Stitching_Explorer().run( null );
	}
}
