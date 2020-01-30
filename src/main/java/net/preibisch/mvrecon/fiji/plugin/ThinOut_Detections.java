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
import java.util.Collection;
import java.util.Date;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointremoval.ThinOut;
import net.preibisch.mvrecon.process.interestpointremoval.ThinOutParameters;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;

public class ThinOut_Detections implements PlugIn
{
	public static String[] removeKeepChoice = new String[]{ "Remove", "Keep" };

	public static int defaultRemoveKeep = 0; // 0 == remove, 1 == keep
	public static int defaultLabel = -1;
	public static String defaultNewLabel = "thinned-out";
	public static double defaultCutoffThresholdMin = 0;
	public static double defaultCutoffThresholdMax = 5;

	@Override
	public void run( final String arg )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "", true, true, true, true, true ) )
			return;

		final SpimData2 data = xml.getData();

		if ( !thinOut( data, SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() ) ) )
			return;

		// write new xml
		SpimData2.saveXML( data, xml.getXMLFileName(), xml.getClusterExtension() );
	}

	public static boolean thinOut( final SpimData2 data, final Collection< ? extends ViewId > viewCollection )
	{
		// filter not present ViewIds
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		final List< ViewId > removed  = SpimData2.filterMissingViews( data, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final ThinOutParameters top = getParameters( data, viewIds );

		if ( top == null )
			return false;

		// thin out detections and save the new interestpoint files
		if ( !ThinOut.thinOut( data, viewIds, top ) )
			return false;

		return true;
	}

	public static ThinOutParameters getParameters(
			final SpimData2 spimData,
			final List< ViewId > viewIds )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose interest point label to thin out" );

		final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, viewIds );

		// choose the first label that is complete if possible
		if ( defaultLabel < 0 || defaultLabel >= labels.length )
		{
			defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					defaultLabel = i;
					break;
				}

			if ( defaultLabel == -1 )
				defaultLabel = 0;
		}

		gd.addChoice( "Interest_points", labels, labels[ defaultLabel ] );
		gd.addStringField( "New_label", defaultNewLabel, 20 );

		gd.addMessage( "" );

		gd.addNumericField( "Lower_threshold", defaultCutoffThresholdMin, 2 );
		gd.addNumericField( "Upper_threshold", defaultCutoffThresholdMax, 2 );
		gd.addChoice( "Defined_range", removeKeepChoice, removeKeepChoice[ defaultRemoveKeep ] );

		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final ThinOutParameters top = new ThinOutParameters();

		// assemble which label has been selected
		top.label = InterestPointTools.getSelectedLabel( labels, defaultLabel = gd.getNextChoiceIndex() );
		top.newLabel = defaultNewLabel = gd.getNextString();

		top.lowerThreshold = defaultCutoffThresholdMin = gd.getNextNumber();
		top.upperThreshold = defaultCutoffThresholdMax = gd.getNextNumber();

		final int removeKeep = defaultRemoveKeep = gd.getNextChoiceIndex();
		if ( removeKeep == 1 )
			top.keep = true;
		else
			top.keep = false;

		if ( top.getMin() >= top.getMax() )
		{
			IOFunctions.println( "You selected the minimal threshold larger than the maximal threshold." );
			IOFunctions.println( "Stopping." );
			return null;
		}
		else
		{
			if ( top.keepRange() )
				IOFunctions.println( "Keeping interest points with distances from " + top.getMin() + " >>> " + top.getMax() );
			else
				IOFunctions.println( "Removing interest points with distances from " + top.getMin() + " >>> " + top.getMax() );
		}

		return top;
	}

	public static void main( final String[] args )
	{
		new ThinOut_Detections().run( null );
	}
}
