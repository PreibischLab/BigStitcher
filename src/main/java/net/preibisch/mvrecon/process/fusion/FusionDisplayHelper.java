package net.preibisch.mvrecon.process.fusion;

import ij.ImagePlus;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.DisplayFusedImagesPopup;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.fusion.FusionTools.ImgDataType;

public class FusionDisplayHelper {
	public static ImagePlus displayVirtually( final RandomAccessibleInterval< FloatType > input )
	{
		return display( input, ImgDataType.VIRTUAL, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayVirtually( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.VIRTUAL, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCopy( final RandomAccessibleInterval< FloatType > input )
	{
		return display( input, ImgDataType.PRECOMPUTED, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCopy( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.PRECOMPUTED, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCached(
			final RandomAccessibleInterval< FloatType > input,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, ImgDataType.CACHED, cellDim, maxCacheSize );
	}

	public static ImagePlus displayCached(
			final RandomAccessibleInterval< FloatType > input,
			 final double min,
			 final double max,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, ImgDataType.CACHED, min, max, cellDim, maxCacheSize );
	}

	public static ImagePlus displayCached( final RandomAccessibleInterval< FloatType > input )
	{
		return displayCached( input, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus displayCached( final RandomAccessibleInterval< FloatType > input, final double min, final double max )
	{
		return display( input, ImgDataType.CACHED, min, max, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}
	
	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType )
	{
		return display( input, imgType, DisplayFusedImagesPopup.cellDim, DisplayFusedImagesPopup.maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType,
			final int[] cellDim,
			final int maxCacheSize )
	{
		return display( input, imgType, 0, 255, cellDim, maxCacheSize );
	}

	public static ImagePlus display(
			final RandomAccessibleInterval< FloatType > input,
			final ImgDataType imgType,
			final double min,
			final double max,
			final int[] cellDim,
			final int maxCacheSize )
	{
		final RandomAccessibleInterval< FloatType > img;

		if ( imgType == ImgDataType.CACHED )
			img = FusionTools.cacheRandomAccessibleInterval( input, maxCacheSize, new FloatType(), cellDim );
		else if ( imgType == ImgDataType.PRECOMPUTED )
			img = FusionTools.copyImg( input, new ImagePlusImgFactory<>(), new FloatType(), null, true );
		else
			img = input;

		// set ImageJ title according to fusion type
		final String title = imgType == ImgDataType.CACHED ? 
				"Fused, Virtual (cached) " : (imgType == ImgDataType.VIRTUAL ? 
						"Fused, Virtual" : "Fused" );

		return DisplayImage.getImagePlusInstance( img, true, title, min, max );
	}
}
