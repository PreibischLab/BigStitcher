package algorithm.globalopt;

import ij.gui.GenericDialog;

public class GlobalOptimizationParameters
{
	public double correlationT;
	public double relativeThreshold;
	public double absoluteThreshold;
	public boolean doTwoRound;
	public boolean useOnlyOverlappingPairs;
	
	public GlobalOptimizationParameters()
	{
		this(0.4, 2.5, 3.5, true, true);		
	}
	
	public GlobalOptimizationParameters(double correlationThreshold, double relativeThreshold, double absoluteThreshold, boolean doTwoRound, boolean useOnlyOverlappingPairs)
	{
		this.correlationT = correlationThreshold;
		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
		this.doTwoRound = doTwoRound;
	}
	
	public static GlobalOptimizationParameters askUserForParameters()
	{
		// ask user for parameters
		GenericDialog gd = new GenericDialog("Global optimization options");
		gd.addNumericField( "cross-correlation threshold", 0.4, 3 );
		gd.addNumericField( "relative error threshold", 2.5, 3 );
		gd.addNumericField( "absolute error threshold", 3.5, 3 );
		gd.addCheckbox( "do two-round optimization", true );
		gd.addCheckbox( "weak links only between approximately overlapping views", true );
		gd.showDialog();
		
		if (gd.wasCanceled())
			return null;
		
		double ccTh = gd.getNextNumber();
		double relTh = gd.getNextNumber();
		double absTh = gd.getNextNumber();
		boolean twoRound = gd.getNextBoolean();
		boolean onlyOverlapping = gd.getNextBoolean();
		
		
		return new GlobalOptimizationParameters(ccTh, relTh, absTh, twoRound, onlyOverlapping);
	}
}
