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

import java.awt.Label;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Vector;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximal;
import net.preibisch.mvrecon.process.fusion.FusionTools;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;


public abstract class BoundingBoxGUI extends BoundingBox
{
	public static int defaultMin[] = null;
	public static int defaultMax[] = null;

	/*
	public static String[] pixelTypes = new String[]{ "32-bit floating point", "16-bit unsigned integer" };
	public static int defaultPixelType = 0;
	protected int pixelType = 0;

	public static String[] imgTypes = new String[]{ "ArrayImg", "PlanarImg (large images, easy to display)", "CellImg (large images)" };
	public static int defaultImgType = 1;
	protected int imgtype = 1;
	*/

	/*
	 * which viewIds to process, set in queryParameters
	 */
	protected final List< ViewId > viewIdsToProcess;
	protected final SpimData2 spimData;

	/*
	 * @param spimData
	 * @param viewIdsToProcess - which view ids to fuse
	 */
	public BoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		// init bounding box with no values, means that we will use the default ones for the dialog
		super( null, null );

		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
	}

	public BoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess, final BoundingBox bb )
	{
		// init bounding box with values from bounding box
		super( bb.getMin(), bb.getMax() );

		this.spimData = spimData;
		this.viewIdsToProcess = viewIdsToProcess;
	}

	protected abstract boolean allowModifyDimensions();

	/*
	 * Query the necessary parameters for the bounding box
	 */
	public boolean queryParameters()
	{
		final GenericDialog gd = getSimpleDialog( allowModifyDimensions() );

		if ( gd == null )
			return false;

		Label label1 = null, label2 = null;

		gd.addMessage( "" );

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label2 = (Label)gd.getMessage();

		if ( !PluginHelper.isHeadless() )
		{
			final ManageListeners m = new ManageListeners( gd, gd.getNumericFields(), gd.getChoices(), label1, label2 );
	
			m.update();
		}

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		if ( allowModifyDimensions() )
		{
			this.min[ 0 ] = (int)Math.round( gd.getNextNumber() );
			this.min[ 1 ] = (int)Math.round( gd.getNextNumber() );
			this.min[ 2 ] = (int)Math.round( gd.getNextNumber() );
	
			this.max[ 0 ] = (int)Math.round( gd.getNextNumber() );
			this.max[ 1 ] = (int)Math.round( gd.getNextNumber() );
			this.max[ 2 ] = (int)Math.round( gd.getNextNumber() );
		}
		else
		{
			setNFIndex( gd, 6 );
		}

		if ( min[ 0 ] > max[ 0 ] || min[ 1 ] > max[ 1 ] || min[ 2 ] > max[ 2 ] )
		{
			IOFunctions.println( "Invalid coordinates, min cannot be larger than max" );
			return false;
		}

		BoundingBoxGUI.defaultMin[ 0 ] = min[ 0 ];
		BoundingBoxGUI.defaultMin[ 1 ] = min[ 1 ];
		BoundingBoxGUI.defaultMin[ 2 ] = min[ 2 ];
		BoundingBoxGUI.defaultMax[ 0 ] = max[ 0 ];
		BoundingBoxGUI.defaultMax[ 1 ] = max[ 1 ];
		BoundingBoxGUI.defaultMax[ 2 ] = max[ 2 ];

		return true;
	}

	protected GenericDialog getSimpleDialog( final boolean allowModifyDimensions )
	{
		final int[] rangeMin = new int[ 3 ];
		final int[] rangeMax = new int[ 3 ];

		for ( int d = 0; d < rangeMin.length; ++d )
		{
			rangeMin[ d ] = Integer.MAX_VALUE;
			rangeMax[ d ] = Integer.MIN_VALUE;
		}

		if ( !setUpDefaultValues( rangeMin, rangeMax ) )
			return null;

		final GenericDialog gd = new GenericDialog( "Manually define Bounding Box" );

		gd.addMessage( "Note: Coordinates are in global coordinates as shown " +
				"in Fiji status bar of a fused datasets", GUIHelper.smallStatusFont );

		gd.addMessage( "", GUIHelper.smallStatusFont );

		gd.addSlider( "Minimal_X", rangeMin[ 0 ], rangeMax[ 0 ], this.min[ 0 ] );
		gd.addSlider( "Minimal_Y", rangeMin[ 1 ], rangeMax[ 1 ], this.min[ 1 ] );
		gd.addSlider( "Minimal_Z", rangeMin[ 2 ], rangeMax[ 2 ], this.min[ 2 ] );

		gd.addMessage( "" );

		gd.addSlider( "Maximal_X", rangeMin[ 0 ], rangeMax[ 0 ], this.max[ 0 ] );
		gd.addSlider( "Maximal_Y", rangeMin[ 1 ], rangeMax[ 1 ], this.max[ 1 ] );
		gd.addSlider( "Maximal_Z", rangeMin[ 2 ], rangeMax[ 2 ], this.max[ 2 ] );

		if ( !allowModifyDimensions )
		{
			for ( int i = gd.getSliders().size() - 6; i < gd.getSliders().size(); ++i )
				((Scrollbar)gd.getSliders().get( i )).setEnabled( false );

			for ( int i = gd.getNumericFields().size() - 6; i < gd.getNumericFields().size(); ++i )
				((TextField)gd.getNumericFields().get( i )).setEnabled( false );
		}

		return gd;
	}

	protected static boolean findRange( final SpimData2 spimData, final List< ViewId > viewIdsToProcess, final int[] rangeMin, final int[] rangeMax )
	{
		final BoundingBox bb = new BoundingBoxMaximal( viewIdsToProcess, spimData ).estimate( "test" );

		if ( bb == null )
			return false;

		for ( int d = 0; d < bb.getMin().length; ++d )
		{
			if ( bb.getMin()[ d ] < rangeMin[ d ] )
				rangeMin[ d ] = bb.getMin()[ d ];
	
			if ( bb.getMax()[ d ] > rangeMax[ d ] )
				rangeMax[ d ] = bb.getMax()[ d ];
		}

		return true;
	}

	/**
	 * populates this.min[] and this.max[] from the defaultMin and defaultMax
	 *
	 * @param rangeMin - will be populated with the maximal dimension that all views span
	 * @param rangeMax - will be populated with the maximal dimension that all views span
	 * 
	 * @return true if it was successful, otherwise false
	 */
	protected abstract boolean setUpDefaultValues( final int[] rangeMin, final int rangeMax[] );

	/**
	 * @param spimData - the spimdata
	 * @param viewIdsToProcess - which view ids to fuse
	 * @return - a new instance without any special properties
	 */
	public abstract BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess );

	/**
	 * @return - to be displayed in the generic dialog
	 */
	public abstract String getDescription();

	/*
	 * @return - the final dimensions including downsampling of this bounding box (to instantiate an img)
	public long[] getDimensions()
	{
		final long[] dim = new long[ this.numDimensions() ];
		this.dimensions( dim );
		
		for ( int d = 0; d < this.numDimensions(); ++d )
			dim[ d ] /= this.getDownSampling();
		
		return dim;
	}
	 */

	public class ManageListeners
	{
		final GenericDialog gd;
		final TextField minX, minY, minZ, maxX, maxY, maxZ;
		final Label label1;
		final Label label2;

		final long[] min = new long[ 3 ];
		final long[] max = new long[ 3 ];

		public ManageListeners(
				final GenericDialog gd,
				final Vector<?> tf,
				final Vector<?> choices,
				final Label label1,
				final Label label2 )
		{
			this.gd = gd;

			this.minX = (TextField)tf.get( 0 );
			this.minY = (TextField)tf.get( 1 );
			this.minZ = (TextField)tf.get( 2 );
			
			this.maxX = (TextField)tf.get( 3 );
			this.maxY = (TextField)tf.get( 4 );
			this.maxZ = (TextField)tf.get( 5 );

			this.label1 = label1;
			this.label2 = label2;

			this.addListeners();
		}

		protected void addListeners()
		{
			this.minX.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.minY.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.minZ.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.maxX.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.maxY.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
			this.maxZ.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
		}
		
		public void update()
		{
			try
			{
				min[ 0 ] = Long.parseLong( minX.getText() );
				min[ 1 ] = Long.parseLong( minY.getText() );
				min[ 2 ] = Long.parseLong( minZ.getText() );
	
				max[ 0 ] = Long.parseLong( maxX.getText() );
				max[ 1 ] = Long.parseLong( maxY.getText() );
				max[ 2 ] = Long.parseLong( maxZ.getText() );
			}
			catch (Exception e ) {}

			final int bytePerPixel = 4;
			final int downsampling = 1;
			final long numPixels = FusionTools.numPixels( min, max, downsampling );
			final long megabytes = (numPixels * bytePerPixel) / (1024*1024);
			
			label1.setText( "Fused image: " + megabytes + " MB" );
			label1.setForeground( GUIHelper.good );

			label2.setText( "Dimensions: " + 
					(max[ 0 ] - min[ 0 ] + 1)/downsampling + " x " + 
					(max[ 1 ] - min[ 1 ] + 1)/downsampling + " x " + 
					(max[ 2 ] - min[ 2 ] + 1)/downsampling + " pixels @ 32 bit and full resolution."  );
		}
	}

	/**
	 * Increase the counter for GenericDialog.getNextNumber, so we can skip recording it
	 * 
	 * @param gd
	 * @param nfIndex
	 */
	private static final void setNFIndex( final GenericDialog gd, final int nfIndex )
	{
		try
		{
			Class< ? > clazz = null;
			boolean found = false;
	
			do
			{
				if ( clazz == null )
					clazz = gd.getClass();
				else
					clazz = clazz.getSuperclass();
	
				if ( clazz != null )
					for ( final Field field : clazz.getDeclaredFields() )
						if ( field.getName().equals( "nfIndex" ) )
							found = true;
			}
			while ( !found && clazz != null );
	
			if ( !found )
			{
				System.out.println( "Failed to find GenericDialog.nfIndex field. Quiting." );
				return;
			}
	
			final Field nfIndexField = clazz.getDeclaredField( "nfIndex" );
			nfIndexField.setAccessible( true );
			nfIndexField.setInt( gd, nfIndex );
		}
		catch ( Exception e ) { e.printStackTrace(); }
	}
}
