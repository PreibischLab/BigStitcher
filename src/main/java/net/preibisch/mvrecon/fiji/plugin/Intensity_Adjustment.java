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

import java.awt.Choice;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.models.AffineModel1D;
import mpicbg.models.IdentityModel;
import mpicbg.models.InterpolatedAffineModel1D;
import mpicbg.models.TranslationModel1D;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.fusion.FusionGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.intensityadjust.IntensityAdjustmentTools;

public class Intensity_Adjustment implements PlugIn
{
	public static double defaultDownsampling = 10;
	public static int defaultMaxInliers = 10000;
	public static boolean defaultAffine = true;
	public static double defaultTranslationRegularization = 0.1;
	public static double defaultIdentityRegularization = 0.1;

	int boundingBox;
	double downsampling;
	List< BoundingBox > allBoxes;
	SpimData2 data;
	List< ViewId > viewIds;

	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "intensity adjustment in overlapping areas", true, true, true, true, true ) )
			return;

		intensityAdjustment(
			result.getData(),
			SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
			result.getClusterExtension(),
			result.getXMLFileName(),
			true );
		
	}

	public boolean intensityAdjustment(
			final SpimData2 data,
			final List< ViewId > viewIds )
	{
		return intensityAdjustment( data, viewIds, "", null, false );
	}

	public boolean intensityAdjustment(
			final SpimData2 data,
			final List< ViewId > views,
			final String clusterExtension,
			final String xmlFileName,
			final boolean saveXML )
	{
		this.data = data;
		this.viewIds = new ArrayList<>( views );

		// filter not present ViewIds
		final List< ViewId > removed = SpimData2.filterMissingViews( data, viewIds );
		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

		final GenericDialog gd = new GenericDialog( "Intensity Adjustment" );

		this.allBoxes = BoundingBoxTools.getAllBoundingBoxes( data, viewIds, true );
		final String[] choices = FusionGUI.getBoundingBoxChoices( allBoxes );
		final String[] choicesForMacro = FusionGUI.getBoundingBoxChoices( allBoxes, false );

		if ( FusionGUI.defaultBB >= choices.length )
			FusionGUI.defaultBB = 0;

		// use macro-compatible choice names in headless mode
		if ( !PluginHelper.isHeadless() )
			gd.addChoice( "Bounding_Box", choices, choices[ FusionGUI.defaultBB ] );
		else
			gd.addChoice( "Bounding_Box", choicesForMacro, choicesForMacro[ FusionGUI.defaultBB ] );

		gd.addSlider( "Downsampling", 1.0, 64.0, defaultDownsampling );
		gd.addNumericField( "Max_inliers", defaultMaxInliers, 0 );

		gd.addMessage( "" );

		gd.addCheckbox( "Affine_intensity mapping (Scaling & Offset)", defaultAffine );
		gd.addSlider( "Offset_only intensity regularization [0.0 ... 1.0]", 0.0, 1.0, defaultTranslationRegularization );
		gd.addSlider( "Unmodified intensity regularization [0.0 ... 1.0]", 0.0, 1.0, defaultIdentityRegularization );

		gd.addMessage( "" );

		Label label1 = null, label2 = null;

		gd.addMessage( "Estimated size: ", GUIHelper.largestatusfont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label1 = (Label)gd.getMessage();
		gd.addMessage( "???x???x??? pixels", GUIHelper.smallStatusFont, GUIHelper.good );
		if ( !PluginHelper.isHeadless() )  label2 = (Label)gd.getMessage();

		if ( !PluginHelper.isHeadless() )
		{
			final ManageListeners m = new ManageListeners(
					gd, (Choice)gd.getChoices().get( 0 ), (TextField)gd.getNumericFields().get( 0 ), label1, label2 );
	
			m.update();
		}

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;

		// Hacky: replace the bounding box choices with a macro-compatible version
		// i.e. choices that do not contain the dimensions (AxBxC px), which we do not know in advance
		if ( !PluginHelper.isHeadless() )
		{
			final Choice bboxChoice = (Choice) gd.getChoices().get( 0 );
			final int selectedBbox = bboxChoice.getSelectedIndex();
			for (int i = 0; i<choices.length; i++)
			{
				bboxChoice.remove( 0 );
				bboxChoice.addItem( choicesForMacro[i] );
			}
			bboxChoice.select( selectedBbox );
		}

		boundingBox = FusionGUI.defaultBB = gd.getNextChoiceIndex();
		downsampling = defaultDownsampling = gd.getNextNumber();
		final int maxInliers = defaultMaxInliers = (int)Math.round( gd.getNextNumber() );

		final boolean affine = defaultAffine = gd.getNextBoolean();
		final double regTrans = defaultTranslationRegularization = gd.getNextNumber();
		final double regIdentity = defaultIdentityRegularization = gd.getNextNumber();

		if ( downsampling == 1.0 )
			downsampling = Double.NaN;

		final HashMap< ViewId, AffineModel1D > intensityMapping;

		if ( affine )
		{
			final InterpolatedAffineModel1D< InterpolatedAffineModel1D< AffineModel1D, TranslationModel1D >, IdentityModel > model =
					new InterpolatedAffineModel1D<>(
							new InterpolatedAffineModel1D<>( new AffineModel1D(), new TranslationModel1D(), regTrans ),
							new IdentityModel(), regIdentity );

			intensityMapping = IntensityAdjustmentTools.computeIntensityAdjustment( data, viewIds, model, allBoxes.get( boundingBox ), downsampling, maxInliers, data.getIntensityAdjustments().getIntensityAdjustments() );
		}
		else
		{
			final InterpolatedAffineModel1D< TranslationModel1D, IdentityModel > model =
					new InterpolatedAffineModel1D<>(
							new TranslationModel1D(),
							new IdentityModel(), regIdentity );

			intensityMapping = IntensityAdjustmentTools.computeIntensityAdjustment( data, viewIds, model, allBoxes.get( boundingBox ), downsampling, maxInliers, data.getIntensityAdjustments().getIntensityAdjustments() );
		}

		data.getIntensityAdjustments().getIntensityAdjustments().putAll( intensityMapping );

		if ( saveXML )
			SpimData2.saveXML( data, xmlFileName, clusterExtension );

		return true;
	}

	class ManageListeners
	{
		final GenericDialog gd;
		final TextField downsampleField;
		final Choice boundingBoxChoice;
		final Label label1;
		final Label label2;

		public ManageListeners(
				final GenericDialog gd,
				final Choice boundingBoxChoice,
				final TextField downsampleField,
				final Label label1,
				final Label label2 )
		{
			this.gd = gd;
			this.boundingBoxChoice = boundingBoxChoice;
			this.downsampleField = downsampleField;
			this.label1 = label1;
			this.label2 = label2;

			this.boundingBoxChoice.addItemListener( new ItemListener() { @Override
				public void itemStateChanged(ItemEvent e) { update(); } });

			this.downsampleField.addTextListener( new TextListener() { @Override
				public void textValueChanged(TextEvent e) { update(); } });
		}

		public void update()
		{
			boundingBox = boundingBoxChoice.getSelectedIndex();
			downsampling = Integer.parseInt( downsampleField.getText() );

			final BoundingBox bb = allBoxes.get( boundingBox );
			final long numPixels = Math.round( FusionTools.numPixels( bb, downsampling ) );

			final int bytePerPixel = 4;
			final long megabytes = (numPixels * bytePerPixel) / (1024*1024);

			label1.setText( "Fused image: " + megabytes + " MB, required total memory ~" + totalRAM( megabytes, bytePerPixel ) +  " MB" );
			label1.setForeground( GUIHelper.good );

			final int[] min = bb.getMin().clone();
			final int[] max = bb.getMax().clone();

			label2.setText( "Dimensions: " + 
					Math.round( (max[ 0 ] - min[ 0 ] + 1)/downsampling ) + " x " + 
					Math.round( (max[ 1 ] - min[ 1 ] + 1)/downsampling ) + " x " + 
					Math.round( (max[ 2 ] - min[ 2 ] + 1)/downsampling ) + " pixels @ " + FusionGUI.pixelTypes[ 0 ] );
		}

		public long totalRAM( long fusedSizeMB, final int bytePerPixel )
		{
			// do we need to load the image data fully?
			long inputImagesMB = 0;

			long maxNumPixelsInput = FusionGUI.maxNumInputPixelsPerInputGroup( data, viewIds, 2 );

			// assume he have to load 50% higher resolved data
			double inputDownSampling = FusionGUI.isMultiResolution( data ) ? downsampling / 1.5 : 1.0;

			final int inputBytePerPixel = FusionGUI.inputBytePerPixel( viewIds.get( 0 ), data );

			if ( FusionGUI.isImgLoaderVirtual( data ) )
			{
				// either 50% of the RAM or 5% of the downsampled input
				inputImagesMB = Math.min(
						Runtime.getRuntime().maxMemory() / ( 1024*1024*2 ),
						( ( ( maxNumPixelsInput / Math.round( inputDownSampling * 1024*1024 ) ) * inputBytePerPixel ) / 20 ) );
			}
			else
			{
				inputImagesMB = Math.round( maxNumPixelsInput / ( inputDownSampling * 1024*1024 ) ) * inputBytePerPixel;
			}

			long processingMB = 0;

			fusedSizeMB /= Math.max( 1, Math.round( Math.pow( fusedSizeMB, 0.3 ) ) );

			return inputImagesMB + processingMB + fusedSizeMB;
		}

	}

	public static void main( final String[] args )
	{
		new ImageJ();

		if ( !System.getProperty("os.name").toLowerCase().contains( "mac" ) )
			GenericLoadParseQueryXML.defaultXMLfilename = "/home/preibisch/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset_tp18.xml";
		else
			GenericLoadParseQueryXML.defaultXMLfilename = "/Users/spreibi/Documents/Microscopy/SPIM/HisYFP-SPIM//dataset.xml";

		new Intensity_Adjustment().run( null );
	}
}
