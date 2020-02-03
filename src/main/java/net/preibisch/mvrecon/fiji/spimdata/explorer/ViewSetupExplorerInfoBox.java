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
package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.ArrayList;
import java.util.Collections;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ViewSetupExplorerInfoBox< AS extends AbstractSpimData< ? > >
{
	public ViewSetupExplorerInfoBox( final AS data, final String xml )
	{
		String text = "";

		text += "ImgLoader:\n";
		text += data.getSequenceDescription().getImgLoader().getClass().getName() + "\n";
		text += data.getSequenceDescription().getImgLoader().toString() + "\n";

		for ( final BasicViewSetup vs : data.getSequenceDescription().getViewSetupsOrdered() )
		{
			text += "\n";
			text += "ViewSetup id=" + vs.getId() + ": \n";

			final Dimensions dim = vs.getSize();
			final VoxelDimensions vDim = vs.getVoxelSize();

			if ( dim == null )
			{
				text += "Dimensions of image stack not loaded yet.\n";
			}
			else
			{
				text += "Dimensions: ";
				for ( int d = 0; d < dim.numDimensions() - 1; ++d )
					text += Long.toString( dim.dimension( d ) ) + " x ";
				text += Long.toString( dim.dimension( dim.numDimensions() - 1 ) ) + "px\n";
			}

			if ( vDim == null )
			{
				text += "Voxel Dimensions of image stack not loaded yet.\n";
			}
			else
			{
				text += "Voxel Dimensions: ";
				for ( int d = 0; d < vDim.numDimensions() - 1; ++d )
					text += Double.toString( vDim.dimension( d ) ) + " x ";
				text += Double.toString( vDim.dimension( vDim.numDimensions() - 1 ) ) + vDim.unit() + "\n";
			}
			
			for ( final String attrib : vs.getAttributes().keySet() )
			{
				final Entity e = vs.getAttributes().get( attrib );

				if ( Angle.class.isInstance( e ) )
				{
					final Angle a = (Angle)e;
					text += attrib + " " + a.getName() + " (id=" + a.getId() + ")";

					if ( a.hasRotation() )
						text += ", Rotation Axis " + Util.printCoordinates( a.getRotationAxis() ) + ", Rotation Angle " + a.getRotationAngleDegrees();

					text += "\n";
				}
				else if ( NamedEntity.class.isInstance( e ) )
					text += attrib + " " +((NamedEntity)e).getName() + " (id=" + e.getId() + ")\n";
				else
					text += attrib + " (id=" + e.getId() + ")\n";
			}
		}

		text += "\nTimePoints:\n";
		String tps = "";
		for ( final TimePoint t : data.getSequenceDescription().getTimePoints().getTimePointsOrdered() )
			tps += t.getId() + ", ";

		text += tps.substring( 0, tps.length() - 2 ) + "\n";

		text += "\nBounding Boxes:\n";

		if ( SpimData2.class.isInstance( data ) )
		{
			final SpimData2 sd = (SpimData2)data;

			if ( sd.getBoundingBoxes().getBoundingBoxes().size() == 0 )
			{
				text += "None defined\n";
			}
			else
			{
				for ( final BoundingBox bb : sd.getBoundingBoxes().getBoundingBoxes() )
					text += BoundingBox.getBoundingBoxDescription( bb ) + "\n";
			}
		}

		text += "\nIntensity Adjustments:\n";

		if ( SpimData2.class.isInstance( data ) )
		{
			final SpimData2 sd = (SpimData2)data;

			if ( sd.getIntensityAdjustments().getIntensityAdjustments().size() == 0 )
			{
				text += "None defined\n";
			}
			else
			{
				final ArrayList< ViewId > views = new ArrayList<>( sd.getIntensityAdjustments().getIntensityAdjustments().keySet() );
				Collections.sort( views );
				for ( final ViewId v : views )
					text += Group.pvid( v ) + ": " + sd.getIntensityAdjustments().getIntensityAdjustments().get( v ) + "\n";
			}
		}

		text += "\nMissing Views:\n";
		if (
				data.getSequenceDescription().getMissingViews() != null &&
				data.getSequenceDescription().getMissingViews().getMissingViews() != null &&
				data.getSequenceDescription().getMissingViews().getMissingViews().size() != 0 )
		{
			final ArrayList< ViewId > ids = new ArrayList< ViewId >();
			ids.addAll( data.getSequenceDescription().getMissingViews().getMissingViews() );
			Collections.sort( ids );

			for ( final ViewId viewId : ids )
				text += "Timepoint: " + viewId.getTimePointId() + ", Viewsetup id: " + viewId.getViewSetupId() + "\n";
		}
		else
		{
			text += "No missing views\n";
		}

		new SimpleInfoBox( "Information", text );
	}
}
