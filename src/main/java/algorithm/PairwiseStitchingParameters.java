package algorithm;

import ij.gui.GenericDialog;

public class PairwiseStitchingParameters
{
	public long minOverlap;
	public int peaksToCheck;
	public boolean doSubpixel;
	
	public PairwiseStitchingParameters()
	{
		this(0, 5, true);
	}
	
	public PairwiseStitchingParameters(long minOverlap, int peaksToCheck, boolean doSubpixel)
	{
		this.minOverlap = minOverlap;
		this.peaksToCheck = peaksToCheck;
		this.doSubpixel = doSubpixel;
	}
	
	public static PairwiseStitchingParameters askUserForParameters()
	{
		// ask user for parameters
		GenericDialog gd = new GenericDialog("Pairwise stitching options");
		gd.addNumericField( "number of peaks to check", 5, 0 );
		gd.addNumericField( "minimal overlap", 0, 0 );
		gd.addCheckbox( "subpixel accuracy", true );
		gd.showDialog();
		
		if (gd.wasCanceled())
			return null;
		
		int peaksToCheck  = (int) gd.getNextNumber();
		long minOverlap = (long) gd.getNextNumber();
		boolean doSubpixel = gd.getNextBoolean();
		
		return new PairwiseStitchingParameters(minOverlap, peaksToCheck, doSubpixel);
	}
	
}
