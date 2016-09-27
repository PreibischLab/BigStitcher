package algorithm.lucaskanade;

import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;

public class TranslationWarp implements WarpFunction
{
	final int n;

	public TranslationWarp(int nDimensions)
	{
		this.n = nDimensions;
	}
	@Override
	public int numParameters()
	{
		return n;
	}

	@Override
	public double partial(RealLocalizable pos, int d, int param)
	{
		return (d == param) ? 1.0 : 0.0;
	}

	@Override
	public AffineGet getAffine(double[] p)
	{
		final AffineTransform3D res = new AffineTransform3D();
		for (int i = 0; i < n; ++i)
			res.set( p[i], i, n );
		return res;
	}

}
