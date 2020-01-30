/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.headless.resave;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;

import java.io.File;
import java.util.List;
import java.util.Map;

import net.preibisch.mvrecon.fiji.ImgLib2Temp;
import net.preibisch.mvrecon.fiji.plugin.resave.*;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

/**
 * Created by schmied on 02/07/15.
 */
public class ResaveHdf5 {

    public static void save( final String xmlfile, final ResaveHdf5Parameter params )
    {
        final ProgressWriter progressWriter = new ProgressWriterIJ();
        progressWriter.out().println( "starting export..." );

        final HeadlessParseQueryXML xml = new HeadlessParseQueryXML();
        final String xmlFileName = xmlfile;
        if ( !xml.loadXML( xmlFileName, false ) ) return;

        if ( Resave_HDF5.loadDimensions( xml.getData(), xml.getViewSetupsToProcess() ) )
        {
            // save the XML again with the dimensions loaded
            SpimData2.saveXML( xml.getData(), xml.getXMLFileName(), xml.getClusterExtension() );
        }

        final Map< Integer, ExportMipmapInfo> perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( xml.getViewSetupsToProcess() );

        Generic_Resave_HDF5.lastExportPath = xmlfile;

        final int firstviewSetupId = xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
        ExportMipmapInfo autoMipmapSettings = perSetupExportMipmapInfo.get( firstviewSetupId );

        boolean lastSetMipmapManual = false;

        boolean lastSplit = false;
        int lastTimepointsPerPartition = 1;
        int lastSetupsPerPartition = 0;
        boolean lastDeflate = true;
        int lastJobIndex = 0;
//
//        if ( params.isUseCluster() )
//        {
//            lastSplit = true;
//        }

        // If we are using user-defined sub-sampling config, enable the below codes.
        //
        //		String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";
        //		String lastChunkSizes = "{16,16,16}, {16,16,16}, {16,16,16}";
        //		final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
        //		final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );

        int[][] resolutions = autoMipmapSettings.getExportResolutions();
        if( params.resolutions != null )
            resolutions = PluginHelper.parseResolutionsString(params.resolutions);

        int[][] subdivisions = autoMipmapSettings.getSubdivisions();
        if( params.subdivisions != null )
            subdivisions = PluginHelper.parseResolutionsString( params.subdivisions );

//        final File seqFile, hdf5File;

//        seqFile = new File(xmlFileName);
//        hdf5File = new File(seqFile.getPath().substring( 0, seqFile.getPath().length() - 4 ) + ".h5");

        int defaultConvertChoice = 1;
        double defaultMin, defaultMax;
        defaultMin = defaultMax = Double.NaN;
        boolean displayClusterProcessing = false;

        final SpimData2 data = xml.getData();
        final List<ViewId> viewIds = SpimData2.getAllViewIdsSorted(data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess());



        Generic_Resave_HDF5.Parameters newParameters = new Generic_Resave_HDF5.Parameters(
                params.setMipmapManual,
                resolutions,
                subdivisions,
                params.seqFile,
                params.hdf5File,
                params.deflate,
                params.split,
                params.timepointsPerPartition,
                params.setupsPerPartition,
                params.onlyRunSingleJob,
                params.jobId,
                params.convertChoice,
                params.min,
                params.max
        );

        // write hdf5
        Generic_Resave_HDF5.writeHDF5(Resave_HDF5.reduceSpimData2(data, viewIds), newParameters, progressWriter);

        // write xml sequence description
        try
        {
            final ImgLib2Temp.Pair< SpimData2, List< String > > result = Resave_HDF5.createXMLObject( data, viewIds, newParameters, null, false );

            xml.getIO().save( result.getA(), newParameters.getSeqFile().getAbsolutePath() );

            // copy the interest points if they exist
            Resave_TIFF.copyInterestPoints(xml.getData().getBasePath(), newParameters.getSeqFile().getParentFile(), result.getB());
        }
        catch ( SpimDataException e )
        {
            throw new RuntimeException( e );
        }


    }

    public static void main( final String[] argv )
    {
        // Test mvn commamnd
        //
        // export MAVEN_OPTS="-Xms4g -Xmx16g -Djava.awt.headless=true"
        // mvn exec:java -Dexec.mainClass="task.ResaveHdf5Task" -Dexec.args="-Dxml_filename=/projects/pilot_spim/moon/test.xml -Dsubsampling_factors='{1,1,1}, {2,2,1}, {4,4,2}' -Dhdf5_chunk_sizes='{16,16,16}, {16,16,16}, {16,16,16}'"
        ResaveHdf5Parameter params = new ResaveHdf5Parameter();
        params.seqFile = new File("/Users/schmied/Desktop/moon/test.xml");
        params.hdf5File = new File(params.seqFile.getPath().substring( 0, params.seqFile.getPath().length() - 4 ) + ".h5");
        ResaveHdf5.save("/Users/schmied/Desktop/moon/test.xml",params);

        System.exit( 0 );
    }

}
