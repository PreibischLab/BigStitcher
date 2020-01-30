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

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.io.File;
import java.util.ArrayList;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.cluster.MergeClusterJobs;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;

import mpicbg.spim.data.SpimDataException;

public class Merge_Cluster_Jobs implements PlugIn
{
	public static String defaultContains1 = "job_";
	public static String defaultContains2 = ".xml";
	public static String defaultNewXML = "dataset_merged.xml";
	public static String defaultMergeXMLDir = null;
	public static boolean defaultDeleteXMLs = false;
	public static boolean defaultDisplayXMLs = true;
	public static boolean ignoreTileFiles = true;

	Color color = GUIHelper.neutral;
	String message = "---";
	ArrayList< File > xmls = new ArrayList< File >();

	// run("Merge Cluster Jobs", "directory=/Users/preibischs/Downloads/A_SPIM_new_pipeline filename_contains=job_ filename_also_contains=.xml display merged_xml=dataset_merged.xml");

	@Override
	public void run( String arg0 )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Select XML's to Merge" );

		if ( defaultMergeXMLDir == null )
			defaultMergeXMLDir = new File( GenericLoadParseQueryXML.defaultXMLfilename ).getParent();

		gd.addDirectoryField( "Directory", defaultMergeXMLDir, 50 );
		gd.addStringField( "Filename_contains", defaultContains1 );
		gd.addStringField( "Filename_also_contains", defaultContains2 );

		final TextField directory = (TextField)gd.getStringFields().firstElement();
		final TextField contains1 = (TextField)gd.getStringFields().get( 1 );
		final TextField contains2 = (TextField)gd.getStringFields().get( 2 );

		gd.addStringField( "Merged_XML", defaultNewXML, 50 );
		gd.addCheckbox( "Display currently selected XML's in log window", defaultDisplayXMLs );

		final Checkbox display = (Checkbox)gd.getCheckboxes().firstElement();
		gd.addCheckbox( "Delete_XML's after successful merge", defaultDeleteXMLs );

		// a first run
		findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText(), defaultDisplayXMLs );

		gd.addMessage( "" );
		gd.addMessage( this.message, GUIHelper.largestatusfont, this.color );

		final Label target = (Label)gd.getMessage();

		addListeners( gd, directory, contains1, contains2, display, target );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final String dir = defaultMergeXMLDir = gd.getNextString();
		final String cont1 = defaultContains1 = gd.getNextString();
		final String cont2 = defaultContains2 = gd.getNextString();
		defaultDisplayXMLs = gd.getNextBoolean();
		final boolean delete = defaultDeleteXMLs = gd.getNextBoolean();
		final File newXML = new File( dir, defaultNewXML = gd.getNextString() );

		IOFunctions.println( "Attempting to merge the following XML's in directory '" + dir + "':" );

		findFiles( new File( dir ), cont1, cont2, false );

		for ( final File f : this.xmls )
			IOFunctions.println( "  " + f.getAbsolutePath() );

		try
		{
			MergeClusterJobs.merge( xmls, newXML );

			IOFunctions.println( "Successfully merged all XML's into one new XML: " + newXML.getAbsolutePath() );

			if ( delete )
			{
				IOFunctions.println( "Deleting all input XML's." );

				for ( final File f : this.xmls )
					f.delete();

				IOFunctions.println( "Done." );
			}
		}
		catch ( final SpimDataException e )
		{
			IOFunctions.println( "Failed to merge XML's: " + e );
			e.printStackTrace();
		}
	}

	protected void addListeners(
			final GenericDialog gd,
			final TextField directory,
			final TextField contains1,
			final TextField contains2,
			final Checkbox display,
			final Label label )
	{
		directory.addTextListener( new TextListener()
		{
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText(), display.getState() );
					update( label );
				}
			}
		});

		contains1.addTextListener( new TextListener()
		{
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText(), display.getState() );
					update( label );
				}
			}
		});

		contains2.addTextListener( new TextListener()
		{
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText(), display.getState() );
					update( label );
				}
			}
		});

		display.addItemListener( new ItemListener()
		{
			@Override
			public void itemStateChanged( final ItemEvent i )
			{
				if ( i.getID() == ItemEvent.ITEM_STATE_CHANGED )
				{
					findFiles( new File( directory.getText() ), contains1.getText(), contains2.getText(), display.getState() );
					update( label );
				}
			}
		});
	}

	protected void update( final Label label )
	{
		label.setText( this.message );
		label.setForeground( this.color );
	}

	protected void findFiles( final File dir, final String contains1, final String contains2, final boolean display )
	{
		this.xmls.clear();

		if ( !dir.isDirectory() )
		{
			this.message = "Path provided is not a directory.";
			this.color = GUIHelper.error;
		}
		else
		{
			for ( final String file : dir.list() )
			{
				if ( ignoreTileFiles )
				{
					if ( file.contains( contains1 ) && file.contains( contains2 ) && !file.contains( "~" ) )
						this.xmls.add( new File( dir, file ) );
				}
				else
				{
					if ( file.contains( contains1 ) && file.contains( contains2 ) )
						this.xmls.add( new File( dir, file ) );
				}
			}

			if ( this.xmls.size() == 0 )
			{
				this.message = "No files found that match the name pattern.";
				this.color = GUIHelper.warning;
			}
			else
			{
				this.message = "Found " + this.xmls.size() + " files that match the name pattern.";
				this.color = GUIHelper.good;

				if ( display )
				{
					IOFunctions.println( "Currently selected XML's: " );

					for ( final File f : this.xmls )
						IOFunctions.println( "  " + f.getAbsolutePath() );
				}
			}
		}
	}

	public static void main( String[] args )
	{
		new Merge_Cluster_Jobs().run( null );
	}
}
