package algorithm.globalopt;

import ij.gui.GenericDialog;

public class GlobalOptimizationParameters
{
	public double correlationT;
	public double relativeThreshold;
	public double absoluteThreshold;
	
	public GlobalOptimizationParameters()
	{
		this(0, 2.5, 3.5);		
	}
	
	public GlobalOptimizationParameters(double correlationThreshold, double relativeThreshold, double absoluteThreshold)
	{
		this.correlationT = correlationThreshold;
		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;		
	}
	
	public static GlobalOptimizationParameters askUserForParameters()
	{
		// ask user for parameters
		GenericDialog gd = new GenericDialog("Global optimization options");
		gd.addNumericField( "cross-correlation threshold", 0.0, 3 );
		gd.addNumericField( "relative error threshold", 2.5, 3 );
		gd.addNumericField( "absolute error threshold", 3.5, 3 );
		gd.showDialog();
		
		if (gd.wasCanceled())
			return null;
		
		double ccTh = gd.getNextNumber();
		double relTh = gd.getNextNumber();
		double absTh = gd.getNextNumber();
		
		return new GlobalOptimizationParameters(ccTh, relTh, absTh);
	}
}
