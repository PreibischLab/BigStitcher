package net.preibisch.stitcher.algorithm;

import ij.gui.GenericDialog;

public class PairwiseStitchingParameters
{
	public double minOverlap;
	public int peaksToCheck;
	public boolean doSubpixel;
	public boolean interpolateCrossCorrelation;

	public PairwiseStitchingParameters()
	{
		this(0, 5, true, false);
	}

	public PairwiseStitchingParameters(double minOverlap, int peaksToCheck, boolean doSubpixel, boolean interpolateCrossCorrelation)
	{
		this.minOverlap = minOverlap;
		this.peaksToCheck = peaksToCheck;
		this.doSubpixel = doSubpixel;
		this.interpolateCrossCorrelation = interpolateCrossCorrelation;
	}

	public static void addQueriesToGD(final GenericDialog gd)
	{
		gd.addNumericField( "number of peaks to check", 5, 0 );
		gd.addNumericField( "minimal overlap (percent of current overlap)", 0, 0 );
		gd.addCheckbox( "subpixel accuracy", true );
		gd.addCheckbox( "interpolate_subpixel_cross_correlation_(warning: slow!)", false );
	}

	public static PairwiseStitchingParameters getParametersFromGD(final GenericDialog gd)
	{
		if (gd.wasCanceled())
			return null;

		int peaksToCheck  = (int) gd.getNextNumber();
		double minOverlap =  Math.min( Math.max( gd.getNextNumber()/100 , 0), 1);
		boolean doSubpixel = gd.getNextBoolean();
		boolean interpolateSubpixel = gd.getNextBoolean();

		return new PairwiseStitchingParameters(minOverlap, peaksToCheck, doSubpixel, interpolateSubpixel);
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
