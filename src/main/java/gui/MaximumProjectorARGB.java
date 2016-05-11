package gui;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;

public class MaximumProjectorARGB extends AccumulateProjector< ARGBType, ARGBType >
{
	public static AccumulateProjectorFactory< ARGBType > factory = new AccumulateProjectorFactory< ARGBType >()
	{
		@Override
		public VolatileProjector createAccumulateProjector(ArrayList< VolatileProjector > sourceProjectors,
				ArrayList< Source< ? > > sources,
				ArrayList< ? extends RandomAccessible< ARGBType > > sourceScreenImages,
				RandomAccessibleInterval< ARGBType > targetScreenImage, int numThreads, ExecutorService executorService)
		{
			return new MaximumProjectorARGB( sourceProjectors, sourceScreenImages, targetScreenImage, numThreads, executorService );
		}
	};

	public MaximumProjectorARGB(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< ARGBType > > sources,
			final RandomAccessibleInterval< ARGBType > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sources, target, numThreads, executorService );
	}

	@Override
	protected void accumulate( final Cursor< ARGBType >[] accesses, final ARGBType target )
	{
		int aMax = 0, rMax = 0, gMax = 0, bMax = 0;
		for ( final Cursor< ARGBType > access : accesses )
		{
			final int value = access.get().get();
			final int a = ARGBType.alpha( value );
			final int r = ARGBType.red( value );
			final int g = ARGBType.green( value );
			final int b = ARGBType.blue( value );
			
			aMax = Math.max( aMax, a );
			rMax = Math.max( rMax, r );
			gMax = Math.max( gMax, g );
			bMax = Math.max( bMax, b );
		}
		
		if ( aMax > 255 )
			aMax = 255;
		if ( rMax > 255 )
			rMax = 255;
		if ( gMax > 255 )
			gMax = 255;
		if ( bMax > 255 )
			bMax = 255;
		
		
		target.set( ARGBType.rgba( rMax, gMax, bMax, aMax ) );
	}
}
