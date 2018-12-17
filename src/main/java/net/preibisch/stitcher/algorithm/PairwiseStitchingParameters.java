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
package net.preibisch.stitcher.algorithm;

import ij.gui.GenericDialog;

public class PairwiseStitchingParameters
{
	public double minOverlap;
	public int peaksToCheck;
	public boolean doSubpixel;
	public boolean interpolateCrossCorrelation;
	public boolean showExpertGrouping;
	public boolean useWholeImage;

	public PairwiseStitchingParameters()
	{
		this(0, 5, true, false, false, false);
	}

	public PairwiseStitchingParameters(double minOverlap, int peaksToCheck, boolean doSubpixel,
		boolean interpolateCrossCorrelation, boolean showExpertGrouping)
	{
		this(minOverlap, peaksToCheck, doSubpixel, interpolateCrossCorrelation, showExpertGrouping, false);
	}

	public PairwiseStitchingParameters(double minOverlap, int peaksToCheck, boolean doSubpixel,
			boolean interpolateCrossCorrelation, boolean showExpertGrouping, boolean useWholeImage )
	{
		this.minOverlap = minOverlap;
		this.peaksToCheck = peaksToCheck;
		this.doSubpixel = doSubpixel;
		this.interpolateCrossCorrelation = interpolateCrossCorrelation;
		this.showExpertGrouping = showExpertGrouping;
		this.useWholeImage = useWholeImage;
	}

	public static void addQueriesToGD(final GenericDialog gd)
	{
		gd.addNumericField( "number of peaks to check", 5, 0 );
		gd.addNumericField( "minimal overlap (percent of current overlap)", 0, 0 );
		gd.addCheckbox( "subpixel accuracy", true );
		gd.addCheckbox( "interpolate_subpixel_cross_correlation_(warning: slow!)", false );
		gd.addCheckbox( "use_whole_image_(warning: slow!)", false );
		gd.addCheckbox( "show_expert_grouping_options", false );
	}

	public static PairwiseStitchingParameters getParametersFromGD(final GenericDialog gd)
	{
		if (gd.wasCanceled())
			return null;

		int peaksToCheck  = (int) gd.getNextNumber();
		double minOverlap =  Math.min( Math.max( gd.getNextNumber()/100 , 0), 1);
		boolean doSubpixel = gd.getNextBoolean();
		boolean interpolateSubpixel = gd.getNextBoolean();
		boolean useWholeImage = gd.getNextBoolean();
		boolean showExpertGrouping = gd.getNextBoolean();

		return new PairwiseStitchingParameters(minOverlap, peaksToCheck, doSubpixel, interpolateSubpixel, showExpertGrouping, useWholeImage);
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
