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
import java.util.List;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.psf.PSFExtraction;

public class PSF_Extract implements PlugIn
{
	public static int defaultLabel = -1;
	public static boolean defaultCorresponding = true;
	public static boolean defaultRemoveMinIntensity = true;
	public static int defaultPSFSizeX = 19;
	public static int defaultPSFSizeY = 19;
	public static int defaultPSFSizeZ = 25;

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		extract( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ), result.getClusterExtension(), result.getXMLFileName(), true );
	}

	public static boolean extract(
		final SpimData2 spimData,
		final Collection< ? extends ViewId > viewCollection )
	{
		return extract( spimData, viewCollection, null, null, false );
	}

	public static boolean extract(
			final SpimData2 spimData,
			final Collection< ? extends ViewId > viewCollection,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXml )
	{
		final ArrayList< ViewId > viewIds = new ArrayList<>();
		viewIds.addAll( viewCollection );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( spimData, viewIds );
		if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		// check which channels and labels are available and build the choices
		final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, viewIds );

		if ( labels.length == 0 )
		{
			IOFunctions.printErr( "No interest points available, stopping. Please run Interest Point Detection first" );
			return false;
		}

		// choose the first label that is complete if possible
		if ( defaultLabel < 0 || defaultLabel >= labels.length )
		{
			defaultLabel = -1;

			for ( int i = 0; i < labels.length; ++i )
				if ( !labels[ i ].contains( InterestPointTools.warningLabel ) )
				{
					defaultLabel = i;
					break;
				}

			if ( defaultLabel == -1 )
				defaultLabel = 0;
		}

		final GenericDialog gd = new GenericDialog( "Select Interest Point Label" );

		gd.addChoice( "Interest_points" , labels, labels[ defaultLabel ] );
		gd.addCheckbox( "Use_Corresponding interest points", defaultCorresponding );

		gd.addMessage( "" );

		gd.addCheckbox( "Remove_min_intensity_projections_from_PSF", defaultRemoveMinIntensity );

		gd.addMessage( "" );

		gd.addSlider( "PSF_size_X (px)", 9, 100, defaultPSFSizeX );
		gd.addSlider( "PSF_size_Y (px)", 9, 100, defaultPSFSizeY );
		gd.addSlider( "PSF_size_Z (px)", 9, 100, defaultPSFSizeZ );

		gd.addMessage( " \nNote: PSF size is in local coordinates [px] of the input view.", GUIHelper.mediumstatusfont );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		final String label = InterestPointTools.getSelectedLabel( labels, defaultLabel = gd.getNextChoiceIndex() );
		final boolean corresponding = defaultCorresponding = gd.getNextBoolean();
		final boolean removeMinIntensity = defaultRemoveMinIntensity = gd.getNextBoolean();
		int psfSizeX = defaultPSFSizeX = (int)Math.round( gd.getNextNumber() );
		int psfSizeY = defaultPSFSizeY = (int)Math.round( gd.getNextNumber() );
		int psfSizeZ = defaultPSFSizeZ = (int)Math.round( gd.getNextNumber() );

		// enforce odd number
		if ( psfSizeX % 2 == 0 )
			defaultPSFSizeX = ++psfSizeX;

		if ( psfSizeY % 2 == 0 )
			defaultPSFSizeY = ++psfSizeY;

		if ( psfSizeZ % 2 == 0 )
			defaultPSFSizeZ = ++psfSizeZ;

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Selected options for PSF extraction: " );
		IOFunctions.println( "Interest point label: " + label );
		IOFunctions.println( "Using corresponding interest points: " + corresponding );
		IOFunctions.println( "Removing min intensity projections from PSF: " + removeMinIntensity );
		IOFunctions.println( "PSF size X (pixels in input image calibration): " + psfSizeX );
		IOFunctions.println( "PSF size Y (pixels in input image calibration): " + psfSizeY );
		IOFunctions.println( "PSF size Z (pixels in input image calibration): " + psfSizeZ );

		int count = 0;

		for ( final ViewId viewId : viewIds )
		{
			IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Extracting PSF for " + Group.pvid( viewId ) + " ... " );

			final PSFExtraction< FloatType > psf = new PSFExtraction< FloatType >( spimData, viewId, label, corresponding, new FloatType(), new long[]{ psfSizeX, psfSizeY, psfSizeZ } );

			if ( psf.hadDetections() )
			{
				++count;

				if ( removeMinIntensity )
					psf.removeMinProjections();

				spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData, viewId, psf.getPSF() ) );
				
				if ( saveXml )
					SpimData2.saveXML( spimData, xmlFileName, clusterExtension );
			}
		}

		IOFunctions.println( "(" + new Date(System.currentTimeMillis()) + "): Extracted " + count + "/" + viewIds.size() + " PSFs." );

		return true;
	}
}
