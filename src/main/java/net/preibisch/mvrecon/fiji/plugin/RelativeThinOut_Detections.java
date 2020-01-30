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

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointremoval.RelativeThinOut;
import net.preibisch.mvrecon.process.interestpointremoval.RelativeThinOutParameters;

public class RelativeThinOut_Detections implements PlugIn
{
	public static int defaultRelativeLabel = 1;

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

		final RelativeThinOutParameters rtop = getParameters( data, viewIds );

		if ( rtop == null )
			return false;

		// thin out detections and save the new interestpoint files
		if ( !RelativeThinOut.thinOut( data, viewIds, rtop ) )
			return false;

		return true;
	}

	public static RelativeThinOutParameters getParameters(
			final SpimData2 spimData,
			final List< ViewId > viewIds )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose relative thin out parameters" );

		final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, viewIds );

		// choose the first label that is complete if possible
		if ( ThinOut_Detections.defaultLabel < 0 || ThinOut_Detections.defaultLabel >= labels.length )
		{
			ThinOut_Detections.defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					ThinOut_Detections.defaultLabel = i;
					break;
				}

			if ( ThinOut_Detections.defaultLabel == -1 )
				ThinOut_Detections.defaultLabel = 0;
		}

		// choose the first label that is complete if possible
		if ( defaultRelativeLabel < 0 || defaultRelativeLabel >= labels.length )
		{
			defaultRelativeLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					defaultRelativeLabel = i;
					break;
				}

			if ( defaultRelativeLabel == -1 )
				defaultRelativeLabel = 0;
		}

		gd.addChoice( "Interest_points", labels, labels[ ThinOut_Detections.defaultLabel ] );
		gd.addChoice( "Relative_to", labels, labels[ defaultRelativeLabel ] );
		gd.addStringField( "New_label", ThinOut_Detections.defaultNewLabel, 20 );

		gd.addMessage( "" );

		gd.addNumericField( "Lower_threshold", ThinOut_Detections.defaultCutoffThresholdMin, 2 );
		gd.addNumericField( "Upper_threshold", ThinOut_Detections.defaultCutoffThresholdMax, 2 );
		gd.addChoice( "Defined_range", ThinOut_Detections.removeKeepChoice, ThinOut_Detections.removeKeepChoice[ ThinOut_Detections.defaultRemoveKeep ] );

		
		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final RelativeThinOutParameters rtop = new RelativeThinOutParameters();

		// assemble which label has been selected
		rtop.label = InterestPointTools.getSelectedLabel( labels, ThinOut_Detections.defaultLabel = gd.getNextChoiceIndex() );
		rtop.relativeLabel = InterestPointTools.getSelectedLabel( labels, defaultRelativeLabel = gd.getNextChoiceIndex() );
		rtop.newLabel = ThinOut_Detections.defaultNewLabel = gd.getNextString();

		rtop.lowerThreshold = ThinOut_Detections.defaultCutoffThresholdMin = gd.getNextNumber();
		rtop.upperThreshold = ThinOut_Detections.defaultCutoffThresholdMax = gd.getNextNumber();

		final int removeKeep = ThinOut_Detections.defaultRemoveKeep = gd.getNextChoiceIndex();
		if ( removeKeep == 1 )
			rtop.keep = true;
		else
			rtop.keep = false;

		if ( rtop.getMin() >= rtop.getMax() )
		{
			IOFunctions.println( "You selected the minimal threshold larger than the maximal threshold." );
			IOFunctions.println( "Stopping." );
			return null;
		}
		else
		{
			if ( rtop.keepRange() )
				IOFunctions.println( "Keeping interest points with distances from " + rtop.getMin() + " >>> " + rtop.getMax() );
			else
				IOFunctions.println( "Removing interest points with distances from " + rtop.getMin() + " >>> " + rtop.getMax() );
		}

		return rtop;
	}

	public static void main( final String[] args )
	{
		new RelativeThinOut_Detections().run( null );
	}
}
