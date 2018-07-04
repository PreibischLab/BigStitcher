package net.preibisch.stitcher.plugin;

import java.io.File;
import java.util.Map;

import fiji.util.gui.GenericDialogPlus;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.Translation3D;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.stitcher.gui.TileConfigurationHelpers;

public class Load_Tile_Configuration implements PlugIn
{
	public static String defaultTCFile = "";

	@Override
	public void run(String arg)
	{
		// load SpimData
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "to load a TileConfiguration for", false, false, false, false, false ) )
			return;
		final SpimData2 data = result.getData();

		// ask for parameters
		final GenericDialogPlus gd = new GenericDialogPlus( "" );
		gd.addFileField( "TileConfiguration file", defaultTCFile );
		gd.addCheckbox( "Use_pixel_units", true );
		gd.addCheckbox( "Keep_metadata_rotation", true );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		defaultTCFile = gd.getNextString();
		final boolean pixelUnits = gd.getNextBoolean();
		final boolean metadataRotate = gd.getNextBoolean();

		// apply
		final Map< ViewId, Translation3D > tcParsed = TileConfigurationHelpers.parseTileConfiguration( new File( defaultTCFile ) );
		final Map< ViewId, Translation3D > transformsForData = TileConfigurationHelpers.getTransformsForData( tcParsed, pixelUnits, data );
		TileConfigurationHelpers.applyToData( transformsForData, pixelUnits, metadataRotate, data );

		// save result
		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );
	}

	public static void main(String[] args)
	{
		new Load_Tile_Configuration().run( "" );
	}

}
