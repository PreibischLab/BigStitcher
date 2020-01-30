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
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.List;
import java.util.Vector;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.img.cell.CellImgFactory;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMinFilterThreshold;
import net.preibisch.mvrecon.process.fusion.FusionTools;

public class MinFilterThresholdBoundingBoxGUI extends BoundingBoxGUI
{
	public static int defaultDownsamplingAutomatic = 4;
	public static double defaultBackgroundIntensity = 5;
	public static int defaultDiscardedObjectSize = 25;
	public static boolean defaultDisplaySegmentationImage = false;

	public MinFilterThresholdBoundingBoxGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	protected boolean allowModifyDimensions()
	{
		return false;
	}

	@Override
	protected boolean setUpDefaultValues( final int[] rangeMin, final int[] rangeMax )
	{
		if ( !findRange( spimData, viewIdsToProcess, rangeMin, rangeMax ) )
			return false;

		this.min = rangeMin.clone();
		this.max = rangeMax.clone();

		if ( defaultMin == null )
			defaultMin = min.clone();

		if ( defaultMax == null )
			defaultMax = max.clone();

		// compute dimensions and update size for this instance
		final long[] dim = new long[ this.min.length ];

		for ( int d = 0; d < dim.length; ++d )
			dim[ d ] = this.max[ d ] - this.min[ d ] + 1;

		final GenericDialog gd = new GenericDialog( this.getDescription() );

		gd.addMessage( "Parameters for automatic segmentation", GUIHelper.largestatusfont );
		
		gd.addSlider( "Background intensity [%]", 1.0, 99.0, defaultBackgroundIntensity );
		gd.addSlider( "Size_of_objects to be discarded", 1, 100, defaultDiscardedObjectSize );
		gd.addMessage( "" );
		gd.addSlider( "Downsampling", 1.0, 10.0, defaultDownsamplingAutomatic );
		gd.addCheckbox( "Display_image_used for segmentation", defaultDisplaySegmentationImage );
		gd.addMessage( "Image size: ???x???x??? pixels", GUIHelper.mediumstatusfont, GUIHelper.good );
		Label l = (Label)gd.getMessage();

		// add listeners and update values
		addListeners( gd, gd.getNumericFields(), l, dim );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;

		final double background = defaultBackgroundIntensity = gd.getNextNumber();
		final int discardedObjectSize = defaultDiscardedObjectSize = (int)Math.round( gd.getNextNumber() );

		final int downsampling = defaultDownsamplingAutomatic = (int)Math.round( gd.getNextNumber() );
		final boolean displaySegmentationImage = defaultDisplaySegmentationImage = gd.getNextBoolean();
		
		// compute approx bounding box
		final BoundingBox bbEstimate = new BoundingBoxMinFilterThreshold(
				spimData,
				null,
				viewIdsToProcess,
				new CellImgFactory<>(),
				background,
				discardedObjectSize,
				displaySegmentationImage,
				downsampling ).estimate( "test" );

		if ( bbEstimate == null )
		{
			return false;
		}
		else
		{
			this.min = bbEstimate.getMin().clone();
			this.max = bbEstimate.getMax().clone();
			BoundingBoxGUI.defaultMin = bbEstimate.getMin().clone();
			BoundingBoxGUI.defaultMax = bbEstimate.getMax().clone();
		}

		return true;
	}

	@Override
	public BoundingBoxGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		return new MinFilterThresholdBoundingBoxGUI( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription()
	{
		return "Automatically find Bounding Box through image filtering";
	}

	protected void addListeners(
			final GenericDialog gd,
			final Vector<?> tf,
			final Label label,
			final long[] dim )
	{
		final TextField downsample = (TextField)tf.get( 2 );

		downsample.addTextListener(
			new TextListener()
			{
				@Override
				public void textValueChanged(TextEvent arg0)
				{
					int downsampling = Integer.parseInt( downsample.getText() );
					
					final long numPixels = FusionTools.numPixels( dim, downsampling );
					final long megabytes = (numPixels * 4) / (1024*1024);
					
					label.setText( "Image size for segmentation: " + 
							(dim[ 0 ])/downsampling + " x " + 
							(dim[ 1 ])/downsampling + " x " + 
							(dim[ 2 ])/downsampling + " pixels, " + megabytes + " MB" );
					label.setForeground( GUIHelper.good );
				}
			} );
	}
}
