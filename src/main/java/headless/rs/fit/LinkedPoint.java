package headless.rs.fit;

import net.imglib2.util.Util;
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
public class LinkedPoint<P> extends Point
{
	private static final long serialVersionUID = 1L;

	final P link;
	
	public LinkedPoint( final double[] l, final P link )
	{
		super( l.clone() );
		this.link = link;
	}

	public LinkedPoint( final double[] l, final double[] w, final P link )
	{
		super( l.clone(), w.clone() );
		this.link = link;
	}

	public P getLinkedObject() { return link; }
	
	public String toString() { return "LinkedPoint " + Util.printCoordinates( l ); }
}
