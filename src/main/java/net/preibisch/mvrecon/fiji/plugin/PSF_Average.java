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
package net.preibisch.mvrecon.fiji.plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.psf.PSFCombination;
import net.preibisch.mvrecon.process.psf.PSFExtraction;

public class PSF_Average implements PlugIn
{
	public static String[] averagingChoices = new String[] {
			"Display only",
			"Display only (remove Min Projections)",
			"Assign to all input views",
			"Assign to all input views (remove Min Projections)",
			"Display & assign to all input views",
			"Display & assign to all input views (remove Min Projections)"};

	public static int defaultAveraging = 0;

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		average( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean average(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewCollection )
	{
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Removed " +  removed.size() + " views because they are not present." );

		final GenericDialog gd = new GenericDialog( "Average PSF's" );

		gd.addChoice( "Averaged PSF", averagingChoices, averagingChoices[ defaultAveraging ] );
		gd.addCheckbox( "Remove_min_intensity_projections_from_PSF", PSF_Extract.defaultRemoveMinIntensity );
		gd.addMessage( "Note: Assigning to all input views will overwrite previous PSF", GUIHelper.smallStatusFont );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		return average( spimData, viewIds, defaultAveraging = gd.getNextChoiceIndex(), PSF_Extract.defaultRemoveMinIntensity = gd.getNextBoolean() );
	}

	public static boolean average(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewIds,
			final int choice,
			final boolean subtractMinProjections )
	{
		final Img< FloatType > avgPSF = averagePSF( spimData, viewIds );

		if ( avgPSF == null )
			return false;

		if ( subtractMinProjections )
			PSFExtraction.removeMinProjections( avgPSF );

		if ( choice == 0 || choice == 2 )
			DisplayImage.getImagePlusInstance( avgPSF, false, "Averaged PSF", 0, 1 ).show();

		if ( choice == 1 || choice == 2 )
		{
			String localFileName = null;

			for ( final ViewId viewId : viewIds )
			{
				if ( localFileName == null )
				{
					final PointSpreadFunction psf = new PointSpreadFunction( spimData, viewId, avgPSF );
					localFileName = psf.getFile();
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Local filename '" + localFileName + "' assigned" );
					spimData.getPointSpreadFunctions().addPSF( viewId, psf );
				}
				else
				{
					spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData.getBasePath(), localFileName ) );
				}

				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assigning '" + localFileName + "' to " + Group.pvid( viewId ) );
			}
		}

		return true;
	}

	public static Img< FloatType > averagePSF( final SpimData2 spimData, final Collection< ? extends ViewId > viewIds )
	{
		final HashMap< ViewId, Img< FloatType > > psfs = new HashMap<>();
		final HashMap< ViewId, PointSpreadFunction > psfLookup = spimData.getPointSpreadFunctions().getPointSpreadFunctions();

		for ( final ViewId viewId : viewIds )
		{
			if ( psfLookup.containsKey( viewId ) )
			{
				final PointSpreadFunction psf = psfLookup.get( viewId );
				psfs.put( viewId, psf.getPSFCopy() );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Averaging '" + psf.getFile() + "' from " + Group.pvid( viewId ) );
			}
			else
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could NOT find psf for " + Group.pvid( viewId ) );
			}
		}

		if ( psfs.isEmpty() )
		{
			IOFunctions.println( "No PSFs available. Stopping." );
			return null;
		}

		final Img< FloatType > avgPSF =  PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );

		// normalize PSF
		PSFExtraction.normalize( avgPSF );

		return avgPSF;
	}
}
