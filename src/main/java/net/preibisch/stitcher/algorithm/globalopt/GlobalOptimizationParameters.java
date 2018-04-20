/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.stitcher.algorithm.globalopt;

import ij.gui.GenericDialog;

public class GlobalOptimizationParameters
{
	public static int defaultGlobalOpt = 2;
	public static boolean defaultExpertGrouping = false;

	public static double defaultRelativeError = 2.5;
	public static double defaultAbsoluteError = 3.5;

	public static int defaultSimple = 3;

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
	public boolean showExpertGrouping;

	/**
	 * just for internal testing of removed links this is set to false
	 */
	public boolean applyResults = true;

	public GlobalOptimizationParameters()
	{
		this( defaultRelativeError, defaultAbsoluteError, GlobalOptType.TWO_ROUND, false );
	}

	public GlobalOptimizationParameters(double relativeThreshold, double absoluteThreshold, GlobalOptType method, boolean showExpertGrouping)
	{
		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;
		this.method = method;
		this.showExpertGrouping = showExpertGrouping;
	}

	public static GlobalOptimizationParameters askUserForParameters(boolean askForGrouping)
	{
		// ask user for parameters
		final GenericDialog gd = new GenericDialog("Global optimization options");
		gd.addChoice( "Global_optimization_strategy", methodDescriptions, methodDescriptions[ defaultGlobalOpt ] );
		gd.addNumericField( "relative error threshold", 2.5, 3 );
		gd.addNumericField( "absolute error threshold", 3.5, 3 );
		if (askForGrouping )
			gd.addCheckbox( "show_expert_grouping_options", defaultExpertGrouping );
		gd.showDialog();

		if (gd.wasCanceled())
			return null;

		final double relTh = gd.getNextNumber();
		final double absTh = gd.getNextNumber();
		final int methodIdx = defaultGlobalOpt = gd.getNextChoiceIndex();
		final boolean expertGrouping = askForGrouping ? gd.getNextBoolean() : false;

		final GlobalOptType method;
		if (methodIdx == 0)
			method = GlobalOptType.SIMPLE;
		else if (methodIdx == 1)
			method = GlobalOptType.ITERATIVE;
		else
			method = GlobalOptType.TWO_ROUND;

		return new GlobalOptimizationParameters(relTh, absTh, method, expertGrouping);
	}
}
