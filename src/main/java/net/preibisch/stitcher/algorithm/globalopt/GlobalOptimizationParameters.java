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
		this( 2.5, 3.5, GlobalOptType.TWO_ROUND );
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
