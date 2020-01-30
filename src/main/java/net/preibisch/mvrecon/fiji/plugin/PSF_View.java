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
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.psf.PSFCombination;
import net.preibisch.mvrecon.process.psf.PSFExtraction;

public class PSF_View implements PlugIn
{
	public static String[] displayChoices = new String[] {
			"Averaged PSF",
			"Averaged transformed PSF",
			"Maximum Projection of averaged PSF",
			"Maximum Projection of averaged transformed PSF" };

	public static int defaultDisplayChoice = 3;

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		display( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean display(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewCollection )
	{
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Removed " +  removed.size() + " views because they are not present." );

		final GenericDialog gd = new GenericDialog( "Display PSF's" );

		gd.addChoice( "Display", displayChoices, displayChoices[ defaultDisplayChoice ] );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		return display( spimData, viewIds, defaultDisplayChoice = gd.getNextChoiceIndex() );
	}

	public static boolean display(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewIdsIn,
			final int choice )
	{
		final ArrayList< ViewId > viewIds = new ArrayList<>();

		for ( final ViewId v : viewIdsIn )
		{
			final PointSpreadFunction psf = spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( v );

			if ( psf == null )
				IOFunctions.println( "No PSF assigned to view " + Group.pvid( v ) );
			else
				viewIds.add( v );
		}

		if ( viewIds.isEmpty() )
		{
			IOFunctions.println( "No PSFs available. Stopping." );
			return false;
		}
		else
		{
			IOFunctions.println( "Displaying PSFs of " + viewIds.size() + " views." );
		}

		if ( choice == 0 )
		{
			DisplayImage.getImagePlusInstance( PSF_Average.averagePSF( spimData, viewIds ), false, "Averaged PSF", 0, 1 ).show();
		}
		else if ( choice == 1 )
		{
			DisplayImage.getImagePlusInstance( averageTransformedPSF( spimData, viewIds ), false, "Averaged transformed PSF", 0, 1 ).show();
		}
		else if ( choice == 2 )
		{
			DisplayImage.getImagePlusInstance( PSFCombination.computeMaxProjectionPSF( PSF_Average.averagePSF( spimData, viewIds ) ), false, "Maximum Projection of averaged PSF", 0, 1 ).show();
		}
		else if ( choice == 3 )
		{
			DisplayImage.getImagePlusInstance( PSFCombination.computeMaxProjectionPSF( averageTransformedPSF( spimData, viewIds ) ), false, "Maximum Projection of averaged transformed PSF", 0, 1 ).show();
		}

		return true;
	}

	public static Img< FloatType > averageTransformedPSF( final SpimData2 spimData, final Collection< ? extends ViewId > viewIds )
	{
		final HashMap< ViewId, Img< FloatType > > psfs = new HashMap<>();
		final HashMap< ViewId, PointSpreadFunction > psfLookup = spimData.getPointSpreadFunctions().getPointSpreadFunctions();

		for ( final ViewId viewId : viewIds )
		{
			if ( psfLookup.containsKey( viewId ) )
			{
				PointSpreadFunction psf = psfLookup.get( viewId );
				Img< FloatType > psfCopy = psf.getPSFCopy();

				// normalize PSF
				PSFExtraction.normalize( psfCopy );

				spimData.getViewRegistrations().getViewRegistration( viewId ).updateModel();
				psfs.put( viewId, PSFExtraction.transformPSF( psfCopy, spimData.getViewRegistrations().getViewRegistration( viewId ).getModel() ) );

				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Transforming & averaging '" + psf.getFile() + "' from " + Group.pvid( viewId ) );
			}
			else
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Could NOT find psf for " + Group.pvid( viewId ) );
			}
		}

		return PSFCombination.computeAverageImage( psfs.values(), new ArrayImgFactory< FloatType >(), true );
	}

}
