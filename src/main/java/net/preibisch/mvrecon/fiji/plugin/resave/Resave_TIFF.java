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

import java.io.File;
import java.util.Date;
import java.util.List;

import bdv.export.ProgressWriter;
import fiji.util.gui.GenericDialogPlus;
import ij.plugin.PlugIn;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class Resave_TIFF extends ResaveTIFF implements PlugIn
{
	public static String defaultPath = null;
	public static int defaultContainer = 1;
	public static boolean defaultCompress = false;

	public static void main( final String[] args )
	{
		new Resave_TIFF().run( null );
	}

	@Override
	public void run( final String arg0 )
	{
		final LoadParseQueryXML lpq = new LoadParseQueryXML();

		if ( !lpq.queryXML( "Resaving as TIFF", "Resave", true, true, true, true, true ) )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );
		
		final Parameters params = getParameters();
		
		if ( params == null )
			return;

		final SpimData2 data = lpq.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, lpq.getViewSetupsToProcess(), lpq.getTimePointsToProcess() );

		// write the TIFF's
		writeTIFF( data, viewIds, new File( params.xmlFile ).getParent(), params.compress, progressWriter );

		// write the XML
		try
		{
			final Pair< SpimData2, List< String > > result = createXMLObject( data, viewIds, params );
			progressWriter.setProgress( 0.95 );

			// write the XML
			lpq.getIO().save( result.getA(), new File( params.xmlFile ).getAbsolutePath() );

			// copy the interest points if they exist
			copyInterestPoints( data.getBasePath(), new File( params.xmlFile ).getParentFile(), result.getB() );
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could not save xml '" + params.xmlFile + "'." );
			e.printStackTrace();
		}
		finally
		{
			progressWriter.setProgress( 1.00 );
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Saved xml '" + params.xmlFile + "'." );
		}
	}

	

	public static Parameters getParameters()
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Resave dataset as TIFF" );

		if ( defaultPath == null )
			defaultPath = LoadParseQueryXML.defaultXMLfilename;

		PluginHelper.addSaveAsFileField( gd, "Select new XML", defaultPath, 80 );
		//gd.addCheckbox( "Lossless compression of TIFF files (ZIP)", defaultCompress );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		final Parameters params = new Parameters();

		params.xmlFile = gd.getNextString();

		if ( !params.xmlFile.endsWith( ".xml" ) )
			params.xmlFile += ".xml";

		defaultPath = LoadParseQueryXML.defaultXMLfilename = params.xmlFile;

		params.compress = false; //defaultCompress = gd.getNextBoolean();

		if ( defaultContainer == 0 )
			params.imgFactory = new ArrayImgFactory< FloatType >();
		else
			params.imgFactory = new CellImgFactory< FloatType >();

		return params;
	}

}
