package net.preibisch.stitcher.gui;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import bdv.viewer.Source;
import bdv.viewer.render.AccumulateProjector;
import bdv.viewer.render.AccumulateProjectorFactory;
import bdv.viewer.render.VolatileProjector;

public class AveragingProjectorARGB extends AccumulateProjector< ARGBType, ARGBType >
{
	public static AccumulateProjectorFactory< ARGBType > factory = new AccumulateProjectorFactory< ARGBType >()
	{
		@Override
		public VolatileProjector createAccumulateProjector(ArrayList< VolatileProjector > sourceProjectors,
				ArrayList< Source< ? > > sources,
				ArrayList< ? extends RandomAccessible< ? extends ARGBType > > sourceScreenImages,
				RandomAccessibleInterval< ARGBType > targetScreenImage, int numThreads, ExecutorService executorService)
		{
			return new AveragingProjectorARGB( sourceProjectors, sourceScreenImages, targetScreenImage, numThreads, executorService );
		}
	};

	public AveragingProjectorARGB(
			final ArrayList< VolatileProjector > sourceProjectors,
			final ArrayList< ? extends RandomAccessible< ? extends ARGBType > > sources,
			final RandomAccessibleInterval< ARGBType > target,
			final int numThreads,
			final ExecutorService executorService )
	{
		super( sourceProjectors, sources, target, numThreads, executorService );
	}

	@Override
	protected void accumulate( final Cursor< ? extends ARGBType >[] accesses, final ARGBType target )
	{
		int aSum = 0, rSum = 0, gSum = 0, bSum = 0;
		int nonZeroAccesses = 0;
		for ( final Cursor< ? extends ARGBType > access : accesses )
		{
			final int value = access.get().get();
			final int a = ARGBType.alpha( value );
			final int r = ARGBType.red( value );
			final int g = ARGBType.green( value );
			final int b = ARGBType.blue( value );
			
			if (a>0 || r>0 || g>0 || b>0)
				nonZeroAccesses++;
			
			aSum += a;
			rSum += r;
			gSum += g;
			bSum += b;
		}
		
		nonZeroAccesses = nonZeroAccesses > 0 ? nonZeroAccesses : 1;
		
		aSum /= nonZeroAccesses;
		rSum /= nonZeroAccesses;
		gSum /= nonZeroAccesses;
		bSum /= nonZeroAccesses;
		
		if ( aSum > 255 )
			aSum = 255;
		if ( rSum > 255 )
			rSum = 255;
		if ( gSum > 255 )
			gSum = 255;
		if ( bSum > 255 )
			bSum = 255;
		
		
		target.set( ARGBType.rgba( rSum, gSum, bSum, aSum ) );
	}
}
