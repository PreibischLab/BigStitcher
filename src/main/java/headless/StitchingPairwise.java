package headless;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;

import algorithm.PairwiseStitching;
import algorithm.PairwiseStitchingParameters;
import ij.ImageJ;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.phasecorrelation.PhaseCorrelationPeak2;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import simulation.SimulateTileStitching;
import spim.process.deconvolution.DeconViews;
import spim.process.interestpointdetection.methods.downsampling.Downsample;

public class StitchingPairwise
{
	public static void main( String[] args )
	{
		new ImageJ();

		final double minOverlap = 0.4;
		final float snr = 8;

		final SimulateTileStitching sts = new SimulateTileStitching( new Random( 123432 ), true, Util.getArrayFromValue( minOverlap, 3 ) );

		final PairwiseStitchingParameters params = new PairwiseStitchingParameters( 0.1, 5, true, false, 100 );
		final ExecutorService service = DeconViews.createExecutorService();

		final Random rnd = new Random( 34 );

		for ( int downsample = 8; downsample <= 8; downsample *= 2 )
		{
			int[] ds = new int[ 3 ];
			ds[ 0 ] = downsample;
			ds[ 1 ] = downsample;
			ds[ 2 ] = Math.max( 1, downsample / 2 );

			IOFunctions.println( "------------------------------" );
			IOFunctions.println( "downsample: " + downsample + " ["  + Util.printCoordinates( ds ) + "]" );
			IOFunctions.println( "------------------------------" );

			double avgDist = 0;
			double avgX = 0;
			double avgY = 0;
			double avgZ = 0;
			final int numTests = 10;

			for ( int i = 0; i < numTests; ++i )
			{
				final double[] ov = new double[ 3 ];
				for ( int d = 0; d < ov.length; ++d )
					ov[ d ] = minOverlap + (rnd.nextDouble() / 10.0);
				sts.init( ov );

				final double[] correct = sts.getCorrectTranslation();
				System.out.println( "Known shift (right relative to left): " + Util.printCoordinates( correct ) );

				final Pair< Img< FloatType >, Img< FloatType > > pair = sts.getNextPair( snr );

				final RandomAccessibleInterval< FloatType > img1 = downsample( pair.getA(), ds );
				final RandomAccessibleInterval< FloatType > img2 = downsample( pair.getB(), ds );

				//SimulateTileStitching.show( img1, "1" );
				//SimulateTileStitching.show( img2, "2" );
				//SimpleMultiThreading.threadHaltUnClean();

				final Translation3D t1 = new Translation3D( 0, 0, 0 );
				final Translation3D t2 = new Translation3D( 0, 0, 0 );
		
				final Pair< double[], Double > r = PairwiseStitching.getShift( img1, img2, t1, t2, params, service );
	
				for ( int d = 0; d < correct.length; ++d )
					r.getA()[ d ] *= ds[ d ];

				for ( int d = 0; d < correct.length; ++d )
					r.getA()[ d ] -= correct[ d ];

				double d = dist( r.getA() );

				IOFunctions.println( r.getA()[ 0 ] + "\t" + r.getA()[ 1 ]  + "\t" + r.getA()[ 2 ] + "\t" + d + "\t" + r.getB() );
	
				avgDist += d;
				avgX += r.getA()[ 0 ];
				avgY += r.getA()[ 1 ];
				avgZ += r.getA()[ 2 ];
			}

			IOFunctions.println( "avg : " + avgDist / (double)numTests );
			IOFunctions.println( "avgX: " + avgX / (double)numTests );
			IOFunctions.println( "avgY: " + avgY / (double)numTests );
			IOFunctions.println( "avgZ: " + avgZ / (double)numTests );
		}
	}

	public static RandomAccessibleInterval< FloatType > downsample( RandomAccessibleInterval< FloatType > input, final int[] downsample )
	{
		int dsx = downsample[ 0 ];
		int dsy = downsample[ 1 ];
		int dsz = downsample[ 2 ];

		while ( dsx > 1 || dsy > 1 || dsz > 1 )
		{
			if ( dsy > 1 )
			{
				input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, true, false } );
				dsy /= 2;
			}

			if ( dsx > 1 )
			{
				input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ true, false, false } );
				dsx /= 2;
			}


			if ( dsz > 1 )
			{
				input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, false, true } );
				dsz /= 2;
			}
		}

		/*
		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ true, false, false } );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, true, false } );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, false, true } );
		*/
		return input;
	}

	public static double dist( final double[] lengths )
	{
		double l = 0;

		for ( final double d : lengths )
			l += d*d;

		return Math.sqrt( l );
	}
}
