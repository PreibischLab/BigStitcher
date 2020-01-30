package net.preibisch.mvrecon.fiji.plugin;

import java.util.HashMap;
import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;

public class VisualizeDetections {
	protected static Img< UnsignedShortType > renderSegmentations(
			final SpimData2 data,
			final ViewId viewId,
			final String label,
			final int detections,
			Interval interval,
			final double downsample )
	{
		final InterestPointList ipl = data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label );
		final List< InterestPoint > list = ipl.getInterestPointsCopy();

		if ( interval == null )
		{
			final int n = list.get( 0 ).getL().length;

			final long[] min = new long[ n ];
			final long[] max = new long[ n ];

			for ( int d = 0; d < n; ++d )
			{
				min[ d ] = Math.round( list.get( 0 ).getL()[ d ] ) - 1;
				max[ d ] = Math.round( list.get( 0 ).getL()[ d ] ) + 1;
			}

			for ( final InterestPoint ip : list )
			{
				for ( int d = 0; d < n; ++d )
				{
					min[ d ] = Math.min( min[ d ], Math.round( ip.getL()[ d ] ) - 1 );
					max[ d ] = Math.max( max[ d ], Math.round( ip.getL()[ d ] ) + 1 );
				}
			}
			
			interval = new FinalInterval( min, max );
		}
		
		// downsample
		final long[] min = new long[ interval.numDimensions() ];
		final long[] max = new long[ interval.numDimensions() ];
		
		for ( int d = 0; d < interval.numDimensions(); ++d )
		{
			min[ d ] = Math.round( interval.min( d ) / downsample );
			max[ d ] = Math.round( interval.max( d ) / downsample ) ;
		}
		
		interval = new FinalInterval( min, max );
	
		final Img< UnsignedShortType > s = new ImagePlusImgFactory< UnsignedShortType >().create( interval, new UnsignedShortType() );
		final RandomAccess< UnsignedShortType > r = Views.extendZero( s ).randomAccess();
		
		final int n = s.numDimensions();
		final long[] tmp = new long[ n ];
		
		if ( detections == 0 )
		{
			IOFunctions.println( "Visualizing " + list.size() + " detections." );
			
			for ( final InterestPoint ip : list )
			{
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( ip.getL()[ d ] / downsample );
	
				r.setPosition( tmp );
				r.get().set( 65535 );
			}
		}
		else
		{
			final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();

			for ( final InterestPoint ip : list )
				map.put( ip.getId(), ip );

			final List< CorrespondingInterestPoints > cList = ipl.getCorrespondingInterestPointsCopy();

			if ( cList.size() == 0 )
			{
				IOFunctions.println( "No corresponding detections available, the dataset was not registered using these detections." );
				return s;
			}

			IOFunctions.println( "Visualizing " + cList.size() + " corresponding detections." );

			for ( final CorrespondingInterestPoints ip : cList )
			{
				for ( int d = 0; d < n; ++d )
					tmp[ d ] = Math.round( map.get( ip.getDetectionId() ).getL()[ d ] / downsample );
	
				r.setPosition( tmp );
				r.get().set( 65535 );
			}
		}

		try
		{
			Gauss3.gauss( new double[]{ 2, 2, 2 }, Views.extendZero( s ), s );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Gaussian Convolution of detections failed: " + e );
			e.printStackTrace();
		}
		catch ( OutOfMemoryError e )
		{
			IOFunctions.println( "Gaussian Convolution of detections failed due to out of memory, just showing plain image: " + e );
		}
		
		return s;
	}
}
