package headless.rs.gradient;

import net.imglib2.Cursor;
import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * Computes the derivative on demand at a certain location, this is useful if it is only a few spots in a big image 
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
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
public class GradientPreCompute extends Gradient
{
	final RandomAccess< FloatType > randomAccess;
	final int n1, n2;
	
	// we need this to iterate the correct area for pre-computation
	final long[] tmp;
	final long[] minIterate; 
	final long[] maxIterate; 

	public GradientPreCompute( final RandomAccessibleInterval<FloatType> source )
	{
		super( source.numDimensions() );

		this.n1 = source.numDimensions();
		this.n2 = n1 + 1;
		this.minIterate = new long[ n1 ];
		this.maxIterate = new long[ n1 ];
		this.tmp  = new long[ n2 ];

		final Img< FloatType > d = preCompute( source );
		this.randomAccess = d.randomAccess();
	}

	public Img< FloatType > preCompute( final RandomAccessibleInterval<FloatType> source )
	{
		return preCompute( source, minIterate, maxIterate, n1, n2 );
	}

	public static Img< FloatType > preCompute( final RandomAccessibleInterval<FloatType> source, final long[] minIterate, final long[] maxIterate, final int n1, final int n2 )
	{
		final long[] tmp  = new long[ n2 ];

		// we need the extra dimension to store "n" values (except it would be one-dimensional)
		final long[] dim = new long[ n2 ];
				
		// we always compute at [0.5, 0.5, ... 0.5], so we cannot compute it for the last value of each dimension
		for ( int d = 0; d < n1; ++d )
		{
			dim[ d ] = source.dimension( d ) - 1;
			minIterate[ d ] = source.min( d );
			maxIterate[ d ] = source.max( d ) - 1;
		}
				
		// "n" values per location
		dim[ n1 ] = n1;
		
		// where to store the precomputed derivatives
		final Img< FloatType > derivatives = new ArrayImgFactory<FloatType>().create( dim, new FloatType() );
			
		// we use a local derivative on demand so that we do not need to duplicate code
		final GradientOnDemand derivativeOnDemand = new GradientOnDemand( source );
		
		// create an interval 
		final RandomAccessibleInterval< FloatType > interval = Views.interval( source, minIterate, maxIterate );
		
		// iterate it and populate the precomputed array
		final Cursor< FloatType > cursor = Views.iterable( interval ).localizingCursor();
		final RandomAccess< FloatType > randomAccess = derivatives.randomAccess();
		final double[] derivativeVector = new double[ n1 ];
		
		while ( cursor.hasNext() )
		{
			cursor.fwd();
			
			// compute the derivative
			derivativeOnDemand.gradientAt( cursor, derivativeVector );
			
			// where to put the computed derivatives
			// PROBLEM: we translate [min_x, min_y, ...] to [0,0,...] because we save it in an Img
			for ( int d = 0; d < n1; ++d )
				tmp[ d ] = cursor.getLongPosition( d ) - minIterate[ d ];
			
			tmp[ n1 ] = 0;
			randomAccess.setPosition( tmp );
			randomAccess.get().setReal( derivativeVector[ 0 ] );
			
			for ( int d = 1; d < n1; ++d )
			{
				randomAccess.fwd( n1 );
				randomAccess.get().setReal( derivativeVector[ d ] );
			}
		}
		
		return derivatives;
	}

	@Override
	public void gradientAt( final Localizable location, final double[] derivativeVector )
	{
		// where to read the computed derivatives
		// PROLBEM SOLVED: also correct for the introduced offset when returning the gradients
		for ( int d = 0; d < n1; ++d )
			tmp[ d ] = location.getLongPosition( d ) - minIterate[ d ];
		
		tmp[ n1 ] = 0;
		randomAccess.setPosition( tmp );
		
		derivativeVector[ 0 ] = randomAccess.get().get();
		
		for ( int d = 1; d < n1; ++d )
		{
			randomAccess.fwd( n1 );
			derivativeVector[ d ] = randomAccess.get().get();
		}
	}

}
