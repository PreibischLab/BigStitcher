package algorithm;

import java.util.HashMap;
import java.util.Map;

import algorithm.globalopt.PairwiseStitchingResult;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.ImgLib2Temp.Pair;

public class StitchingResults
{
	Map<Pair<ViewId, ViewId>, PairwiseStitchingResult> pairwiseResults;
	
	public StitchingResults()
	{
		pairwiseResults = new HashMap<>();
	}

	public Map< Pair< ViewId, ViewId >, PairwiseStitchingResult > getPairwiseResults()
	{
		return pairwiseResults;
	}
	
	public double getAvgCorrelation(ViewId vid)
	{
		double sum = 0.0;
		int count = 0;
		for (PairwiseStitchingResult psr : pairwiseResults.values())
		{
			if (vid.equals( psr .pair().getA()) || vid.equals( psr .pair().getB()))
			{
				sum += psr.r();
				count++;
			}
							
		}
		
		if (count == 0)
			return 0;
		else
			return sum/count;
	}
}
