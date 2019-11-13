/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
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
package net.preibisch.stitcher.algorithm;

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.util.Date;

import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.Downsample;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;


public class DownsampleTools
{
	public static final int[] ds = { 1, 2, 4, 8, 16, 32, 64, 128 };

	public static < T extends RealType<T> > void openAndDownsampleAdjustTransformation(
			final BasicImgLoader imgLoader,
			final ViewId vd,
			long[] downsampleFactors,
			final AffineTransform3D t )
	{
		long dsx = downsampleFactors[0];
		long dsy = downsampleFactors[1];
		long dsz = downsampleFactors[2];

		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapResolutions();

			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			t.set( mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapTransforms()[ bestLevel ] );
			
			dsx /= fx;
			dsy /= fy;
			dsz /= fz;
		}
		else
		{
			t.identity();
		}

		// fix scaling
		t.set( t.get( 0, 0 ) * dsx, 0, 0 );
		t.set( t.get( 1, 1 ) * dsy, 1, 1 );
		t.set( t.get( 2, 2 ) * dsz, 2, 2 );
	}

	public static < T extends RealType<T> > RandomAccessibleInterval< T > openAndDownsample(
			final BasicImgLoader imgLoader,
			final ViewId vd,
			long[] downsampleFactors )
	{
		
		System.out.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Requesting Img from ImgLoader (tp=" + vd.getTimePointId() + ", setup=" + vd.getViewSetupId() + ")" );


		long dsx = downsampleFactors[0];
		long dsy = downsampleFactors[1];
		long dsz = downsampleFactors[2];

		RandomAccessibleInterval< T > input = null;

		if ( ( dsx > 1 || dsy > 1 || dsz > 1 ) && MultiResolutionImgLoader.class.isInstance( imgLoader ) )
		{
			MultiResolutionImgLoader mrImgLoader = ( MultiResolutionImgLoader ) imgLoader;

			double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getMipmapResolutions();

			int bestLevel = 0;
			for ( int level = 0; level < mipmapResolutions.length; ++level )
			{
				double[] factors = mipmapResolutions[ level ];
				
				// this fails if factors are not ints
				final int fx = (int)Math.round( factors[ 0 ] );
				final int fy = (int)Math.round( factors[ 1 ] );
				final int fz = (int)Math.round( factors[ 2 ] );
				
				if ( fx <= dsx && fy <= dsy && fz <= dsz && contains( fx, ds ) && contains( fy, ds ) && contains( fz, ds ) )
					bestLevel = level;
			}

			final int fx = (int)Math.round( mipmapResolutions[ bestLevel ][ 0 ] );
			final int fy = (int)Math.round( mipmapResolutions[ bestLevel ][ 1 ] );
			final int fz = (int)Math.round( mipmapResolutions[ bestLevel ][ 2 ] );

			dsx /= fx;
			dsy /= fy;
			dsz /= fz;

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): " +
					"View " + Group.pvid( vd ) + ", " +
					"using precomputed Multiresolution Images [" + fx + "x" + fy + "x" + fz + "], " +
					"Remaining downsampling [" + dsx + "x" + dsy + "x" + dsz + "]" );

			input = (RandomAccessibleInterval< T >) mrImgLoader.getSetupImgLoader( vd.getViewSetupId() ).getFloatImage( vd.getTimePointId(), bestLevel, false, LOAD_COMPLETELY );
		}
		else
		{
			input =  (RandomAccessibleInterval< T >) imgLoader.getSetupImgLoader( vd.getViewSetupId() ).getImage( vd.getTimePointId(), LOAD_COMPLETELY );
		}

		return downsample( input, new long[]{ dsx, dsy, dsz } );
	}
	
	public static < T extends RealType<T> > RandomAccessibleInterval< T > downsample(
			RandomAccessibleInterval< T > input,
			final long[] downsampleFactors )
	{
		boolean is2d = input.numDimensions() == 2;
		
		long dsx = downsampleFactors[0];
		long dsy = downsampleFactors[1];
		long dsz = 1;
		if (!is2d)
			dsz = downsampleFactors[2];

		ImgFactory< T > f = null;

		if ( Img.class.isInstance( input ) )
			// factory is not implemented for e.g. LazyCellImg yet
			try
			{
				f = ((Img<T>)input).factory();
			} catch (UnsupportedOperationException e) {}

		if ( f == null )
			f = new ArrayImgFactory();
		
		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ true, false, false } );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, true, false } );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, f, new boolean[]{ false, false, true } );

		return input;
	}

	private static final boolean contains( final int i, final int[] values )
	{
		for ( final int j : values )
			if ( i == j )
				return true;

		return false;
	}
}
