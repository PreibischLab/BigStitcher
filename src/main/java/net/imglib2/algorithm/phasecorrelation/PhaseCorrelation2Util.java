package net.imglib2.algorithm.phasecorrelation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.IterableInterval;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;



public class PhaseCorrelation2Util {
	
	/**
	 * copy source to dest. they do not have to be of the same size, but source must fit in dest
	 * @param source
	 * @param dest
	 */
	public static <T extends RealType<T>, S extends RealType<S>> void copyRealImage(IterableInterval<T> source, RandomAccessibleInterval<S> dest) {
		RandomAccess<S> destRA = dest.randomAccess();
		Cursor<T> srcC = source.cursor();
		
		
		while (srcC.hasNext()){
			srcC.fwd();
			destRA.setPosition(srcC);
			destRA.get().setReal(srcC.get().getRealDouble());
		}
	}
	
	/**
	 * calculate the size difference of two Dimensions objects (dim2-dim1)
	 * @param dim1
	 * @param dim2
	 * @return
	 */	
	public static int[] getSizeDifference(Dimensions dim1, Dimensions dim2) {
		int[] diff = new int[dim1.numDimensions()];
		for (int i = 0; i < dim1.numDimensions(); i++){
			diff[i] = (int) (dim2.dimension(i) - dim1.dimension(i));
		}		
		return diff;
	}
	
	/**
	 * calculate the size of an extended image big enough to hold dim1 and dim2
	 * with each dimension also enlarged by extensionFactor times its size (rounded up the next even number)
	 * @param dim1
	 * @param dim2
	 * @param extensionFactor
	 * @return
	 */
	public static FinalDimensions getExtendedSize(Dimensions dim1, Dimensions dim2, double extensionFactor) {
		long[] extDims = new long[dim1.numDimensions()];
		for (int i = 0; i <dim1.numDimensions(); i++){
			extDims[i] = dim1.dimension(i) > dim2.dimension(i) ? dim1.dimension(i) : dim2.dimension(i);
			long extEachSide = (long) Math.ceil(extensionFactor/2 * extDims[i]);
			extDims[i] += 2*extEachSide;
		}
		return new FinalDimensions(extDims);		
	}
	
	/**
	 * return a BlendedExtendedMirroredRandomAccesible of img extended by extension Factor in each dimension
	 * @param img
	 * @param extensionFactor
	 * @return
	 */
	public static <T extends RealType<T>> RandomAccessible<T> extendImageByFactor(RandomAccessibleInterval<T> img, double extensionFactor)
	{
		int[] extEachSide = new int[img.numDimensions()];
		for (int i = 0; i <img.numDimensions(); i++){
			extEachSide[i] = (int) Math.ceil(extensionFactor/2 * img.dimension(i));			
		}
		return new BlendedExtendedMirroredRandomAccesible2<T>(img, extEachSide);
	}
	
	
	/**
	 * return a BlendedExtendedMirroredRandomAccesible of img extended to extDims
	 * @param img
	 * @param extensionFactor
	 * @return
	 */
	public static <T extends RealType<T>> RandomAccessible<T> extendImageToSize(RandomAccessibleInterval<T> img, Dimensions extDims)
	{
		int[] extEachSide = getSizeDifference(img, extDims);
		for (int i = 0; i< img.numDimensions(); i++){
			extEachSide[i] /= 2;
		}
		return new BlendedExtendedMirroredRandomAccesible2<T>(img, extEachSide);
	}
	
