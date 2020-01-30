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
package net.preibisch.mvrecon.fiji.plugin.queryXML;

import ij.ImageJ;

import java.util.ArrayList;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.plugin.Define_Multi_View_Dataset;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;

public class LoadParseQueryXML extends GenericLoadParseQueryXML< SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader, XmlIoSpimData2 >
{
	public LoadParseQueryXML() { super( new XmlIoSpimData2( "" ) ); }

	public boolean queryXML(
			final String additionalTitle,
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTiles,
			final boolean askForTimepoints )
	{
		return queryXML( additionalTitle, "Process", askForAngles, askForChannels, askForIllum, askForTiles, askForTimepoints );
	}

	public boolean queryXML(
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTiles,
			final boolean askForTimepoints )
	{
		return queryXML( "", "Process", askForAngles, askForChannels, askForIllum, askForTiles, askForTimepoints );
	}
	
	/**
	 * Asks the user for a valid XML (real time parsing)
	 * @param additionalTitle - additional tile
	 * @param query - the query
	 * @param askForAngles - ask the user if he/she wants to select a subset of angles, otherwise all angles are selected
	 * @param askForChannels - ask the user if he/she wants to select a subset of channels, otherwise all channels are selected
	 * @param askForIllum - ask the user if he/she wants to select a subset of illuminations, otherwise all illuminations are selected
	 * @param askForTiles - ask the user if he/she wants to select a subset of tiles, otherwise all tiles are selected
	 * @param askForTimepoints - ask the user if he/she wants to select a subset of timepoints, otherwise all timepoints are selected
	 * @return null if cancelled or timepointlistsize = 0
	 */
	public boolean queryXML(
			final String additionalTitle,
			String query,
			final boolean askForAngles,
			final boolean askForChannels,
			final boolean askForIllum,
			final boolean askForTiles,
			final boolean askForTimepoints
			)
	{
		final ArrayList< String > specifyAttributes = new ArrayList< String >();

		if ( askForTimepoints )
			specifyAttributes.add( "Timepoint" );

		if ( askForChannels )
			specifyAttributes.add( "channel" );

		if ( askForAngles )
			specifyAttributes.add( "angle" );

		if ( askForIllum )
			specifyAttributes.add( "illumination" );
		
		if ( askForTiles )
			specifyAttributes.add( "tile" );

		return queryXML( additionalTitle, query, specifyAttributes );
	}

	@Override
	public boolean queryXML(
			final String additionalTitle,
			String query,
			List< String > specifyAttributes )
	{
		boolean success = super.queryXML( additionalTitle, query, specifyAttributes );

		if ( success && this.data == null && buttonText != null )
		{
			final Pair< SpimData2, String > dataset = new Define_Multi_View_Dataset().defineDataset( true );

			if ( dataset == null )
				return false;

			data = dataset.getA();
			xmlfilename = dataset.getB();
			io = new XmlIoSpimData2( "" );

			return true;
		}

		// make sure the internal IO is updated to reflect the cluster saving
		if ( success )
			this.getIO().setClusterExt( this.getClusterExtension() );

		return success;
	}

	/**
	 * @return All angles that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Angle > getAnglesToProcess() { return (List< Angle >)(Object)attributeInstancesToProcess.get( "angle" ); }

	/**
	 * @return All channels that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Channel > getChannelsToProcess() { return (List< Channel >)(Object)attributeInstancesToProcess.get( "channel" ); }

	/**
	 * @return All illumination directions that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Illumination > getIlluminationsToProcess() { return (List< Illumination >)(Object)attributeInstancesToProcess.get( "illumination" ); }

	/**
	 * @return All tiles directions that should be processed
	 */
	@SuppressWarnings("unchecked")
	public List< Tile > getTilesToProcess() { return (List< Tile >)(Object)attributeInstancesToProcess.get( "tile" ); }

	//@Override
	//public XmlIoSpimData2 getIO() { return (XmlIoSpimData2)io; }

	public static void main( String args[] )
	{
		new ImageJ();
		IOFunctions.printIJLog = true;
	
		final LoadParseQueryXML lpq = new LoadParseQueryXML();
		
		final ArrayList< String > queryFor = new ArrayList< String >();
		queryFor.add( "Timepoint" );
		queryFor.add( "channel" );
		queryFor.add( "angle" );
		queryFor.add( "Tile" );
		queryFor.add( "illumination" );
		
		lpq.queryXML( true, true, true, true, true );
		
		for ( final TimePoint i : lpq.getTimePointsToProcess() )
			System.out.println( i.getId() );
	
		for ( final ViewSetup v : lpq.getViewSetupsToProcess() )
		{
			System.out.println( v.getId() + " " + v.getAngle().getName() + " " + v.getChannel().getName() + " " + v.getIllumination().getName() );
		}
	}
}
