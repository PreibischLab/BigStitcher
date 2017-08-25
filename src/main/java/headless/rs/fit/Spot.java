package headless.rs.fit;

import java.util.ArrayList;

import headless.rs.gradient.Gradient;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * Radial Symmetry Package
 *
 * This software is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de) & Timothee Lionnet
 */
public class Spot implements RealLocalizable
{
	public final SymmetryCenter< ? extends SymmetryCenter< ? > > center;// = new SymmetryCenter3d();
	public final ArrayList< PointFunctionMatch > candidates = new ArrayList<PointFunctionMatch>();
	public final ArrayList< PointFunctionMatch > inliers = new ArrayList<PointFunctionMatch>();
	public final double[] scale = new double[]{ 1, 1, 1 };

	public int numRemoved = -1;
	public double avgCost = -1, minCost = -1, maxCost = -1;

	// from where it was ran
	public long[] loc = null;

	public final int n;

	public Spot( final int n )
	{
		this.n = n;

		if ( n == 2 )
			center = new SymmetryCenter2d();
		else if ( n == 3 )
			center = new SymmetryCenter3d();
		else
			throw new RuntimeException( "only 2d and 3d is allowed." );
	}

	// public void setOriginalLocation(int[] loc) { this.loc = loc; }
	public void setOriginalLocation(long[] loc) { this.loc = loc; }
	public long[] getOriginalLocation() { return loc; }

	@Override
	public String toString()
	{
		String result = "center: ";

		for ( int d = 0; d < n; ++d )
			result += center.getSymmetryCenter( d )/scale[ d ] + " ";

		result += " Removed = " + numRemoved + "/" + candidates.size() + " error = " + minCost + ";" + avgCost + ";" + maxCost;

		return result;
	}

	public double[] getCenter()
	{
		final double[] c = new double[ n ];

		getCenter( c );

		return c; 
	}

	public void getCenter( final double[] c )
	{ 
		for ( int d = 0; d < n; ++d )
			c[ d ] = center.getSymmetryCenter( d ) / scale[ d ];
	}

	public double computeAverageCostCandidates() { return computeAverageCost( candidates ); }
	public double computeAverageCostInliers() { return computeAverageCost( inliers ); }

	public double computeAverageCost( final ArrayList< PointFunctionMatch > set )
	{
		if ( set.size() == 0 )
		{
			minCost = Double.MAX_VALUE;
			maxCost = Double.MAX_VALUE;
			avgCost = Double.MAX_VALUE;

			return avgCost;
		}

		minCost = Double.MAX_VALUE;
		maxCost = 0;
		avgCost = 0;

		for ( final PointFunctionMatch pm : set )
		{
			pm.apply( center );
			final double d = pm.getDistance();

			avgCost += d;
			minCost = Math.min( d, minCost );
			maxCost = Math.max( d, maxCost );
		}

		avgCost /= set.size();

		return avgCost;
	}

	public void updateScale( final float[] scale )
	{
		for ( final PointFunctionMatch pm : candidates )
		{
			final OrientedPoint p = (OrientedPoint)pm.getP1();

			final double[] l = p.getL();
			final double[] w = p.getW();
			final double[] ol = p.getOrientationL();
			final double[] ow = p.getOrientationW();

			for ( int d = 0; d < l.length; ++d )
			{
				w[ d ] = l[ d ] * scale[ d ];
				ow[ d ] = ol[ d ] / scale[ d ];
			}
		}

		for ( int d = 0; d < scale.length; ++d )
			this.scale[ d ] = scale[ d ];
	}

