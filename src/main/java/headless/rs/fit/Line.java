package headless.rs.fit;

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.models.IllDefinedDataPointsException;
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
public class Line extends AbstractFunction<Line>
{
	final int minNumPoints = 2;
	
	double n, m;	

	/**
	 * @return - the center of the circle in x
	 */
	public double getN() { return n; }

	/**
	 * @return - the center of the circle in y
	 */
	public double getM() { return m; }
	
	@Override
	public int getMinNumPoints() { return minNumPoints; }

	public void fitFunction( final Collection<Point> points ) throws NotEnoughDataPointsException
	{
		final int numPoints = points.size();
		
		if ( numPoints < minNumPoints )
			throw new NotEnoughDataPointsException( "Not enough points, at least " + minNumPoints + " are necessary." );
		
		// compute matrices
		final double[] delta = new double[ 4 ];
		final double[] tetha = new double[ 2 ];
		
		for ( final Point p : points )
		{
			final double x = p.getW()[ 0 ]; 
			final double y = p.getW()[ 1 ]; 
			
			final double xx = x*x;
			final double xy = x*y;
			
			delta[ 0 ] += xx;
			delta[ 1 ] += x;
			delta[ 2 ] += x;
			delta[ 3 ] += 1;
			
			tetha[ 0 ] += xy;
			tetha[ 1 ] += y;
		}
				
		// invert matrix
		MatrixFunctions.invert2x2( delta );
		
		this.m = delta[ 0 ] * tetha[ 0 ] + delta[ 1 ] * tetha[ 1 ];
		this.n = delta[ 2 ] * tetha[ 0 ] + delta[ 3 ] * tetha[ 1 ];
	}

	@Override
	public double distanceTo( final Point point )
	{
		final double x1 = point.getW()[ 0 ]; 
		final double y1 = point.getW()[ 1 ];
		
		
		return Math.abs( y1 - m*x1 - n ) / ( Math.sqrt( m*m + 1 ) );
	}
	
	public static int i = 0;
	
	@Override
	public void set( final Line m )
	{
		this.n = m.getN();
		this.m = m.getM();
		this.setCost( m.getCost() );
	}

	@Override
	public Line copy()
	{
		Line c = new Line();
		
		c.n = getN();
		c.m = getM();
		c.setCost( getCost() );
		
		return c;
	}
	
	public static void main( String[] args ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		final ArrayList< Point > points = new ArrayList<Point>();

		points.add( new Point( new double[]{ 1f, -3.95132f } ) );
		points.add( new Point( new double[]{ 2f, 6.51205f } ) );
		points.add( new Point( new double[]{ 3f, 18.03612f } ) );
		points.add( new Point( new double[]{ 4f, 28.65245f } ) );
		points.add( new Point( new double[]{ 5f, 42.05581f } ) );
		points.add( new Point( new double[]{ 6f, 54.01327f } ) );
		points.add( new Point( new double[]{ 7f, 64.58747f } ) );
		points.add( new Point( new double[]{ 8f, 76.48754f } ) );
		points.add( new Point( new double[]{ 9f, 89.00033f } ) );
		
		final ArrayList< PointFunctionMatch > candidates = new ArrayList<PointFunctionMatch>();
		final ArrayList< PointFunctionMatch > inliers = new ArrayList<PointFunctionMatch>();
		
		for ( final Point p : points )
			candidates.add( new PointFunctionMatch( p ) );
		
		final Line l = new Line();
		
		l.ransac( candidates, inliers, 100, 0.1, 0.5 );
		
		System.out.println( inliers.size() );
		
		l.fit( inliers );
		
		System.out.println( "y = " + l.m + " x + " + l.n );
		for ( final PointFunctionMatch p : inliers )
			System.out.println( l.distanceTo( p.getP1() ) );
		
		//System.out.println( l.distanceTo( new Point( new float[]{ 1f, 0f } ) ) );
	}
}
