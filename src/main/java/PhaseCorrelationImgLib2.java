import net.imglib2.algorithm.Benchmark;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.Algorithm;
import net.imglib2.algorithm.MultiThreaded;
import net.imglib2.algorithm.fft2.FFT;
import net.imglib2.algorithm.fft2.FFTMethods;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.outofbounds.OutOfBoundsMirrorExpWindowingFactory;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.imglib.algorithm.fft.PhaseCorrelationPeak;
import mpicbg.imglib.image.Image;




/*
 * translation of ImgLib(1) mpicbg.imglib.algorithm.fft.PhaseCorrelation
 * to ImgLib2
 */

public class PhaseCorrelationImgLib2 <T extends RealType<T>, S extends RealType<S>> implements Algorithm, Benchmark, MultiThreaded
{
	
	final int numDimensions;
	boolean computeFFTinParalell = true;
	boolean keepPCM = false;
	RandomAccessibleInterval<T> image1;
	RandomAccessibleInterval<S> image2;
	RandomAccessibleInterval<FloatType> invPCM;
	int numPeaks;
	int[] minOverlapPx;
	float normalizationThreshold;
	boolean verifyWithCrossCorrelation;
	ArrayList<PhaseCorrelationPeak> phaseCorrelationPeaks;

	String errorMessage = "";
	int numThreads;
	long processingTime;
	
	
	public PhaseCorrelationImgLib2( final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2, final int numPeaks, final boolean verifyWithCrossCorrelation )
	{
		this.image1 = image1;
		this.image2 = image2;
		this.numPeaks = numPeaks;
		this.verifyWithCrossCorrelation = verifyWithCrossCorrelation;

		this.numDimensions = image1.numDimensions();
		this.normalizationThreshold = 1E-5f;
		
		this.minOverlapPx = new int[ numDimensions ];		
		setMinimalPixelOverlap( 3 );
		
		setNumThreads();
		processingTime = -1;
	}
	
	public PhaseCorrelationImgLib2( final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2 )
	{
		this( image1, image2, 5, true );
	}
	
	public void setComputeFFTinParalell( final boolean computeFFTinParalell ) { this.computeFFTinParalell = computeFFTinParalell; }
	public void setInvestigateNumPeaks( final int numPeaks ) { this.numPeaks = numPeaks; }
	public void setKeepPhaseCorrelationMatrix( final boolean keepPCM ) { this.keepPCM = keepPCM; }
	public void setNormalizationThreshold( final int normalizationThreshold ) { this.normalizationThreshold = normalizationThreshold; }
	public void setVerifyWithCrossCorrelation( final boolean verifyWithCrossCorrelation ) { this.verifyWithCrossCorrelation = verifyWithCrossCorrelation; }
	public void setMinimalPixelOverlap( final int[] minOverlapPx ) { this.minOverlapPx = minOverlapPx.clone(); } 
	public void setMinimalPixelOverlap( final int minOverlapPx ) 
	{ 
		for ( int d = 0; d < numDimensions; ++d )
			this.minOverlapPx[ d ] = minOverlapPx;
	}
	
	public boolean getComputeFFTinParalell() { return computeFFTinParalell; }
	public int getInvestigateNumPeaks() { return numPeaks; }
	public boolean getKeepPhaseCorrelationMatrix() { return keepPCM; }
	public float getNormalizationThreshold() { return normalizationThreshold; }
	public boolean getVerifyWithCrossCorrelation() { return verifyWithCrossCorrelation; }
	public int[] getMinimalPixelOverlap() { return minOverlapPx.clone(); }
	public RandomAccessibleInterval<FloatType> getPhaseCorrelationMatrix() { return invPCM; }
	public PhaseCorrelationPeak getShift() { return phaseCorrelationPeaks.get( phaseCorrelationPeaks.size() -1 ); }
	public ArrayList<PhaseCorrelationPeak> getAllShifts() { return phaseCorrelationPeaks; }
	
