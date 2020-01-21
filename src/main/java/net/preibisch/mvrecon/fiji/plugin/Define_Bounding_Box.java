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
package net.preibisch.mvrecon.fiji.plugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.boundingbox.BDVBoundingBoxGUI;
import net.preibisch.mvrecon.fiji.plugin.boundingbox.BoundingBoxGUI;
import net.preibisch.mvrecon.fiji.plugin.boundingbox.MaximumBoundingBoxGUI;
import net.preibisch.mvrecon.fiji.plugin.boundingbox.MinFilterThresholdBoundingBoxGUI;
import net.preibisch.mvrecon.fiji.plugin.boundingbox.ModifyDefinedBoundingBoxGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;

public class Define_Bounding_Box implements PlugIn
{
	public static ArrayList< BoundingBoxGUI > staticBoundingBoxAlgorithms = new ArrayList< BoundingBoxGUI >();
	public static int defaultBoundingBoxAlgorithm = 1;
	public static String defaultName = "My Bounding Box";

	static
	{
		IOFunctions.printIJLog = true;

		staticBoundingBoxAlgorithms.add( new ModifyDefinedBoundingBoxGUI( null, null ) );
		staticBoundingBoxAlgorithms.add( new BDVBoundingBoxGUI( null, null ) );
		staticBoundingBoxAlgorithms.add( new MaximumBoundingBoxGUI( null, null ) );
		staticBoundingBoxAlgorithms.add( new MinFilterThresholdBoundingBoxGUI( null, null ) );
	}

	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "bounding box definition", true, true, true, true, true ) )
			return;

		defineBoundingBox(
			result.getData(),
			SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
			result.getClusterExtension(),
			result.getXMLFileName(),
			true );
		
	}

	public BoundingBox defineBoundingBox(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		return defineBoundingBox( data, viewIds, "", null, false );
	}

	public BoundingBox defineBoundingBox(
			final SpimData2 data,
			final List< ViewId > viewIds,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( data, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final String[] boundingBoxDescriptions = new String[ staticBoundingBoxAlgorithms.size() ];

		for ( int i = 0; i < staticBoundingBoxAlgorithms.size(); ++i )
			boundingBoxDescriptions[ i ] = staticBoundingBoxAlgorithms.get( i ).getDescription();

		if ( defaultBoundingBoxAlgorithm >= boundingBoxDescriptions.length )
			defaultBoundingBoxAlgorithm = 0;

		final GenericDialog gd = new GenericDialog( "Bounding Box Definition" );

		defaultName = updateDefaultName( defaultName, data.getBoundingBoxes().getBoundingBoxes() );

		gd.addChoice( "Bounding_Box", boundingBoxDescriptions, boundingBoxDescriptions[ defaultBoundingBoxAlgorithm ] );
		gd.addStringField( "Bounding_Box_Name", defaultName, 30 );

		// show existing bounding boxes
		gd.addMessage( "" );
		GUIHelper.displayBoundingBoxes( gd, data.getBoundingBoxes().getBoundingBoxes() );
		gd.addMessage( "" );

		GUIHelper.addWebsite( gd );

		if ( data.getBoundingBoxes().getBoundingBoxes().size() > 5 )
			GUIHelper.addScrollBars( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final int boundingBoxAlgorithm = defaultBoundingBoxAlgorithm = gd.getNextChoiceIndex();
		final String boundingBoxName = gd.getNextString();

		for ( final BoundingBox bb : data.getBoundingBoxes().getBoundingBoxes() )
			if ( bb.getTitle().equals( boundingBoxName ) )
				IOFunctions.println( "WARNING: A bounding box with the name '" + boundingBoxName + "' already exists and will be overwritten!!!" );

		final BoundingBoxGUI boundingBox = staticBoundingBoxAlgorithms.get( boundingBoxAlgorithm ).newInstance( data, viewIds );

		if ( !boundingBox.queryParameters() )
			return null;

		boundingBox.setTitle( boundingBoxName );
		defaultName = boundingBoxName;

		for ( int i = data.getBoundingBoxes().getBoundingBoxes().size() - 1; i >= 0; --i )
			if ( data.getBoundingBoxes().getBoundingBoxes().get( i ).getTitle().equals( boundingBoxName ) )
				data.getBoundingBoxes().getBoundingBoxes().remove( i );

		data.getBoundingBoxes().addBoundingBox( boundingBox );

		if ( saveXML )
			SpimData2.saveXML( data, xmlFileName, clusterExtension );

		return boundingBox;
	}

	protected String updateDefaultName( String defaultName, final List< BoundingBox > bbs )
	{
		if ( bbs == null || bbs.size() == 0 )
			return defaultName;

		boolean collision = false;

		do
		{
			collision = false;

			for ( final BoundingBox bb : bbs )
				if ( bb.getTitle().equals( defaultName ) )
					collision = true;

			if ( collision )
				defaultName += "1";
		}
		while ( collision );

		return defaultName;
	}
	public static void main( final String[] args )
	{
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Define_Bounding_Box().run( null );
	}
}
