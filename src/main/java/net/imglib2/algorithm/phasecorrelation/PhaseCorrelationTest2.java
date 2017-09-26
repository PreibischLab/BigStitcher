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
package net.imglib2.algorithm.phasecorrelation;

import java.io.File;
import java.util.concurrent.Executors;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.preibisch.stitcher.algorithm.DownsampleTools;
import net.preibisch.stitcher.algorithm.PairwiseStitching;
import net.preibisch.stitcher.algorithm.PairwiseStitchingParameters;

public class PhaseCorrelationTest2
{
	public static void main(String[] args)
	{
		RandomAccessibleInterval<  FloatType > image1 = ImgLib2Util.openAs32Bit( new File( "73.tif.zip" ) );
		RandomAccessibleInterval<  FloatType > image2 = ImgLib2Util.openAs32Bit( new File( "73m5-10-13.tif.zip" ) );
		//RandomAccessibleInterval<  FloatType > image2 = ImgLib2Util.openAs32Bit( new File( "73m5,75-10,25-12,6.tif.zip" ) );
		
		image1 = DownsampleTools.downsample( image1, new long[] {4,4,2} );
		image2 = DownsampleTools.downsample( image2, new long[] {4,4,2} );
		
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
