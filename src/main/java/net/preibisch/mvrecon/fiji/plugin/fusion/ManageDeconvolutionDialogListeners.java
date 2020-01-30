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

import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.List;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ManageDeconvolutionDialogListeners
{
	final GenericDialog gd;
	final TextField downsampleField;
	final Choice boundingBoxChoice, inputCacheChoice, nonRigidChoice, weightCacheChoice, blockChoice, computeOnChoice, splitChoice;
	final Label label1;
	final Label label2;
	final DeconvolutionGUI decon;

	int boundingBoxOld = -1;

	public ManageDeconvolutionDialogListeners(
			final GenericDialog gd,
			final Choice boundingBoxChoice,
			final TextField downsampleField,
			final Choice inputCacheChoice,
			final Choice nonRigidChoice,
			final Choice weightCacheChoice,
			final Choice blockChoice,
			final Choice computeOnChoice,
			final Choice splitChoice,
			final Label label1,
			final Label label2,
			final DeconvolutionGUI decon )
	{
		this.gd = gd;
		this.boundingBoxChoice = boundingBoxChoice;
		this.downsampleField = downsampleField;
		this.inputCacheChoice = inputCacheChoice;
		this.nonRigidChoice = nonRigidChoice;
		this.weightCacheChoice = weightCacheChoice;
		this.blockChoice = blockChoice;
		this.computeOnChoice = computeOnChoice;
		this.splitChoice = splitChoice;
		this.label1 = label1;
		this.label2 = label2;
		this.decon = decon;

		this.boundingBoxChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.downsampleField.addTextListener( new TextListener() { @Override
			public void textValueChanged(TextEvent e) { update(); } });

		this.inputCacheChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		if ( this.nonRigidChoice != null )
			this.nonRigidChoice.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });

		this.weightCacheChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.blockChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.computeOnChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });

		this.splitChoice.addItemListener( new ItemListener() { @Override
			public void itemStateChanged(ItemEvent e) { update(); } });
	}
	
	public void update()
	{
		/*
		System.out.println( boundingBoxChoice.getSelectedItem() );
		System.out.println( downsampleField.getText() );
		System.out.println( pixelTypeChoice.getSelectedItem() );
		System.out.println( cachingChoice.getSelectedItem() );
		System.out.println( contentbasedCheckbox.getState() );
		System.out.println( splitChoice.getSelectedItem() );
		*/

		decon.boundingBox = boundingBoxChoice.getSelectedIndex();
		decon.downsampling = Integer.parseInt( downsampleField.getText() );
		decon.cacheTypeInputImg = inputCacheChoice.getSelectedIndex();
		decon.cacheTypeWeights = weightCacheChoice.getSelectedIndex();
		decon.blockSizeIndex = blockChoice.getSelectedIndex();
		decon.computeOnIndex = computeOnChoice.getSelectedIndex();
		decon.splittingType = splitChoice.getSelectedIndex();

		if ( boundingBoxOld != decon.boundingBox )
		{
			decon.maxBlock = maxBlock();
			blockChoice.remove( 5 );
			blockChoice.add( "one block " + Util.printCoordinates( decon.maxBlock ) + " for the entire image" );
			blockChoice.select( decon.blockSizeIndex );
		}
		boundingBoxOld = decon.boundingBox;

		final BoundingBox bb = decon.allBoxes.get( decon.boundingBox );
		final long numPixels = FusionTools.numPixels( bb, decon.downsampling );

		final int bytePerPixel = 4;
		final long megabytes = (numPixels * bytePerPixel) / (1024*1024);
		final long totalRAM = totalRAM( megabytes, bytePerPixel );
		final long maxRAM = Runtime.getRuntime().maxMemory() / (1024*1024);

		if ( totalRAM == -1 )
		{
			label1.setText( "Deconvolved image: " + megabytes + " MB, total RAM unknown (depends on blocksize)" );

			label1.setForeground( GUIHelper.warning );
		}
		else
		{
			label1.setText( "Deconvolved image: " + megabytes + " MB, required total RAM ~" + totalRAM + "MB" );

			if ( maxRAM > totalRAM * 1.25 )
				label1.setForeground( GUIHelper.good );
			else if ( maxRAM > totalRAM )
				label1.setForeground( GUIHelper.warning );
			else
				label1.setForeground( GUIHelper.error );
		}

		final int[] min = bb.getMin();
		final int[] max = bb.getMax();

		label2.setText( "Dimensions: " + 
				Math.round( (max[ 0 ] - min[ 0 ] + 1)/decon.downsampling ) + " x " + 
				Math.round( (max[ 1 ] - min[ 1 ] + 1)/decon.downsampling ) + " x " + 
				Math.round( (max[ 2 ] - min[ 2 ] + 1)/decon.downsampling ) + " pixels @ " + FusionGUI.pixelTypes[ 0 ] );
	}

	public long[] maxBlock()
	{
		final BoundingBox bb = decon.allBoxes.get( decon.boundingBox );
		final long[] size = new long[ bb.numDimensions() ];
		bb.dimensions( size );

		for ( int d = 0; d < size.length; ++d )
			size[ d ] += decon.maxDimPSF[ d ];

		return size;
	}

	public long totalRAM( long fusedSizeMB, final int bytePerPixel )
	{
		// do we need to load the image data fully?
		long inputImagesMB = 0;

		long maxNumPixelsInput = FusionGUI.maxNumInputPixelsPerInputGroup( decon.getSpimData(), decon.getViews(), decon.getSplittingType() );

		// assume he have to load 50% higher resolved data
		final double inputDownSampling = decon.isMultiResolution() ? decon.downsampling / 1.5 : 1.0;

		// assume something about caching
		final long twentyPercentRAM = Runtime.getRuntime().maxMemory() / ( 1024*1024*5 );

		// input stuff
		final int inputBytePerPixel = FusionGUI.inputBytePerPixel( decon.views.get( 0 ), decon.spimData );
		final int numViews = maxNumGroups( decon.spimData, decon.views, decon, decon.splittingType );

		if ( decon.isImgLoaderVirtual() )
		{
			// either 20% of the RAM or 5% of the downsampled input
			inputImagesMB = Math.min( twentyPercentRAM,
					( ( ( maxNumPixelsInput / Math.round( inputDownSampling * 1024*1024 ) ) * inputBytePerPixel ) / 20 ) );
		}
		else
		{
			inputImagesMB = Math.round( maxNumPixelsInput / ( inputDownSampling * 1024*1024 ) ) * inputBytePerPixel;
		}

		long processingMB = 0;

		// weights
		if ( decon.cacheTypeWeights == 0 ) // Virtual
			processingMB += 100;
		else if ( decon.cacheTypeWeights == 1 ) // Cached
			processingMB += Math.min( twentyPercentRAM, fusedSizeMB * numViews / 20 );
		else
			processingMB += fusedSizeMB * numViews;

		// images
		if ( decon.cacheTypeInputImg == 0 ) // Virtual
			processingMB += 100;
		else if ( decon.cacheTypeInputImg == 1 ) // Cached
			processingMB += Math.min( twentyPercentRAM, fusedSizeMB * numViews / 20 );
		else
			processingMB += fusedSizeMB * numViews;

		// blocks
		final long blockPixels;

		if ( decon.blockSizeIndex == 0 )
			blockPixels = Util.pow( 256, 3 );
		else if ( decon.blockSizeIndex == 1 )
			blockPixels = Util.pow( 512, 3 );
		else if ( decon.blockSizeIndex == 2 )
			blockPixels = Util.pow( 768, 3 );
		else if ( decon.blockSizeIndex == 3 )
			blockPixels = Util.pow( 1024, 3 );
		else if ( decon.blockSizeIndex == 4 )
			return -1; // unknown
		else
			blockPixels = decon.maxBlock[ 0 ] * decon.maxBlock[ 1 ] * decon.maxBlock[ 2 ];

		final long blockMB = (blockPixels * 4) / (1024 * 1024);

		// 1.25 == overhead for border pixels of the blocks
		final long numBlocksParalell = Math.round( Math.max( 1, Math.pow( fusedSizeMB * 1.25 / blockMB, 0.3 ) ) );

		// keep the psiBlocks in memory
		processingMB += blockMB * numBlocksParalell;

		// FFT's (plus outofbouds)
		if ( decon.computeOnIndex == 0 )
			processingMB += blockMB * 2 * numViews * 1.1;

		// the temporary images per thread (one or two threads)
		if ( decon.computeOnIndex == 0 || decon.computeOnIndex == 1 )
			processingMB += blockMB * 2 * 1.5;

		if ( nonRigidChoice != null && nonRigidChoice.getSelectedIndex() < nonRigidChoice.getItemCount() - 1 )
			fusedSizeMB *= 1.5;

		return inputImagesMB + processingMB + fusedSizeMB;
	}

	public static int maxNumGroups( final SpimData2 spimData, final List< ViewId > views, final DeconvolutionGUI decon, final int splittingType )
	{
		int maxGroups = 0;

		for ( final Group< ViewDescription > deconGroup : FusionGUI.getFusionGroups( spimData, views, splittingType ) )
		{
			final List< Group< ViewDescription > > deconVirtualViews = Group.getGroupsSorted( decon.getDeconvolutionGrouping( deconGroup ) );

			maxGroups = Math.max( maxGroups, deconVirtualViews.size() );
		}

		return maxGroups;
	}

}
