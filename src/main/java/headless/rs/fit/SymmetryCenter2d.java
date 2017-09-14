package headless.rs.fit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import net.imglib2.util.Util;

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
public class SymmetryCenter2d extends AbstractFunction<SymmetryCenter2d> implements SymmetryCenter<SymmetryCenter2d>
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -8877129758374682611L;

	/**
	 * We need at least 2 points to fit
	 */
	final int minNumPoints = 2;
	
	/**
	 * the symmetry center
	 */
	double xc, yc;

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
		final double[] delta = new double[ 4 ];
		final double[] tetha = new double[ 2 ];
		
		for ( final Point point : points )
		{
			final OrientedPoint p = (OrientedPoint)point;
			
			final double xk = p.getW()[ 0 ]; 
			final double yk = p.getW()[ 1 ]; 
			
			final double ak = p.getOrientationW()[ 0 ]; 
			final double bk = p.getOrientationW()[ 1 ]; 
			
			if ( ak == 0 && bk == 0 )
				continue;
			
			final double ak2 = ak*ak;
			final double bk2 = bk*bk;
			
			final double mk2 = ak2 + bk2;
			final double ab = ( ak * bk )/mk2;
			
			delta[ 0 ] += 1 - ak2/mk2;
			delta[ 1 ] -= ab;
			
			delta[ 2 ] -= ab;
			delta[ 3 ] += 1 - bk2/mk2;


			tetha[ 0 ] += xk * ( 1 - ( ak * ak )/mk2 ) - ( ak * bk * yk )/mk2;
			tetha[ 1 ] += yk * ( 1 - ( bk * bk )/mk2 ) - ( ak * bk * xk )/mk2;
		}
				
		MatrixFunctions.invert2x2( delta );
		
		this.xc = delta[ 0 ] * tetha[ 0 ] + delta[ 1 ] * tetha[ 1 ]; 
		this.yc = delta[ 2 ] * tetha[ 0 ] + delta[ 3 ] * tetha[ 1 ];
	}
	
	/**
	 * Compute the distance between a line defined by and {@link OrientedPoint} and this {@link SymmetryCenter2d}
	 */
	@Override
	public double distanceTo( final Point point )
	{
		final OrientedPoint p = (OrientedPoint)point;

		final double xk = p.getW()[ 0 ]; 
		final double yk = p.getW()[ 1 ]; 

		final double ak = p.getOrientationW()[ 0 ]; 
		final double bk = p.getOrientationW()[ 1 ]; 

		final double dx = xk - xc;
		final double dy = yk - yc;
		
		final double tmp1 = ak*dx + bk*dy;
		
		return ( dx*dx + dy*dy ) - ( ( tmp1 * tmp1 )/( ak*ak + bk*bk ) );
	}

	@Override
	public int getMinNumPoints() { return minNumPoints; }

	public double getXc() { return xc; }
	public double getYc() { return yc; }

	@Override
	public double getSymmetryCenter( final int d )
	{
		if ( d == 0 )
			return xc;
		else
			return yc;
	}

	@Override
	public void getSymmetryCenter( final double center[] )
	{
		center[ 0 ] = xc;
		center[ 1 ] = yc;
	}

	@Override
	public void getSymmetryCenter( final float center[] )
	{
		center[ 0 ] = (float)xc;
		center[ 1 ] = (float)yc;
	}

	@Override
	public void set( final SymmetryCenter2d m )
	{
		this.xc = m.getXc();
		this.yc = m.getYc();
		this.setCost( m.getCost() );
	}

	@Override
	public SymmetryCenter2d copy() 
	{
		final SymmetryCenter2d center = new SymmetryCenter2d();

		center.xc = this.xc;
		center.yc = this.yc;
		
		center.setCost( this.getCost() );
		
		return center;
	}
	
	@Override
	public int numDimensions() { return 2; }	

	public static void main( String[] args ) throws NotEnoughDataPointsException
	{
		final Random rnd = new Random( 345 );
		final ArrayList< Point > list = new ArrayList<Point>();
		
		
		final double c[] = new double[]{ rnd.nextFloat()*2-1, rnd.nextFloat()*2-1 };
		System.out.println( "Center should be: " + Util.printCoordinates( c ) );
		
		for ( int i = 0; i < 10; ++i )
		{
			final double v[] = new double[]{ rnd.nextFloat()*2-1, rnd.nextFloat()*2-1 };
			final double p[] = new double[]{ c[ 0 ] - v[ 0 ]*2.3f, c[ 1 ] - v[ 1 ]*2.3f };
		
			list.add( new OrientedPoint( p, v, 1 ) );
		}
		
		//list.add( new OrientedPoint( new float[]{ -1, 0, 0 }, new float[]{ 1, 0, 0 }, 1 ) );
		//list.add( new OrientedPoint( new float[]{ 0, -5, 0 }, new float[]{ 0, 1, 0 }, 1 ) );
		//list.add( new OrientedPoint( new float[]{ 0.0f, 0, -5 }, new float[]{ 0, 0, 1 }, 1 ) );
		
		final SymmetryCenter2d center = new SymmetryCenter2d();
		
		center.fitFunction( list );
		
		System.out.println( "center: " + center.xc + " " + center.yc );
		
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
	}
}
