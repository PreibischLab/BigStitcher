/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.statistics;

import java.util.List;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;

public class RegistrationStatistics implements Comparable< RegistrationStatistics >
{
	double minError = 0;
	double avgError = 0;
	double maxError = 0;

	double minRatio = 1;
	double maxRatio = 0;
	double avgRatio = 0;

	int numValidPairs = 0;
	int numInvalidPairs = 0;
	final int timePoint;

	/*
	 * Call this class after a registration is performed and it will collect the
	 * information it wants
	 *
	 */
	public RegistrationStatistics(
			final int timepoint,
			final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairwiseResults )
	{
		this.timePoint = timepoint;

		collect( timepoint, pairwiseResults );
	}

	public RegistrationStatistics( final int timePoint, final double minError, final double avgError, final double maxError, final double minRatio, final double avgRatio, final double maxRatio, final int numValidPairs, final int numInvalidPairs )
	{
		this.timePoint = timePoint;
		this.minError = minError;
		this.avgError = avgError;
		this.maxError = maxError;
		this.minRatio = minRatio;
		this.avgRatio = avgRatio;
		this.maxRatio = maxRatio;
		this.numValidPairs = numValidPairs;
		this.numInvalidPairs = numInvalidPairs;
	}

	int getTimePoint() { return timePoint; }
	double getMinError() { return minError; }
	double getAvgError() { return avgError; }
	double getMaxError() { return maxError; }
	double getMinRatio() { return minRatio; }
	double getAvgRatio() { return avgRatio; }
	double getMaxRatio() { return maxRatio; }
	int getNumValidPairs() { return numValidPairs; }
	int getNumInvalidPairs() { return numInvalidPairs; }

	protected void collect(
			final int timepoint,
			final List< ? extends Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > > pairwiseResults )
	{
		minError = Double.MAX_VALUE;
		avgError = 0;
		maxError = -1;

		minRatio = 1;
		maxRatio = 0;
		avgRatio = 0;

		for ( final Pair< ? extends Pair< ViewId, ViewId >, ? extends PairwiseResult< ? > > match : pairwiseResults )
			if ( match.getA().getA().getTimePointId() == timepoint || match.getA().getB().getTimePointId() == timepoint )
			{
				final int numCandidates = match.getB().getCandidates().size();
				final int numInliers = match.getB().getInliers().size();
				final double error = match.getB().getError();

				if ( !Double.isNaN( error ) && numCandidates > 0 && numInliers > 0 )
				{
					++numValidPairs;

					maxError = Math.max( maxError, error );
					avgError += error;
					minError = Math.min( minError, error );

					final double ratio = (double)numInliers / (double)numCandidates;
					maxRatio = Math.max( maxRatio, ratio );
					avgRatio += ratio;
					minRatio = Math.min( minRatio, ratio );
				}
				else
				{
					++numInvalidPairs;
				}
			}

		if ( numValidPairs > 0 )
		{
			avgError /= (double)numValidPairs;
			avgRatio /= (double)numValidPairs;
		}
	}

	@Override
	public int compareTo( final RegistrationStatistics o )
	{
		return getTimePoint() - o.getTimePoint();
	}
}
