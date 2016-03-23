package net.imglib2.algorithm.phasecorrelation;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Executors;

import org.junit.Test;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class PhaseCorrelationTest {

	@Test
	public void testPC() {
		
		// TODO: very large shifts (nearly no overlap) lead to incorrect shift determination (as expected)
		// maybe we can optimize behaviour in this situation
		Img< FloatType > img = ArrayImgs.floats( 200, 200 );
		Random rnd = new Random( System.currentTimeMillis() );
		
		for( FloatType t : img )
			t.set( rnd.nextFloat());
		
		long shiftX = 28;
		long shiftY = 0;
		
		FinalInterval interval1 = new FinalInterval(new long[] {50, 50});
		FinalInterval interval2 = Intervals.translate(interval1, shiftX, 0);
		interval2 = Intervals.translate(interval2, shiftY, 1);

		
		int [] extension = new int[img.numDimensions()];
		Arrays.fill(extension, 10);
		
		RandomAccessibleInterval<FloatType> pcm = PhaseCorrelation2.calculatePCM(Views.interval(img, interval1), Views.zeroMin(Views.interval(img, interval2)), extension, new ArrayImgFactory<FloatType>(), 
				new FloatType(), new ArrayImgFactory<ComplexFloatType>(), new ComplexFloatType(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
		
		PhaseCorrelationPeak2 shiftPeak = PhaseCorrelation2.getShift(pcm, Views.interval(img, interval1), Views.zeroMin(Views.interval(img, interval2)), 20, null, Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
		
		long[] expected = new long[]{shiftX, shiftY};
		long[] found = new long[img.numDimensions()];
		
		
		
		shiftPeak.getShift().localize(found);
		
		assertArrayEquals(expected, found);
		
	}

	
	@Test
	public void testPCNegativeShift() {
		
		// TODO: very large shifts (nearly no overlap) lead to incorrect shift determination (as expected)
		// maybe we can optimize behaviour in this situation
		Img< FloatType > img = ArrayImgs.floats( 200, 200 );
		Random rnd = new Random( System.currentTimeMillis() );
		
		for( FloatType t : img )
			t.set( rnd.nextFloat());
		
		long shiftX = -20;
		long shiftY = -2;
		
		FinalInterval interval2 = new FinalInterval(new long[] {50, 50});
		FinalInterval interval1 = Intervals.translate(interval2, -shiftX, 0);
		interval1 = Intervals.translate(interval1, -shiftY, 1);

		int [] extension = new int[img.numDimensions()];
		Arrays.fill(extension, 10);
		
		RandomAccessibleInterval<FloatType> pcm = PhaseCorrelation2.calculatePCM(Views.zeroMin(Views.interval(img, interval1)), Views.zeroMin(Views.interval(img, interval2)), extension, new ArrayImgFactory<FloatType>(), 
				new FloatType(), new ArrayImgFactory<ComplexFloatType>(), new ComplexFloatType(), Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
		
		PhaseCorrelationPeak2 shiftPeak = PhaseCorrelation2.getShift(pcm, Views.zeroMin(Views.interval(img, interval1)), Views.zeroMin(Views.interval(img, interval2)), 20, null, Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
		
		long[] expected = new long[]{shiftX, shiftY};
		long[] found = new long[img.numDimensions()];
		
		
		
		shiftPeak.getShift().localize(found);
		
		assertArrayEquals(expected, found);
		
	}
}