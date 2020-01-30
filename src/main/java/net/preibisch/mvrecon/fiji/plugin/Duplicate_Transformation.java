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

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

public class Duplicate_Transformation implements PlugIn
{
	public static String[] duplicationChoice = new String[]{
		"One timepoint to other timepoints",
		"One channel to other channels",
		"One illumination direction to other illumination directions",
		"One angle to other angles",
		"One tile to other tiles"
	};

	public static String[] transformationChoice = new String[]{
		"Replace all transformations",
		"Add last transformation only",
		"Add multiple transformations"
	};

	public static String[] tpChoice = new String[]{ "All Timepoints", "Single Timepoint (Select from List)", "Multiple Timepoints (Select from List)", "Range of Timepoints (Specify by Name)" };
	public static int defaultTPChoice = 0;
	public static int defaultTimePointIndex = 0;
	public static boolean[] defaultTimePointIndices = null;
	public static String defaultTimePointString = null;

	public static String[] angleChoice = new String[]{ "All Angles", "Single Angle (Select from List)", "Multiple Angles (Select from List)", "Range of Angles (Specify by Name)" };
	public static int defaultAngleChoice = 0;
	public static int defaultAngleIndex = 0;
	public static boolean[] defaultAngleIndices = null;
	public static String defaultAngleString = null;

	public static String[] channelChoice = new String[]{ "All Channels", "Single Channel (Select from List)", "Multiple Channels (Select from List)", "Range of Channels (Specify by Name)" };
	public static int defaultChannelChoice = 0;
	public static int defaultChannelIndex = 0;
	public static boolean[] defaultChannelIndices = null;
	public static String defaultChannelString = null;

	public static String[] illumChoice = new String[]{ "All Illumination Directions", "Single Illumination Directions (Select from List)", "Multiple Illumination Directions (Select from List)", "Range of Illumination Directions (Specify by Name)" };
	public static int defaultIllumChoice = 0;
	public static int defaultIllumIndex = 0;
	public static boolean[] defaultIllumIndices = null;
	public static String defaultIllumString = null;

	public static String[] tileChoice = new String[]{ "All Tiles", "Single Tile (Select from List)", "Multiple Tiles (Select from List)", "Range of Tiles (Specify by Name)" };
	public static int defaultTileChoice = 0;
	public static int defaultTileIndex = 0;
	public static boolean[] defaultTileIndices = null;
	public static String defaultTileString = null;

	public static int defaultChoice = 0;
	public static int defaultTransformationChoice = 0;
	public static int defaultNumTransformations = 2;
	public static int defaultTimePoint = 0;
	public static int defaultSelectedTimePointIndex = 1;
	public static int defaultChannel = 0;
	public static int defaultSelectedChannelIndex = 1;
	public static int defaultIllum = 0;
	public static int defaultSelectedIllumIndex = 1;
	public static int defaultAngle = 0;
	public static int defaultSelectedAngleIndex = 1;
	public static int defaultTile = 0;
	public static int defaultSelectedTileIndex = 1;

