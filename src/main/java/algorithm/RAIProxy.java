package algorithm;

import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.type.numeric.RealType;
//import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;


public class RAIProxy <T extends RealType<T>> implements RandomAccessibleInterval< T >
{
	private RandomAccessibleInterval< T > rai;
	private BasicImgLoader imgLoader;
	private ViewId vid;
	private long[] downsampleFactors;

	public RAIProxy(BasicImgLoader imgLoader, ViewId vid, long[] downsampleFactors )
	{
		this.rai = null;
		this.downsampleFactors = downsampleFactors;
		this.imgLoader = imgLoader;
		this.vid = vid;
	}
	
	private void loadIfNecessary()
	{
		// FIXME: use DownsampleTools from SPIM_Registration
		if (rai == null)
			rai = DownsampleTools.openAndDownsample( imgLoader, vid, downsampleFactors );
	}
	
	@Override
	public RandomAccess< T > randomAccess()
	{
		loadIfNecessary();
		return rai.randomAccess();
	}

	@Override
	public RandomAccess< T > randomAccess(Interval interval)
	{
		loadIfNecessary();
		return rai.randomAccess( interval );
	}

	@Override
	public int numDimensions()
	{
		loadIfNecessary();
		return rai.numDimensions();
	}

	@Override
	public long min(int d)
	{
		loadIfNecessary();
		return rai.min( d );
	}

	@Override
	public void min(long[] min)
	{
		loadIfNecessary();
		rai.min( min );
		
	}

	@Override
	public void min(Positionable min)
	{
		loadIfNecessary();
		rai.min( min );
		
	}

	@Override
	public long max(int d)
	{
		loadIfNecessary();
		return rai.max( d );
	}

	@Override
	public void max(long[] max)
	{
		loadIfNecessary();
		rai.max( max );
		
	}

	@Override
	public void max(Positionable max)
	{
		loadIfNecessary();
		rai.max( max );		
	}

	@Override
	public double realMin(int d)
	{
		loadIfNecessary();
		return rai.realMin( d );
	}

	@Override
	public void realMin(double[] min)
	{
		loadIfNecessary();
		rai.realMin( min );
		
	}

	@Override
	public void realMin(RealPositionable min)
	{
		loadIfNecessary();
		rai.realMin( min );
		
	}

	@Override
	public double realMax(int d)
	{
		loadIfNecessary();
		return rai.realMax( d );
	}

	@Override
	public void realMax(double[] max)
	{
		loadIfNecessary();
		rai.realMax( max );
	}

	@Override
	public void realMax(RealPositionable max)
	{
		loadIfNecessary();
		rai.realMax( max );
		
	}

	@Override
	public void dimensions(long[] dimensions)
	{
		loadIfNecessary();
		rai.dimensions( dimensions );
		
	}

	@Override
	public long dimension(int d)
	{
		loadIfNecessary();
		return rai.dimension( d );
	}

}
