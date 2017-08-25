package headless.rs.fit;

import mpicbg.models.CoordinateTransform;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;

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
public class PointFunctionMatch extends PointMatch
{
	private static final long serialVersionUID = -8070932126418631690L;

	//final protected Function<Point> function;

	double distance = 0;
	
	public PointFunctionMatch( final Point p1 )
	{
		super( p1, null );
	}
	
	//public Function<Point> getFunction() { return function; }

	/**
	 * 	Here one could compute and return the closest point on the function to p1,
	 *  but it is not well defined as there could be more than one...
	 */
	@Deprecated
	@Override
	public Point getP2() { return null; }
	
	public void apply( final CoordinateTransform t )
	{
		distance = (float)((Function<Point>)t).distanceTo( p1 );
	}
	
	public void apply( final CoordinateTransform t, final float amount )
	{
		distance = (float)((Function<Point>)t).distanceTo( p1 );
	}
	
	@Override
	public double getDistance() { return distance; }
}
