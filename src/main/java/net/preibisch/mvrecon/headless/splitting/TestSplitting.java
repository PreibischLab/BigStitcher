package net.preibisch.mvrecon.headless.splitting;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import net.preibisch.mvrecon.process.splitting.SplittingTools;

public class TestSplitting
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		SpimData2 spimData;

		// generate 4 views with 1000 corresponding beads, single timepoint
		//spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		/*
			<size>2048 2048 900</size>
			<size>0.40625 0.40625 0.8125</size>
		 */

		//final String file = "/Volumes/home/Data/Expansion Microscopy/dataset.xml";
		final String file = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml";

		final String fileOut = file.replace( ".xml", ".split.xml" );

		System.out.println( "in: " + file );
		System.out.println( "out: " + fileOut );

		// load drosophila
		spimData = new XmlIoSpimData2( "" ).load( file );

		//SpimData2 newSD = SplittingTools.splitImages( spimData, new long[] { 30, 30, 15 }, new long[] { 600, 600, 300 } );
		SpimData2 newSD = SplittingTools.splitImages( spimData, new long[] { 30, 30, 10 }, new long[] { 200, 200, 40 } );
		// drosophila with 1000 views

		final ViewSetupExplorer< SpimData2, XmlIoSpimData2 > explorer = new ViewSetupExplorer<SpimData2, XmlIoSpimData2 >( newSD, fileOut, new XmlIoSpimData2( "" ) );
		explorer.getFrame().toFront();
	}

}
