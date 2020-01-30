package net.preibisch.mvrecon.process.export;

import fiji.util.gui.GenericDialogPlus;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;

public class Save3dTIFFGui extends Save3dTIFF implements ImgExportGui {

	public Save3dTIFFGui(String path) {
		super(path);
	}

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Save fused images as 3D TIFF" );

		if ( defaultPath == null || defaultPath.length() == 0 )
		{
			defaultPath = fusion.getSpimData().getBasePath().getAbsolutePath();
			
			if ( defaultPath.endsWith( "/." ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 1 );
			
			if ( defaultPath.endsWith( "/./" ) )
				defaultPath = defaultPath.substring( 0, defaultPath.length() - 2 );
		}

		PluginHelper.addSaveAsDirectoryField( gd, "Output_file_directory", defaultPath, 80 );
		gd.addStringField( "Filename_addition", defaultFN );
		gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", Resave_TIFF.defaultCompress );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		this.path = defaultPath = gd.getNextString().trim();
		this.fnAddition = defaultFN = gd.getNextString().trim();
		this.compress = Resave_TIFF.defaultCompress = gd.getNextBoolean();

		return true;
	}
	@Override
	public ImgExportGui newInstance() { return new Save3dTIFFGui( path ); }


}
