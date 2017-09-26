package net.preibisch.stitcher.algorithm.globalopt;

import ij.gui.GenericDialog;

public class GlobalOptimizationParameters
{
	public enum GlobalOptType
	{
		SIMPLE,
		ITERATIVE,
		TWO_ROUND
	}

	private final static String[] methodDescriptions = {
			"Simple One-Round",
			"One-Round with iterative dropping of bad links",
			"Two-Round using Metadata to align unconnected Tiles"
	};

	public GlobalOptType method;
	public double relativeThreshold;
	public double absoluteThreshold;

	public GlobalOptimizationParameters()
	{
		this( 2.5, 3.5, GlobalOptType.ITERATIVE );
	}

	public GlobalOptimizationParameters(double relativeThreshold, double absoluteThreshold, GlobalOptType method)
	{
		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
		this.method = method;
	}

	public static GlobalOptimizationParameters askUserForParameters()
	{
		// ask user for parameters
		final GenericDialog gd = new GenericDialog("Global optimization options");
		gd.addChoice( "Global_optimization_strategy", methodDescriptions, methodDescriptions[1] );
		gd.addNumericField( "relative error threshold", 2.5, 3 );
		gd.addNumericField( "absolute error threshold", 3.5, 3 );
		gd.showDialog();

		if (gd.wasCanceled())
			return null;

		final double relTh = gd.getNextNumber();
		final double absTh = gd.getNextNumber();
		final int methodIdx = gd.getNextChoiceIndex();

		final GlobalOptType method;
		if (methodIdx == 0)
			method = GlobalOptType.SIMPLE;
		else if (methodIdx == 1)
			method = GlobalOptType.ITERATIVE;
		else
			method = GlobalOptType.TWO_ROUND;

		return new GlobalOptimizationParameters(relTh, absTh, method);
	}
}