	@Override
	public void run( final String arg0 )
	{
		final GenericDialog gd = new GenericDialog( "Define Duplication" );
		gd.addChoice( "Apply transformation of", duplicationChoice, duplicationChoice[ defaultChoice ] );
		gd.showDialog();
		if ( gd.wasCanceled() )
			return;
		
		final int choice = defaultChoice = gd.getNextChoiceIndex();

		final boolean askForTimepoints = choice != 0;
		final boolean askForChannels = choice != 1;
		final boolean askForIllum = choice != 2;
		final boolean askForAngles = choice != 3;
		final boolean askForTiles = choice != 4;

		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "duplicating transformations", "Apply to", askForAngles, askForChannels, askForIllum, askForTiles, askForTimepoints ) )
			return;
		
		if ( !askForTimepoints )
		{
			if ( result.getTimePointsToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one timepoint available, cannot apply to another timepoint." );
				return;
			}
			else
			{
				if ( !applyTimepoints( result ) )
					return;
			}
		}
		else if ( !askForChannels )
		{
			if ( result.getChannelsToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one channel available, cannot apply to another channel." );
				return;
			}
			else
			{
				if ( !applyChannels( result ) )
					return;
			}			
		}
		else if ( !askForIllum )
		{
			if ( result.getIlluminationsToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one illumination direction available, cannot apply to another illumination direction." );
				return;
			}
			else
			{
				if ( !applyIllums( result ) )
					return;
			}			
		}
		else if ( !askForAngles )
		{
			if ( result.getAnglesToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one angle available, cannot apply to another angle." );
				return;
			}
			else
			{
				if ( !applyAngles( result ) )
					return;
			}			
		}
		else if ( !askForTiles )
		{
			if ( result.getTilesToProcess().size() == 1 )
			{
				IOFunctions.println( "Only one tile available, cannot apply to another tile." );
				return;
			}
			else
			{
				if ( !applyTiles( result ) )
					return;
			}			
		}

		// now save it in case something was applied
		SpimData2.saveXML( result.getData(), new File( result.getXMLFileName() ).getName(), result.getClusterExtension() );
	}

	protected void askForRegistrations( final GenericDialog gd )
	{
		gd.addMessage( "" );
		gd.addChoice( "Duplicate_which_transformations", transformationChoice, transformationChoice[ defaultTransformationChoice ] );
	}

	/*
	 * 
	 * @param gd
	 * @return -1 means invalid/cancelled, 0 means all, &gt;0 means how many
	 */
	protected int parseRegistrations( final GenericDialog gd )
	{
		int transformation = defaultTransformationChoice = gd.getNextChoiceIndex();
		
		if ( transformation == 2 )
		{
			final GenericDialog gd2 = new GenericDialog( "Choose number of transformations" );
			gd2.addNumericField( "Number of transformations to add", defaultNumTransformations, 0 );
			
			gd2.showDialog();
			
			if ( gd2.wasCanceled() )
				return -1;
			else
				transformation = (int)Math.round( gd2.getNextNumber() );
		}
		
		return transformation;
	}

	protected void duplicateTransformations( final int transformations, final ViewId sourceViewId, final ViewId targetViewId, final SpimData2 spimData )
	{
		final ViewDescription sourceVD = spimData.getSequenceDescription().getViewDescription( 
				sourceViewId.getTimePointId(), sourceViewId.getViewSetupId() );

		final ViewDescription targetVD = spimData.getSequenceDescription().getViewDescription( 
				targetViewId.getTimePointId(), targetViewId.getViewSetupId() );

		final ViewSetup sourceVS = sourceVD.getViewSetup();
		final ViewSetup targetVS = targetVD.getViewSetup();

		IOFunctions.println( "Source viewId t=" + sourceVD.getTimePoint().getName() + ", ch=" + sourceVS.getChannel().getName() + ", ill=" + sourceVS.getIllumination().getName() + ", angle=" + sourceVS.getAngle().getName() );
		IOFunctions.println( "Target viewId t=" + targetVD.getTimePoint().getName() + ", ch=" + targetVS.getChannel().getName() + ", ill=" + targetVS.getIllumination().getName() + ", angle=" + targetVS.getAngle().getName() );  
		
		if ( !sourceVD.isPresent() || !targetVD.isPresent() )
		{
			if ( !sourceVD.isPresent() )
				IOFunctions.println( "Source viewId is NOT present" );
			
			if ( !targetVD.isPresent() )
				IOFunctions.println( "Target viewId is NOT present" );
			
			return;
		}
		
		// update the view registration
		final ViewRegistrations viewRegistrations = spimData.getViewRegistrations();
		
		final ViewRegistration vrSource = viewRegistrations.getViewRegistration( sourceViewId );
		final ViewRegistration vrTarget = viewRegistrations.getViewRegistration( targetViewId );
		
		// reset the transformation and add all
		if ( transformations == 0 )
		{
			vrTarget.identity();

			for ( final ViewTransform vt : vrSource.getTransformList() )
			{
				IOFunctions.println( "Concatenationg model " + vt.getName() + ", " + vt.asAffine3D() );
				vrTarget.concatenateTransform( vt );
			}
		}
		else
		{
			// copy the last n transformations
			final ArrayList< ViewTransform > vts = new ArrayList< ViewTransform >();
			for ( int k = 0; k < transformations; ++k )
				vts.add( vrSource.getTransformList().get( k ) );
			
			// and add them at the end
			for ( int k = vts.size() - 1; k >= 0; --k )
			{
				final ViewTransform vt = vts.get( k );
				IOFunctions.println( "Adding model " + vt.getName() + ", " + vt.asAffine3D() );
				vrTarget.preconcatenateTransform( vt );
			}
		}
	}

	protected boolean applyTimepoints( final LoadParseQueryXML result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target timepoints" );
		
		final String[] timepoints = assembleTimepoints( result.getTimePointsToProcess() );
		
		if ( defaultTimePoint >= timepoints.length )
			defaultTimePoint = 0;
		
		gd.addChoice( "Source timepoint", timepoints, timepoints[ defaultTimePoint ] );
		gd.addChoice( "Target timepoint(s)", tpChoice, tpChoice[ defaultTPChoice ] );

		askForRegistrations( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final TimePoint source = result.getTimePointsToProcess().get( defaultTimePoint = gd.getNextChoiceIndex() );
		final ArrayList< TimePoint > targets = new ArrayList< TimePoint >();
		
		final int choice = defaultTPChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedTimePointIndex >= timepoints.length )
				defaultSelectedTimePointIndex = 1;
			
			final int selection = GenericLoadParseQueryXML.queryIndividualEntry( "Timepoint", timepoints, defaultSelectedTimePointIndex );
			
			if ( selection >= 0 )
				targets.add( result.getTimePointsToProcess().get( defaultSelectedTimePointIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple timepoints or timepoints defined by pattern
		{
			final boolean[] selection;
			String[] defaultTimePoint = new String[]{ defaultTimePointString };
			
			if ( choice == 2 )
				selection = GenericLoadParseQueryXML.queryMultipleEntries( "Timepoints", timepoints, defaultTimePointIndices );
			else
				selection = GenericLoadParseQueryXML.queryPattern( "Timepoints", timepoints, defaultTimePoint );
			
			if ( selection == null )
				return false;
			else
			{
				defaultTimePointIndices = selection;
				
				if ( choice == 3 )
					defaultTimePointString = defaultTimePoint[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getTimePointsToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getTimePointsToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of timepoints is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );

			if ( transformations < 0 )
				return false;

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying timepoint " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final Channel c : result.getChannelsToProcess() )
						for ( final Illumination i : result.getIlluminationsToProcess() )
							for ( final Angle a : result.getAnglesToProcess() )
								for ( final Tile x : result.getTilesToProcess() )
								{
									final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), source, c, a, i, x );
									final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), targets.get( j ), c, a, i, x );
									
									// this happens only if a viewsetup is not present in any timepoint
									// (e.g. after appending fusion to a dataset)
									if ( sourceViewId == null || targetViewId == null )
										continue;
	
									duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
								}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyChannels( final LoadParseQueryXML result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target channels" );
		
		final String[] channels = assembleChannels( result.getChannelsToProcess() );
		
		if ( defaultChannel >= channels.length )
			defaultChannel = 0;
		
		gd.addChoice( "Source channel", channels, channels[ defaultChannel ] );
		gd.addChoice( "Target channel(s)", channelChoice, channelChoice[ defaultChannelChoice ] );
		
		askForRegistrations( gd );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Channel source = result.getChannelsToProcess().get( defaultChannel = gd.getNextChoiceIndex() );
		final ArrayList< Channel > targets = new ArrayList< Channel >();
		
		final int choice = defaultChannelChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedChannelIndex >= channels.length )
				defaultSelectedChannelIndex = 1;
			
			final int selection = GenericLoadParseQueryXML.queryIndividualEntry( "Channel", channels, defaultSelectedChannelIndex );
			
			if ( selection >= 0 )
				targets.add( result.getChannelsToProcess().get( defaultSelectedChannelIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple channels or channels defined by pattern
		{
			final boolean[] selection;
			String[] defaultChannel = new String[]{ defaultChannelString };
			
			if ( choice == 2 )
				selection = GenericLoadParseQueryXML.queryMultipleEntries( "Channels", channels, defaultChannelIndices );
			else
				selection = GenericLoadParseQueryXML.queryPattern( "Channels", channels, defaultChannel );
			
			if ( selection == null )
				return false;
			else
			{
				defaultChannelIndices = selection;
				
				if ( choice == 3 )
					defaultChannelString = defaultChannel[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getChannelsToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getChannelsToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of channels is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );
			
			if ( transformations < 0 )
				return false;
			
			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying chanel " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Illumination i : result.getIlluminationsToProcess() )
							for ( final Angle a : result.getAnglesToProcess() )
								for ( final Tile x : result.getTilesToProcess() )
								{
									final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, source, a, i, x );
									final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, targets.get( j ), a, i, x );
									
									// this happens only if a viewsetup is not present in any timepoint
									// (e.g. after appending fusion to a dataset)
									if ( sourceViewId == null || targetViewId == null )
										continue;
	
									duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
								}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyIllums( final LoadParseQueryXML result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target illumination directions" );
		
		final String[] illums = assembleIllums( result.getIlluminationsToProcess() );
		
		if ( defaultIllum >= illums.length )
			defaultIllum = 0;
		
		gd.addChoice( "Source illumination direction", illums, illums[ defaultIllum ] );
		gd.addChoice( "Target illumination direction(s)", illumChoice, illumChoice[ defaultIllumChoice ] );

		askForRegistrations( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Illumination source = result.getIlluminationsToProcess().get( defaultIllum = gd.getNextChoiceIndex() );
		final ArrayList< Illumination > targets = new ArrayList< Illumination >();
		
		final int choice = defaultIllumChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedIllumIndex >= illums.length )
				defaultSelectedIllumIndex = 1;
			
			final int selection = GenericLoadParseQueryXML.queryIndividualEntry( "Illumination direction", illums, defaultSelectedIllumIndex );
			
			if ( selection >= 0 )
				targets.add( result.getIlluminationsToProcess().get( defaultSelectedIllumIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple illum dir or illum dirs defined by pattern
		{
			final boolean[] selection;
			String[] defaultIllum = new String[]{ defaultIllumString };
			
			if ( choice == 2 )
				selection = GenericLoadParseQueryXML.queryMultipleEntries( "Illumination directions", illums, defaultIllumIndices );
			else
				selection = GenericLoadParseQueryXML.queryPattern( "Illumination directions", illums, defaultIllum );
			
			if ( selection == null )
				return false;
			else
			{
				defaultIllumIndices = selection;
				
				if ( choice == 3 )
					defaultIllumString = defaultIllum[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getIlluminationsToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getIlluminationsToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of illumination directions is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );
			
			if ( transformations < 0 )
				return false;

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying illumination direction " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Channel c : result.getChannelsToProcess() )
							for ( final Angle a : result.getAnglesToProcess() )
								for ( final Tile x : result.getTilesToProcess() )
								{
									final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, source, x );
									final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, targets.get( j ), x );
									
									// this happens only if a viewsetup is not present in any timepoint
									// (e.g. after appending fusion to a dataset)
									if ( sourceViewId == null || targetViewId == null )
										continue;
	
									duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
								}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyAngles( final LoadParseQueryXML result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target angles" );
		
		final String[] angles = assembleAngles( result.getAnglesToProcess() );
		
		if ( defaultAngle >= angles.length )
			defaultAngle = 0;
		
		gd.addChoice( "Source angle", angles, angles[ defaultAngle ] );
		gd.addChoice( "Target angles(s)", angleChoice, angleChoice[ defaultAngleChoice ] );

		askForRegistrations( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Angle source = result.getAnglesToProcess().get( defaultAngle = gd.getNextChoiceIndex() );
		final ArrayList< Angle > targets = new ArrayList< Angle >();
		
		final int choice = defaultAngleChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedAngleIndex >= angles.length )
				defaultSelectedAngleIndex = 1;
			
			final int selection = GenericLoadParseQueryXML.queryIndividualEntry( "Angle", angles, defaultSelectedAngleIndex );
			
			if ( selection >= 0 )
				targets.add( result.getAnglesToProcess().get( defaultSelectedAngleIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple angle or angles defined by pattern
		{
			final boolean[] selection;
			String[] defaultAngle = new String[]{ defaultAngleString };
			
			if ( choice == 2 )
				selection = GenericLoadParseQueryXML.queryMultipleEntries( "Angles", angles, defaultAngleIndices );
			else
				selection = GenericLoadParseQueryXML.queryPattern( "Angles", angles, defaultAngle );
			
			if ( selection == null )
				return false;
			else
			{
				defaultAngleIndices = selection;
				
				if ( choice == 3 )
					defaultAngleString = defaultAngle[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getAnglesToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getAnglesToProcess() );				
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of angles is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );

			if ( transformations < 0 )
				return false;

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying angle " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Channel c : result.getChannelsToProcess() )
							for ( final Illumination i : result.getIlluminationsToProcess() )
								for ( final Tile x : result.getTilesToProcess() )
								{
									final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, source, i, x );
									final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, targets.get( j ), i, x );
									
									// this happens only if a viewsetup is not present in any timepoint
									// (e.g. after appending fusion to a dataset)
									if ( sourceViewId == null || targetViewId == null )
										continue;
	
									duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
								}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected boolean applyTiles( final LoadParseQueryXML result )
	{
		final GenericDialog gd = new GenericDialog( "Define source and target tiles" );
		
		final String[] tiles = assembleTiles( result.getTilesToProcess() );
		
		if ( defaultTile >= tiles.length )
			defaultTile = 0;
		
		gd.addChoice( "Source tiles", tiles, tiles[ defaultTile ] );
		gd.addChoice( "Target tile(s)", tileChoice, tileChoice[ defaultTileChoice ] );

		askForRegistrations( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		final Tile source = result.getTilesToProcess().get( defaultTile = gd.getNextChoiceIndex() );
		final ArrayList< Tile > targets = new ArrayList< Tile >();
		
		final int choice = defaultTileChoice = gd.getNextChoiceIndex();
		
		if ( choice == 1 )
		{
			if ( defaultSelectedTileIndex >= tiles.length )
				defaultSelectedTileIndex = 1;
			
			final int selection = GenericLoadParseQueryXML.queryIndividualEntry( "Tile", tiles, defaultSelectedTileIndex );
			
			if ( selection >= 0 )
				targets.add( result.getTilesToProcess().get( defaultSelectedTileIndex = selection ) );
			else
				return false;
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple tiles or tiles defined by pattern
		{
			final boolean[] selection;
			String[] defaultTile = new String[]{ defaultTileString };
			
			if ( choice == 2 )
				selection = GenericLoadParseQueryXML.queryMultipleEntries( "Tiles", tiles, defaultTileIndices );
			else
				selection = GenericLoadParseQueryXML.queryPattern( "Tiles", tiles, defaultTile );
			
			if ( selection == null )
				return false;
			else
			{
				defaultTileIndices = selection;
				
				if ( choice == 3 )
					defaultTileString = defaultTile[ 0 ];
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						targets.add( result.getTilesToProcess().get( i ) );
			}
		}
		else
		{
			targets.addAll( result.getTilesToProcess() );
		}
		
		if ( targets.size() == 0 )
		{
			IOFunctions.println( "List of tiles is empty. Stopping." );
			return false;
		}
		else
		{
			final int transformations = parseRegistrations( gd );

			if ( transformations < 0 )
				return false;

			int countApplied = 0;
			
			for ( int j = 0; j < targets.size(); ++j )
				if ( !source.equals( targets.get( j ) ) )
				{
					IOFunctions.println( "Applying tile " + source.getName() + " >>> " + targets.get( j ).getName() );
					++countApplied;
					
					for ( final TimePoint t : result.getTimePointsToProcess() )
						for ( final Channel c : result.getChannelsToProcess() )
							for ( final Illumination i : result.getIlluminationsToProcess() )
								for ( final Angle a : result.getAnglesToProcess() )
								{
									final ViewId sourceViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, i, source );
									final ViewId targetViewId = SpimData2.getViewId( result.getData().getSequenceDescription(), t, c, a, i, targets.get( j ) );
									
									// this happens only if a viewsetup is not present in any timepoint
									// (e.g. after appending fusion to a dataset)
									if ( sourceViewId == null || targetViewId == null )
										continue;
	
									duplicateTransformations( transformations, sourceViewId, targetViewId, result.getData() );
								}
				}
			
			if ( countApplied == 0 )
				return false;
		}
		return true;
	}

	protected String[] assembleTimepoints( final List< TimePoint > timepoints )
	{
		final String[] tps = new String[ timepoints.size() ];
		
		for ( int t = 0; t < tps.length; ++t )
			tps[ t ] = timepoints.get( t ).getName();
		
		return tps;
	}

	protected String[] assembleChannels( final List< Channel > channels )
	{
		final String[] chs = new String[ channels.size() ];
		
		for ( int t = 0; t < chs.length; ++t )
			chs[ t ] = channels.get( t ).getName();
		
		return chs;
	}

	protected String[] assembleIllums( final List< Illumination > illums )
	{
		final String[] is = new String[ illums.size() ];
		
		for ( int t = 0; t < is.length; ++t )
			is[ t ] = illums.get( t ).getName();
		
		return is;
	}

	protected String[] assembleAngles( final List< Angle > angles )
	{
		final String[] as = new String[ angles.size() ];
		
		for ( int t = 0; t < as.length; ++t )
			as[ t ] = angles.get( t ).getName();
		
		return as;
	}

	protected String[] assembleTiles( final List< Tile > tiles )
	{
		final String[] ts = new String[ tiles.size() ];
		
		for ( int t = 0; t < ts.length; ++t )
			ts[ t ] = tiles.get( t ).getName();
		
		return ts;
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new Duplicate_Transformation().run( null );
	}
}
