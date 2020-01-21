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
package net.preibisch.mvrecon.fiji.plugin.boundingbox;

import java.util.List;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxBigDataViewer;

import mpicbg.spim.data.sequence.ViewId;

public class BDVBoundingBoxGUI extends BoundingBoxGUI
{
	public BDVBoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean allowModifyDimensions()
	{
		return false;
	}

	@Override
	protected boolean setUpDefaultValues( final int[] rangeMin, final int[] rangeMax )
	{
		if ( !findRange( spimData, viewIdsToProcess, rangeMin, rangeMax ) )
			return false;

		final BoundingBox bb = new BoundingBoxBigDataViewer( spimData, viewIdsToProcess ).estimate( "temp" );

		this.min = bb.getMin().clone();
		this.max = bb.getMax().clone();

		if ( defaultMin == null )
			defaultMin = min.clone();

		if ( defaultMax == null )
			defaultMax = max.clone();

		return true;
	}

	@Override
	public BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new BDVBoundingBoxGUI( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Define using the BigDataViewer interactively";
	}

	
}
