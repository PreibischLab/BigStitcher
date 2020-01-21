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

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.DifferenceOfGaussianGUI;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.DifferenceOfMeanGUI;
import net.preibisch.mvrecon.fiji.plugin.interestpointdetection.InterestPointDetectionGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;

import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

/**
 * Plugin to detect interest points, store them on disk, and link them into the XML
 * 
 * Different plugins to detect interest points are supported, needs to implement the
 * {@link InterestPointDetectionGUI} interface
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Interest_Point_Detection implements PlugIn
{
	public static ArrayList< InterestPointDetectionGUI > staticAlgorithms = new ArrayList< InterestPointDetectionGUI >();
	public static int defaultAlgorithm = 1;
	public static boolean defaultDefineAnisotropy = false;
	public static boolean defaultSetMinMax = false;
	public static boolean defaultLimitDetections = false;
	public static String defaultLabel = "beads";

	public static boolean defaultGroupTiles = true;
	public static boolean defaultGroupIllums = true;
	public static ExplorerWindow< ?, ? > currentPanel;

	static
	{
		IOFunctions.printIJLog = true;
		staticAlgorithms.add( new DifferenceOfMeanGUI( null, null ) );
		staticAlgorithms.add( new DifferenceOfGaussianGUI( null, null ) );
	}
	
	@Override
	public void run( final String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "perfoming interest point detection", true, true, true, true, true ) )
			return;

		detectInterestPoints(
				result.getData(),
				SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
				result.getClusterExtension(),
				result.getXMLFileName(),
				true );
	}

	/*
	 * Does just the detection, no saving
	 * 
	 * @param data
	 * @param viewIds
	 * @return
	 */
	public boolean detectInterestPoints(
			final SpimData2 data,
			final Collection< ? extends ViewId > viewCollection )
	{
		return detectInterestPoints( data, viewCollection, "", null, false );
	}

	public boolean detectInterestPoints(
			final SpimData2 data,
			final Collection< ? extends ViewId > viewCollection,
			final String xmlFileName,
			final boolean saveXML )
	{
		return detectInterestPoints( data, viewCollection, "", xmlFileName, saveXML );
	}

	public boolean detectInterestPoints(
			final SpimData2 data,
			final Collection< ? extends ViewId > viewCollection,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
		// filter not present ViewIds
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		final List< ViewId > removed = SpimData2.filterMissingViews( data, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// the GenericDialog needs a list[] of String
		final String[] descriptions = new String[ staticAlgorithms.size() ];
		
		for ( int i = 0; i < staticAlgorithms.size(); ++i )
			descriptions[ i ] = staticAlgorithms.get( i ).getDescription();
		
		if ( defaultAlgorithm >= descriptions.length )
			defaultAlgorithm = 0;
		
		final GenericDialog gd = new GenericDialog( "Detect Interest Points" );
		
		gd.addChoice( "Type_of_interest_point_detection", descriptions, descriptions[ defaultAlgorithm ] );
		gd.addStringField( "Label_interest_points", defaultLabel );

		gd.addCheckbox( "Define_anisotropy for segmentation", defaultDefineAnisotropy );
		gd.addCheckbox( "Set_minimal_and_maximal_intensity", defaultSetMinMax );
		gd.addCheckbox( "Limit_amount_of_detections" , defaultLimitDetections );

		gd.addMessage( "" );

		final HashSet< Integer > tiles = new HashSet<>();
		for ( final ViewId viewId : viewIds )
			tiles.add( data.getSequenceDescription().getViewDescription( viewId ).getViewSetup().getTile().getId() );

		final HashSet< Integer > illums = new HashSet<>();
		for ( final ViewId viewId : viewIds )
			illums.add( data.getSequenceDescription().getViewDescription( viewId ).getViewSetup().getIllumination().getId() );

		if ( tiles.size() > 1 )
			gd.addCheckbox( "Group_tiles", defaultGroupTiles );

		if ( illums.size() > 1 )
			gd.addCheckbox( "Group_illuminations", defaultGroupIllums );

		if ( tiles.size() > 1 || illums.size() > 1 )
		{
			if ( !FusionGUI.isMultiResolution( data ) )
				gd.addMessage( "WARNING: Grouping will be very slow since no Multiresolution Format like HDF5 is used.", GUIHelper.smallStatusFont, GUIHelper.warning );
			else
				gd.addMessage( "You are using a Multiresolution ImgLoader, Grouping should be ok.", GUIHelper.smallStatusFont, GUIHelper.good );
		}

		gd.addMessage( "" );
		GUIHelper.addWebsite( gd );
		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		final int algorithm = defaultAlgorithm = gd.getNextChoiceIndex();

		// how are the detections called (e.g. beads, nuclei, ...)
		final String label = defaultLabel = gd.getNextString();
		final boolean defineAnisotropy = defaultDefineAnisotropy = gd.getNextBoolean();
		final boolean setMinMax = defaultSetMinMax = gd.getNextBoolean();
		final boolean limitDetections = defaultLimitDetections = gd.getNextBoolean();

		boolean groupTiles = false;
		if ( tiles.size() > 1 )
			groupTiles = defaultGroupTiles = gd.getNextBoolean();

		boolean groupIllums = false;
		if ( illums.size() > 1 )
			groupIllums = defaultGroupIllums = gd.getNextBoolean();

		final InterestPointDetectionGUI ipd = staticAlgorithms.get( algorithm ).newInstance(
				data,
				viewIds );

		// the interest point detection should query its parameters
		if ( !ipd.queryParameters( defineAnisotropy, setMinMax, limitDetections, groupTiles, groupIllums ) )
			return false;

		// if grouped, we need to get the min/max intensity for all groups
		ipd.preprocess();

		// now extract all the detections
		for ( final TimePoint tp : SpimData2.getAllTimePointsSorted( data, viewIds ) )
		{
			final HashMap< ViewId, List< InterestPoint > > points = ipd.findInterestPoints( tp );

			InterestPointTools.addInterestPoints( data, label, points, ipd.getParameters() );

			// update metadata if necessary
			if ( data.getSequenceDescription().getImgLoader() instanceof AbstractImgLoader )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Updating metadata ... " );
				try
				{
					( (AbstractImgLoader)data.getSequenceDescription().getImgLoader() ).updateXMLMetaData( data, false );
				}
				catch( Exception e )
				{
					IOFunctions.println( "Failed to update metadata, this should not happen: " + e );
				}
			}

			if ( currentPanel != null )
				currentPanel.updateContent();

			// save the xml
			if ( saveXML )
				SpimData2.saveXML( data, xmlFileName, clusterExtension );
		}

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): DONE." );

		return true;
	}

	public static void main( final String[] args )
	{
		LoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml";

		new ImageJ();
		new Interest_Point_Detection().run( null );
	}
}
