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
package net.preibisch.mvrecon.fiji.plugin.resave;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.AWTEvent;
import java.awt.Checkbox;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.Toggle_Cluster_Options;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import bdv.export.ProposeMipmaps;
import bdv.export.SubTaskProgressWriter;
import bdv.export.WriteSequenceToHdf5;
import bdv.export.WriteSequenceToHdf5.DefaultLoopbackHeuristic;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.img.hdf5.Partition;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;

public class Generic_Resave_HDF5 extends GenericResaveHDF5 implements PlugIn
{
	public static final String[] convertChoices = {
		"Use min/max of each image (might flicker over time)",
		"Use min/max of first image (might saturate intenities over time)",
		"Manually define min/max" };

	public static int defaultConvertChoice = 1;
	public static double defaultMin = 0, defaultMax = 5;

	public static void main( final String[] args )
	{
		new Generic_Resave_HDF5().run( null );
	}

	@Override
	public void run( final String arg )
	{
		final File file = getInputXML();
		if ( file == null )
			return;

		final XmlIoSpimDataMinimal io = new XmlIoSpimDataMinimal();
		SpimDataMinimal spimData;
		try
		{
			spimData = io.load( file.getAbsolutePath() );
		}
		catch ( final SpimDataException e )
		{
			throw new RuntimeException( e );
		}

		final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = ProposeMipmaps.proposeMipmaps( spimData.getSequenceDescription() );

		final int firstviewSetupId = spimData.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
		final Parameters params = getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, true );
		if ( params == null )
			return;

		final ProgressWriter progressWriter = new ProgressWriterIJ();
		progressWriter.out().println( "starting export..." );

		// write hdf5
		writeHDF5( spimData, params, progressWriter );

