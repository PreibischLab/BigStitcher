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
package net.preibisch.mvrecon.headless.boundingbox;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import ij.ImageJ;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.array.ArrayImgFactory;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.boundingbox.MinFilterThresholdBoundingBoxGUI;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMinFilterThreshold;
import net.preibisch.mvrecon.process.deconvolution.DeconViews;
import net.preibisch.mvrecon.process.fusion.FusionDisplayHelper;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class TestRealDataBoundingBox
{
	public static void main( String[] args ) throws SpimDataException
	{
		new ImageJ();

		// one common ExecutorService for all
		final ExecutorService service = DeconViews.createExecutorService();

		// test a real scenario
		final SpimData2 spimData = new XmlIoSpimData2( "" ).load( "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM/dataset.xml" );;

		final List< ViewId > viewIds = new ArrayList< ViewId >();
		viewIds.addAll( spimData.getSequenceDescription().getViewDescriptions().values() );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		System.out.println( viewIds.size() + " views in total." );

		final BoundingBoxMinFilterThreshold estimation = new BoundingBoxMinFilterThreshold(
				spimData,
				service,
				viewIds,
				new ArrayImgFactory<>(),
				MinFilterThresholdBoundingBoxGUI.defaultBackgroundIntensity,
				MinFilterThresholdBoundingBoxGUI.defaultDiscardedObjectSize,
				true,
				8 );

		final BoundingBox bb = estimation.estimate( "MinFilterThresholdBoundingBoxGUI" );

		service.shutdown();

		FusionDisplayHelper.displayCopy( FusionTools.fuseVirtual( spimData, viewIds, true, false, 1, bb, 2.0, null ).getA(), estimation.getMinIntensity(), estimation.getMaxIntensity() ).show();
	}
}
