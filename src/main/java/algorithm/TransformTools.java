package algorithm;

import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AbstractTranslation;

public class TransformTools {
	
	public static FinalRealInterval applyTranslation(RealInterval img, AbstractTranslation translation){
		final int n = img.numDimensions();
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = img.realMin(i) + translation.getTranslation(i);
			max[i] = img.realMax(i) + translation.getTranslation(i);
		}
		return new FinalRealInterval(min, max);
	}
	
	/**
	 * get overlap in local image coordinates (assuming min = (0,0,..))
	 * @param img
	 * @param overlap
	 * @return
	 */
	public static FinalRealInterval getLocalOverlap(RealInterval img, RealInterval overlap){
		final int n = img.numDimensions();
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = Math.max(0, overlap.realMin(i) - img.realMin(i)) ;
			max[i] = Math.max(0,overlap.realMax(i) - img.realMax(i));
		}
		return new FinalRealInterval(min, max);
	}
	
	/**
	 * create an integer interval from real interval, being conservatie on the size
	 * (min is ceiled, max is floored)
	 * @param overlap
	 * @return
	 */
	public static FinalInterval getLocalRasterOverlap(RealInterval overlap)
	{
		final int n = overlap.numDimensions();
		final long [] min = new long [n];
		final long [] max = new long [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = Math.round(Math.ceil(overlap.realMin(i)));
			max[i] = Math.round(Math.floor(overlap.realMax(i)));			
		}
		
		return new FinalInterval(min, max);
	}
	
	
	public static FinalRealInterval getOverlap(final RealInterval img1, final RealInterval img2){
		final int n = img1.numDimensions();
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = Math.max(img1.realMin(i), img2.realMin(i));
			max[i] = Math.min(img1.realMax(i), img2.realMax(i));
			
			// intervals do not overlap
			if ( max[i] < min [i])
				return null;
		}
		
		return new FinalRealInterval(min, max);
	}
	
	

}
