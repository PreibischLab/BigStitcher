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

import ij.gui.GenericDialog;

import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass.CenterOfMassPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass.CenterOfMassParameters;

import mpicbg.spim.data.sequence.ViewId;

/**
 * Center of mass GUI
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class CenterOfMassGUI extends PairwiseGUI
{
	final static String[] centerChoice = new String[]{ "Average", "Median" };
	public static int defaultCenterChoice = 0;

	protected int centerType = 0;

	@Override
	public CenterOfMassPairwise< InterestPoint > pairwiseMatchingInstance()
	{
		return new CenterOfMassPairwise< InterestPoint >( new CenterOfMassParameters( centerType ) );
	}

	@Override
	public MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance()
	{
		return new CenterOfMassPairwise< GroupedInterestPoint< ViewId > >( new CenterOfMassParameters( centerType ) );
	}

	@Override
	public void addQuery( final GenericDialog gd )
	{
		gd.addChoice( "Type of Center Computation", centerChoice, centerChoice[ defaultCenterChoice ] );
	}

	@Override
	public boolean parseDialog( final GenericDialog gd )
	{
		this.centerType = defaultCenterChoice = gd.getNextChoiceIndex();

		return true;
	}

	@Override
	public CenterOfMassGUI newInstance() { return new CenterOfMassGUI(); }

	@Override
	public String getDescription() { return "Center of mass (translation invariant)";}

	@Override
	public TransformationModelGUI getMatchingModel() { return new TransformationModelGUI( 0 ); }

	@Override
	public double getMaxError() { return Double.NaN; }

	@Override
	public double globalOptError() { return 5.0; }
}
