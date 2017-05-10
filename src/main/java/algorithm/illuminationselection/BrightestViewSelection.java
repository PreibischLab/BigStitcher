package algorithm.illuminationselection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.scijava.Context;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imagej.ops.OpService;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.RealSum;
import net.imglib2.view.Views;

public class BrightestViewSelection extends BasicViewSelection<ViewId>
{

	private OpService ops;
	public BrightestViewSelection(AbstractSequenceDescription<?,?,?> sd) 
	{	
		super( sd );
		this.ops = new Context(OpService.class).getService( OpService.class );
	}
	
	public BrightestViewSelection(AbstractSpimData<AbstractSequenceDescription<?,?,?>> data) { this(data.getSequenceDescription()); }

	public <T extends RealType< T >> ViewId getBestViewMean(Collection<? extends ViewId> views)
	{
		if (views.size() < 1)
			return null;
		
		BasicImgLoader imgLoader = sd.getImgLoader();
		
		ViewId currentBest = null;
		T currentBestMean = null;
		
		if (MultiResolutionImgLoader.class.isInstance( imgLoader ))
		{
			MultiResolutionImgLoader mrImgLoader = (MultiResolutionImgLoader) imgLoader;
			
			for (ViewId view : views)
			{
				
				MultiResolutionSetupImgLoader< ? > setupImgLoader = mrImgLoader.getSetupImgLoader( view.getViewSetupId() );
				

				RandomAccessibleInterval< T > image = (RandomAccessibleInterval< T >) setupImgLoader.getImage( view.getTimePointId(), setupImgLoader.getMipmapResolutions().length - 1 );
				T mean = ops.stats().mean( Views.iterable( image ) );
				
				if (currentBest == null)
				{
					currentBest = view;
					currentBestMean = mean;
				}
				else if (mean.compareTo( currentBestMean ) >= 0)
				{
					currentBest = view;
					currentBestMean = mean;
				}
			}
		}
		else
		{
			for (ViewId view : views)
			{
				RandomAccessibleInterval< T > image = (RandomAccessibleInterval< T >) imgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId() );
				T mean = ops.stats().mean( Views.iterable( image ) );			
				
				if (currentBest == null)
				{
					currentBest = view;
					currentBestMean = mean;
				}
				else if (mean.compareTo( currentBestMean ) > 0)
				{
					currentBest = view;
					currentBestMean = mean;
				}
			}
			
		}
				
		return currentBest;
	}
	
	
	
	public <T extends RealType<T>> T getMean(IterableInterval< T > img)
	{
		RealSum sum = new RealSum();
		long nPix = 0;
		
		for (T t : img)
		{
			sum.add( t.getRealDouble() );
			nPix++;
		}
		
		T res = img.firstElement().createVariable();
		res.setReal( sum.getSum()/nPix );
		return res;
		
	}
	
	@Override
	public ViewId getBestView(Collection< ? extends ViewId > views)
	{
		return getBestViewMean( views );
	}

}
