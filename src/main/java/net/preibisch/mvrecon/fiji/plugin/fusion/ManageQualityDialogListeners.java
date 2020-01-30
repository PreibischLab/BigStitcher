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
package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;

import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.fusion.FusionTools;

import ij.gui.GenericDialog;

public class ManageQualityDialogListeners
{
	final GenericDialog gd;
	final TextField downsampleField;
	final Choice boundingBoxChoice, splitChoice;
	final Checkbox anisoCheckbox;
	final Label label1;
	final Label label2;
	final QualityGUI quality;

	double anisoF;

	public ManageQualityDialogListeners(
			final GenericDialog gd,
			final Choice boundingBoxChoice,
			final TextField downsampleField,
			final Checkbox anisoCheckbox,
			final Choice splitChoice,
			final Label label1,
			final Label label2,
			final QualityGUI quality )
	{
		this.gd = gd;
		this.boundingBoxChoice = boundingBoxChoice;
		this.downsampleField = downsampleField;
		this.anisoCheckbox = anisoCheckbox;
		this.splitChoice = splitChoice;
		this.label1 = label1;
		this.label2 = label2;
		this.quality = quality;

		this.boundingBoxChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.downsampleField.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update(); } });

		this.splitChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		if ( this.anisoCheckbox != null )
		{
			this.anisoF = quality.getAnisotropyFactor();
			this.anisoCheckbox.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });
		}
}
	
	public void update()
	{
		quality.boundingBox = boundingBoxChoice.getSelectedIndex();
		quality.downsampling = Integer.parseInt( downsampleField.getText() );
		quality.splittingType = splitChoice.getSelectedIndex();
		if ( anisoCheckbox != null )
		{
			quality.preserveAnisotropy = anisoCheckbox.getState();
			
			if ( quality.preserveAnisotropy )
				this.anisoF = quality.getAnisotropyFactor();
			else
				this.anisoF = 1.0;
		}
		else
		{
			this.anisoF = 1.0;
			quality.preserveAnisotropy = false;
		}

		final BoundingBox bb = quality.allBoxes.get( quality.boundingBox );
		final long numPixels = Math.round( FusionTools.numPixels( bb, quality.downsampling ) / anisoF );

		final int bytePerPixel = 4;
		final long megabytes = (numPixels * bytePerPixel) / (1024*1024);

		label1.setText( "Fused image: " + megabytes + " MB, required total memory ~" + totalRAM( megabytes, bytePerPixel ) +  " MB" );
		label1.setForeground( GUIHelper.good );

		final int[] min = bb.getMin().clone();
		final int[] max = bb.getMax().clone();

		if ( quality.preserveAnisotropy )
		{
			min[ 2 ] = (int)Math.round( Math.floor( min[ 2 ] / anisoF ) );
			max[ 2 ] = (int)Math.round( Math.ceil( max[ 2 ] / anisoF ) );
		}

		label2.setText( "Dimensions: " + 
				Math.round( (max[ 0 ] - min[ 0 ] + 1)/quality.downsampling ) + " x " + 
				Math.round( (max[ 1 ] - min[ 1 ] + 1)/quality.downsampling ) + " x " + 
				Math.round( (max[ 2 ] - min[ 2 ] + 1)/(quality.downsampling ) ) + " pixels @ " + FusionGUI.pixelTypes[ 0 ] );
	}

	public long totalRAM( long fusedSizeMB, final int bytePerPixel )
	{
		// do we need to load the image data fully?
		long inputImagesMB = 0;

		long maxNumPixelsInput = FusionGUI.maxNumInputPixelsPerInputGroup( quality.getSpimData(), quality.getViews(), quality.getSplittingType() );

		final int inputBytePerPixel = FusionGUI.inputBytePerPixel( quality.views.get( 0 ), quality.spimData );

		if ( quality.isImgLoaderVirtual() )
		{
			// either 80% of the RAM or 100% of the downsampled input
			inputImagesMB = Math.min(
					Math.round( ( Runtime.getRuntime().maxMemory() / ( 1024*1024 ) ) * 0.8 ),
					( ( maxNumPixelsInput / Math.round( 1024*1024 ) ) * inputBytePerPixel ) );
		}
		else
		{
			inputImagesMB = Math.round( maxNumPixelsInput / ( 1024*1024 ) ) * inputBytePerPixel;
		}

		long processingMB = 100;

		return inputImagesMB + processingMB + fusedSizeMB;
	}
}
