package algorithm.lucaskanade;

import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineGet;

public interface WarpFunction
{
	/**
	 * @return the number of parameters of the warp function.
	 */
	public int numParameters();

	/**
	 * Compute the partial derivative of the <em>d</em>th dimension of the
	 * warped image coordinate by the <em>param</em>th parameter of the warp
	 * function. The partial derivative is evaluated at at the given coordinates
	 * and the parameter vector <em>p=0</em>.
	 *
	 * @param pos
	 *            coordinates at which to evaluate the derivative.
	 * @param d
	 *            dimension of the warped image coordinate whose derivative to
	 *            take.
	 * @param param
	 *            parameter by which to derive.
	 * @return the partial derivative
	 */
	public double partial( RealLocalizable pos, int d, int param );

	/**
	 * get affine transform corresponding to a given parameter vector.
	 *
	 * @param p parameter vector
	 * @return the transform
	 */
	public AffineGet getAffine( double[] p );
}