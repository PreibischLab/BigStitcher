package net.imglib2.algorithm.phasecorrelation;

import java.util.ArrayList;

import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class FourNeighborhoodExtrema
{
	public static < T extends RealType< T > > ArrayList< Pair< Localizable, Double > > findMax( final RandomAccessible< T > img, final Interval region, final int maxN )
	{
		final Cursor< T > c = Views.iterable( Views.interval( img, region ) ).localizingCursor();
		final RandomAccess< T > r = img.randomAccess();
		final int n = img.numDimensions();

		final ArrayList< Pair< Localizable, Double > > list = new ArrayList< Pair< Localizable, Double > >();

		for ( int i = 0; i < maxN; ++i )
			list.add( new ValuePair< Localizable, Double >( null, -Double.MAX_VALUE ) );

A:		while ( c.hasNext() )
		{
			final double type = c.next().getRealDouble();
			r.setPosition( c );

			for ( int d = 0; d < n; ++d )
			{
				r.fwd( d );
				if ( type < r.get().getRealDouble() )
					continue A;
	
				r.bck( d );
				r.bck( d );
				
				if ( type < r.get().getRealDouble() )
					continue A;

				r.fwd( d );
			}

			
			for ( int i = maxN - 1; i >= 0; --i )
			{
				if ( type < list.get( i ).getB() )
				{
					if ( i == maxN - 1 )
					{
						continue A;
					}
					else
					{
						list.add( i + 1, new ValuePair< Localizable, Double >( new Point( c ), type ) );
						list.remove( maxN );
						continue A;
					}
				}
			}

			list.add( 0, new ValuePair< Localizable, Double >( new Point( c ), type ) );
			list.remove( maxN );
		}

		// remove all null elements
		for ( int i = maxN -1; i >= 0; --i )
			if ( list.get( i ).getA() == null )
				list.remove(  i );

		return list;
	}

	public static void main( String[] args )
	{
		int maxN = 3;
		ArrayList< Double > list = new ArrayList< Double >();

		list.add( 5.0 );
		list.add( 2.0 );
		list.add( 0.0 );

		double type = 10;
		
		for ( int i = maxN - 1; i >= 0; --i )
		{
			if ( type < list.get( i ) )
			{
				if ( i == maxN - 1 )
				{
					printList( list );
					System.exit( 0 );
				}
				else
				{
					list.add( i + 1, type );
					list.remove( maxN );
					printList( list );
					System.exit( 0 );
				}
			}
		}

		list.add( 0, type );
		list.remove( maxN );
		printList( list );
	}
	
	public static void printList( ArrayList< Double > list )
	{
		for ( double d : list )
			System.out.println( d );
	}
}