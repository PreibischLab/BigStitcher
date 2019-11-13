package net.preibisch.stitcher.algorithm.illuminationselection;

import java.util.Collection;
import java.util.Date;

import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.quality.FRCRealRandomAccessible;
import net.preibisch.mvrecon.process.quality.FRCTools;

public class RelativeFRCSelection implements ViewSelection< ViewId >
{
	final AbstractSequenceDescription< ?, ?, ? > sd;

	final int zStepSize;
	final int fftSize;
	final boolean relative;
	final boolean smooth;

	public RelativeFRCSelection(
			final AbstractSequenceDescription< ?, ?, ? > sd,
			final int zStepSize,
			final int fftSize,
			final boolean relative,
			final boolean smooth
			)
	{
		this.sd = sd;

		this.zStepSize = zStepSize;
		this.fftSize = fftSize;
		this.relative = relative;
		this.smooth = smooth;
	}

	@Override
	public ViewId getBestView( final Collection< ? extends ViewId > views )
	{
		if ( views.size() < 1 )
			return null;
		else if ( views.size() == 1 )
			return views.iterator().next();

		final BasicImgLoader imgLoader = sd.getImgLoader();

		ViewId currentBest = null;
		double currentBestQuality = -Double.MAX_VALUE;

		for ( final ViewId view : views)
		{
			final FRCRealRandomAccessible< FloatType > frc =
					FRCTools.computeFRC( view, imgLoader, zStepSize, fftSize, relative, smooth );

			final double quality = frc.getTotalAvgQuality();

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Evaluated view " + Group.pvid( view ) + " at full resolution: " + quality );

			if (currentBest == null)
			{
				currentBest = view;
				currentBestQuality = quality;
			}
			else if ( quality >= currentBestQuality )
			{
				currentBest = view;
				currentBestQuality = quality;
			}
		}

		return currentBest;
	}

	@Override
	public boolean runMultithreaded() { return false; }
}
