package input;

import java.util.List;

import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import net.imglib2.converter.Converters;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import algorithm.AveragedRandomAccessible;

public class FractalImgLoader implements ImgLoader
{
	final List<Interval> intervals;
	final VoxelDimensions vd0;
	final AveragedRandomAccessible< LongType > fractalsRA;

	public FractalImgLoader( final List<Interval> intervals, final VoxelDimensions vd0, final AveragedRandomAccessible< LongType > fractalsRA )
	{
		this.intervals = intervals;
		this.vd0 = vd0;
		this.fractalsRA = fractalsRA;
	}

	public RandomAccessibleInterval< LongType > getImageAtInterval(Interval interval){
		return  Views.zeroMin( Views.interval( fractalsRA, interval) );
		/*
		// we want a Img here, so that we can downsaple later
		Img<LongType> resImg = new ArrayImgFactory<LongType>().create( raiT, new LongType() );
		
		ops.copy().rai( resImg, raiT );
		//ImgLib2Util.copyRealImage(raiT, resImg) ;
		return resImg;*/
	}

	@Override
	public SetupImgLoader< ? > getSetupImgLoader( final int setupId )
	{
		return new SetupImgLoader< LongType >()
		{

			@Override
			public RandomAccessibleInterval< LongType > getImage(int timepointId, ImgLoaderHint... hints)
			{
				return getImageAtInterval( intervals.get( setupId / 2 ));
			}

			@Override
			public LongType getImageType() {return new LongType();}
			

			@Override
			public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
					ImgLoaderHint... hints)
			{
				return Converters.convert( getImage( timepointId, hints ), new Converter< LongType, FloatType >()
				{
					@Override
					public void convert(LongType input, FloatType output){output.setReal( input.getRealDouble() );}
				}, new FloatType() );
				
			}

			@Override
			public Dimensions getImageSize(int timepointId)
			{
				return intervals.get( 0 );
			}

			@Override
			public VoxelDimensions getVoxelSize(int timepointId)
			{
				return vd0;
			}
			
		};
	}
}
