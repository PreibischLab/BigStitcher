package net.imglib2.algorithm.phasecorrelation;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ij.ImageJ;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ComplexType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

public class PhaseCorrelation2 {
	
	
	
	/**
	 * calculate the phase correlation of fft1 and fft2, save result to res
	 * fft1 and fft2 will be altered by the function
	 * @param fft1
	 * @param fft2
	 * @param pcm
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>, R extends RealType<R>> void calculatePCMInPlace(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, RandomAccessibleInterval<R> pcm)
	{
		// normalize in place
		PhaseCorrelation2Util.normalizeInterval(fft1, fft1);
		PhaseCorrelation2Util.normalizeInterval(fft2, fft2);
		// conjugate in place
		PhaseCorrelation2Util.complexConjInterval(fft2, fft2);
		// in-place multiplication
		PhaseCorrelation2Util.multiplyComplexIntervals(fft1, fft2, fft1);
		FFT.complexToReal(fft1, pcm);
		
	}
	
	/**
	 * calculate the phase correlation of fft1 and fft2, save result to res
	 * fft1 and fft2 will NOT be altered by the function
	 * @param fft1
	 * @param fft2
	 * @param pcm
	 */
	public static <T extends ComplexType<T> & NativeType<T>, S extends ComplexType<S> & NativeType<S>, R extends RealType<R>> void calculatePCM(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, RandomAccessibleInterval<R> pcm)
	{
		RandomAccessibleInterval<T> fft1Copy = new ArrayImgFactory<T>().create(fft1, Views.iterable(fft1).firstElement().createVariable());
		RandomAccessibleInterval<S> fft2Copy = new ArrayImgFactory<S>().create(fft2, Views.iterable(fft2).firstElement().createVariable());
		
		// normalize, save to copies
		PhaseCorrelation2Util.normalizeInterval(fft1, fft1Copy);
		PhaseCorrelation2Util.normalizeInterval(fft2, fft2Copy);
		// conjugate
		PhaseCorrelation2Util.complexConjInterval(fft2Copy, fft2Copy);
		// in-place multiplication
		PhaseCorrelation2Util.multiplyComplexIntervals(fft1Copy, fft2Copy, fft1Copy);
		FFT.complexToReal(fft1Copy, pcm);
		
	}
	
	
	/**
	 * calculate phase correlation of fft1 and fft2, return result in a new Img
	 * @param fft1
	 * @param fft2
	 * @return
	 */
	public static <T extends ComplexType<T>, S extends ComplexType<S>, R extends RealType<R>> RandomAccessibleInterval<R> calculatePCMInPlace(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, ImgFactory<R> factory, R type){
		
		long[] paddedDimensions = new long[fft1.numDimensions()];
		long[] realSize = new long[fft1.numDimensions()];
		
		FFTMethods.dimensionsComplexToRealFast(fft1, paddedDimensions, realSize);
		RandomAccessibleInterval<R> res = factory.create(realSize, type);
		
		calculatePCMInPlace(fft1, fft2, res);
		
		return res;
	}
	
	/**
	 * calculate phase correlation of fft1 and fft2, doing the calculations in copies of the ffts, return result in a new Img
	 * @param fft1
	 * @param fft2
	 * @return
	 */
	public static <T extends ComplexType<T> & NativeType<T>, S extends ComplexType<S> & NativeType <S>, R extends RealType<R>> RandomAccessibleInterval<R> calculatePCM(
			RandomAccessibleInterval<T> fft1, RandomAccessibleInterval<S> fft2, ImgFactory<R> factory, R type){
		
		long[] paddedDimensions = new long[fft1.numDimensions()];
		long[] realSize = new long[fft1.numDimensions()];
		
		FFTMethods.dimensionsComplexToRealFast(fft1, paddedDimensions, realSize);
		RandomAccessibleInterval<R> res = factory.create(realSize, type);
		
		calculatePCM(fft1, fft2, res);
		
		return res;
	}
	