	@Override
	public boolean process() {
		
		
		// calculate ffts
		// TODO: padding / extension correct?
		Dimensions maxDims = getMaxDimensions(image1, image2);
		
		// expand images by 0.1 x the maximal size of an image in each dimension
		FinalInterval tExt = new FinalInterval(maxDims);
		final int[] fadeOutDistance = new int[tExt.numDimensions()];
		for (int i = 0; i <tExt.numDimensions(); i++){
			tExt = Intervals.expand(tExt, (long) (tExt.dimension(i) * 0.05) ,i);
			fadeOutDistance[i] = (int) ((tExt.dimension(i) - maxDims.dimension(i)) / 2);
		}
		// access in Runnable later on wants this as final
		final FinalInterval ext = new FinalInterval(tExt);
		
		final long[] padding = new long[maxDims.numDimensions()];
		long[] fftDim = new long[maxDims.numDimensions()];
		FFTMethods.dimensionsRealToComplexFast(ext, padding, fftDim);
		
		final RandomAccessibleInterval<ComplexFloatType> fft1 = new ArrayImgFactory<ComplexFloatType>().create(fftDim, new ComplexFloatType());
		final RandomAccessibleInterval<ComplexFloatType> fft2 = new ArrayImgFactory<ComplexFloatType>().create(fftDim, new ComplexFloatType());
		
		
		//final OutOfBoundsMirrorExpWindowingFactory<T, RandomAccessibleInterval<T>> oobfImg1 = new OutOfBoundsMirrorExpWindowingFactory<T, RandomAccessibleInterval<T>>(fadeOutDistance);
		//final OutOfBoundsMirrorExpWindowingFactory<S, RandomAccessibleInterval<S>> oobfImg2 = new OutOfBoundsMirrorExpWindowingFactory<S, RandomAccessibleInterval<S>>(fadeOutDistance);

		// FIXME:
		// Blended extended images are slower than zero-padded or mirrored with exp window 
		
		if (!computeFFTinParalell){
		FFT.realToComplex(Views.interval(new BlendedExtendedMirroredRandomAccesible<T>(image1, fadeOutDistance), FFTMethods.paddingIntervalCentered(ext, new FinalDimensions(padding))), fft1);
		FFT.realToComplex(Views.interval(new BlendedExtendedMirroredRandomAccesible<S>(image2, fadeOutDistance), FFTMethods.paddingIntervalCentered(ext, new FinalDimensions(padding))), fft2);
		//FFT.realToComplex(Views.interval(Views.extendZero(image2), FFTMethods.paddingIntervalCentered(image2, new FinalDimensions(padding))), fft2);
		} 
		else 
		{ // compute FFT in parallel
			ExecutorService exe = Executors.newFixedThreadPool(2);
			final AtomicInteger ai = new AtomicInteger(0);
			for (int i  = 0; i < 2; i++){
				exe.submit(new Runnable() {
					
					@Override
					public void run() {
						final int myNumber = ai.getAndIncrement();
						if (myNumber == 0) {
							FFT.realToComplex(Views.interval(new BlendedExtendedMirroredRandomAccesible<T>(image1, fadeOutDistance), FFTMethods.paddingIntervalCentered(ext, new FinalDimensions(padding))), fft1, numThreads/2);
						} else {
							FFT.realToComplex(Views.interval(new BlendedExtendedMirroredRandomAccesible<S>(image2, fadeOutDistance), FFTMethods.paddingIntervalCentered(ext, new FinalDimensions(padding))), fft2, numThreads/2);
						}

					}
				});
				
			}

			exe.shutdown();
			try {
				exe.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//ImageJFunctions.show(fft1);
		
		// normalize & comp. conjugate & multiply
		normalize(fft1);
		normalize(fft2);
		FFTMethods.complexConjugate(fft2);
		multiplyInPlace(fft1, fft2);
		
		// inverse FFT
		invPCM = new ArrayImgFactory<FloatType>().create(padding, new FloatType());		
		FFT.complexToReal(fft1, invPCM);
		
		//ImageJFunctions.show(invPCM);
		
		extractPhaseCorrelationPeaks();
		
		if ( !verifyWithCrossCorrelation )
			return true;

		verifyWithCrossCorrelation();
		
		if ( !keepPCM )
			invPCM = null;
		
		return true;
		
		
	}
	
	protected void extractPhaseCorrelationPeaks()
	{
		ArrayList<PhaseCorrelationPeak> peaks = new ArrayList<PhaseCorrelationPeak>();		
		Cursor<FloatType> cPcm = Views.iterable(invPCM).cursor();
		
		RectangleShape shape = new RectangleShape(1, true);
		for (Neighborhood<FloatType> neighborhood : shape.neighborhoods(Views.interval(Views.extendMirrorSingle(invPCM), invPCM))){
			cPcm.fwd();
			boolean maximum = true;
			for (FloatType f : neighborhood){
				maximum = maximum && (f.compareTo(cPcm.get()) < 0);
			}
			
			if (maximum)
			{
				// remove smallest saved peak if we already have enough peaks and new higher peak is incoming				
				if (peaks.size() == numPeaks && cPcm.get().get() > peaks.get(0).getPhaseCorrelationPeak()) {
					peaks.remove(0);
				} else if (peaks.size() == numPeaks) {continue;}

				
					int[] pos = new int[invPCM.numDimensions()];
					cPcm.localize(pos);
					
					for (int d = 0; d < invPCM.numDimensions(); d++){
						pos[d] = (int) (pos[ d ] > invPCM.dimension(d) / 2 ? pos[d] - invPCM.dimension(d) : pos[d]) ;
					}
					
					PhaseCorrelationPeak tPk = new PhaseCorrelationPeak(pos, cPcm.get().get());
					
					cPcm.localize(pos);
					tPk.setOriginalInvPCMPosition(pos);
					
					// TODO: avoid always sorting?
					peaks.add(tPk);
					Collections.sort(peaks);
				}
			}
			
		phaseCorrelationPeaks = peaks;
		
	}
	
	protected void verifyWithCrossCorrelation()
 {
		final boolean[][] coordinates = getRecursiveCoordinates(numDimensions);
		final ArrayList<PhaseCorrelationPeak> newPeakList = new ArrayList<PhaseCorrelationPeak>();
		
		//
		// get all the different possiblities
		//
		long[] dimInvPCM = new long[invPCM.numDimensions()];
		invPCM.dimensions(dimInvPCM);
		
		for ( final PhaseCorrelationPeak peak : phaseCorrelationPeaks )
		{
			for ( int i = 0; i < coordinates.length; ++i )
			{
				final boolean[] currentPossiblity = coordinates[ i ];
				
				final int[] peakPosition = peak.getPosition();
				
				for ( int d = 0; d < currentPossiblity.length; ++d )
				{
					if ( currentPossiblity[ d ] )
					{
						if ( peakPosition[ d ]  < 0 )
							peakPosition[ d ] += dimInvPCM[ d ];
						else
							peakPosition[ d ] -= dimInvPCM[ d ];
					}
				}
				
				final PhaseCorrelationPeak newPeak = new PhaseCorrelationPeak( peakPosition, peak.getPhaseCorrelationPeak() );
				newPeak.setOriginalInvPCMPosition( peak.getOriginalInvPCMPosition() );
				newPeakList.add( newPeak );
			}			
		}
		
		
		ExecutorService exe = Executors.newFixedThreadPool(numThreads);
		final AtomicInteger ai = new AtomicInteger(0);
		
		for (int i  = 0; i < numThreads; i++){
			exe.submit(new Runnable() {
				
				@Override
				public void run() {
					final int myNumber = ai.getAndIncrement();

					for (int i = 0; i < newPeakList.size(); ++i)
						if (i % numThreads == myNumber) {
							final PhaseCorrelationPeak peak = newPeakList.get(i);
							final long[] numPixels = new long[1];

							peak.setCrossCorrelationPeak((float) testCrossCorrelation(peak.getPosition(), image1,
									image2, minOverlapPx, numPixels));
							peak.setNumPixels(numPixels[0]);

							// sort by cross correlation peak
							peak.setSortPhaseCorrelation(false);
						}

				}
			});
			
		}

		exe.shutdown();
		try {
			exe.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// update old list and sort
		phaseCorrelationPeaks.clear();
		phaseCorrelationPeaks.addAll(newPeakList);
		Collections.sort(phaseCorrelationPeaks);

	}
	
	public static boolean[][] getRecursiveCoordinates( final int numDimensions )
	{
		boolean[][] positions = new boolean[ Util.pow( 2, numDimensions ) ][ numDimensions ];

		setCoordinateRecursive( numDimensions - 1, numDimensions, new int[ numDimensions ], positions );

		return positions;
	}

	/**
	 * recursively get coordinates covering all binary combinations for the given dimensionality
	 *
	 * example for 3d:
	 *
	 * x y z index
	 * 0 0 0 [0]
	 * 1 0 0 [1]
	 * 0 1 0 [2]
	 * 1 1 0 [3]
	 * 0 0 1 [4]
	 * 1 0 1 [5]
	 * 0 1 1 [6]
	 * 1 1 1 [7]
	 *
	 * All typical call will look like that:
	 *
	 * boolean[][] positions = new boolean[ MathLib.pow( 2, numDimensions ) ][ numDimensions ];
	 * MathLib.setCoordinateRecursive( numDimensions - 1, numDimensions, new int[ numDimensions ], positions );
	 *
	 * @param dimension - recusively changed current dimension, init with numDimensions - 1
	 * @param numDimensions - the number of dimensions
	 * @param location - recursively changed current state, init with new int[ numDimensions ]
	 * @param result - where the result will be stored when finished, needes a boolean[ MathLib.pow( 2, numDimensions ) ][ numDimensions ]
	 */
	public static void setCoordinateRecursive( final int dimension, final int numDimensions, final int[] location, final boolean[][] result )
	{
		final int[] newLocation0 = new int[ numDimensions ];
		final int[] newLocation1 = new int[ numDimensions ];

		for ( int d = 0; d < numDimensions; d++ )
		{
			newLocation0[ d ] = location[ d ];
			newLocation1[ d ] = location[ d ];
		}

		newLocation0[ dimension ] = 0;
		newLocation1[ dimension ] = 1;

		if ( dimension == 0 )
		{
			// compute the index in the result array ( binary to decimal conversion )
			int index0 = 0, index1 = 0;

			for ( int d = 0; d < numDimensions; d++ )
			{
				index0 += newLocation0[ d ] * Math.pow( 2, d );
				index1 += newLocation1[ d ] * Math.pow( 2, d );
			}

			// fill the result array
			for ( int d = 0; d < numDimensions; d++ )
			{
				result[ index0 ][ d ] = (newLocation0[ d ] == 1);
				result[ index1 ][ d ] = (newLocation1[ d ] == 1);
			}
		}
		else
		{
			setCoordinateRecursive( dimension - 1, numDimensions, newLocation0, result );
			setCoordinateRecursive( dimension - 1, numDimensions, newLocation1, result );
		}

	}

	public static <T extends RealType<T>, S extends RealType<S>> double testCrossCorrelation( final int[] shift, final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2 )
	{
		return testCrossCorrelation( shift, image1, image2, 5 );
	}

	public static <T extends RealType<T>, S extends RealType<S>> double testCrossCorrelation( final int[] shift, final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2, final int minOverlapPx )
	{
		return testCrossCorrelation( shift, image1, image2, minOverlapPx, null );
	}

	public static <T extends RealType<T>, S extends RealType<S>> double testCrossCorrelation( final int[] shift, final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2, final int minOverlapPx, final long[] numPixels )
	{
		return testCrossCorrelation( shift, image1, image2, Util.getArrayFromValue( minOverlapPx, image1.numDimensions()), numPixels );
	}

	public static <T extends RealType<T>, S extends RealType<S>> double testCrossCorrelation( final int[] shift, final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2, final int[] minOverlapPx )
	{
		return testCrossCorrelation( shift, image1, image2, minOverlapPx, null );
	}
	
	public static <T extends RealType<T>, S extends RealType<S>> double testCrossCorrelation( final int[] shift, final RandomAccessibleInterval<T> image1, final RandomAccessibleInterval<S> image2, final int[] minOverlapPx, final long[] numPixels )
	{
	
		final int numDimensions = image1.numDimensions();
		double correlationCoefficient = 0;
		
		final long[] overlapSize = new long[ numDimensions ];
		final long[] offsetImage1 = new long[ numDimensions ];
		final long[] offsetImage2 = new long[ numDimensions ];
		
		long numPx = 1;
		
		for ( int d = 0; d < numDimensions; ++d )
		{
			if ( shift[ d ] >= 0 )
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
				
				// they are not overlapping ( this might happen due to fft zeropadding and extension
				if ( shift[ d ] >= image1.dimension( d ) )
				{
					if ( numPixels != null && numPixels.length > 0 )
						numPixels[ 0 ] = 0;
					
					return 0;
				}
				
				offsetImage1[ d ] = shift[ d ];
				offsetImage2[ d ] = 0;
				overlapSize[ d ] = Math.min( image1.dimension( d ) - shift[ d ],  image2.dimension( d ) );
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
				if ( shift[ d ] >= image2.dimension( d ) )
				{
					if ( numPixels != null && numPixels.length > 0 )
						numPixels[ 0 ] = 0;
					
					return 0;
				}

				offsetImage1[ d ] = 0;
				offsetImage2[ d ] = -shift[ d ];
				overlapSize[ d ] =  Math.min( image2.dimension( d ) + shift[ d ],  image1.dimension( d ) );				
			}
			
			numPx *= overlapSize[ d ];
			
			if ( overlapSize[ d ] < minOverlapPx[ d ] )
			{
				if ( numPixels != null && numPixels.length > 0 )
					numPixels[ 0 ] = 0;
				
				return 0;
			}
		}
		
		
		if ( numPixels != null && numPixels.length > 0 )
			numPixels[ 0 ] = numPx;

		Interval iimg1 = new FinalInterval(overlapSize);
		Interval iimg2 = new FinalInterval(overlapSize);
		
		for (int i = 0; i < iimg1.numDimensions(); i++){
			iimg1 = Intervals.translate(iimg1, offsetImage1[i], i);
			iimg2 = Intervals.translate(iimg2, offsetImage2[i], i);
		}
		
		final Cursor<T> roiCursor1 = Views.interval(image1, iimg1).cursor();
		final Cursor<S> roiCursor2 = Views.interval(image2, iimg2).cursor();
						
		//
		// compute average
		//
		double avg1 = 0;
		double avg2 = 0;
		
		while ( roiCursor1.hasNext() )
		{
			roiCursor1.fwd();
			roiCursor2.fwd();

			avg1 += roiCursor1.get().getRealFloat();
			avg2 += roiCursor2.get().getRealFloat();
		}

		avg1 /= (double) numPx;
		avg2 /= (double) numPx;
				
		//
		// compute cross correlation
		//
		roiCursor1.reset();
		roiCursor2.reset();
				
		double var1 = 0, var2 = 0;
		double coVar = 0;
		
		while ( roiCursor1.hasNext() )
		{
			roiCursor1.fwd();
			roiCursor2.fwd();

			final float pixel1 = roiCursor1.get().getRealFloat();
			final float pixel2 = roiCursor2.get().getRealFloat();
			
			final double dist1 = pixel1 - avg1;
			final double dist2 = pixel2 - avg2;

			coVar += dist1 * dist2;
			var1 += dist1 * dist1;
			var2 += dist2 * dist2;
		}		
		
		var1 /= (double) numPx;
		var2 /= (double) numPx;
		coVar /= (double) numPx;

		double stDev1 = Math.sqrt(var1);
		double stDev2 = Math.sqrt(var2);

		// all pixels had the same color....
		if (stDev1 == 0 || stDev2 == 0)
		{
			if ( stDev1 == stDev2 && avg1 == avg2 )
				return 1;
			else
				return 0;
		}

		// compute correlation coeffienct
		correlationCoefficient = coVar / (stDev1 * stDev2);
		
		return correlationCoefficient;
	}
		

	private void normalize( RandomAccessibleInterval<ComplexFloatType> img )
	{
		Cursor<ComplexFloatType> c = Views.iterable(img).cursor();
		while (c.hasNext()){
			c.fwd();
			float real = c.get().getRealFloat();
			float complex = c.get().getImaginaryFloat();
			
			final float length = (float)Math.sqrt( real*real + complex*complex );
			
			if ( length < normalizationThreshold )
			{
				c.get().setComplexNumber(0.0f, 0.0f);
			}
			else
			{
				c.get().setComplexNumber(real/length, complex/length);
			}
			
		}
	}
	
	protected void multiplyInPlace( final RandomAccessibleInterval<ComplexFloatType> fftImage1, final RandomAccessibleInterval<ComplexFloatType> fftImage2 )
	{
		final Cursor<ComplexFloatType> cursor1 = Views.iterable(fftImage1).cursor();
		final Cursor<ComplexFloatType> cursor2 = Views.iterable(fftImage2).cursor();
		
		while ( cursor1.hasNext() )
		{
			cursor1.fwd();
			cursor2.fwd();
			
			cursor1.get().mul( cursor2.get() );
		}
	}
	
	@Override
	public int getNumThreads() {
		return numThreads;
	}

	@Override
	public void setNumThreads() {
		this.numThreads = Runtime.getRuntime().availableProcessors(); 		
	}

	@Override
	public void setNumThreads(int numThreads) {
		this.numThreads = numThreads;		
	}

	@Override
	public long getProcessingTime() {
		return processingTime;
	}

	@Override
	public boolean checkInput() {
		if ( errorMessage.length() > 0 )
		{
			return false;
		}
		
		if ( image1 == null || image2 == null)
		{
			errorMessage = "One of the input images is null";
			return false;
		}
		
		if ( image1.numDimensions() != image2.numDimensions() )
		{
			errorMessage = "Dimensionality of images is not the same";
			return false;
		}
		
		return true;
	}

	@Override
	public String getErrorMessage() {
		return errorMessage;
	}


	
	
	/*
	 * copy ImgLib Image to ImgLib2 Img
	 */
	public static <R extends mpicbg.imglib.type.numeric.RealType<R>, Q extends RealType<Q>> void imageToImg(Image<R> src, RandomAccessibleInterval<Q> dest)
	{		
		mpicbg.imglib.cursor.LocalizableCursor<R> cSrc = src.createLocalizableCursor();
		RandomAccess<Q> raDest = dest.randomAccess();
		
		while ( cSrc.hasNext()){
			cSrc.fwd();
			raDest.setPosition(cSrc.getPosition());			
			raDest.get().setReal(cSrc.getType().getRealDouble());
		}		
	}
	
	/*
	 * copy ImgLib2 Img to ImgLib Image
	 */
	public static <R extends mpicbg.imglib.type.numeric.RealType<R>, Q extends RealType<Q>> void imgToImage(RandomAccessibleInterval<Q> src, Image<R> dest) 
	{
		Cursor<Q> cSrc = Views.iterable(src).localizingCursor();
		mpicbg.imglib.cursor.LocalizableByDimCursor<R> cDest = dest.createLocalizableByDimCursor();
		int[] pos = new int[src.numDimensions()];
		while (cSrc.hasNext()) {
			cSrc.fwd();
			cSrc.localize(pos);
			cDest.moveTo(pos);
			cDest.getType().setReal(cSrc.get().getRealDouble());
		}
		
	}
	
	public static FinalDimensions getMaxDimensions(Dimensions img1, Dimensions img2){
		int[] dims = new int[img1.numDimensions()];
		for (int i = 0; i < img1.numDimensions(); i++) {
			dims[i] = (int) ( (img1.dimension(i) > img2.dimension(i)) ? img1.dimension(i):  img2.dimension(i) );
		}
		return new FinalDimensions(dims);		
	}
	

	
	public static void main(String[] args) {
		
		new ImageJ();
		
		Img<FloatType> img1 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img1.tif"));
		Img<FloatType> img2 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img2small.tif"));
		
		PhaseCorrelationImgLib2<FloatType, FloatType> testPC = new PhaseCorrelationImgLib2<FloatType, FloatType>(img1, img2);
		testPC.setKeepPhaseCorrelationMatrix(true);
		testPC.setVerifyWithCrossCorrelation(true);
		testPC.process();
		
		ImageJFunctions.show(testPC.invPCM);
		
		
		
		
//		ImagePlus imp1 = IJ.openImage(new File("src/main/resources/img8.tif").getAbsolutePath());
//		ImagePlus imp2 = IJ.openImage(new File("src/main/resources/img9.tif").getAbsolutePath());
//		
//		Image<mpicbg.imglib.type.numeric.integer.UnsignedByteType>  image1 = mpicbg.imglib.image.ImagePlusAdapter.wrapByte(imp1);
//		Image<mpicbg.imglib.type.numeric.integer.UnsignedByteType>  image2 = mpicbg.imglib.image.ImagePlusAdapter.wrapByte(imp2);
//		
//		PhaseCorrelation<mpicbg.imglib.type.numeric.integer.UnsignedByteType, mpicbg.imglib.type.numeric.integer.UnsignedByteType> pc =
//									new PhaseCorrelation<mpicbg.imglib.type.numeric.integer.UnsignedByteType, mpicbg.imglib.type.numeric.integer.UnsignedByteType>(image1, image2);
//		
//		pc.setKeepPhaseCorrelationMatrix(true);
//		if (!pc.process()){
//			System.out.println("ERROR");
//		}
//								
//		Img<FloatType> il1img = new ArrayImgFactory<FloatType>().create(pc.getPhaseCorrelationMatrix().getDimensions(), new FloatType());
//		imageToImg(pc.getPhaseCorrelationMatrix(), il1img);
//		ImageJFunctions.show(il1img);
		
		
	}
}
