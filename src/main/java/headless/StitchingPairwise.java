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
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import simulation.SimulateTileStitching;
import spim.process.deconvolution.DeconViews;
import spim.process.interestpointdetection.methods.downsampling.Downsample;

public class StitchingPairwise
{
	public static void main( String[] args )
	{
		new ImageJ();

		final double overlap = 0.2;
		final float snr = 8;

		final SimulateTileStitching sts = new SimulateTileStitching( new Random( System.currentTimeMillis() ), true, overlap );

		final PairwiseStitchingParameters params = new PairwiseStitchingParameters( 0.1, 5, true, false, 100 );
		final ExecutorService service = DeconViews.createExecutorService();

		final double[] correct = sts.getCorrectTranslation();
		System.out.println( "Known shift (right relative to left): " + Util.printCoordinates( correct ) );

		final ArrayList< double[] > distances = new ArrayList<>();

		int downsample = 2;

		for ( int i = 0; i < 10; ++i )
		{
			final Pair< Img< FloatType >, Img< FloatType > > pair = sts.getNextPair( snr );

			final RandomAccessibleInterval< FloatType > img1 = downsample( pair.getA(), downsample );
			final RandomAccessibleInterval< FloatType > img2 = downsample( pair.getB(), downsample );
	
			final Translation3D t1 = new Translation3D( 0, 0, 0 );
			final Translation3D t2 = new Translation3D( 0, 0, 0 );
	
			final Pair< double[], Double > r = PairwiseStitching.getShift( img1, img2, t1, t2, params, service );
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Computed shift: " + Util.printCoordinates( r.getA() ) + ", R=" + r.getB() );

			for ( int d = 0; d < correct.length; ++d )
				r.getA()[ d ] = correct[ d ] - r.getA()[ d ];

			distances.add( r.getA() );
		}

		for ( final double[] dist : distances )
			System.out.println( dist[ 0 ] + "\t" + dist[ 1 ]  + "\t" + dist[ 2 ] + "\t" + dist( dist ) );
	}

	public static RandomAccessibleInterval< FloatType > downsample( RandomAccessibleInterval< FloatType > input, final int downsample )
	{
		int dsx = downsample;
		int dsy = downsample;
		int dsz = downsample;

		for ( ;dsx > 1; dsx /= 2 )
			input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ true, false, false } );

		for ( ;dsy > 1; dsy /= 2 )
			input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, true, false } );

		for ( ;dsz > 1; dsz /= 2 )
			input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, false, true } );

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
