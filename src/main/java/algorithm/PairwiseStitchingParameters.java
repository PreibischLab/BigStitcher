package algorithm;

import ij.gui.GenericDialog;

public class PairwiseStitchingParameters
{
	public long minOverlap;
	public int peaksToCheck;
	public boolean doSubpixel;
	public boolean doLucasKanade;
	public int maxIterations;

	public PairwiseStitchingParameters()
	{
		this(0, 5, true, false, 1000);
	}

	public PairwiseStitchingParameters(long minOverlap, int peaksToCheck, boolean doSubpixel, boolean doLucasKanade, int maxIterations)
	{
		this.minOverlap = minOverlap;
		this.peaksToCheck = peaksToCheck;
		this.doSubpixel = doSubpixel;
		this.doLucasKanade = doLucasKanade;
		this.maxIterations = maxIterations;
	}

	public static void addQueriesToGD(final GenericDialog gd)
	{
		gd.addNumericField( "number of peaks to check", 5, 0 );
		gd.addNumericField( "minimal overlap", 0, 0 );
		gd.addCheckbox( "subpixel accuracy", true );
		//gd.addCheckbox( "use Lucas-Kanade algorithm", false );
		//gd.addNumericField( "max number of iterations", 500, 0 );
	}

	public static PairwiseStitchingParameters getParametersFromGD(final GenericDialog gd)
	{
		if (gd.wasCanceled())
			return null;

		int peaksToCheck  = (int) gd.getNextNumber();
		long minOverlap = (long) gd.getNextNumber();
		boolean doSubpixel = gd.getNextBoolean();
		//boolean doLucasKanade = gd.getNextBoolean();
		//int maxIterations = (int) gd.getNextNumber();

		return new PairwiseStitchingParameters(minOverlap, peaksToCheck, doSubpixel, false, 0);
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
