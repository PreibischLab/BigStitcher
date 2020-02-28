package net.preibisch.mvrecon.process.export;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import bdv.export.WriteSequenceToHdf5;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.tools.MergePartitionList;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionExportInterface;
import net.preibisch.mvrecon.fiji.plugin.resave.GenericResaveHDF5GUI;
import net.preibisch.mvrecon.fiji.plugin.resave.ResaveHDF5;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class AppendSpimData2HDF5Gui extends AppendSpimData2HDF5 implements ImgExportGui {

	@Override
	public boolean queryParameters( final FusionExportInterface fusion )
	{
		System.out.println( "queryParameters()" );

		this.fusion = fusion;

		// define new timepoints and viewsetups
		final Pair< List< TimePoint >, List< ViewSetup > > newStructure = defineNewViewSetups( fusion );
		this.newTimepoints = newStructure.getA();
		this.newViewSetups = newStructure.getB();

		this.spimData = (SpimData2) fusion.getSpimData();

		Hdf5ImageLoader il;
		if (Hdf5ImageLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ))
			il = ( Hdf5ImageLoader ) spimData.getSequenceDescription().getImgLoader();
		else
			return false;

		perSetupExportMipmapInfo = ResaveHDF5.proposeMipmaps( newViewSetups );

		String fn = il.getHdf5File().getAbsolutePath();
		if ( fn.endsWith( ".h5" ) )
			fn = fn.substring( 0, fn.length() - ".h5".length() );
		String fusionHdfFilename = "";
		String fusionXmlFilename = "";
		for ( int i = 0;; ++i )
		{
			fusionHdfFilename = String.format( "%s-f%d.h5", fn, i );
			fusionXmlFilename = String.format( "%s-f%d.xml", fn, i );
			if ( !new File( fusionHdfFilename ).exists() && !new File( fusionXmlFilename ).exists() )
				break;
		}

		boolean is16bit = fusion.getPixelType() == 1;

		final int firstviewSetupId = newViewSetups.get( 0 ).getId();
		params = GenericResaveHDF5GUI.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), false, getDescription(), is16bit );
		if ( params == null )
		{
			System.out.println( "abort " );
			return false;
		}
		params.setHDF5File( new File( fusionHdfFilename ) );
		params.setSeqFile( new File( fusionXmlFilename ) );

		Pair< SpimData2, HashMap< ViewId, Partition > > init = ExportSpimData2HDF5.initSpimData(
				newTimepoints, newViewSetups, params, perSetupExportMipmapInfo );
		fusionOnlySpimData = init.getA();
		viewIdToPartition = init.getB();

		perSetupExportMipmapInfo.putAll(
				MergePartitionList.getHdf5PerSetupExportMipmapInfos( spimData.getSequenceDescription() ) );

		appendSpimData2( spimData, newTimepoints, newViewSetups );

		ArrayList< Partition > mergedPartitions = MergePartitionList.getMergedHdf5PartitionList(
				spimData.getSequenceDescription(), fusionOnlySpimData.getSequenceDescription() );

		String mergedHdfFilename = "";
		for ( int i = 0;; ++i )
		{
			mergedHdfFilename = String.format( "%s-m%d.h5", fn, i );
			if ( !new File( mergedHdfFilename ).exists() )
				break;
		}

		SequenceDescription seq = spimData.getSequenceDescription();
		Hdf5ImageLoader newLoader = new Hdf5ImageLoader(
				new File( mergedHdfFilename ), mergedPartitions, seq, false );
		seq.setImgLoader( newLoader );
		WriteSequenceToHdf5.writeHdf5PartitionLinkFile( spimData.getSequenceDescription(), perSetupExportMipmapInfo );

		return true;
	}

	
	@Override
	public ImgExportGui newInstance()
	{
		//BoundingBoxGUI.defaultPixelType = 1; // set to 16 bit by default
		return new AppendSpimData2HDF5Gui();
	}

}
