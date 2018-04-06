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
package net.preibisch.stitcher.algorithm.lucaskanade;


import ij.gui.GenericDialog;

public class LucasKanadeParameters
{
	public enum WarpFunctionType{
		TRANSLATION,
		RIGID,
		AFFINE
	}

	final static WarpFunctionType defaultModelType = WarpFunctionType.TRANSLATION;

	static String[] modelChoices = new String[]{
			"Translation", "Rigid", "Affine"
	};

	public final int maxNumIterations;
	public final WarpFunctionType modelType;
	public final double minParameterChange;
	public final boolean showExpertGrouping;

	public LucasKanadeParameters(WarpFunctionType modelType, int maxNumIterations, double minParameterChange, boolean showExpertGrouping)
	{
		this.modelType = modelType;
		this.maxNumIterations = maxNumIterations;
		this.minParameterChange = minParameterChange;
		this.showExpertGrouping = showExpertGrouping;
	}

	/**
	 * constructor with default optimization parameters (max 100 its, min 0.01 parameter vector magnitude change)
	 * @param modelType the type of alignment we wish to do
	 */
	public LucasKanadeParameters(WarpFunctionType modelType)
	{
		this( modelType, 100, 0.01, false);
	}

	/**
	 * generate a WarpFunction of the requested type
	 * @param numDimensions the number of dimensions
	 * @return an instance of the selected WarpFunction
	 */
	public WarpFunction getWarpFunctionInstance(int numDimensions)
	{
		if (modelType == WarpFunctionType.TRANSLATION)
			return new TranslationWarp( numDimensions );
		else if (modelType == WarpFunctionType.RIGID)
			return new RigidWarp( numDimensions );
		else if (modelType == WarpFunctionType.AFFINE)
			return new AffineWarp( numDimensions );
		else return null;
	}

	public static void addQueriesToGD(final GenericDialog gd, boolean askForModelType)
	{
		gd.addNumericField( "maximum_iterations", 100, 0, 10, "" );
		gd.addNumericField( "minimum_parameter_change_for_convergence", 0.01, 2, 10, "" );
		if (askForModelType)
			gd.addChoice( "transformation_type", modelChoices, modelChoices[0] );
		gd.addCheckbox( "show_expert_grouping_options", false );
	}

	public static LucasKanadeParameters getParametersFromGD(final GenericDialog gd, boolean askForModelType)
	{
		if (gd.wasCanceled())
			return null;

		final int nIterations  = (int) gd.getNextNumber();
		final double minParameterChance = gd.getNextNumber();

		final WarpFunctionType modelType;
		if (askForModelType)
		{
			final int modelIdx = gd.getNextChoiceIndex();
			modelType = WarpFunctionType.values()[modelIdx];
		}
		else
			modelType = defaultModelType;

		boolean expertGrouping = gd.getNextBoolean();

		return new LucasKanadeParameters(modelType, nIterations, minParameterChance, expertGrouping);
	}

	/**
	 * query parameters from user, use default model type.
	 * @return parameter object, or null if dialog was cancelled
	 */
	public static LucasKanadeParameters askUserForParameters()
	{
		return askUserForParameters( false );
	}

	public static LucasKanadeParameters askUserForParameters(boolean askForModelType)
	{
		// ask user for parameters
		GenericDialog gd = new GenericDialog("Pairwise stitching options");
		addQueriesToGD( gd , askForModelType);

		gd.showDialog();
		return getParametersFromGD( gd, askForModelType );
	}

	public static void main(String[] args)
	{
		LucasKanadeParameters params = askUserForParameters(false);
		System.out.println( params.minParameterChange );
		System.out.println( params.modelType );
	}
	
}
