package algorithm.globalopt;

import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.ImgLib2Temp.Pair;

public class PairwiseStitchingResult
{
	final private Pair< ViewId, ViewId > pair;
	final private double[] relativeVector;
	final private double r;

	public PairwiseStitchingResult( final Pair< ViewId, ViewId > pair, final double[] relativeVector, final double r )
	{
		this.pair = pair;
		this.relativeVector = relativeVector;
		this.r = r;
	}

	public Pair< ViewId, ViewId > pair() { return pair; }
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
