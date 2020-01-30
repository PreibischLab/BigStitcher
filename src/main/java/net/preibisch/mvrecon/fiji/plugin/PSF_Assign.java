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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.FinalInterval;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunction;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class PSF_Assign implements PlugIn
{
	public static String[] assignTypeChoices = new String[] {
			"Assign existing PSF to all selected views",
			"Assign new PSF to all selected views",
			"Duplicate PSFs from other channel",
			"Duplicate PSFs from other timepoint" };

	public static int defaultAssignType = 0;

	public static int defaultPSF = 0;
	public static String defaultPSFPath = "";

	public static int defaultChannelFrom = 0;
	public static int defaultChannelTo = 0;

	public static int defaultTimePointFrom = 0;

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Dataset Fusion", true, true, true, true, true ) )
			return;

		assign(result.getData(), SpimData2.getAllViewIdsSorted(result.getData(),
			result.getViewSetupsToProcess(), result.getTimePointsToProcess()), result
				.getClusterExtension(), result.getXMLFileName(), true);
	}
	
	public static boolean assign(
		final SpimData2 spimData,
		final Collection< ? extends ViewId > viewCollection)
	{
		return assign(spimData, viewCollection, null, null, false);
	}

	public static boolean assign(
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
		if ( removed.size() > 0 ) IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Removed " +  removed.size() + " views because they are not present." );

		final GenericDialog gd = new GenericDialog( "Assign PSF to views" );

		gd.addChoice( "Type of PSF assignment", assignTypeChoices, assignTypeChoices[ defaultAssignType ] );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return false;

		final int assignType = defaultAssignType = gd.getNextChoiceIndex();
		
		if ( assignType == 0 ) // "Assign existing PSF to all selected views"
		{
			final GenericDialog gd1 = new GenericDialog( "Assign existing PSF to views" );

			final ArrayList< ViewId > psfs = viewsWithUniquePSFs( spimData.getPointSpreadFunctions() );
			final String[] psfTitles = assemblePSFs( psfs, spimData.getPointSpreadFunctions().getPointSpreadFunctions() );

			if ( psfTitles.length == 0 )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): No PSFs found." );
				return false;
			}

			if ( defaultPSF >= psfTitles.length )
				defaultPSF = 0;

			gd1.addChoice( "Select PSF", psfTitles, psfTitles[ defaultPSF ] );

			gd1.showDialog();
			if ( gd1.wasCanceled() )
				return false;

			final int psfChoice = defaultPSF = gd1.getNextChoiceIndex();
			final String file = spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( psfs.get( psfChoice ) ).getFile();

			for ( final ViewId viewId : viewIds )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assigning '" + file + "' to " + Group.pvid( viewId ) );
				spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData.getBasePath(), file ) );
				if ( saveXml )
					SpimData2.saveXML( spimData, xmlFileName, clusterExtension );
			}
		}
		else if ( assignType == 1 ) // "Assign new PSF to all selected views"
		{
			final GenericDialogPlus gd1 = new GenericDialogPlus( "Assign new PSF to views" );

			gd1.addFileField( "Specify PSF file", defaultPSFPath, 80 );
			gd1.addMessage( "Note: File dimensions must be odd, with the center of the PSF in the middle", GUIHelper.mediumstatusfont );

			gd1.showDialog();
			if ( gd1.wasCanceled() )
				return false;

			final String fileName = gd1.getNextString();
			final File file = new File( fileName );

			final Img< FloatType > img = loadAndTestPSF( file );

			if ( img == null )
				return false;

			String localFileName = null;

			for ( final ViewId viewId : viewIds )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assigning '" + file + "' to " + Group.pvid( viewId ) );

				if ( localFileName == null )
				{
					final PointSpreadFunction psf = new PointSpreadFunction( spimData, viewId, img );
					localFileName = psf.getFile();
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Local filename '" + localFileName + "' assigned" );
					spimData.getPointSpreadFunctions().addPSF( viewId, psf );
					if ( saveXml )
						SpimData2.saveXML( spimData, xmlFileName, clusterExtension );
				}
				else
				{
					spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData.getBasePath(), localFileName ) );
					if ( saveXml )
						SpimData2.saveXML( spimData, xmlFileName, clusterExtension );
				}
			}
		}
		else if ( assignType == 2 ) // "Duplicate PSFs from other channel"
		{
			final ArrayList< Channel > channels = SpimData2.getAllChannelsSorted( spimData, viewIds );

			final boolean[] present = new boolean[ channels.size() ];
			final boolean[] valid = new boolean[ channels.size() ];

			for ( int c = 0; c < channels.size(); ++c )
			{
				present[ c ] = false;
				valid[ c ] = true;
			}

			for ( final ViewId viewId : viewIds )
			{
				final ViewDescription vd = spimData.getSequenceDescription().getViewDescription( viewId );

				for ( int c = 0; c < channels.size(); ++c )
				{
					if ( vd.getViewSetup().getChannel().getId() == channels.get( c ).getId() )
					{
						present[ c ] = true;
						if ( !spimData.getPointSpreadFunctions().getPointSpreadFunctions().containsKey( viewId ) )
							valid[ c ] = false;
					}
				}
			}

			final ArrayList< Channel > candidateChannels = new ArrayList<>();
			final ArrayList< Channel > targetChannels = new ArrayList<>();

			for ( int c = 0; c < channels.size(); ++c )
			{
				if ( present[ c ] )
				{
					targetChannels.add( channels.get( c ) );
					if ( valid[ c ] )
						candidateChannels.add( channels.get( c ) );
				}
			}

			if ( candidateChannels.size() == 0 )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): No channels found that have PSFs for all selected views." );
				return false;
			}

			final GenericDialogPlus gd1 = new GenericDialogPlus( "Assign PSFs from one to another channel" );

			final String[] from = new String[ candidateChannels.size() ];
			final String[] to = new String[ targetChannels.size() ];

			for ( int c = 0; c < candidateChannels.size(); ++c )
				from[ c ] = candidateChannels.get( c ).getName();

			for ( int c = 0; c < targetChannels.size(); ++c )
				to[ c ] = targetChannels.get( c ).getName();

			if ( defaultChannelFrom >= from.length )
				defaultChannelFrom = 0;

			if ( defaultChannelTo >= to.length )
				defaultChannelTo = 0;

			gd1.addChoice( "Source_channel", from, from[ defaultChannelFrom ] );
			gd1.addChoice( "Target_channel", to, to[ defaultChannelTo ] );

			gd1.showDialog();
			if ( gd1.wasCanceled() )
				return false;

			final Channel source = candidateChannels.get( defaultChannelFrom =  gd1.getNextChoiceIndex() );
			final Channel target = targetChannels.get( defaultChannelTo =  gd1.getNextChoiceIndex() );


			for ( final ViewId viewId : viewIds )
			{
				final ViewDescription vdT = spimData.getSequenceDescription().getViewDescription( viewId );

				if ( vdT.getViewSetup().getChannel().getId() == target.getId() )
				{
					// which is the corresponding viewId with the different channel?
					ViewDescription corresponding = null;

					for ( final ViewId viewSearch : viewIds )
					{
						final ViewDescription vdS = spimData.getSequenceDescription().getViewDescription( viewSearch );

						if ( vdS.getViewSetup().getChannel().getId() == source.getId() )
						{
							if (
								vdS.getViewSetup().getTile().getId() == vdT.getViewSetup().getTile().getId() &&
								vdS.getViewSetup().getAngle().getId() == vdT.getViewSetup().getAngle().getId() &&
								vdS.getViewSetup().getIllumination().getId() == vdT.getViewSetup().getIllumination().getId() && 
								vdS.getTimePointId() == vdT.getTimePointId() )
							{
								corresponding = vdS;
								break;
							}
						}
					}

					if ( corresponding == null )
					{
						IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): No corresponding view found for: " + Group.pvid( vdT ) );
					}
					else
					{
						final String file = spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( corresponding ).getFile();

						IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assigning '" + file + "' from " +  Group.pvid( corresponding ) + " to " + Group.pvid( viewId ) );

						spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData.getBasePath(), file ) );
						
						if ( saveXml )
							SpimData2.saveXML( spimData, xmlFileName, clusterExtension );
					}
				}
			}
		}
		else if ( assignType == 3 ) // "Duplicate PSFs from other timepoint"
		{
			final ArrayList< TimePoint > timepoints = SpimData2.getAllTimePointsSorted( spimData, viewIds );

			final boolean[] present = new boolean[ timepoints.size() ];
			final boolean[] valid = new boolean[ timepoints.size() ];

			for ( int t = 0; t < timepoints.size(); ++t )
			{
				present[ t ] = false;
				valid[ t ] = true;
			}

			for ( final ViewId viewId : viewIds )
			{
				for ( int t = 0; t < timepoints.size(); ++t )
				{
					if ( viewId.getTimePointId() == timepoints.get( t ).getId() )
					{
						present[ t ] = true;
						if ( !spimData.getPointSpreadFunctions().getPointSpreadFunctions().containsKey( viewId ) )
							valid[ t ] = false;
					}
				}
			}

			final ArrayList< TimePoint > candidateTimePoints = new ArrayList<>();

			for ( int t = 0; t < timepoints.size(); ++t )
				if ( present[ t ] && valid[ t ] )
					candidateTimePoints.add( timepoints.get( t ) );

			if ( candidateTimePoints.size() == 0 )
			{
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): No timepoints found that have PSFs for all selected views." );
				return false;
			}

			final GenericDialogPlus gd1 = new GenericDialogPlus( "Assign PSFs from one to other timepoints" );

			final String[] from = new String[ candidateTimePoints.size() ];

			for ( int t = 0; t < candidateTimePoints.size(); ++t )
				from[ t ] = "Timepoint " + candidateTimePoints.get( t ).getId();

			if ( defaultTimePointFrom >= from.length )
				defaultTimePointFrom = 0;

			gd1.addChoice( "Source_timepoint", from, from[ defaultTimePointFrom ] );

			gd1.showDialog();
			if ( gd1.wasCanceled() )
				return false;

			final TimePoint source = candidateTimePoints.get( defaultTimePointFrom =  gd1.getNextChoiceIndex() );

			for ( final ViewId viewId : viewIds )
			{
				if ( viewId.getTimePointId() != source.getId() )
				{
					// which is the corresponding viewId with the different channel?
					ViewId corresponding = null;

					for ( final ViewId viewSearch : viewIds )
					{
						if ( viewId.getViewSetupId() == viewSearch.getViewSetupId() )
						{
							corresponding = viewSearch;
							break;
						}
					}

					if ( corresponding == null )
					{
						IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): No corresponding view found for: " + Group.pvid( viewId ) );
					}
					else
					{
						final String file = spimData.getPointSpreadFunctions().getPointSpreadFunctions().get( corresponding ).getFile();

						IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Assigning '" + file + "' from " +  Group.pvid( corresponding ) + " to " + Group.pvid( viewId ) );

						spimData.getPointSpreadFunctions().addPSF( viewId, new PointSpreadFunction( spimData.getBasePath(), file ) );
						
						if ( saveXml )
							SpimData2.saveXML( spimData, xmlFileName, clusterExtension );
					}
				}
			}
		}

		return true;
	}

	public static Img< FloatType > loadAndTestPSF( final File file )
	{
		final Img< FloatType > img = IOFunctions.openAs32Bit( file );

		if ( img == null )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Couldn't load: '" + file + "'" );
			return null;
		}

		if ( img.numDimensions() != 3 )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Image is not 3-dimensional, but dim=" + img.numDimensions() );
			return null;
		}

		if ( img.dimension( 0 ) % 2 != 1 || img.dimension( 1 ) % 2 != 1 || img.dimension( 2 ) % 2 != 1 )
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Image dimensions are not odd, but dim=" + Util.printInterval( new FinalInterval( img ) ) );
			return null;
		}

		return img;
	}

	public static String[] assemblePSFs( final ArrayList< ViewId > views, HashMap< ViewId, PointSpreadFunction > psfs )
	{
		final String[] psfTitles = new String[ views.size() ];

		for ( int i = 0; i < views.size(); ++i )
		{
			final ViewId v = views.get( i );

			psfTitles[ i ] = Group.pvid( v ) + " '" + psfs.get( v ).getFile() + "'";
		}

		return psfTitles;
	}

	public static ArrayList< ViewId > viewsWithUniquePSFs( final PointSpreadFunctions psfs )
	{
		final HashMap< String, ViewId > psfFiles = new HashMap<>();

		for ( final ViewId v : psfs.getPointSpreadFunctions().keySet() )
			psfFiles.put( psfs.getPointSpreadFunctions().get( v ).getFile(), v );

		final ArrayList< ViewId > allViewsWithUniqueNames = new ArrayList<>();
		allViewsWithUniqueNames.addAll( psfFiles.values() );
		Collections.sort( allViewsWithUniqueNames );

		return allViewsWithUniqueNames;
	}

	public static void main( String[] args )
	{
		final HashMap< String, ViewId > psfFiles = new HashMap<>();
		psfFiles.put( "psf_1.tif", new ViewId( 0, 0 ) );
		psfFiles.put( "psf_1.tif", new ViewId( 0, 1 ) );

		for ( final String s : psfFiles.keySet() )
			System.out.println( s );
	}
}
