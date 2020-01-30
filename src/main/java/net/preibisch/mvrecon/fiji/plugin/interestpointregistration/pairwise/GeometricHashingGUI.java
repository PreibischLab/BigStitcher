/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.pairwise;

import java.awt.Font;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.geometrichashing.GeometricHashingPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.geometrichashing.GeometricHashingParameters;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.ransac.RANSACParameters;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;

public class GeometricHashingGUI extends PairwiseGUI
{
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;
	public static int defaultRANSACIterationChoice = 1;
	protected TransformationModelGUI model = null;

	protected RANSACParameters ransacParams;
	protected GeometricHashingParameters ghParams;

	@Override
	public GeometricHashingPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		return new GeometricHashingPairwise< InterestPoint >( ransacParams, ghParams );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		return new GeometricHashingPairwise< GroupedInterestPoint< ViewId > >( ransacParams, ghParams );
	}

	@Override
	public GeometricHashingGUI newInstance() { return new GeometricHashingGUI(); }

	@Override
	public String getDescription() { return "Fast descriptor-based (rotation invariant)";}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		if ( presetModel == null )
		{
			gd.addChoice( "Transformation model", TransformationModelGUI.modelChoice, TransformationModelGUI.modelChoice[ defaultModel ] );
			gd.addCheckbox( "Regularize_model", defaultRegularize );
		}

		gd.addSlider( "Redundancy for descriptor matching", 0, 10, GeometricHashingParameters.redundancy );
		gd.addSlider( "Significance required for a descriptor match", 1.0, 20.0, GeometricHashingParameters.ratioOfDistance );

		gd.addMessage( "" );
		gd.addMessage( "Parameters for robust model-based outlier removal (RANSAC)", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );

		gd.addSlider( "Allowed_error_for_RANSAC (px)", 0.5, 100.0, RANSACParameters.max_epsilon );
		gd.addChoice( "Number_of_RANSAC_iterations", RANSACParameters.ransacChoices, RANSACParameters.ransacChoices[ defaultRANSACIterationChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		if ( presetModel == null )
		{
			model = new TransformationModelGUI( defaultModel = gd.getNextChoiceIndex() );

			if ( defaultRegularize = gd.getNextBoolean() )
			{
				if ( !model.queryRegularizedModel() )
					return false;
			}
		}
		else
		{
			model = presetModel;
		}

		final int redundancy = GeometricHashingParameters.redundancy = (int)Math.round( gd.getNextNumber() );
		final float ratioOfDistance = GeometricHashingParameters.ratioOfDistance = (float)gd.getNextNumber();
		final float maxEpsilon = RANSACParameters.max_epsilon = (float)gd.getNextNumber();
		final int ransacIterations = RANSACParameters.ransacChoicesIterations[ defaultRANSACIterationChoice = gd.getNextChoiceIndex() ];

		final float minInlierRatio;
		if ( ratioOfDistance >= 2 )
			minInlierRatio = RANSACParameters.min_inlier_ratio;
		else if ( ratioOfDistance >= 1.5 )
			minInlierRatio = RANSACParameters.min_inlier_ratio / 10;
		else
			minInlierRatio = RANSACParameters.min_inlier_ratio / 100;

		this.ghParams = new GeometricHashingParameters( model.getModel(), GeometricHashingParameters.differenceThreshold, ratioOfDistance, redundancy );
		this.ransacParams = new RANSACParameters( maxEpsilon, minInlierRatio, RANSACParameters.min_inlier_factor, ransacIterations );

		IOFunctions.println( "Selected Paramters:" );
		IOFunctions.println( "model: " + defaultModel );
		IOFunctions.println( "redundancy: " + redundancy );
		IOFunctions.println( "ratioOfDistance: " + ratioOfDistance );
		IOFunctions.println( "maxEpsilon: " + maxEpsilon );
		IOFunctions.println( "ransacIterations: " + ransacIterations );
		IOFunctions.println( "minInlierRatio: " + minInlierRatio );

		return true;
	}

	@Override
	public TransformationModelGUI getMatchingModel() { return model; }

	@Override
	public double getMaxError() { return ransacParams.getMaxEpsilon(); }

	@Override
	public double globalOptError() { return ransacParams.getMaxEpsilon(); }
}
