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

import mpicbg.spim.data.sequence.ViewId;

public abstract class PairwiseGUI
{
	protected TransformationModelGUI presetModel = null;

	/*
	 * adds the questions this registration wants to ask
	 * 
	 * @param gd
	 */
	public abstract void addQuery( final GenericDialog gd );
	
	/*
	 * queries the questions asked before
	 * 
	 * @param gd
	 * @return
	 */
	public abstract boolean parseDialog( final GenericDialog gd );
	
	/**
	 * @return - a new instance without any special properties
	 */
	public abstract PairwiseGUI newInstance();

	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();

	/**
	 * @return - the object that will perform a pairwise matching and can return a result
	 */
	public abstract MatcherPairwise< InterestPoint > pairwiseMatchingInstance();

	/**
	 * This is not good style, but when creating the object we do not know which generic parameter will be required
	 * as the user specifies this later (could be a factory)
	 * 
	 * @return - the object that will perform a pairwise matching and can return a result for grouped interestpoints
	 */
	public abstract MatcherPairwise< GroupedInterestPoint< ViewId > > pairwiseGroupedMatchingInstance();

	/**
	 * @return - the model the user chose to perform the registration with
	 */
	public abstract TransformationModelGUI getMatchingModel();

	/**
	 * @return - a maximal error as selected by the user or Double.NaN if not applicable
	 */
	public abstract double getMaxError();

	/**
	 * @return - the error allowed for the global optimization
	 */
	public abstract double globalOptError();

	/**
	 * @param model - predefines a transformation model to use (if applicable)
	 */
	public void presetTransformationModel( final TransformationModelGUI model ) { this.presetModel = model; }
}