	/**
	 * calculate and return the phase correlation matrix of two images
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>, C extends ComplexType<C>> RandomAccessibleInterval<R> calculatePCM(
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, double extensionFactor,
			ImgFactory<R> factory, R type, ImgFactory<C> fftFactory, C fftType){
		
		Dimensions extSize = PhaseCorrelation2Util.getExtendedSize(img1, img2, extensionFactor);
		long[] paddedDimensions = new long[extSize.numDimensions()];
		long[] fftSize = new long[extSize.numDimensions()];
		FFTMethods.dimensionsRealToComplexFast(extSize, paddedDimensions, fftSize);
		
		RandomAccessibleInterval<C> fft1 = fftFactory.create(fftSize, fftType);
		RandomAccessibleInterval<C> fft2 = fftFactory.create(fftSize, fftType);
		
		FFT.realToComplex(Views.interval(PhaseCorrelation2Util.extendImageByFactor(img1, extensionFactor), 
				FFTMethods.paddingIntervalCentered(img1, new FinalInterval(paddedDimensions))), fft1);
		FFT.realToComplex(Views.interval(PhaseCorrelation2Util.extendImageByFactor(img2, extensionFactor), 
				FFTMethods.paddingIntervalCentered(img2, new FinalInterval(paddedDimensions))), fft2);
		
		RandomAccessibleInterval<R> pcm = calculatePCMInPlace(fft1, fft2, factory, type);
		return pcm;
		
	}
	
	/**
	 * calculate PCM with default extension
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>, C extends ComplexType<C>> RandomAccessibleInterval<R> calculatePCM(
			RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, ImgFactory<R> factory, R type,
			ImgFactory<C> fftFactory, C fftType) {
		return calculatePCM(img1, img2, 0.1, factory, type, fftFactory, fftType);
	}
	
	
	/**
	 * calculate the shift between two images from the phase correlation matrix
	 * @param pcm
	 * @param img1
	 * @param img2
	 * @param nHighestPeaks
	 * @param minOverlap
	 * @param service
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>> PhaseCorrelationPeak2 getShift(
			RandomAccessibleInterval<T> pcm, RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, int nHighestPeaks,
			Dimensions minOverlap, ExecutorService service)
	{
		List<PhaseCorrelationPeak2> peaks = PhaseCorrelation2Util.getPCMMaxima(pcm);
		peaks = PhaseCorrelation2Util.getHighestPCMMaxima(peaks, nHighestPeaks);
		PhaseCorrelation2Util.expandPeakListToPossibleShifts(peaks, pcm, img1, img2);		
		PhaseCorrelation2Util.calculateCrossCorrParallel(peaks, img1, img2, minOverlap, service);		
		Collections.sort(peaks, Collections.reverseOrder(new PhaseCorrelationPeak2.ComparatorByCrossCorrelation()));
		
		return peaks.get(0);
	}
	
	/**
	 * calculate the sift with default parameters (5 highest pcm peaks are considered, no minimum overlap, temporary thread pool)
	 * @param pcm
	 * @param img1
	 * @param img2
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>, R extends RealType<R>> PhaseCorrelationPeak2 getShift(
			RandomAccessibleInterval<T> pcm, RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2)
	{
		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		PhaseCorrelationPeak2 res = getShift(pcm, img1, img2, 5, null, service);
		service.shutdown();
		return res;		
	}	
	
	
		
	public static void main(String[] args) {
		
		new ImageJ();
		
		Img<FloatType> img1 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img1.tif"));
		Img<FloatType> img2 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img2small.tif"));
		
		RandomAccessibleInterval<FloatType> pcm = calculatePCM(img1, img2, new ArrayImgFactory<FloatType>(), new FloatType(),
				new ArrayImgFactory<ComplexFloatType>(), new ComplexFloatType());
		
		PhaseCorrelationPeak2 shiftPeak = getShift(pcm, img1, img2);
		
		RandomAccessibleInterval<FloatType> res = PhaseCorrelation2Util.dummyFuse(img1, img2, shiftPeak);
				
		ImageJFunctions.show(res);
	}

}