	// searches for spots in the image which is the part of the fullImage
	/**
	 * 
	 * @param interval - where to extract the spots from (must be the interval from which the gradient was computed)
	 * @param peaks - the peaks for which to extract a spot (that includes the gradients)
	 * @param derivative - the derivative image
	 * @param spotSize - the support region for each spot
	 * 
	 * @return
	 */
	public static <T extends RealType<T> > ArrayList< Spot > extractSpots(
			final ArrayList< long[] > peaks,
			final Gradient derivative,
			final long[] spotSize )
	{
		// size around the detection to use (spotSize)
		// we detect at 0.5, 0.5, 0.5 - so we need an even size (as 2 pixels have been condensed into 1 when computing the gradient)
		// no gradient: 0-1-2 (to compute something for 1 we need three pixels)
		// gradient: 0,5-1,5 (to compute something for 1 we need two pixels, because pixel 0 is actually at position 0,5 and pixel 1 at position 1,5 --- 0,5-pixel-shift)

		final int numDimensions = derivative.numDimensions();
		final long[] min = new long[ numDimensions ];
		final long[] max = new long[ numDimensions ];

		final ArrayList< Spot > spots = new ArrayList<>();

		final Gradient gradient = derivative;

		for ( final long[] peak : peaks )
		{
			final Spot spot = new Spot( numDimensions );
			spot.setOriginalLocation( peak );

			// this part defines the possible values		
			for ( int e = 0; e < numDimensions; ++e )
			{
				min[ e ] = peak[ e ] - spotSize[ e ] / 2;
				max[ e ] = min[ e ] + spotSize[ e ] - 1;
			}

			final FinalInterval spotInterval = new FinalInterval( min, max );

			// define a local region to iterate around the potential detection
			final IntervalIterator cursor = new IntervalIterator( spotInterval );

			while ( cursor.hasNext() )
			{
				cursor.fwd();

				final double[] v = new double[ numDimensions ];
				gradient.gradientAt( cursor, v );
				
				if ( length( v ) != 0 )
				{
					final double[] p = new double[ numDimensions ];

					for ( int e = 0; e < numDimensions; ++e )
						p[ e ] = cursor.getIntPosition( e ) + 0.5f; // we add 0,5 to correct for the half-pixel-shift of the gradient image (because pixel 0 is actually at position 0,5 and pixel 1 at position 1,5)

					spot.candidates.add( new PointFunctionMatch( new OrientedPoint( p, v, 1 ) ) );
				}
			}
			spots.add( spot );
		}
		return spots;
	}
	
	public static void fitCandidates( final ArrayList< Spot > spots ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		for ( final Spot spot : spots )
			spot.center.fit( spot.candidates );
	}

	public static void fitInliers( final ArrayList< Spot > spots ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		for ( final Spot spot : spots )
			spot.center.fit( spot.inliers );
	}

	public static void ransac( final ArrayList< Spot > spots, final int iterations, final double maxError, final double inlierRatio )
	{
		// TODO: This is only to make it reproducible
		//AbstractModel.resetRandom();

		for ( final Spot spot : spots )
		{
			try 
			{
				ransac( spot, iterations, maxError, inlierRatio );
			} 
			catch (NotEnoughDataPointsException e) 
			{
				spot.inliers.clear();
				spot.numRemoved = spot.candidates.size();
				System.out.println( "e1" );
			}
			catch (IllDefinedDataPointsException e)
			{
				spot.inliers.clear();
				spot.numRemoved = spot.candidates.size();
				System.out.println( "e2" );
			}
		}
	}

	public static void ransac( final Spot spot, final int iterations, final double maxError, final double inlierRatio ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		spot.center.ransac( spot.candidates, spot.inliers, iterations, maxError, inlierRatio );
		spot.numRemoved = spot.candidates.size() - spot.inliers.size();

		if ( spot.inliers.size() >= spot.center.getMinNumPoints() )
			spot.center.fit( spot.inliers );
	}

	public static <T extends RealType<T> > void drawRANSACArea( final ArrayList< Spot > spots, final RandomAccessibleInterval< T > draw)
	{
		final int numDimensions = draw.numDimensions();

		for ( final Spot spot : spots )
		{
			if ( spot.inliers.size() == 0 )
				continue;
			// Using extension because sometimes spots are not fully inside of the image
			final RandomAccess< T > drawRA =  Views.extendMirrorSingle(draw).randomAccess();
			final double[] scale = spot.scale;

			for ( final PointFunctionMatch pm : spot.inliers )
			{
				final Point p = pm.getP1();
				for ( int d = 0; d < numDimensions; ++d )
					drawRA.setPosition( Math.round( p.getW()[ d ]/scale[ d ] ), d );
				// set color to error value
				drawRA.get().setReal(pm.getDistance());
			}
		}
	}
	
	public static double length( final double[] f )
	{
		double l = 0;

		for ( final double v : f )
			l += v*v;

		l = Math.sqrt( l );

		return l;
	}

	public static void norm( final double[] f )
	{
		double l = length( f );

		if ( l == 0 )
			return;

		for ( int i = 0; i < f.length; ++i )
			f[ i ] = ( f[ i ] / l );
	}

	@Override
	public int numDimensions() { return n; }

	@Override
	public void localize( final float[] position ) { center.getSymmetryCenter( position ); }

	@Override
	public void localize( final double[] position ) { center.getSymmetryCenter( position ); }

	@Override
	public float getFloatPosition( final int d ) { return (float)center.getSymmetryCenter( d ); }

	@Override
	public double getDoublePosition( final int d ) { return center.getSymmetryCenter( d ); }	
}
