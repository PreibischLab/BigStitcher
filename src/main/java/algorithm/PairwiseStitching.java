package algorithm;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;

import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.algorithm.phasecorrelation.PhaseCorrelation2;
import net.imglib2.algorithm.phasecorrelation.PhaseCorrelationPeak2;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import spim.fiji.ImgLib2Temp.Pair;
import net.imglib2.util.Util;
import spim.fiji.ImgLib2Temp.ValuePair;
import net.imglib2.view.Views;

public class PairwiseStitching {

	/**
	 * The absolute shift of input2 relative to after PCM input1 (without t1 and t2 - they just help to speed it up)
	 * 
	 * @param img1 - zero-min interval, starting at (0,0,...)
	 * @param img2 - zero-min interval, starting at (0,0,...)
	 * @param t1
	 * @param t2
	 * @param subpixelShift
	 * @param minOverlap
	 * @param service
	 * @return
	 */
	public static <T extends RealType<T>, S extends RealType<S>> Pair< double[], Double > getShift(
			final RandomAccessibleInterval<T> input1,
			final RandomAccessibleInterval<S> input2,
			final AbstractTranslation t1,
			final AbstractTranslation t2,
			final int numPeaks,
			final boolean subpixelShift,
			final Dimensions minOverlap,
			final ExecutorService service )
	{
		final RandomAccessibleInterval<T> img1;
		final RandomAccessibleInterval<S> img2;
		
		// make sure it is zero-min
		if ( !Views.isZeroMin( input1 ) )
			img1 = Views.zeroMin( input1 );
		else
			img1 = input1;

		if ( !Views.isZeroMin( input2 ) )
			img2 = Views.zeroMin( input2 );
		else
			img2 = input2;

		//ImageJFunctions.show( img1 );
		//ImageJFunctions.show( img2 );
		
		final RealInterval transformed1 = TransformTools.applyTranslation( img1, t1 );
		final RealInterval transformed2 = TransformTools.applyTranslation( img2, t2 );

		System.out.println( "1: " + Util.printInterval( img1 ) );
		System.out.println( "1: " + TransformTools.printRealInterval( transformed1 ));
		System.out.println( "2: " + Util.printInterval( img2 ) );
		System.out.println( "2: " + TransformTools.printRealInterval( transformed2 ));

		final RealInterval overlap = TransformTools.getOverlap( transformed1, transformed2 );
		System.out.println( "O: " + TransformTools.printRealInterval( overlap ));

		// not overlapping
		if ( overlap == null )
			return null;

		final RealInterval localOverlap1 = TransformTools.getLocalOverlap( transformed1, overlap );
		final RealInterval localOverlap2 = TransformTools.getLocalOverlap( transformed2, overlap );

		final Interval interval1 = TransformTools.getLocalRasterOverlap( localOverlap1 );
		final Interval interval2 = TransformTools.getLocalRasterOverlap( localOverlap2 );
		
		System.out.println( "1: " + TransformTools.printRealInterval( localOverlap1 ));
		System.out.println( "1: " + Util.printInterval( interval1 ) );
		System.out.println( "2: " + TransformTools.printRealInterval( localOverlap2 ));
		System.out.println( "2: " + Util.printInterval( interval2 ) );
		

		// test if the overlap is too small to begin with
		if ( minOverlap != null )
			for ( int d = 0; d < img1.numDimensions(); ++d )
				if ( img1.dimension( d ) < minOverlap.dimension( d ) )
					return null;

		//
		// call the phase correlation
		//
		final int [] extension = new int[ img1.numDimensions() ];
		Arrays.fill( extension, 10 );

		//ImageJFunctions.show( Views.zeroMin( Views.interval( img1, interval1 ) ) );
		//ImageJFunctions.show( Views.zeroMin( Views.interval( img2, interval2 ) ) );

		System.out.println( "FFT" );
		// TODO: Do not extend by mirror inside, but do that out here on the full image,
		//       so we feed it RandomAccessible + an Interval we want to use for the PCM > also zero-min inside
		final RandomAccessibleInterval<FloatType> pcm = PhaseCorrelation2.calculatePCM(
				Views.zeroMin( Views.interval( img1, interval1 ) ),
				Views.zeroMin( Views.interval( img2, interval2 ) ),
				extension,
				new ArrayImgFactory<FloatType>(),
				new FloatType(), new ArrayImgFactory<ComplexFloatType>(),
				new ComplexFloatType(),
				service );
		
		final PhaseCorrelationPeak2 shiftPeak = PhaseCorrelation2.getShift(
				pcm,
				Views.zeroMin( Views.interval( img1, interval1 ) ),
				Views.zeroMin( Views.interval( img2, interval2 ) ),
				numPeaks,
				minOverlap,
				subpixelShift,
				service );

		// the best peak is horrible or no peaks were found at all, return null
		if (shiftPeak == null || Double.isInfinite( shiftPeak.getCrossCorr() ) )
			return null;

		final RealLocalizable shift;

		if ( shiftPeak.getSubpixelShift() == null )
			shift = shiftPeak.getShift();
		else
			shift = shiftPeak.getSubpixelShift();

		// adapt shift for the entire image, not only the overlapping parts
		final double[] entireIntervalShift = new double[ img1.numDimensions() ];

		for ( int d = 0; d < img1.numDimensions(); ++d )
		{
			// correct for the int/real coordinate mess
			final double intervalSubpixelOffset1 = interval1.realMin( d ) - localOverlap1.realMin( d ); // a_s
			final double intervalSubpixelOffset2 = interval2.realMin( d ) - localOverlap2.realMin( d ); // b_s

			final double localRasterShift = shift.getDoublePosition( d ); // d'
			final double localRelativeShift = localRasterShift + ( intervalSubpixelOffset2 - intervalSubpixelOffset1 );

			// correct for the initial shift between the two inputs
			entireIntervalShift[ d ] = ( transformed2.realMin( d ) - transformed1.realMin( d ) ) + localRelativeShift;
		}
		
		return new ValuePair<>( entireIntervalShift, shiftPeak.getCrossCorr() );
	}
	
}
