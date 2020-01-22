package net.preibisch.stitcher.headless;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.type.numeric.integer.LongType;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import net.preibisch.stitcher.algorithm.PairwiseStitching;
import net.preibisch.stitcher.algorithm.PairwiseStitchingParameters;
import net.preibisch.stitcher.input.FractalImgLoader;
import net.preibisch.stitcher.input.FractalSpimDataGenerator;

public class TestPairwiseStitching {

	public static void main(String[] args)
	{
		final AffineTransform3D m = new AffineTransform3D();
		double scale = 200;
		m.set( scale, 0.0f, 0.0f, 0.0f, 0.0f, scale, 0.0f, 0.0f, 0.0f, 0.0f, scale, 0.0f );

		final AffineTransform3D mShift = new AffineTransform3D();
		double shift = 100;
		mShift.set( 1.0f, 0.0f, 0.0f, shift, 0.0f, 1.0f, 0.0f, shift, 0.0f, 0.0f, 1.0f, shift );
		final AffineTransform3D mShift2 = new AffineTransform3D();
		double shift2x = 1200;
		double shift2y = 300;
		mShift2.set( 1.0f, 0.0f, 0.0f, shift2x, 0.0f, 1.0f, 0.0f, shift2y, 0.0f, 0.0f, 1.0f, 0.0f );

		final AffineTransform3D mShift3 = new AffineTransform3D();
		double shift3x = 500;
		double shift3y = 1300;
		mShift3.set( 1.0f, 0.0f, 0.0f, shift3x, 0.0f, 1.0f, 0.0f, shift3y, 0.0f, 0.0f, 1.0f, 0.0f );

		AffineTransform3D m2 = m.copy();
		AffineTransform3D m3 = m.copy();
		m.preConcatenate( mShift );
		m2.preConcatenate( mShift2 );
		m3.preConcatenate( mShift3 );

		Interval start = new FinalInterval( new long[] { -399, -399, 0 }, new long[] { 0, 0, 1 } );
		List< Interval > intervals = FractalSpimDataGenerator.generateTileList( start, 7, 6, 0.2f );

		List< Interval > falseStarts = FractalSpimDataGenerator.generateTileList( start, 7, 6, 0.30f );

		FractalSpimDataGenerator fsdg = new FractalSpimDataGenerator( 3 );
		fsdg.addFractal( m );
		fsdg.addFractal( m2 );
		fsdg.addFractal( m3 );

		Map< Integer, RandomAccessibleInterval< LongType > > rais = new HashMap< >();
		Map< Integer, TranslationGet > tr = new HashMap< >();

		List< TranslationGet > tileTranslations = FractalSpimDataGenerator.getTileTranslations( falseStarts );

		FractalImgLoader imgLoader = (FractalImgLoader) fsdg.generateSpimData( intervals ).getSequenceDescription()
				.getImgLoader();
		for ( int i = 0; i < intervals.size(); i++ )
		{
			rais.put( i, imgLoader.getImageAtInterval( intervals.get( i ) ) );
			tr.put( i, tileTranslations.get( i ) );
		}

		List< PairwiseStitchingResult< Integer > > pairwiseShifts = PairwiseStitching.getPairwiseShifts( rais, tr,
				new PairwiseStitchingParameters(),
				Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() ) );

		
		Map< Integer, AffineGet > collect = tr.entrySet().stream().collect( Collectors.toMap( e -> 
			e.getKey(), e -> {AffineTransform3D res = new AffineTransform3D(); res.set( e.getValue().getRowPackedCopy() ); return res; } ));
		
		// TODO: replace with new globalOpt code
		
//		Map< Set<Integer>, AffineGet > globalOptimization = GlobalTileOptimization.twoRoundGlobalOptimization( new TranslationModel3D(),
//				rais.keySet().stream().map( ( c ) -> {Set<Integer> s = new HashSet<>(); s.add( c ); return s;}).collect( Collectors.toList() ), 
//				null, 
//				collect,
//				pairwiseShifts, new GlobalOptimizationParameters() );
//
//		System.out.println( globalOptimization );
	}
}
