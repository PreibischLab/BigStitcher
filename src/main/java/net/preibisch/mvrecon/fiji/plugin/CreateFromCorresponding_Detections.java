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
import net.preibisch.mvrecon.process.interestpointremoval.CreateFromCorrespondencesParameters;
import net.preibisch.mvrecon.process.interestpointremoval.CreateInterestPointsFromCorrespondences;

public class CreateFromCorresponding_Detections implements PlugIn
{
	public static int defaultLabel = -1;
	public static String defaultNewLabel = "corresponding";

	@Override
	public void run( final String arg )
	{
		final LoadParseQueryXML xml = new LoadParseQueryXML();

		if ( !xml.queryXML( "", true, true, true, true, true ) )
			return;

		final SpimData2 data = xml.getData();

		if ( !create( data, SpimData2.getAllViewIdsSorted( data, xml.getViewSetupsToProcess(), xml.getTimePointsToProcess() ) ) )
			return;

		// write new xml
		SpimData2.saveXML( data, xml.getXMLFileName(), xml.getClusterExtension() );
	}

	public static boolean create( final SpimData2 data, final Collection< ? extends ViewId > viewCollection )
	{
		// filter not present ViewIds
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		final List< ViewId > removed  = SpimData2.filterMissingViews( data, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final CreateFromCorrespondencesParameters params = getParameters( data, viewIds );

		if ( params == null )
			return false;

		// thin out detections and save the new interestpoint files
		if ( !CreateInterestPointsFromCorrespondences.createFor( data, viewIds, params ) )
			return false;

		return true;
	}

	public static CreateFromCorrespondencesParameters getParameters(
			final SpimData2 spimData,
			final List< ViewId > viewIds )
	{
		// build up the dialog
		final GenericDialog gd = new GenericDialog( "Choose corresponding interest points to redefine" );

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

		gd.addChoice( "Corresponding_interest_points", labels, labels[ defaultLabel ] );
		gd.addStringField( "New_label", defaultNewLabel, 20 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		final CreateFromCorrespondencesParameters params = new CreateFromCorrespondencesParameters();

		// assemble which label has been selected
		params.correspondingLabel = InterestPointTools.getSelectedLabel( labels, defaultLabel = gd.getNextChoiceIndex() );
		params.newLabel = defaultNewLabel = gd.getNextString();

		return params;
	}

	public static void main( final String[] args )
	{
		new CreateFromCorresponding_Detections().run( null );
	}
}
