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
package net.preibisch.mvrecon.fiji.plugin.resave;

import java.util.Date;
import java.util.List;
import java.util.Map;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.plugin.Toggle_Cluster_Options;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.resave.GenericResaveHDF5.Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class Resave_HDF5 extends ResaveHDF5 implements PlugIn
{
	public static void main( final String[] args )
	{
		new Resave_HDF5().run( null );
	}

	@Override
	public void run( final String arg0 )
	{
		boolean rememberClusterProcessing = Toggle_Cluster_Options.displayClusterProcessing;
		Toggle_Cluster_Options.displayClusterProcessing = false;

		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "Resaving as HDF5", "Resave", true, true, true, true, true ) )
			return;

		Toggle_Cluster_Options.displayClusterProcessing = rememberClusterProcessing;

		// load all dimensions if they are not known (required for estimating the mipmap layout)
		if ( loadDimensions( xml.getData(), xml.getViewSetupsToProcess() ) )
		{
			// save the XML again with the dimensions loaded
			SpimData2.saveXML( xml.getData(), xml.getXMLFileName(), xml.getClusterExtension() );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = proposeMipmaps( xml.getViewSetupsToProcess() );

		GenericResaveHDF5GUI.lastExportPath = LoadParseQueryXML.defaultXMLfilename;

		final int firstviewSetupId = xml.getData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final Parameters params = GenericResaveHDF5GUI.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, true );

		if ( params == null )
			return;

		LoadParseQueryXML.defaultXMLfilename = params.getSeqFile().toString();

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		final SpimData2 data = xml.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() );

		// write hdf5
		GenericResaveHDF5GUI.writeHDF5( reduceSpimData2( data, viewIds ), params, progressWriter );

		// write xml sequence description
		if ( !params.onlyRunSingleJob || params.jobId == 0 )
		{
			try
			{
				final Pair< SpimData2, List< String > > result = createXMLObject( data, viewIds, params, progressWriter, false );

				xml.getIO().save( result.getA(), params.seqFile.getAbsolutePath() );
				progressWriter.setProgress( 0.95 );
				
				// copy the interest points if they exist
				Resave_TIFF.copyInterestPoints( xml.getData().getBasePath(), params.getSeqFile().getParentFile(), result.getB() );
			}
			catch ( SpimDataException e )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.getSeqFile() + "': " + e );
				throw new RuntimeException( e );
			}
			finally
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.getSeqFile() + "'." );
			}
		}
		progressWriter.setProgress( 1.0 );
		progressWriter.out().println( "done" );
	}	
}
