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
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class PairwiseStitching {

	/**
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
	public static <T extends RealType<T>, S extends RealType<S>> Pair< RealLocalizable, Double > getShift(
			final RandomAccessibleInterval<T> input1,
			final RandomAccessibleInterval<S> input2,
			final AbstractTranslation t1,
			final AbstractTranslation t2,
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

		final RealInterval transformed1 = TransformTools.applyTranslation( img1, t1 );
		final RealInterval transformed2 = TransformTools.applyTranslation( img2, t2 );

		final RealInterval overlap = TransformTools.getOverlap( transformed1, transformed2 );

		// not overlapping
		if ( overlap == null )
			return null;

		final RealInterval localOverlap1 = TransformTools.getLocalOverlap( transformed1, overlap );
		final RealInterval localOverlap2 = TransformTools.getLocalOverlap( transformed2, overlap );

		final Interval interval1 = TransformTools.getLocalRasterOverlap( localOverlap1 );
		final Interval interval2 = TransformTools.getLocalRasterOverlap( localOverlap2 );

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
				20,
				minOverlap,
				subpixelShift,
				service );

		// the best peak is horrible, return null
		if ( Double.isInfinite( shiftPeak.getCrossCorr() ) )
			return null;

		final RealLocalizable shift;

		if ( shiftPeak.getSubpixelShift() == null )
			shift = shiftPeak.getShift();
		else
			shift = shiftPeak.getSubpixelShift();

		// adapt shift for the entire image, not only the overlapping parts
		//interval1, interval2
		final double[] entireIntervalShift = new double[ img1.numDimensions() ];

		for ( int d = 0; d < img1.numDimensions(); ++d )
		{
			final double intervalSubpixelOffset1 = interval1.realMin( d ) - localOverlap1.realMin( d ); // a_s
			final double intervalSubpixelOffset2 = interval2.realMin( d ) - localOverlap2.realMin( d ); // b_s
			
			final double intervalOffset1 = localOverlap1.realMin( d ); // a_v
			final double intervalOffset2 = localOverlap2.realMin( d ); // b_v
			
			final double localRasterShift = shift.getDoublePosition( d ); // d'
			
			final double globalShift = localRasterShift - intervalOffset2 - intervalSubpixelOffset2 + intervalOffset1 + intervalSubpixelOffset1;
		}
		
		return new ValuePair<>( shift, shiftPeak.getCrossCorr() );
		
	}
	
}
