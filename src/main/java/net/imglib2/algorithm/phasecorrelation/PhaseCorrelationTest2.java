package net.imglib2.algorithm.phasecorrelation;

import java.io.File;
import java.util.concurrent.Executors;

import algorithm.DownsampleTools;
import algorithm.PairwiseStitching;
import algorithm.PairwiseStitchingParameters;
import ij.ImageJ;
import input.GenerateSpimData;
import mpicbg.spim.data.SpimData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.Translation2D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;

public class PhaseCorrelationTest2
{
	public static void main(String[] args)
	{
		RandomAccessibleInterval<  FloatType > image1 = ImgLib2Util.openAs32Bit( new File( "73.tif.zip" ) );
		RandomAccessibleInterval<  FloatType > image2 = ImgLib2Util.openAs32Bit( new File( "73m5-10-13.tif.zip" ) );
		//RandomAccessibleInterval<  FloatType > image2 = ImgLib2Util.openAs32Bit( new File( "73m5,75-10,25-12,6.tif.zip" ) );
		
		image1 = DownsampleTools.downsample( image1, new long[] {4,4,2}, new AffineTransform3D() );
		image2 = DownsampleTools.downsample( image2, new long[] {4,4,2}, new AffineTransform3D() );
		
		//Img<  FloatType > image1 = ImgLib2Util.openAs32Bit( new File( "boats.tif" ) );
		//Img<  FloatType > image2 = ImgLib2Util.openAs32Bit( new File( "boatsm10,5-m20,5.tif" ) );
		//Img<  FloatType > image2 = ImgLib2Util.openAs32Bit( new File( "boatsm5,75-10,25.tif" ) );
		
		
		//new ImageJ();
		/*
		SpimData mySd = GenerateSpimData.grid3x2();
		RandomAccessibleInterval< UnsignedShortType > image1 = (RandomAccessibleInterval< UnsignedShortType >) mySd.getSequenceDescription()
																					.getImgLoader().getSetupImgLoader( 0 ).getImage( 0, null );
		RandomAccessibleInterval< UnsignedShortType > image2 = (RandomAccessibleInterval< UnsignedShortType >) mySd.getSequenceDescription()
				.getImgLoader().getSetupImgLoader( 1 ).getImage( 0, null );
		
		//ImageJFunctions.show( image1 );
		//ImageJFunctions.show( image2 );
		*/
		Translation3D translation2 = new Translation3D(0, 0, 0);
		
		PairwiseStitchingParameters params = new PairwiseStitchingParameters();
		params.doSubpixel = true;
		
		Pair< Translation, Double > shift = PairwiseStitching.getShift( image1, image2,
									new Translation3D(),
									translation2, 
									params,
									Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() ) );
		
		System.out.println( Util.printCoordinates( shift.getA().getTranslationCopy() ));
		System.out.print( shift.getB() );
	}
}
