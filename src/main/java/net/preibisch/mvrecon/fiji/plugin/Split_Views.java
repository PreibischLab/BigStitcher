package net.preibisch.mvrecon.fiji.plugin;

import java.awt.Color;
import java.util.HashMap;

import fiji.util.gui.GenericDialogPlus;
import ij.ImageJ;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import net.preibisch.mvrecon.process.splitting.SplittingTools;

public class Split_Views implements PlugIn
{
	public static int defaultImgX = 600;
	public static int defaultImgY = 600;
	public static int defaultImgZ = 200;

	public static int defaultOverlapX = 60;
	public static int defaultOverlapY = 60;
	public static int defaultOverlapZ = 20;

	public static String defaultPath = null;

	public static int defaultChoice = 0;
	private static final String[] resultChoice = new String[] { "Display", "Save & Close" };

	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "splitting/subdiving of views", false, false, false, false, false ) )
			return;

		final SpimData2 data = xml.getData();

		split( data, xml.getXMLFileName() );
	}

	public static boolean split(
			final SpimData2 data,
			final String saveAs,
			final int sx,
			final int sy,
			final int sz,
			final int ox,
			final int oy,
			final int oz,
			final boolean display )
	{
		final SpimData2 newSD = SplittingTools.splitImages( data, new long[] { ox, oy, oz }, new long[] { sx, sy, sz } );

		if ( display )
		{
			final ViewSetupExplorer< SpimData2, XmlIoSpimData2 > explorer = new ViewSetupExplorer<SpimData2, XmlIoSpimData2 >( newSD, saveAs, new XmlIoSpimData2( "" ) );
			explorer.getFrame().toFront();
		}
		else
		{
			SpimData2.saveXML( data, saveAs, "" );
		}

		return true;
	}

	public static boolean split( final SpimData2 data, final String fileName )
	{
		final Pair< HashMap< String, Integer >, long[] > imgSizes = collectImageSizes( data );

		IOFunctions.println( "Current image sizes of dataset :");

		for ( final String size : imgSizes.getA().keySet() )
			IOFunctions.println( imgSizes.getA().get( size ) + "x: " + size );

		final GenericDialogPlus gd = new GenericDialogPlus( "Dataset splitting/subdividing" );

		gd.addSlider( "New_Image_Size_X", 100, 2000, defaultImgX );
		gd.addSlider( "New_Image_Size_Y", 100, 2000, defaultImgY );
		gd.addSlider( "New_Image_Size_Z", 100, 2000, defaultImgZ );

		gd.addMessage( "" );

		gd.addSlider( "Overlap_X", 10, 200, defaultOverlapX );
		gd.addSlider( "Overlap_Y", 10, 200, defaultOverlapY );
		gd.addSlider( "Overlap_Z", 10, 200, defaultOverlapZ );

		gd.addMessage( "Minimal image sizes per dimension: " + Util.printCoordinates( imgSizes.getB() ), GUIHelper.mediumstatusfont, Color.DARK_GRAY );

		gd.addMessage( "" );

		IOFunctions.println( fileName );
		if ( defaultPath == null || defaultPath.trim().length() == 0 )
		{
			final int index = fileName.indexOf( ".xml");
			if ( index > 0 )
				defaultPath = fileName.substring( 0, index ) + ".split.xml";
			else
				defaultPath = fileName + ".split.xml";
		}

		gd.addFileField("New_XML_File", defaultPath);
		gd.addChoice( "Split_Result", resultChoice, resultChoice[ defaultChoice ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final int sx = defaultImgX = (int)Math.round( gd.getNextNumber() );
		final int sy = defaultImgY = (int)Math.round( gd.getNextNumber() );
		final int sz = defaultImgZ = (int)Math.round( gd.getNextNumber() );

		final int ox = defaultOverlapX = (int)Math.round( gd.getNextNumber() );
		final int oy = defaultOverlapY = (int)Math.round( gd.getNextNumber() );
		final int oz = defaultOverlapZ = (int)Math.round( gd.getNextNumber() );

		final String saveAs = defaultPath = gd.getNextString();
		final int choice = defaultChoice = gd.getNextChoiceIndex();

		return split( data, saveAs, sx, sy, sz, ox, oy, oz, choice == 0 );
	}

	public static Pair< HashMap< String, Integer >, long[] > collectImageSizes( final AbstractSpimData< ? > data )
	{
		final HashMap< String, Integer > sizes = new HashMap<>();

		long[] minSize = null;

		for ( final BasicViewSetup vs : data.getSequenceDescription().getViewSetupsOrdered() )
		{
			final Dimensions dim = vs.getSize();

			String size = Long.toString( dim.dimension( 0 ) );
			for ( int d = 1; d < dim.numDimensions(); ++d )
				size += "x" + dim.dimension( d );

			if ( sizes.containsKey( size ) )
				sizes.put( size, sizes.get( size ) + 1 );
			else
				sizes.put( size, 1 );

			if ( minSize == null )
			{
				minSize = new long[ dim.numDimensions() ];
				dim.dimensions( minSize );
			}
			else
			{
				for ( int d = 0; d < dim.numDimensions(); ++d )
					minSize[ d ] = Math.min( minSize[ d ], dim.dimension( d ) );
			}
		}

		return new ValuePair<HashMap<String,Integer>, long[]>( sizes, minSize );
	}

	public static void main( String[] args )
	{
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/steffi/Desktop/HisYFP-SPIM/dataset.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml";//"/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Split_Views().run( null );
	}
}