	/**
	 * calculate the crosscorrelation of img1 and img2 for all shifts represented by a PhasecorrelationPeak List in parallel using a specified
	 * ExecutorService. service remains functional after the call
	 * @param peaks
	 * @param img1
	 * @param img2
	 * @param minOverlapPx minimal number of overlapping pixels in each Dimension, may be null to indicate no minimum
	 * @param service
	 */
	public static <T extends RealType<T>, S extends RealType<S>> void calculateCrossCorrParallel(
			List<PhaseCorrelationPeak2> peaks, final RandomAccessibleInterval<T> img1, final RandomAccessibleInterval<S> img2,
			final Dimensions minOverlapPx, ExecutorService service)
	{
		List<Future<?>> futures = new ArrayList<Future<?>>();
		
		for (final PhaseCorrelationPeak2 p : peaks){
			futures.add(service.submit(new Runnable() {
				
				@Override
				public void run() {
					p.calculateCrossCorr(img1, img2, minOverlapPx);					
				}
			}));
		}
		
		for (Future<?> f: futures){
			try {
				f.get();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * find local maxima in PCM
	 * @param pcm
	 * @param service
	 * @return
	 */
	public static <T extends RealType<T>> List<PhaseCorrelationPeak2> getPCMMaxima(RandomAccessibleInterval<T> pcm, ExecutorService service){
		
		// FIXME: LocalExtrema.findLocalExtrema does not return correct maxima??
		// minimum value of maxima is 0
//		T thresh = Views.iterable(pcm).firstElement().createVariable();
//		thresh.setReal(thresh.getMinValue());
//		thresh.setZero();
//		List<Point> maxima = LocalExtrema.findLocalExtrema(pcm, new LocalExtrema.MaximumCheck<T>(thresh), service);
		
		List<PhaseCorrelationPeak2> res = new ArrayList<PhaseCorrelationPeak2>();
		RandomAccess<T> raPCM = pcm.randomAccess();
		
		List<Point> maxima = new ArrayList<Point>();			
		Cursor<T> cPcm = Views.iterable(pcm).cursor();
		
		RectangleShape shape = new RectangleShape(1, true);
		for (Neighborhood<T> neighborhood : shape.neighborhoods(Views.interval(Views.extendPeriodic(pcm), pcm))){
			cPcm.fwd();
			boolean maximum = true;
			for (T f : neighborhood){
				maximum = maximum && (f.compareTo(cPcm.get()) < 0);
			}
			if (maximum){
				maxima.add(new Point(cPcm));
			}
		}		
		
		for (Point p: maxima){
			raPCM.setPosition(p);
			res.add(new PhaseCorrelationPeak2(p, raPCM.get().getRealDouble() ));
		}
		return res;		
	}
	
	/**
	 * find maxima in PCM, use a temporary thread pool for calculation
	 * @param pcm
	 * @return
	 */
	public static <T extends RealType<T>> List<PhaseCorrelationPeak2> getPCMMaxima(RandomAccessibleInterval<T> pcm){
		ExecutorService tExecService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		List<PhaseCorrelationPeak2> res = getPCMMaxima(pcm, tExecService);
		tExecService.shutdown();
		return res;
	}
	
	/**
	 * sort PCM Peaks by phaseCorrelation and return a new list containing just the nToKeep highest peaks
	 * @param rawPeaks
	 * @param nToKeep
	 * @return
	 */
	public static List<PhaseCorrelationPeak2> getHighestPCMMaxima(List<PhaseCorrelationPeak2> rawPeaks, long nToKeep){
		Collections.sort(rawPeaks, Collections.reverseOrder( new PhaseCorrelationPeak2.ComparatorByPhaseCorrelation()));
		List<PhaseCorrelationPeak2> res = new ArrayList<PhaseCorrelationPeak2>();
		for (int i = 0; i < nToKeep; i++){
			res.add(new PhaseCorrelationPeak2(rawPeaks.get(i)));
		}
		return res;
	}
	/**
	 * expand a list of PCM maxima to to a list containing all possible shifts corresponding to these maxima
	 * @param peaks
	 * @param peak
	 * @param pcmDims
	 * @param img1Dims
	 * @param img2Dims
	 */
	public static void expandPeakListToPossibleShifts(List<PhaseCorrelationPeak2> peaks,
			Dimensions pcmDims, Dimensions img1Dims, Dimensions img2Dims)
	{
		List<PhaseCorrelationPeak2> res = new ArrayList<PhaseCorrelationPeak2>();
		for (PhaseCorrelationPeak2 p : peaks){
			res.addAll(expandPeakToPossibleShifts(p, pcmDims, img1Dims, img2Dims));
		}
		peaks.clear();
		peaks.addAll(res);
	}
	
	/**
	 * expand a single maximum in the PCM to a list of possible shifts corresponding to that peak
	 * an offset due to different images sizes is accounted for
	 * @param peak
	 * @param pcmDims
	 * @param img1Dims
	 * @param img2Dims
	 * @return
	 */
	public static List<PhaseCorrelationPeak2> expandPeakToPossibleShifts(
			PhaseCorrelationPeak2 peak, Dimensions pcmDims, Dimensions img1Dims, Dimensions img2Dims)
	{
		int[] originalPeakWithShift = new int[pcmDims.numDimensions()];
		peak.getPcmLocation().localize(originalPeakWithShift);
		
		int[] extensionImg1 = getSizeDifference(img1Dims, pcmDims);
		int[] extensionImg2 = getSizeDifference(img2Dims, pcmDims);
		int[] offset = new int[pcmDims.numDimensions()];
		for(int i = 0; i < offset.length; i++){
			offset[i] = (extensionImg2[i] - extensionImg1[i] ) / 2;
			originalPeakWithShift[i] += offset[i];
			originalPeakWithShift[i] %= pcmDims.dimension(i); 
		}		
		
		List<PhaseCorrelationPeak2> shiftedPeaks = new ArrayList<PhaseCorrelationPeak2>();
		for (int i = 0; i < Math.pow(2, pcmDims.numDimensions()); i++){
			int[] possibleShift = originalPeakWithShift.clone();
			PhaseCorrelationPeak2 peakWithShift = new PhaseCorrelationPeak2(peak);
			for (int d = 0; d < pcmDims.numDimensions(); d++){
				/*
				 * mirror the shift around the origin in dimension d if (i / 2^d) is even
				 * --> all possible shifts
				 */
				if ((i / (int) Math.pow(2, d) % 2) == 0){
					possibleShift[d] = possibleShift[d] < 0 ? possibleShift[d] + (int) pcmDims.dimension(d) : possibleShift[d] - (int) pcmDims.dimension(d);
				}
			}
			peakWithShift.setShift(new Point(possibleShift));
			shiftedPeaks.add(peakWithShift);
		}
		
		return shiftedPeaks;
	}
	
	/**
	 * get intervals corresponding to overlapping area in two images (relative to image origins)
	 * will return null if there is no overlap
	 * @param img1
	 * @param img2
	 * @param shift
	 * @return
	 */
	public static Pair<Interval, Interval> getOverlapIntervals(Dimensions img1, Dimensions img2, Localizable shift){
		
		final int numDimensions = img1.numDimensions();
		final long[] offsetImage1 = new long[ numDimensions ];
		final long[] offsetImage2 = new long[ numDimensions ];
		final long[] maxImage1 = new long[ numDimensions ];
		final long[] maxImage2 = new long[ numDimensions ];
		
		long overlapSize;
		
		for ( int d = 0; d < numDimensions; ++d )
		{
			if ( shift.getLongPosition(d) >= 0 )
			{
				// two possiblities
				//
				//               shift=start              end
				//                 |					   |
				// A: Image 1 ------------------------------
				//    Image 2      ----------------------------------
				//
				//               shift=start	    end
				//                 |			     |
				// B: Image 1 ------------------------------
				//    Image 2      -------------------
				
				// they are not overlapping ( this might happen due to fft zeropadding and extension )
				if ( shift.getLongPosition(d) >= img1.dimension( d ) )
				{
					return null;
				}
				
				offsetImage1[ d ] = shift.getLongPosition(d);
				offsetImage2[ d ] = 0;
				overlapSize = Math.min( img1.dimension( d ) - shift.getLongPosition(d),  img2.dimension( d ) );
				maxImage1[ d ] = offsetImage1[d] + overlapSize -1;
				maxImage2[ d ] = offsetImage2[d] + overlapSize -1;
			}
			else
			{
				// two possiblities
				//
				//          shift start                	  end
				//            |	   |			`		   |
				// A: Image 1      ------------------------------
				//    Image 2 ------------------------------
				//
				//          shift start	     end
				//            |	   |          |
				// B: Image 1      ------------
				//    Image 2 -------------------
				
				// they are not overlapping ( this might happen due to fft zeropadding and extension
				if ( shift.getLongPosition(d) <= -img2.dimension( d ) )
				{
					return null;
				}

				offsetImage1[ d ] = 0;
				offsetImage2[ d ] = -shift.getLongPosition(d);
				overlapSize =  Math.min( img2.dimension( d ) + shift.getLongPosition(d),  img1.dimension( d ) );
				maxImage1[ d ] = offsetImage1[d] + overlapSize -1;
				maxImage2[ d ] = offsetImage2[d] + overlapSize -1;
			}
			
		}		
		
		FinalInterval img1Interval = new FinalInterval(offsetImage1, maxImage1);
		FinalInterval img2Interval = new FinalInterval(offsetImage2, maxImage2);
		
		Pair<Interval, Interval> res = new ValuePair<Interval, Interval>(img1Interval, img2Interval);		
		return res;		
	}
	
	/**
	 * multiply complex numbers c1 and c2, set res to the result of multiplication
	 * @param c1
	 * @param c2
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>, T extends ComplexType<T>> void multiplyComplex(
			R c1, S c2, T res)
	{
		double a = c1.getRealDouble();
		double b = c1.getImaginaryDouble();
		double c = c2.getRealDouble();
		double d = c2.getImaginaryDouble();
		res.setReal(a*c - b*d);
		res.setImaginary(a*d + b*c);
	}
	
	/**
	 * pixel-wise multiplication of img1 and img2
	 * res is overwritten by the result
	 * @param img1
	 * @param img2
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>, T extends ComplexType<T>> void multiplyComplexIntervals(
			RandomAccessibleInterval<R> img1, RandomAccessibleInterval<S> img2, RandomAccessibleInterval<T> res) 
	{
		final RandomAccess<R> ra1 = img1.randomAccess();
		final RandomAccess<S> ra2 = img2.randomAccess();
		final Cursor<T> cursorRes = Views.iterable(res).cursor();
		
		while ( cursorRes.hasNext() )
		{
			cursorRes.fwd();
			ra1.setPosition(cursorRes);
			ra2.setPosition(cursorRes);
			
			multiplyComplex(ra1.get(), ra2.get(), cursorRes.get());
		}
		
	}
	
	/**
	 * calculate complex conjugate of c, save result to res
	 * @param c
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void complexConj(R c, S res) {
		res.setComplexNumber(c.getRealDouble(), - c.getImaginaryDouble());
	}
	
	/**
	 * calculate element-wise complex conjugate of img, save result to res
	 * @param img
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void complexConjInterval(
			RandomAccessibleInterval<R>	img, RandomAccessibleInterval<S> res)
	{
		Cursor<S> cRes = Views.iterable(res).cursor();
		RandomAccess<R> raImg = img.randomAccess();
		while (cRes.hasNext()){
			cRes.fwd();
			raImg.setPosition(cRes);
			complexConj(raImg.get(), cRes.get());
		}
		
		
	}
	
	/**
	 * normalize complex number c1 to length 1, save result to res
	 * if the length of c1 is less than normalizationThreshold, set res to 0
	 * @param c1
	 * @param res
	 * @param normalizationThreshold
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void normalize( R c1, S res, double normalizationThreshold)
	{
		double len = c1.getPowerDouble();
		if (len > normalizationThreshold){
			res.setReal(c1.getRealDouble()/len);
			res.setImaginary(c1.getImaginaryDouble()/len);
		} else {
			res.setComplexNumber(0, 0);
		}
		
	}
	
	/**
	 * normalization with default threshold
	 * @param c1
	 * @param res
	 */
	public static <R extends ComplexType<R>, S extends ComplexType<S>> void normalize( R c1, S res){
		normalize(c1, res, 1E-5);
	}	


	/**
	 * normalize complex valued img to length 1, pixel-wise, saving result to res
	 * if the length of a pixel is less than normalizationThreshold, set res to 0
	 * @param img
	 * @param res
	 * @param normalizationThreshold
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>>void normalizeInterval(
			RandomAccessibleInterval<T> img, RandomAccessibleInterval<S> res, double normalizationThreshold) 
	{
		Cursor<S> cRes = Views.iterable(res).cursor();
		RandomAccess<T> raImg = img.randomAccess();
		
		while (cRes.hasNext()){
			cRes.fwd();
			raImg.setPosition(cRes);
			normalize(raImg.get(), cRes.get(), normalizationThreshold);			
		}
		
	}
	/**
	 * normalization with default threshold
	 * @param img
	 * @param res
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>>void normalizeInterval(
			RandomAccessibleInterval<T> img, RandomAccessibleInterval<S> res){
		normalizeInterval(img, res, 1E-5);
	}
	
	/**
	 * get the mean pixel intensity of an img
	 * @param img
	 * @return
	 */
	public static <T extends RealType<T>> double getMean(RandomAccessibleInterval<T> img)
	{
		double sum = 0.0;
		long n = 0;
		for (T pix: Views.iterable(img)){
			sum += pix.getRealDouble();
			n++;
		}
		return sum/n;
	}
	
	/**
	 * get pixel-value correlation of two RandomAccessibleIntervals
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>> double getCorrelation (
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2)
	{
		double m1 = getMean(img1);
		double m2 = getMean(img2);
		
		// square sums
		double sum11 = 0.0, sum22 = 0.0, sum12 = 0.0; 
		
		Cursor<T> c1 = Views.iterable(img1).cursor();
		RandomAccess<S> r2 = img2.randomAccess();
		
		while (c1.hasNext()){
			c1.fwd();
			r2.setPosition(c1);
			sum11 += (c1.get().getRealDouble() - m1) * (c1.get().getRealDouble() - m1);
			sum22 += (r2.get().getRealDouble() - m2) * (r2.get().getRealDouble() - m2);
			sum12 += (c1.get().getRealDouble() - m1) * (r2.get().getRealDouble() - m2);
		}
		
		// all pixels had the same color....
		if (sum11 == 0 || sum22 == 0)
		{
			if ( sum11 == sum22 && m1 == m2 )
				return 1;
			else
				return 0;
		}
		
		return sum12 / Math.sqrt(sum11 * sum22);
		
	}

	
	/**
	 * test stitching, create new image with img2 copied over img1 at the specified shift
	 * @param img1
	 * @param img2
	 * @param shiftPeak
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>> RandomAccessibleInterval<FloatType> dummyFuse(RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, PhaseCorrelationPeak2 shiftPeak)
	{
		long[] shift = new long[img1.numDimensions()];
		shiftPeak.getShift().localize(shift);
		long[] minImg1 = new long[img1.numDimensions()];
		long[] minImg2 = new long[img1.numDimensions()];
		long[] maxImg1 = new long[img1.numDimensions()];
		long[] maxImg2 = new long[img1.numDimensions()];
		long[] min = new long[img1.numDimensions()];
		long[] max = new long[img1.numDimensions()];
		
		for (int i = 0; i < img1.numDimensions(); i++){
			minImg1[i] = 0;
			maxImg1[i] = img1.dimension(i) -1;
			minImg2[i] = shiftPeak.getShift().getLongPosition(i);
			maxImg2[i] = img2.dimension(i) + minImg2[i] - 1;
			
			min[i] =  Math.min(minImg1[i], minImg2[i]);
			max[i] = Math.max(maxImg1[i], maxImg2[i]);
		}
		
		
		RandomAccessibleInterval<FloatType> res = new ArrayImgFactory<FloatType>().create(new FinalInterval(min, max), new FloatType());
		copyRealImage(Views.iterable(img1), Views.translate(res, min));
		copyRealImage(Views.iterable(Views.translate(img2, shift)), Views.translate(res, min));
		return res;	
		
	}

}
