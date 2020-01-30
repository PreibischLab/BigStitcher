package net.preibisch.mvrecon.process.export;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import bdv.img.hdf5.Partition;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.ResaveHDF5;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class ExportSpimData2HDF5Gui extends ExportSpimData2HDF5 implements ImgExportGui {

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		this.fusion = fusion;

		// define new timepoints and viewsetups
		final Pair< List< TimePoint >, List< ViewSetup > > newStructure = ExportSpimData2TIFF.defineNewViewSetups( fusion, fusion.getDownsampling(), fusion.getAnisotropyFactor() );
		this.newTimepoints = newStructure.getA();
		this.newViewSetups = newStructure.getB();

		System.out.println( this + " " + fusion );

		perSetupExportMipmapInfo = ResaveHDF5.proposeMipmaps( newViewSetups );

		String fn = defaultXMLfilename;
		if ( fn.endsWith( ".xml" ) )
			fn = fn.substring( 0, fn.length() - ".xml".length() );
		for ( int i = 0;; ++i )
		{
			Generic_Resave_HDF5.lastExportPath = String.format( "%s-f%d.xml", fn, i );
			if ( !new File( Generic_Resave_HDF5.lastExportPath ).exists() )
				break;
		}

		boolean is16bit = fusion.getPixelType() == 1;

		final int firstviewSetupId = newViewSetups.get( 0 ).getId();
		params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, getDescription(), is16bit );

		if ( params == null )
		{
			System.out.println( "abort " );
			return false;
		}

		Pair< SpimData2, HashMap< ViewId, Partition > > init = initSpimData( newTimepoints, newViewSetups, params, perSetupExportMipmapInfo );
		this.spimData = init.getA();
		viewIdToPartition = init.getB();

		return true;
	}
	
	@Override
	public ImgExportGui newInstance()
	{
		System.out.println( "newInstance()" );
		return new ExportSpimData2HDF5Gui();
	}
}
