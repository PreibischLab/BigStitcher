package algorithm;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;

public class PairwiseStitching {

	public static <T extends RealType<T>, S extends RealType<S>> Pair<double[], Double> getShift(
			RandomAccessibleInterval<T> img1,
			RandomAccessibleInterval<S> img2,
			AbstractTranslation t1,
			AbstractTranslation t2)
	
	{
		//TODO: implement me!
		return null;		
		
	}
	
}
