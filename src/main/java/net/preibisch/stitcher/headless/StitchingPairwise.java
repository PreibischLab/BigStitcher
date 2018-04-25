/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.stitcher.headless;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import ij.ImageJ;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.io.TextFileAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.fft2.FFTConvolution;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.RealSum;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.Downsample;
import net.preibisch.simulation.SimulateTileStitching;
import net.preibisch.stitcher.algorithm.PairwiseStitching;
import net.preibisch.stitcher.algorithm.PairwiseStitchingParameters;

public class StitchingPairwise
{
	public static void main( String[] args )
	{
		parseTexts();
		System.exit( 0 );

		//new ImageJ();
		IOFunctions.printIJLog = false;

		final double minOverlap = 0.85;

		final ExecutorService service = FFTConvolution.createExecutorService( Runtime.getRuntime().availableProcessors() - 4  );
		System.out.println( "numThreads = " + ( Runtime.getRuntime().availableProcessors() - 4 ) );

		for ( int snr = 32; snr <= 64; snr *= 2 )
		{
			final ArrayList< Callable< Void > > tasks = new ArrayList< Callable< Void > >();

			for ( int i = 0; i < 4; i++ )
			{
				final int snrInt = snr;
				final boolean subpixel = true;
				final int downsample;
				
				if ( i == 0 )
					downsample = 1;
				else if ( i == 1 )
					downsample = 2;
				else if ( i == 2 )
					downsample = 4;
				else
					downsample = 8;
	
				System.out.println( i + " " + snrInt + " " + downsample + " " + subpixel );

				tasks.add( new Callable< Void >()
				{
					
					@Override
					public Void call() throws Exception
					{
						final float snr = snrInt;

						final SimulateTileStitching sts = new SimulateTileStitching( new Random( 123432 ), true, Util.getArrayFromValue( minOverlap, 3 ), service );
				
						final PairwiseStitchingParameters params = new PairwiseStitchingParameters( 0.1, 5, subpixel, subpixel, false );
				
						final Random rnd = new Random( 34 );
				
						//for ( int downsample = 8; downsample >= 1; downsample /= 2 )
						{
							final PrintWriter out;
							if ( subpixel )
								out = TextFileAccess.openFileWrite( new File( "subpixel_snr_" + Math.round( snr ) + "ds" + downsample + ".txt" ) );
							else
								out = TextFileAccess.openFileWrite( new File( "nosubpixel_snr_" + Math.round( snr ) + "ds" + downsample + ".txt" ) );
							
							int[] ds = new int[ 3 ];
							ds[ 0 ] = downsample;
							ds[ 1 ] = downsample;
							ds[ 2 ] = Math.max( 1, downsample / 2 );
				
							IOFunctions.println( "------------------------------" );
							IOFunctions.println( "subpixel:" + subpixel + ", SNR: " + snr + ", downsample: " + downsample + " ["  + Util.printCoordinates( ds ) + "]" );
							IOFunctions.println( "------------------------------" );
				
							final int numTests = 300;

							for ( int i = 0; i < numTests; ++i )
							{
								final double[] ov = new double[ 3 ];
								for ( int d = 0; d < ov.length; ++d )
									ov[ d ] = minOverlap + (rnd.nextDouble() / 10.0);
				
								System.out.println( Util.printCoordinates( ov ) );
								sts.init( ov, rnd.nextBoolean() );
				
								final double[] correct = sts.getCorrectTranslation();
								//System.out.println( "Known shift (right relative to left): " + Util.printCoordinates( correct ) );
				
								final Pair< Img< FloatType >, Img< FloatType > > pair = sts.getNextPair( snr );
	
								//ImageJFunctions.show( pair.getA() );
								//ImageJFunctions.show( pair.getB() );
	
								final RandomAccessibleInterval< FloatType > img1 = downsample( pair.getA(), ds, service );
								final RandomAccessibleInterval< FloatType > img2 = downsample( pair.getB(), ds, service );
	
								//SimulateTileStitching.show( img1, "1" );
								//SimulateTileStitching.show( img2, "2" );

								long time = System.currentTimeMillis();

								final Translation3D t1 = new Translation3D( 0, 0, 0 );
								final Translation3D t2 = new Translation3D( 0, 0, 0 );

								final Pair< Translation, Double > r = PairwiseStitching.getShift( img1, img2, t1, t2, params, service );

								double[] shift = r.getA().getTranslationCopy();

								for ( int d = 0; d < correct.length; ++d )
									shift[ d ] *= ds[ d ];

								for ( int d = 0; d < correct.length; ++d )
									shift[ d ] -= correct[ d ];

								double d = dist( shift );

								time = System.currentTimeMillis() - time;

								IOFunctions.println( time + "ms, " + shift[ 0 ] + " " + shift[ 1 ]  + " " + shift[ 2 ] + " " + d + " " + r.getB() );
								//SimpleMultiThreading.threadHaltUnClean();
								out.println( shift[ 0 ] + "\t" + shift[ 1 ]  + "\t" + shift[ 2 ] + "\t" + d + "\t" + r.getB() + "\t" + time );
								out.flush();
							}

							out.close();
						}

						return null;
					}
				} );
			}

			try
			{
				service.invokeAll( tasks );
			} catch ( InterruptedException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public static void parseTexts()
	{
		final boolean subpixel = true;

		for ( int downsample = 1; downsample <= 8; downsample *= 2 )
		{
			final ArrayList< Double > time = new ArrayList<>();

			for ( int snr = 8; snr <= 8; snr *= 2 )
			{
				final BufferedReader in;
	
				try
				{
					if ( subpixel )
						in = TextFileAccess.openFileReadEx( new File( "subpixel_snr_" + Math.round( snr ) + "ds" + downsample + ".txt" ) );
					else
						in = TextFileAccess.openFileRead( new File( "nosubpixel_snr_" + Math.round( snr ) + "ds" + downsample + ".txt" ) );
	
					final ArrayList< Double > values = new ArrayList<>();

					while ( in.ready() )
					{
						//out.println( shift[ 0 ] + "\t" + shift[ 1 ]  + "\t" + shift[ 2 ] + "\t" + d + "\t" + r.getB() );
						final String[] line = in.readLine().trim().split( "\t" );
						
						System.out.println( Double.parseDouble( line[ 3 ] ) );
						//values.add( Double.parseDouble( line[ 3 ] ) );
						//time.add( Double.parseDouble( line[ 5 ] ) );
					}

					//final double mean = Util.averageDouble( values );
					//final double stdev = stDevDouble( values, mean );
					//System.out.println( snr + "\t" + downsample + "\t" + mean + "\t" + stdev );
				}
				catch ( Exception e )
				{
					continue;
				}
			}

			//final double mean = Util.averageDouble( time );
			//final double stdev = stDevDouble( time, mean );
			//System.out.println( downsample + "\t" + mean + "\t" + stdev );

			System.out.println( );
		}
	}

	public static double stDevDouble( final List< Double > values, final double mean )
	{
		final double size = values.size();
		double stdev = 0;

		for ( final double v : values )
			stdev += Math.pow( v - mean, 2 );

		return Math.sqrt( stdev / size );
	}

	public static RandomAccessibleInterval< FloatType > downsample( RandomAccessibleInterval< FloatType > input, final int[] downsample, final ExecutorService taskExecutor )
	{
		int dsx = downsample[ 0 ];
		int dsy = downsample[ 1 ];
		int dsz = downsample[ 2 ];

		while ( dsx > 1 || dsy > 1 || dsz > 1 )
		{
			if ( dsy > 1 )
			{
				input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, true, false }, taskExecutor );
				dsy /= 2;
			}

			if ( dsx > 1 )
			{
				input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ true, false, false }, taskExecutor );
				dsx /= 2;
			}


			if ( dsz > 1 )
			{
				input = Downsample.simple2x( input, new ArrayImgFactory<>(), new boolean[]{ false, false, true }, taskExecutor );
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
