package headless.rs.fit;

import mpicbg.models.NoninvertibleModelException;

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
public class MatrixFunctions
{
	/**
	 * Cannot instantiate this class
	 */
	private MatrixFunctions() {}
	
	final public static void invert2x2( final float[] matrix )
	{
		final float a = matrix[ 0 ];
		final float b = matrix[ 1 ];
		final float c = matrix[ 2 ];
		final float d = matrix[ 3 ];
		
		final float det = 1.0f / ( a*d - b*c );
		
		matrix[ 0 ] = d * det;
		matrix[ 1 ] = -b * det;
		matrix[ 2 ] = -c * det;
		matrix[ 3 ] = a * det;
	}

	final public static void invert2x2( final double[] matrix )
	{
		final double a = matrix[ 0 ];
		final double b = matrix[ 1 ];
		final double c = matrix[ 2 ];
		final double d = matrix[ 3 ];
		
		final double det = 1.0 / ( a*d - b*c );
		
		matrix[ 0 ] = d * det;
		matrix[ 1 ] = -b * det;
		matrix[ 2 ] = -c * det;
		matrix[ 3 ] = a * det;
	}

	final static public void invert3x3( final double[] m ) throws NoninvertibleModelException
	{
		assert m.length == 9 : "Matrix3x3 supports 3x3 double[][] only.";
		
		final double det = det3x3( m );
		if ( det == 0 ) throw new NoninvertibleModelException( "Matrix not invertible." );
		
		final double i00 = ( m[ 4 ] * m[ 8 ] - m[ 5 ] * m[ 7 ] ) / det;
		final double i01 = ( m[ 2 ] * m[ 7 ] - m[ 1 ] * m[ 8 ] ) / det;
		final double i02 = ( m[ 1 ] * m[ 5 ] - m[ 2 ] * m[ 4 ] ) / det;
		
		final double i10 = ( m[ 5 ] * m[ 6 ] - m[ 3 ] * m[ 8 ] ) / det;
		final double i11 = ( m[ 0 ] * m[ 8 ] - m[ 2 ] * m[ 6 ] ) / det;
		final double i12 = ( m[ 2 ] * m[ 3 ] - m[ 0 ] * m[ 5 ] ) / det;
		
		final double i20 = ( m[ 3 ] * m[ 7 ] - m[ 4 ] * m[ 6 ] ) / det;
		final double i21 = ( m[ 1 ] * m[ 6 ] - m[ 0 ] * m[ 7 ] ) / det;
		final double i22 = ( m[ 0 ] * m[ 4 ] - m[ 1 ] * m[ 3 ] ) / det;
		
		m[ 0 ] = i00;
		m[ 1 ] = i01;
		m[ 2 ] = i02;

		m[ 3 ] = i10;
		m[ 4 ] = i11;
		m[ 5 ] = i12;

		m[ 6 ] = i20;
		m[ 7 ] = i21;
		m[ 8 ] = i22;
	}

	
	/**
	 * Calculate the determinant of a matrix given as a float[] (row after row).
	 * 
	 * @author Stephan Saalfeld
	 * 
	 * @param a matrix given row by row
	 * 
	 * @return determinant
	 */
	final static public double det3x3( final double[] a )
	{
		assert a.length == 9 : "Matrix3x3 supports 3x3 float[][] only.";
		
		return
			a[ 0 ] * a[ 4 ] * a[ 8 ] +
			a[ 3 ] * a[ 7 ] * a[ 2 ] +
			a[ 6 ] * a[ 1 ] * a[ 5 ] -
			a[ 2 ] * a[ 4 ] * a[ 6 ] -
			a[ 5 ] * a[ 7 ] * a[ 0 ] -
			a[ 8 ] * a[ 1 ] * a[ 3 ];
	}
	
}
