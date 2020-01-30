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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters;

import java.util.Map;
import java.util.Set;

import mpicbg.models.Model;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.Subset;

public class FixMapBackParameters
{
	public static String[] fixViewsChoice = new String[]{
			"Fix first view",
			"Select fixed view",
			"Do not fix views" };

	public static String[] mapBackChoice = new String[]{
			"Do not map back (use this if views are fixed)",
			"Map back to first view using translation model",
			"Map back to first view using rigid model",
			"Map back to user defined view using translation model",
			"Map back to user defined view using rigid model" };

	public Set< ViewId > fixedViews;
	public Model< ? > model;
	public Map< Subset< ViewId >, Pair< ViewId, Dimensions > > mapBackViews;
}
