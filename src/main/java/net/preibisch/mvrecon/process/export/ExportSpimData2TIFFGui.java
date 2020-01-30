package net.preibisch.mvrecon.process.export;

import java.io.File;
import java.util.List;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;

public class ExportSpimData2TIFFGui extends ExportSpimData2TIFF implements ImgExportGui {
	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		if ( Resave_TIFF.defaultPath == null )
			Resave_TIFF.defaultPath = "";
		
		this.params = Resave_TIFF.getParameters();
		
		if ( this.params == null )
			return false;

		this.path = new File( new File( this.params.getXMLFile() ).getParent() );
		this.saver = new Save3dTIFF( this.path.toString(), this.params.compress() );

		// define new timepoints and viewsetups
		final Pair< List< TimePoint >, List< ViewSetup > > newStructure = defineNewViewSetups( fusion, fusion.getDownsampling(), fusion.getAnisotropyFactor() );
		this.newTimepoints = newStructure.getA();
		this.newViewSetups = newStructure.getB();

		this.fusion = fusion; // we need it later to find the right new ViewId for a FusionGroup
		this.newSpimData = createSpimData2( newTimepoints, newViewSetups, params );

		return true;
	}
	

	@Override
	public ImgExportGui newInstance() { return new ExportSpimData2TIFFGui(); }

}