		// write xml sequence description
		try
		{
			writeXML( spimData, io, params, progressWriter );
		}
		catch ( SpimDataException e )
		{
			throw new RuntimeException( e );
		}
	}

	static boolean lastSetMipmapManual = false;

	static String lastSubsampling = "{1,1,1}, {2,2,1}, {4,4,2}";

	static String lastChunkSizes = "{16,16,16}, {16,16,16}, {16,16,16}";

	static boolean lastSplit = false;

	static int lastTimepointsPerPartition = 1;

	static int lastSetupsPerPartition = 0;

	static boolean lastDeflate = true;

	static int lastJobIndex = 0;

	public static String lastExportPath = "/Users/pietzsch/Desktop/spimrec2.xml";

	public static File getInputXML()
	{
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileFilter( new FileFilter()
		{
			@Override
			public String getDescription()
			{
				return "xml files";
			}

			@Override
			public boolean accept( final File f )
			{
				if ( f.isDirectory() )
					return true;
				if ( f.isFile() )
				{
					final String s = f.getName();
					final int i = s.lastIndexOf('.');
					if (i > 0 &&  i < s.length() - 1) {
						final String ext = s.substring(i+1).toLowerCase();
						return ext.equals( "xml" );
					}
				}
				return false;
			}
		} );

		if ( fileChooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION )
			return fileChooser.getSelectedFile();
		else
			return null;
	}

	public static Parameters getParameters( final ExportMipmapInfo autoMipmapSettings, final boolean askForXMLPath, final boolean is16bit )
	{
		return getParameters( autoMipmapSettings, askForXMLPath, "Export for BigDataViewer", is16bit );
	}

	public static Parameters getParameters( final ExportMipmapInfo autoMipmapSettings, final boolean askForXMLPath, final String dialogTitle, final boolean is16bit )
	{
		final boolean displayClusterProcessing = Toggle_Cluster_Options.displayClusterProcessing;
		if ( displayClusterProcessing )
		{
			lastSplit = true;
			lastTimepointsPerPartition = 1;
			lastSetupsPerPartition = 0;
		}

		while ( true )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( dialogTitle );

			final Checkbox cManualMipmap, cSplit;
			final TextField tfSubsampling, tfChunkSizes, tfSplitTimepoints, tfSplitSetups;

			gd.addCheckbox( "manual_mipmap_setup", lastSetMipmapManual );
			if ( !PluginHelper.isHeadless() )
				cManualMipmap = ( Checkbox ) gd.getCheckboxes().lastElement();
			else
				cManualMipmap = null;

			gd.addStringField( "Subsampling_factors", lastSubsampling, 25 );
			if ( !PluginHelper.isHeadless() )
				tfSubsampling = ( TextField ) gd.getStringFields().lastElement();
			else
				tfSubsampling = null;

			gd.addStringField( "Hdf5_chunk_sizes", lastChunkSizes, 25 );
			if ( !PluginHelper.isHeadless() )
				tfChunkSizes = ( TextField ) gd.getStringFields().lastElement();
			else
				tfChunkSizes = null;

			gd.addMessage( "" );
			gd.addCheckbox( "split_hdf5", lastSplit );
			if ( !PluginHelper.isHeadless() )
				cSplit = ( Checkbox ) gd.getCheckboxes().lastElement();
			else
				cSplit = null;

			gd.addNumericField( "timepoints_per_partition", lastTimepointsPerPartition, 0, 25, "" );
			if ( !PluginHelper.isHeadless() )
				tfSplitTimepoints = ( TextField ) gd.getNumericFields().lastElement();
			else
				tfSplitTimepoints = null;

			gd.addNumericField( "setups_per_partition", lastSetupsPerPartition, 0, 25, "" );
			if ( !PluginHelper.isHeadless() )
				tfSplitSetups = ( TextField ) gd.getNumericFields().lastElement();
			else
				tfSplitSetups = null;

			if ( displayClusterProcessing )
				gd.addNumericField( "run_only_job_number", lastJobIndex, 0, 25, "" );

			gd.addMessage( "" );
			gd.addCheckbox( "use_deflate_compression", lastDeflate );


			if ( askForXMLPath )
			{
				gd.addMessage( "" );
				PluginHelper.addSaveAsFileField( gd, "Export_path", lastExportPath, 25 );
			}

			if ( !is16bit )
			{
				gd.addMessage( "" );
				gd.addMessage( "Currently, only 16-bit data is supported for HDF5. Please define how to convert to 16bit.", GUIHelper.mediumstatusfont );
				gd.addChoice( "Convert_32bit", convertChoices, convertChoices[ defaultConvertChoice ] );
			}

			final String autoSubsampling = ProposeMipmaps.getArrayString( autoMipmapSettings.getExportResolutions() );
			final String autoChunkSizes = ProposeMipmaps.getArrayString( autoMipmapSettings.getSubdivisions() );

			if ( !PluginHelper.isHeadless() )
			{
				gd.addDialogListener( new DialogListener()
				{
					@Override
					public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
					{
						gd.getNextBoolean();
						gd.getNextString();
						gd.getNextString();
						gd.getNextBoolean();
						gd.getNextNumber();
						gd.getNextNumber();
						if ( displayClusterProcessing )
							gd.getNextNumber();
						gd.getNextBoolean();
						if ( askForXMLPath )
							gd.getNextString();
						if ( !is16bit )
							gd.getNextChoiceIndex();
						if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cManualMipmap )
						{
							final boolean useManual = cManualMipmap.getState();
							tfSubsampling.setEnabled( useManual );
							tfChunkSizes.setEnabled( useManual );
							if ( !useManual )
							{
								tfSubsampling.setText( autoSubsampling );
								tfChunkSizes.setText( autoChunkSizes );
							}
						}
						else if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == cSplit )
						{
							final boolean split = cSplit.getState();
							tfSplitTimepoints.setEnabled( split );
							tfSplitSetups.setEnabled( split );
						}
						return true;
					}
				} );

				tfSubsampling.setEnabled( lastSetMipmapManual );
				tfChunkSizes.setEnabled( lastSetMipmapManual );
				if ( !lastSetMipmapManual )
				{
					tfSubsampling.setText( autoSubsampling );
					tfChunkSizes.setText( autoChunkSizes );
				}

				tfSplitTimepoints.setEnabled( lastSplit );
				tfSplitSetups.setEnabled( lastSplit );

				if ( displayClusterProcessing )
				{
					cSplit.setEnabled( false );
					tfSplitTimepoints.setEnabled( false );
					tfSplitSetups.setEnabled( false );
				}
			}

			gd.showDialog();
			if ( gd.wasCanceled() )
				return null;

			lastSetMipmapManual = gd.getNextBoolean();
			lastSubsampling = gd.getNextString();
			lastChunkSizes = gd.getNextString();
			lastSplit = gd.getNextBoolean();
			lastTimepointsPerPartition = ( int ) gd.getNextNumber();
			lastSetupsPerPartition = ( int ) gd.getNextNumber();
			if ( displayClusterProcessing )
			{
				lastJobIndex = ( int ) gd.getNextNumber();
			}
			lastDeflate = gd.getNextBoolean();
			if ( askForXMLPath )
				lastExportPath = gd.getNextString();
			if ( !is16bit )
				defaultConvertChoice = gd.getNextChoiceIndex();

			// parse mipmap resolutions and cell sizes
			final int[][] resolutions = PluginHelper.parseResolutionsString( lastSubsampling );
			final int[][] subdivisions = PluginHelper.parseResolutionsString( lastChunkSizes );
			if ( resolutions.length == 0 )
			{
				IJ.showMessage( "Cannot parse subsampling factors " + lastSubsampling );
				continue;
			}
			if ( subdivisions.length == 0 )
			{
				IJ.showMessage( "Cannot parse hdf5 chunk sizes " + lastChunkSizes );
				continue;
			}
			else if ( resolutions.length != subdivisions.length )
			{
				IJ.showMessage( "subsampling factors and hdf5 chunk sizes must have the same number of elements" );
				continue;
			}

			final File seqFile, hdf5File;

			if ( askForXMLPath )
			{
				String seqFilename = lastExportPath;
				if ( !seqFilename.endsWith( ".xml" ) )
					seqFilename += ".xml";
				seqFile = new File( seqFilename );
				final File parent = seqFile.getParentFile();
				if ( parent == null || !parent.exists() || !parent.isDirectory() )
				{
					IJ.showMessage( "Invalid export filename " + seqFilename );
					continue;
				}
				final String hdf5Filename = seqFilename.substring( 0, seqFilename.length() - 4 ) + ".h5";
				hdf5File = new File( hdf5Filename );
			}
			else
			{
				seqFile = hdf5File = null;
			}

			if ( defaultConvertChoice == 2 )
			{
				if ( Double.isNaN( defaultMin ) )
					defaultMin = 0;

				if ( Double.isNaN( defaultMax ) )
					defaultMax = 5;

				final GenericDialog gdMinMax = new GenericDialog( "Define min/max" );

				gdMinMax.addNumericField( "Min_Intensity_for_16bit_conversion", defaultMin, 1 );
				gdMinMax.addNumericField( "Max_Intensity_for_16bit_conversion", defaultMax, 1 );
				gdMinMax.addMessage( "Note: the typical range for multiview deconvolution is [0 ... 10] & for fusion the same as the input intensities., ",GUIHelper.mediumstatusfont );

				gdMinMax.showDialog();

				if ( gdMinMax.wasCanceled() )
					return null;
	
				defaultMin = gdMinMax.getNextNumber();
				defaultMax = gdMinMax.getNextNumber();
			}
			else
			{
				defaultMin = defaultMax = Double.NaN;
			}

			return new Parameters(
					lastSetMipmapManual, resolutions, subdivisions, seqFile, hdf5File, lastDeflate, lastSplit,
					lastTimepointsPerPartition, lastSetupsPerPartition, displayClusterProcessing, lastJobIndex,
					defaultConvertChoice, defaultMin, defaultMax );
		}
	}
}
