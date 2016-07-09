package algorithm.globalopt;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;


public class PairwiseStitchingResult <C extends Comparable< C >>
{
	final private Pair< C, C > pair;
	final private double[] relativeVector;
	final private double r;
	

	public PairwiseStitchingResult( final Pair< C, C > pair, final double[] relativeVector, final double r )
	{
		this.pair = pair;
		this.relativeVector = relativeVector;
		this.r = r;
	}

	public Pair< C, C > pair() { return pair; }
	public double[] relativeVector() { return relativeVector; }
	public double r() { return r; }

	public double[] negativeRelationVector()
	{
		final double[] tmp = new double[ relativeVector.length ];
		for ( int d = 0; d < tmp.length; ++d )
			tmp[ d ] = -relativeVector[ d ];
		return tmp;
	}
}
