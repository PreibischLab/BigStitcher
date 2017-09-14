package headless.rs.fit;

import java.util.ArrayList;
import java.util.Collection;

import mpicbg.models.AbstractModel;
import mpicbg.models.CoordinateTransform;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.NotEnoughDataPointsException;
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
public abstract class AbstractFunction< M extends AbstractFunction< M > > extends AbstractModel< M > implements Function< Point >
{
	@Override
	public int getMinNumMatches() { return getMinNumPoints(); }

	@Deprecated
	@Override
	public <P extends PointMatch> void fit( final Collection<P> matches ) throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		final ArrayList<Point> list = new ArrayList<Point>();
		
		for ( final P pm : matches )
			list.add( pm.getP1() );
		
		fitFunction( list );
	}

	@Override
	public double[] apply( final double[] location ) { return null; }

	@Override
	public void applyInPlace( final double[] location ) {}
}
