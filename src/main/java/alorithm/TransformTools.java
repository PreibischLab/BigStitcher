package alorithm;

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
	
	public static FinalRealInterval getLocalOverlap(RealInterval img, RealInterval overlap){
		final int n = img.numDimensions();
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = img.realMin(i) - overlap.realMin(i);
			max[i] = img.realMax(i) - overlap.realMax(i);
		}
		return new FinalRealInterval(min, max);
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
