package net.imglib2.algorithm.phasecorrelation;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Random;

import net.imglib2.Localizable;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;

import org.junit.Test;

public class FourNeighborhoodExtremaTest
{

	@Test
	public void test()
	{
		Img< FloatType > img = ArrayImgs.floats( 50, 50 );
		Random rnd = new Random( 4353 );
		
		for( FloatType t : img )
			t.set( rnd.nextFloat() );
		
		ArrayList< Pair< Localizable, Double > > correct = new ArrayList< Pair<Localizable,Double> >();
		//RandomAccess< FloatType > ra = 
		//for ( int i = 0; i < 10; ++i )
			
		
		int i = 5;
		
		assertTrue( i == 5 );
	}

}
