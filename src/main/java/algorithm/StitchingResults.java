package algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import algorithm.globalopt.PairwiseStitchingResult;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;


public class StitchingResults
{
	Map<Pair<ViewId, ViewId>, PairwiseStitchingResult> pairwiseResults;
	Map<ViewId, double[]> globalShifts;
	
	public StitchingResults()
	{
		pairwiseResults = new HashMap<>();
		globalShifts = new HashMap<>();
	}

	public Map< Pair< ViewId, ViewId >, PairwiseStitchingResult > getPairwiseResults() { return pairwiseResults; }
	
	public Map< ViewId, double[] > getGlobalShifts() { return globalShifts;	}
	
	/**
	 * save the PairwiseStitchingResult for a pair of ViewIds, using the sorted ViewIds as a key
	 * use this method to ensure consistency of the pairwiseResults Map 
	 * @param pair
	 * @param res
	 */
	public void setPairwiseResultForPair(Pair<ViewId, ViewId> pair, PairwiseStitchingResult res )
	{
		Pair< ViewId, ViewId > key = pair.getA().compareTo( pair.getB() ) < 0 ? pair : new ValuePair<>(pair.getB(), pair.getA());
		pairwiseResults.put( key, res );
	}	
	public PairwiseStitchingResult getPairwiseResultsForPair(Pair<ViewId, ViewId> pair)
	{
		Pair< ViewId, ViewId > key = pair.getA().compareTo( pair.getB() ) < 0 ? pair : new ValuePair<>(pair.getB(), pair.getA());
		return pairwiseResults.get( key );
	}
	public void removePairwiseResultForPair(Pair<ViewId, ViewId> pair)
	{
		Pair< ViewId, ViewId > key = pair.getA().compareTo( pair.getB() ) < 0 ? pair : new ValuePair<>(pair.getB(), pair.getA());
		pairwiseResults.remove( key );
	}
	
	
	public ArrayList< PairwiseStitchingResult > getAllPairwiseResultsForViewId(ViewId vid)
	{
		ArrayList< PairwiseStitchingResult > res = new ArrayList<>();
		for (Pair< ViewId, ViewId > p : pairwiseResults.keySet())
		{
			if (p.getA().equals( vid ) || p.getB().equals( vid )){
				res.add( pairwiseResults.get( p ) );
			}
		}
		return res;
	}
	
	double getVectorLength(double[] vec){
		double res = 0;
		for (int i = 0; i<vec.length; i++)
			res += vec[i] * vec[i];
		return Math.sqrt( res );
	}
	
	double[] getVectorDiff(double[] a, double[] b)
	{
		double[] res = new double[a.length];
		for (int i = 0; i<a.length; i++)
			res[i] = b[i] - a[i];
		return res;
	}
	
	public ArrayList< Double > getErrors(ViewId vid)
	{
		List<PairwiseStitchingResult> psrs = getAllPairwiseResultsForViewId( vid );
		ArrayList< Double > res = new ArrayList<>();
		for (PairwiseStitchingResult psr : psrs)
		{
			if (globalShifts.containsKey( psr.pair().getA()) && globalShifts.containsKey( psr.pair().getB() ))
			{
				double[] relativeGlobal = getVectorDiff( globalShifts.get( psr.pair().getA() ), globalShifts.get( psr.pair().getB() ) );
				res.add( new Double(getVectorLength(  getVectorDiff( relativeGlobal, psr.relativeVector() ) )) );
			}
				
		}
		return res;		
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
	
	public static void main(String[] args)
	{
		StitchingResults sr = new StitchingResults();
		sr.getPairwiseResults().put( new ValuePair<>(new ViewId( 0, 0 ), new ViewId( 0, 1 )), null );
		sr.getPairwiseResults().put( new ValuePair<>(new ViewId( 0, 0 ), new ViewId( 0, 1 )), null );
		sr.getPairwiseResults().put( new ValuePair<>(new ViewId( 0, 1 ), new ViewId( 0, 2 )), null );
		
		ArrayList< PairwiseStitchingResult > psr = sr.getAllPairwiseResultsForViewId( new ViewId( 0, 0 ) );
		System.out.println( psr.size() );
	}
	
	
}
