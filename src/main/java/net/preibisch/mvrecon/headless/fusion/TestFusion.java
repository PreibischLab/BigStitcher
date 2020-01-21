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
package net.preibisch.mvrecon.headless.fusion;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import ij.ImageJ;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.headless.boundingbox.TestBoundingBox;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;

public class TestFusion
{
	public static void main( String[] args )
	{
		new ImageJ();

		// generate 4 views with 1000 corresponding beads, single timepoint
		SpimData2 spimData = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample( new int[]{ 0, 90, 135 } ) );

		System.out.println( "Views present:" );

		for ( final ViewId viewId : spimData.getSequenceDescription().getViewDescriptions().values() )
			System.out.println( Group.pvid( viewId ) );

		testFusion( spimData );
	}

	public static void testFusion( final SpimData2 spimData )
	{
		Interval bb = TestBoundingBox.testBoundingBox( spimData, false );

		// select views to process
		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// downsampling
		double downsampling = Double.NaN;

		//
		// display virtually fused
		//
		final RandomAccessibleInterval< FloatType > virtual = FusionTools.fuseVirtual( spimData, viewIds, bb, downsampling ).getA();
		DisplayImage.getImagePlusInstance( virtual, true, "Fused, Virtual", 0, 255 ).show();

		//
		// actually fuse into an image multithreaded
		//
		final long[] size = new long[ bb.numDimensions() ];
		bb.dimensions( size );

		IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Reserving memory for fused image and copying, size = " + Util.printCoordinates( size ) );

		final RandomAccessibleInterval< FloatType > fusedImg = FusionTools.copyImg( virtual, new ImagePlusImgFactory<>(), new FloatType(), null, true );

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Finished fusion process." );

		DisplayImage.getImagePlusInstance( fusedImg, false, "Fused", 0, 255 ).show();
	}
}
