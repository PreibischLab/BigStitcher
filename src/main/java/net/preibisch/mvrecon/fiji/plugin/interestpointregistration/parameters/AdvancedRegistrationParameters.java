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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters.BasicRegistrationParameters.RegistrationType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.AllToAll;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.AllToAllRange;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.IndividualTimepoints;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.ReferenceTimepoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.range.TimepointRange;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

public class AdvancedRegistrationParameters
{
	public int range, referenceTimePoint, fixViewsIndex, mapBackIndex;
	public boolean groupTimePoints, showStatistics;

	public PairwiseSetup< ViewId > pairwiseSetupInstance(
			final RegistrationType registrationType,
			final List< ViewId > views,
			final Set< Group< ViewId > > groups )
	{
		if ( registrationType == RegistrationType.TIMEPOINTS_INDIVIDUALLY )
			return new IndividualTimepoints( views, groups );
		else if ( registrationType == RegistrationType.ALL_TO_ALL )
			return new AllToAll<>( views, groups );
		else if ( registrationType == RegistrationType.ALL_TO_ALL_WITH_RANGE )
			return new AllToAllRange< ViewId, TimepointRange< ViewId > >( views, groups, new TimepointRange<>( range ) );
		else
			return new ReferenceTimepoint( views, groups, referenceTimePoint );
	}

	public HashSet< Group< ViewId > > getGroups(
			final SpimData2 data,
			final List< ViewId > views,
			final boolean groupTiles,
			final boolean groupIllums,
			final boolean groupChannels )
	{
		return getGroups( data, views, groupTiles, groupIllums, groupChannels, groupTimePoints );
	}

	public static HashSet< Group< ViewId > > getGroups(
			final SpimData2 data,
			final List< ? extends ViewId > views,
			final boolean groupTiles,
			final boolean groupIllums,
			final boolean groupChannels,
			final boolean groupTimePoints )
	{
		final HashSet< Group< ViewId > > groups = new HashSet<>();

		if ( groupTimePoints )
		{
			final ArrayList< Integer > timepoints = SpimData2.getAllTimePointsSortedUnchecked( views );

			//final HashSet< Class< ? extends Entity > > groupingFactor = new HashSet<>();
			//groupingFactor.add( TimePoint.class );
			//Group.splitBy( vds, groupingFactor )

			for ( final int tp : timepoints )
			{
				
				final Group< ViewId > group = new Group<>();

				for ( final ViewId viewId : views )
					if ( viewId.getTimePointId() == tp )
						group.getViews().add( viewId );

				groups.add( group );
			}

			IOFunctions.println( "Identified: " + groups.size() + " groups when grouping by TimePoint." );
			int i = 0;
			for ( final Group< ViewId > group : groups )
				IOFunctions.println( "Timepoint-Group " + (i++) + ":" + group );
		}

		// combine vs split
		if ( groupTiles || groupIllums || groupChannels )
		{
			final ArrayList< ViewDescription > vds = new ArrayList<>();

			for ( final ViewId viewId : views )
				vds.add( data.getSequenceDescription().getViewDescription( viewId ) );
	
			final HashSet< Class< ? extends Entity > > groupingFactor = new HashSet<>();
			String end = "";

			if ( groupTiles )
			{
				groupingFactor.add( Tile.class );
				end = "tile";
			}

			if ( groupIllums )
			{
				groupingFactor.add( Illumination.class );
				if ( end.length() > 0 )
					end += ", illumination";
				else
					end = "illumination";
			}

			if ( groupChannels )
			{
				groupingFactor.add( Channel.class );
				if ( end.length() > 0 )
					end += ", channel";
				else
					end = "channel";
			}

			final List< Group< ViewDescription > > groupsTmp = Group.combineBy( vds, groupingFactor );

			IOFunctions.println( "Identified: " + groupsTmp.size() + " groups when grouping by " + end + "." );
			int i = 0;
			for ( final Group< ViewDescription > group : groupsTmp )
			{
				IOFunctions.println( end + "-Group " + (i++) + ":" + group );
				groups.add( (Group< ViewId >)(Object)group );
			}
		}

		return groups;
	}
}
