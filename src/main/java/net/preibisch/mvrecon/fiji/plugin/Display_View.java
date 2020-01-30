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

import java.util.List;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.imageplus.ImagePlusImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgFactoryImgLoader;
import net.preibisch.mvrecon.process.export.DisplayImage;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Display_View implements PlugIn
{
	public static String[] pixelTypes = new String[]{ "32-bit floating point", "16-bit unsigned integer" };
	public static int defaultPixelType = 0;
	protected int pixelType = 0;

	public static String[] imgTypes = new String[]{ "ArrayImg", "PlanarImg (large images, easy to display)", "CellImg (large images)" };
	public static int defaultImgType = 1;
	protected int imgtype = 1;

	public static int defaultAngleChoice = 0;
	public static int defaultChannelChoice = 0;
	public static int defaultIlluminationChoice = 0;
	public static int defaultTileChoice = 0;
	public static int defaultTimepointChoice = 0;

	@Override
	public void run(String arg0)
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "displaying a view", false,false, false, false, false ) )
			return;

		final GenericDialog gd = new GenericDialog( "Select View" );

		final List< TimePoint > timepoints = result.getTimePointsToProcess();
		final String[] timepointNames = new String[ timepoints.size() ];
		for ( int i = 0; i < timepointNames.length; ++i )
			timepointNames[ i ] = result.getTimePointsToProcess().get( i ).getName();

		final List< Angle > angles = result.getData().getSequenceDescription().getAllAnglesOrdered();
		final String[] angleNames = new String[ angles.size() ];
		for ( int i = 0; i < angles.size(); ++i )
			angleNames[ i ] = angles.get( i ).getName();

		final List< Channel > channels = result.getData().getSequenceDescription().getAllChannelsOrdered();
		final String[] channelNames = new String[ channels.size() ];
		for ( int i = 0; i < channels.size(); ++i )
			channelNames[ i ] = channels.get( i ).getName();

		final List< Illumination > illuminations = result.getData().getSequenceDescription().getAllIlluminationsOrdered();
		final String[] illuminationNames = new String[ illuminations.size() ];
		for ( int i = 0; i < illuminations.size(); ++i )
			illuminationNames[ i ] = illuminations.get( i ).getName();

		final List< Tile > tiles = result.getData().getSequenceDescription().getAllTilesOrdered();
		final String[] tileNames = new String[ tiles.size() ];
		for ( int i = 0; i < tiles.size(); ++i )
			tileNames[ i ] = tiles.get( i ).getName();

		gd.addChoice( "Angle", angleNames, angleNames[ defaultAngleChoice ] );
		gd.addChoice( "Channel", channelNames, channelNames[ defaultChannelChoice ] );
		gd.addChoice( "Illumination", illuminationNames, illuminationNames[ defaultIlluminationChoice ] );
		gd.addChoice( "Tile", tileNames, tileNames[ defaultTileChoice ] );
		gd.addChoice( "Timepoint", timepointNames, timepointNames[ defaultTimepointChoice ] );
		gd.addMessage( "" );
		gd.addChoice( "Pixel_type", pixelTypes, pixelTypes[ defaultPixelType ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final Angle angle = angles.get( defaultAngleChoice = gd.getNextChoiceIndex() );
		final Channel channel = channels.get( defaultChannelChoice = gd.getNextChoiceIndex() );
		final Illumination illumination = illuminations.get( defaultIlluminationChoice = gd.getNextChoiceIndex() );
		final Tile tile = tiles.get( defaultTileChoice = gd.getNextChoiceIndex() );
		final TimePoint tp = timepoints.get( defaultTimepointChoice = gd.getNextChoiceIndex() );
		final int pixelType = defaultPixelType = gd.getNextChoiceIndex();

		// get the corresponding viewid
		final ViewId viewId = SpimData2.getViewId( result.getData().getSequenceDescription(), tp, channel, angle, illumination, tile );
		final String name = Group.pvid( viewId );

		// this happens only if a viewsetup is not present in any timepoint
		// (e.g. after appending fusion to a dataset)
		if ( viewId == null )
		{
			IOFunctions.println( "This ViewSetup is not present for this timepoint: angle: " + name );
			return;
		}
		
		// get the viewdescription
		final ViewDescription viewDescription = result.getData().getSequenceDescription().getViewDescription( 
				viewId.getTimePointId(), viewId.getViewSetupId() );

		// check if this viewid is present in the current timepoint
		if ( !viewDescription.isPresent() )
		{
			IOFunctions.println( "This ViewSetup is not present for this timepoint: angle: " + name );
			return;
		}
		
		// display it
		display( result.getData(), viewId, pixelType, name );
	}

	public static void display( final AbstractSpimData< ? > spimData, final ViewId viewId, final int pixelType, final String name )
	{
		final ImgLoader imgLoader = (ImgLoader)spimData.getSequenceDescription().getImgLoader();
		final ImgFactory< ? extends NativeType< ? > > factory;
		final AbstractImgFactoryImgLoader il;

		// load as ImagePlus directly if possible
		if ( AbstractImgFactoryImgLoader.class.isInstance( imgLoader ) )
		{
			il = (AbstractImgFactoryImgLoader)imgLoader;
			factory = il.getImgFactory();
			il.setImgFactory( new ImagePlusImgFactory< FloatType >());
		}
		else
		{
			il = null;
			factory = null;
		}

		// display it
		DisplayImage export = new DisplayImage();
		
		if ( pixelType == 0 )
			export.exportImage( ((ImgLoader)spimData.getSequenceDescription().getImgLoader()).getSetupImgLoader( viewId.getViewSetupId() ).getFloatImage( viewId.getTimePointId(), false ), name );
		else
		{
			@SuppressWarnings( "unchecked" )
			RandomAccessibleInterval< UnsignedShortType > img =
				( RandomAccessibleInterval< UnsignedShortType > ) ((ImgLoader)spimData.getSequenceDescription().getImgLoader()).getSetupImgLoader( viewId.getViewSetupId() ).getImage( viewId.getTimePointId() );
			export.exportImage( img, name );
		}

		if ( factory != null && il != null )
			il.setImgFactory( factory );
	}

	public static void main( String[] args )
	{
		new ImageJ();
		new Display_View().run( null );
	}
}
