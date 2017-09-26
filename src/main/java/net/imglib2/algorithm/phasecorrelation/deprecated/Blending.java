package net.imglib2.algorithm.phasecorrelation.deprecated;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.type.numeric.real.FloatType;

/** 
 * 
 * RealRandomAccess that computed cosine-blending for a certain interval
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 * @deprecated
 */
public class Blending implements RealRandomAccessible< FloatType >
{
	final Interval interval;
	final float[] border, blending;

	/**
	 * RealRandomAccess that computes a blending function for a certain {@link Interval}
	 * 
	 * @param interval - the interval it is defined on (return zero outside of it)
	 * @param border - how many pixels to skip before starting blending (on each side of each dimension)
	 * @param blending - how many pixels to compute the blending function on (on each side of each dimension)
	 */
	public Blending( final Interval interval, final float[] border, final float[] blending )
	{
		// in case the interval is actually image data re-instantiate just a simple FinalInterval
		this.interval = new FinalInterval( interval );
		this.border = border;
		this.blending = blending;
	}

	@Override
	public int numDimensions() { return interval.numDimensions(); }

	@Override
	public RealRandomAccess<FloatType> realRandomAccess()
	{
		return new BlendingRealRandomAccess( interval, border, blending );
	}

	@Override
	public RealRandomAccess<FloatType> realRandomAccess( final RealInterval interval )
	{
		return realRandomAccess();
	}
}
