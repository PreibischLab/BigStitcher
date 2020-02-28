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

import java.awt.AWTEvent;
import java.awt.Choice;
import java.awt.event.ItemEvent;
import java.util.ArrayList;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.gui.DialogListener;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionGUI;
import net.preibisch.mvrecon.fiji.datasetmanager.LightSheetZ1GUI;
import net.preibisch.mvrecon.fiji.datasetmanager.MicroManagerGUI;
import net.preibisch.mvrecon.fiji.datasetmanager.MultiViewDatasetDefinition;
import net.preibisch.mvrecon.fiji.datasetmanager.StackList;
import net.preibisch.mvrecon.fiji.datasetmanager.StackListGUI;
import net.preibisch.mvrecon.fiji.datasetmanager.StackListImageJGUI;
import net.preibisch.mvrecon.fiji.datasetmanager.StackListLOCIGUI;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.plugin.util.MyMultiLineLabel;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class Define_Multi_View_Dataset implements PlugIn
{
	final public static ArrayList< MultiViewDatasetDefinition > staticDatasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();

	public static int defaultDatasetDef = 0;

	public static String defaultXMLName = "dataset.xml";

	final int numLinesDocumentation = 15;
	final int numCharacters = 80;
	
	static
	{
		IOFunctions.printIJLog = true;
		staticDatasetDefinitions.add( new FileListDatasetDefinitionGUI() );
		staticDatasetDefinitions.add( new StackListLOCIGUI<StackListGUI>() );
		staticDatasetDefinitions.add( new StackListImageJGUI<StackListGUI>() );
		staticDatasetDefinitions.add( new LightSheetZ1GUI() );
		staticDatasetDefinitions.add( new MicroManagerGUI() );
	}

	public static void supportDHM() { staticDatasetDefinitions.add( new DHMGUI() ); }

	@Override
	public void run( String arg0 ) 
	{
		defineDataset( true );
	}

	public Pair< SpimData2, String > defineDataset( final boolean save )
	{
		final ArrayList< MultiViewDatasetDefinition > datasetDefinitions = new ArrayList< MultiViewDatasetDefinition >();
		
		for ( final MultiViewDatasetDefinition mvd : staticDatasetDefinitions )
			datasetDefinitions.add( mvd.newInstance() );
		
		// verify that there are definitions
		final int numDatasetDefinitions = datasetDefinitions.size();
		
		if ( numDatasetDefinitions == 0 )
		{
			IJ.log( "No Multi-View Dataset Definitions available." );
			return null;
		}
		
		// get their names
		final String[] titles = new String[ numDatasetDefinitions ];
		
		for ( int i = 0; i < datasetDefinitions.size(); ++i )
			titles[ i ] = datasetDefinitions.get( i ).getTitle();
		
		// query the dataset definition to use
		final GenericDialogPlus gd1 = new GenericDialogPlus( "Choose method to define dataset" );

		if ( defaultDatasetDef >= numDatasetDefinitions )
			defaultDatasetDef = 0;

		gd1.addChoice( "Define_Dataset using:", titles, titles[ defaultDatasetDef ] );
		//Choice choice = (Choice)gd1.getChoices().lastElement();
		gd1.addStringField( "Project_filename (will be created):", defaultXMLName, 30 );

		gd1.addMessage(
				"We recommend using the AutoLoader for dataset definition. Please note that only one\n"
				+ "XML per directory is currently supported. All functionality is macro-scriptable via\n"
				+ "BigStitcher > Batch Processing.", GUIHelper.smallStatusFont );

		/*
		final MyMultiLineLabel label = MyMultiLineLabel.addMessage( gd1,
				formatEntry( datasetDefinitions.get( defaultDatasetDef ).getExtendedDescription(), numCharacters, numLinesDocumentation ),
				new Font( Font.MONOSPACED, Font.PLAIN, 11 ),
				Color.BLACK );
						
		addListeners( gd1, choice, label, datasetDefinitions );*/
		
		GUIHelper.addWebsite( gd1 );
		
		gd1.showDialog();
		if ( gd1.wasCanceled() )
			return null;
		
		defaultDatasetDef = gd1.getNextChoiceIndex();
		final String xmlFileName = defaultXMLName = gd1.getNextString();
		
		// run the definition
		final MultiViewDatasetDefinition def = datasetDefinitions.get( defaultDatasetDef );
		
		IOFunctions.println( defaultDatasetDef );
		
		final SpimData2 spimData = def.createDataset();
		
		if ( spimData == null )
		{
			IOFunctions.println( "Defining multi-view dataset failed." );
			return null;
		}
		else
		{
			final String xml = SpimData2.saveXML( spimData, xmlFileName, "" );

			if ( xml != null )
			{
				GenericLoadParseQueryXML.defaultXMLfilename = xml;
				return new ValuePair< SpimData2, String >( spimData, xml );
			}
			else
			{
				return null;
			}
		}
	}
	
	public static String[] formatEntry( String line, final int numCharacters, final int numLines )
	{
		if ( line == null )
			line = "";
		
		String[] split = line.split( "\n" );
		
		if ( split.length != numLines )
		{
			String[] split2 = new String[ numLines ];

			for ( int j = 0; j < Math.min( split.length, numLines ); ++j )
				split2[ j ] = split[ j ];
			
			for ( int j = Math.min( split.length, numLines ); j < numLines; ++j )
				split2[ j ] = "";

			split = split2;
		}
		
		for ( int j = 0; j < split.length; ++j )
		{
			String s = split[ j ];
			
			if ( s.length() > 80 )
				s = s.substring( 0, 80 );
			
			// fill up to numCharacters + 3
			for ( int i = s.length(); i < numCharacters + 3; ++i )
				s = s + " ";
			
			split[ j ] = s;
		}
		
		return split;
	}

	protected void addListeners( final GenericDialog gd, final Choice choice, final MyMultiLineLabel label, final ArrayList< MultiViewDatasetDefinition > datasetDefinitions )
	{
		gd.addDialogListener( new DialogListener()
		{
			@Override
			public boolean dialogItemChanged( final GenericDialog dialog, final AWTEvent e )
			{
				if ( e instanceof ItemEvent && e.getID() == ItemEvent.ITEM_STATE_CHANGED && e.getSource() == choice )
				{
					label.setText( formatEntry( datasetDefinitions.get( choice.getSelectedIndex() ).getExtendedDescription(), numCharacters, numLinesDocumentation ) );
				}
				return true;
			}
		} );
		
	}
	
	public static void main( String args[] )
	{
		StackList.defaultDirectory = "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM";

		IOFunctions.printIJLog = true;
		new ImageJ();
		new Define_Multi_View_Dataset().run( null );
		
		//System.exit( 0 );
	}
}
