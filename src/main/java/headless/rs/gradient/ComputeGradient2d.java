package headless.rs.gradient;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;

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
public class ComputeGradient2d< T extends RealType< T > > implements ComputeGradient< T >
{
	final RandomAccess< T > randomAccess;
	
	public ComputeGradient2d( final RandomAccess< T > randomAccess )
	{
		this.randomAccess = randomAccess;
	}
	
	@Override
	public void gradientAt( final Localizable location, final double[] derivativeVector )
	{
		randomAccess.setPosition( location );
		
		// we need 4 points
		final double p0 = randomAccess.get().getRealDouble();
		randomAccess.fwd( 0 );
		final double p1 = randomAccess.get().getRealDouble();
		randomAccess.fwd( 1 );
		final double p3 = randomAccess.get().getRealDouble();
		randomAccess.bck( 0 );
		final double p2 = randomAccess.get().getRealDouble();
		
		derivativeVector[ 0 ] = ( ( (p1+p3) - (p0+p2) ) / 2.0 );
		derivativeVector[ 1 ] = ( ( (p2+p3) - (p0+p1) ) / 2.0 );
	}
}
