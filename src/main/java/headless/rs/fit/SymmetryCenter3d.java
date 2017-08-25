package headless.rs.fit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import net.imglib2.util.Util;


import mpicbg.models.NoninvertibleModelException;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;

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
public class SymmetryCenter3d extends AbstractFunction<SymmetryCenter3d> implements SymmetryCenter<SymmetryCenter3d>
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 6317425588261467332L;

	/**
	 * We need at least 2 points to fit
	 */
	final int minNumPoints = 2;
	
	/**
	 * the symmetry center
	 */
	double xc, yc, zc;
	
	/**
	 * Fit the function to a list of {@link OrientedPoint}s.
	 */
	@Override
	public void fitFunction( final Collection<Point> points ) throws NotEnoughDataPointsException
	{
		final int numPoints = points.size();
		
		if ( numPoints < minNumPoints )
			throw new NotEnoughDataPointsException( "Not enough points, at least " + minNumPoints + " are necessary." );
		
		// compute matrices
		final double[] delta = new double[ 9 ];
		final double[] tetha = new double[ 3 ];

		double sumW = 0;
		for ( final Point point : points )
		{
			final OrientedPoint p = (OrientedPoint)point;
			sumW += Spot.length( p.getOrientationW() );
		}

		for ( final Point point : points )
		{
			final OrientedPoint p = (OrientedPoint)point;
			final double w = Spot.length( p.getOrientationW() ) / sumW;

			final double xk = p.getW()[ 0 ]; 
			final double yk = p.getW()[ 1 ]; 
			final double zk = p.getW()[ 2 ]; 

			final double ak = p.getOrientationW()[ 0 ]; 
			final double bk = p.getOrientationW()[ 1 ]; 
			final double ck = p.getOrientationW()[ 2 ];
			
			if ( ak == 0 && bk == 0 && ck == 0 )
				continue;
			
			final double ak2 = ak*ak;
			final double bk2 = bk*bk;
			final double ck2 = ck*ck;
			
			final double mk2 = ak2 + bk2 + ck2;
			final double ab = ( ak * bk )/mk2;
			final double ac = ( ak * ck )/mk2;
			final double bc = ( bk * ck )/mk2;
			
			delta[ 0 ] += w*(1 - ak2/mk2);
			delta[ 1 ] -= w*ab;
			delta[ 2 ] -= w*ac;
			
			delta[ 3 ] -= w*ab;
			delta[ 4 ] += w*(1 - bk2/mk2);
			delta[ 5 ] -= w*bc;

			delta[ 6 ] -= w*ac;
			delta[ 7 ] -= w*bc;
			delta[ 8 ] += w*(1 - ck2/mk2);

			tetha[ 0 ] += w* (xk * ( 1 - ( ak * ak )/mk2 ) - ( ak * ck * zk )/mk2 - ( ak * bk * yk )/mk2);
			tetha[ 1 ] += w* (yk * ( 1 - ( bk * bk )/mk2 ) - ( ak * bk * xk )/mk2 - ( bk * ck * zk )/mk2);
			tetha[ 2 ] += w* (zk * ( 1 - ( ck * ck )/mk2 ) - ( ak * ck * xk )/mk2 - ( bk * ck * yk )/mk2);
		}
				
		try
		{
			MatrixFunctions.invert3x3( delta );
		}
		catch ( NoninvertibleModelException e )
		{
			xc = yc = zc = Double.NaN;
			//System.out.println( "Cannot determine center, cannot compute determinant." );
			return;
		}
		
		this.xc = delta[ 0 ] * tetha[ 0 ] + delta[ 1 ] * tetha[ 1 ] + delta[ 2 ] * tetha[ 2 ]; 
		this.yc = delta[ 3 ] * tetha[ 0 ] + delta[ 4 ] * tetha[ 1 ] + delta[ 5 ] * tetha[ 2 ];
		this.zc = delta[ 6 ] * tetha[ 0 ] + delta[ 7 ] * tetha[ 1 ] + delta[ 8 ] * tetha[ 2 ];

	}
	
	/**
	 * Compute the distance between a line defined by and {@link OrientedPoint} and this {@link SymmetryCenter3d}
	 */
	@Override
	public double distanceTo( final Point point )
	{
		final OrientedPoint p = (OrientedPoint)point;

		final double xk = p.getW()[ 0 ]; 
		final double yk = p.getW()[ 1 ]; 
		final double zk = p.getW()[ 2 ]; 

		final double ak = p.getOrientationW()[ 0 ]; 
		final double bk = p.getOrientationW()[ 1 ]; 
		final double ck = p.getOrientationW()[ 2 ]; 

		final double dx = xk - xc;
		final double dy = yk - yc;
		final double dz = zk - zc;
		
		final double tmp1 = ak*dx + bk*dy + ck*dz;
		
		return ( dx*dx + dy*dy + dz*dz ) - ( ( tmp1 * tmp1 )/( ak*ak + bk*bk + ck*ck ) );
	}

	@Override
	public int getMinNumPoints() { return minNumPoints; }

	public double getXc() { return xc; }
	public double getYc() { return yc; }
	public double getZc() { return zc; }
	
	@Override
	public double getSymmetryCenter( final int d )
	{
		if ( d == 0 )
			return xc;
		else if ( d == 1 )
			return yc;
		else
			return zc;
	}

	@Override
	public void getSymmetryCenter( final double center[] )
	{
		center[ 0 ] = xc;
		center[ 1 ] = yc;
		center[ 2 ] = zc;
	}

	@Override
	public void getSymmetryCenter( final float center[] )
	{
		center[ 0 ] = (float)xc;
		center[ 1 ] = (float)yc;
		center[ 2 ] = (float)zc;
	}

	@Override
	public void set( final SymmetryCenter3d m )
	{
		this.xc = m.getXc();
		this.yc = m.getYc();
		this.zc = m.getZc();
		this.setCost( m.getCost() );
	}

	@Override
	public SymmetryCenter3d copy() 
	{
		final SymmetryCenter3d center = new SymmetryCenter3d();

		center.xc = this.xc;
		center.yc = this.yc;
		center.zc = this.zc;
		
		center.setCost( this.getCost() );
		
		return center;
	}

	@Override
	public int numDimensions() { return 2; }	

	public static void main( String[] args ) throws NotEnoughDataPointsException
	{
		final Random rnd = new Random( 345 );
		final ArrayList< Point > list = new ArrayList<Point>();
		
		
		final double c[] = new double[]{ rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1 };
		System.out.println( "Center should be: " + Util.printCoordinates( c ) );
		
		for ( int i = 0; i < 10; ++i )
		{
			final double v[] = new double[]{ rnd.nextFloat()*2-1, rnd.nextFloat()*2-1, rnd.nextFloat()*2-1 };
			final double p[] = new double[]{ c[ 0 ] - v[ 0 ]*2.3f, c[ 1 ] - v[ 1 ]*2.3f, c[ 2 ] - v[ 2 ]*2.3f };
		
			list.add( new OrientedPoint( p, v, 1 ) );
		}
		
		//list.add( new OrientedPoint( new float[]{ -1, 0, 0 }, new float[]{ 1, 0, 0 }, 1 ) );
		//list.add( new OrientedPoint( new float[]{ 0, -5, 0 }, new float[]{ 0, 1, 0 }, 1 ) );
		//list.add( new OrientedPoint( new float[]{ 0.0f, 0, -5 }, new float[]{ 0, 0, 1 }, 1 ) );
		
		final SymmetryCenter3d center = new SymmetryCenter3d();
		
		center.fitFunction( list );
		
		System.out.println( "center: " + center.xc + " " + center.yc + " " + center.zc );
		
		for ( final Point p : list )
		{
			System.out.println( "Distance: " + center.distanceTo( p ) );
		}
	}

	@Override
	public void setSymmetryCenter( final double center, final int d )
	{
		if ( d == 0 )
			xc = center;
		else if ( d == 1 )
			yc = center;
		else if ( d == 2 )
			zc = center;
	}
}
