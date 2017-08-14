package algorithm;

import ij.gui.GenericDialog;

public class PairwiseStitchingParameters
{
	public double minOverlap;
	public int peaksToCheck;
	public boolean doSubpixel;
	public boolean doLucasKanade;
	public int maxIterations;
	public boolean interpolateCrossCorrelation;

	public PairwiseStitchingParameters()
	{
		this(0, 5, true, false, 1000, false);
	}

	public PairwiseStitchingParameters(double minOverlap, int peaksToCheck, boolean doSubpixel, boolean doLucasKanade, int maxIterations,
			boolean interpolateCrossCorrelation)
	{
		this.minOverlap = minOverlap;
		this.peaksToCheck = peaksToCheck;
		this.doSubpixel = doSubpixel;
		this.doLucasKanade = doLucasKanade;
		this.maxIterations = maxIterations;
		this.interpolateCrossCorrelation = interpolateCrossCorrelation;
	}

	public static void addQueriesToGD(final GenericDialog gd)
	{
		gd.addNumericField( "number of peaks to check", 5, 0 );
		gd.addNumericField( "minimal overlap (percent of current overlap)", 0, 0 );
		gd.addCheckbox( "subpixel accuracy", true );
		//gd.addCheckbox( "use Lucas-Kanade algorithm", false );
		//gd.addNumericField( "max number of iterations", 500, 0 );
		gd.addCheckbox( "interpolate_subpixel_cross_correlation_(warning: slow!)", false );
	}

	public static PairwiseStitchingParameters getParametersFromGD(final GenericDialog gd)
	{
		if (gd.wasCanceled())
			return null;

		int peaksToCheck  = (int) gd.getNextNumber();
		double minOverlap =  Math.min( Math.max( gd.getNextNumber()/100 , 0), 1);
		boolean doSubpixel = gd.getNextBoolean();
		//boolean doLucasKanade = gd.getNextBoolean();
		//int maxIterations = (int) gd.getNextNumber();
		boolean interpolateSubpixel = gd.getNextBoolean();

		return new PairwiseStitchingParameters(minOverlap, peaksToCheck, doSubpixel, false, 0, interpolateSubpixel);
	}

	public static PairwiseStitchingParameters askUserForParameters()
	{
		// ask user for parameters
		GenericDialog gd = new GenericDialog("Pairwise stitching options");
		addQueriesToGD( gd );

		gd.showDialog();
		return getParametersFromGD( gd );
	}
}
