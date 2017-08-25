package headless.rs.fit;

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
public class OrientedPoint extends Point
{
	private static final long serialVersionUID = -5947160364235033692L;

	/**
	 * Orientation in local coordinates
	 */
	protected final double[] ol;

	/**
	 * Orientation in world coordinates
	 */
	protected final double[] ow;

	/**
	 * gradient magnitude
	 */
	protected final float magnitude;

	// TODO: Multithreading-save
	protected final double[] tmp;
	
	public OrientedPoint( final double[] position, final double[] vector, final float magnitude )
	{
		super( position );

		ol = vector;
		ow = ol.clone();
		this.magnitude = magnitude;
		
		tmp = new double[ ol.length ];
	}
	
	/**
	 * @return The magnitude of the orientation/gradient
	 */
	public double getMagnitude(){ return magnitude; }	

	/**
	 * @return The orientation/gradient in local coordinates
	 */
	public double[] getOrientationL(){ return ol; }
	
	/**
	 * @return The orientation/gradient in world coordinates
	 */
	public double[] getOrientationW(){ return ow; }
	
	/**
	 * angle between this points orientation
	 * the direction from this point to p (in world coordinates).
	 * 
	 * @param p
	 * @return angle in radians [0, pi]
	 */
	public double angleTo( Point p )
	{
		double len = 0;
		for ( int d = 0; d < ow.length; ++d )
		{
			tmp[ d ] = p.getW()[ d ] - w[ d ];
			len += tmp[ d ] * tmp[ d ];
		}
		len = Math.sqrt( len );

		double dot = 0;
		for ( int d = 0; d < ow.length; ++d )
		{
			dot += ow[ d ] * tmp[ d ] / len;
		}
		return Math.acos( dot );
	}
}
