package net.preibisch.stitcher.algorithm.lucaskanade;

import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;

public class AffineWarp implements WarpFunction
{
	final int n;
	final int m;

	public AffineWarp( final int numDimensions )
	{
		n = numDimensions;
		m = n * ( n + 1 );
	}

	@Override
	public int numParameters()
	{
		return m;
	}

	@Override
	public double partial( final RealLocalizable pos, final int d, final int param )
	{
		final int i = param / ( n + 1 );
		if ( i != d )
			return 0.0;
		final int j = param - i * ( n + 1 );
		return j == n ? 1.0 : pos.getDoublePosition( j );
	}

	@Override
	public AffineGet getAffine( final double[] p )
	{
		final AffineTransform t = new AffineTransform( n );
		int i = 0;
		for ( int r = 0; r < n; ++r )
			for ( int c = 0; c < n + 1; ++c )
				t.set( ( c==r ? 1 : 0 ) + p[ i++ ], r, c );
		return t;
	}
}
