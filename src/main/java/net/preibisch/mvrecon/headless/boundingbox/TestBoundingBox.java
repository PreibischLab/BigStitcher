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
package net.preibisch.mvrecon.headless.boundingbox;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.headless.registration.TestRegistration;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxBigDataViewer;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxEstimation;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

public class TestBoundingBox
{
	public static void main( String[] args )
	{
		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testBoundingBox( spimData, true );
	}

	public static BoundingBox testBoundingBox( final SpimData2 spimData, final boolean bdv )
	{
		// run the whole pipeline
		TestRegistration.testRegistration( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		BoundingBoxEstimation estimation;

		if ( bdv )
			estimation = new BoundingBoxBigDataViewer( spimData, viewIds );
		else
			estimation = new BoundingBoxMaximal( viewIds, spimData );

		final BoundingBox bb = estimation.estimate( "Full Bounding Box" );

		System.out.println( bb );

		return bb;
	}

	public static BoundingBox getBoundingBox( final SpimData2 spimData, final String bbTitle )
	{
		BoundingBox boundingBox = null;

		for ( final BoundingBox bb : spimData.getBoundingBoxes().getBoundingBoxes() )
		{
			System.out.println( "Bounding box: " + bb.getTitle() );

			if ( bb.getTitle().equals( bbTitle ) )
				boundingBox = bb;
		}

		if ( boundingBox == null )
		{
			System.out.println( "Bounding box '" + bbTitle + "' not found." );
			return null;
		}

		return boundingBox;
	}
}
