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
import ij.gui.GenericDialog;
import net.imagej.patcher.HeadlessGenericDialog;

import java.awt.Button;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

public class PluginHelper
{
	public static boolean isHeadless() { return GenericDialog.class.getSuperclass().equals( HeadlessGenericDialog.class ); }

	public static void addSaveAsFileField( final GenericDialogPlus dialog, final String label, final String defaultPath, final int columns)
	{
		dialog.addStringField( label, defaultPath, columns );

		if ( isHeadless() )
			return;

		final TextField text = ( TextField ) dialog.getStringFields().lastElement();
		final GridBagLayout layout = ( GridBagLayout ) dialog.getLayout();
		final GridBagConstraints constraints = layout.getConstraints( text );

		final Button button = new Button( "Browse..." );
		final ChooseXmlFileListener listener = new ChooseXmlFileListener( text );
		button.addActionListener( listener );
		button.addKeyListener( dialog );

		final Panel panel = new Panel();
		panel.setLayout( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panel.add( text );
		panel.add( button );

		layout.setConstraints( panel, constraints );
		dialog.add( panel );
	}

	public static void addSaveAsDirectoryField( final GenericDialogPlus dialog, final String label, final String defaultPath, final int columns)
	{
		dialog.addStringField( label, defaultPath, columns );

		if ( isHeadless() )
			return;

		final TextField text = ( TextField ) dialog.getStringFields().lastElement();
		final GridBagLayout layout = ( GridBagLayout ) dialog.getLayout();
		final GridBagConstraints constraints = layout.getConstraints( text );

		final Button button = new Button( "Browse..." );
		final ChooseDirectoryListener listener = new ChooseDirectoryListener( text );
		button.addActionListener( listener );
		button.addKeyListener( dialog );

		final Panel panel = new Panel();
		panel.setLayout( new FlowLayout( FlowLayout.LEFT, 0, 0 ) );
		panel.add( text );
		panel.add( button );

		layout.setConstraints( panel, constraints );
		dialog.add( panel );
	}

	public static class ChooseXmlFileListener implements ActionListener
	{
		TextField text;

		public ChooseXmlFileListener( final TextField text )
		{
			this.text = text;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			File directory = new File( text.getText() );
			while ( directory != null && !directory.exists() )
				directory = directory.getParentFile();

			final JFileChooser fc = new JFileChooser( directory );
			fc.setFileFilter( new FileFilter()
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

			fc.setFileSelectionMode( JFileChooser.FILES_ONLY );

			final int returnVal = fc.showSaveDialog( null );
			if ( returnVal == JFileChooser.APPROVE_OPTION )
			{
				String f = fc.getSelectedFile().getAbsolutePath();
				if ( ! f.endsWith( ".xml" ) )
					f += ".xml";
				text.setText( f );
			}
		}
	}

	public static class ChooseDirectoryListener implements ActionListener
	{
		TextField text;

		public ChooseDirectoryListener( final TextField text )
		{
			this.text = text;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			File directory = new File( text.getText() );
			while ( directory != null && !directory.exists() )
				directory = directory.getParentFile();

			final JFileChooser fc = new JFileChooser( directory );
			fc.setFileFilter( new FileFilter()
			{
				@Override
				public String getDescription()
				{
					return "directories";
				}

				@Override
				public boolean accept( final File f )
				{
					if ( f.isDirectory() && f.exists() )
						return true;
					else
						return false;
				}
			} );

			fc.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );

			final int returnVal = fc.showSaveDialog( null );
			if ( returnVal == JFileChooser.APPROVE_OPTION )
			{
				String f = fc.getSelectedFile().getAbsolutePath();
				File ff = new File( f );
				if ( ff.exists() && ff.isDirectory() )
					text.setText( f );
				else
					text.setText( ff.getParentFile().toString() );
				
			}
		}
	}

	public static int[][] parseResolutionsString( final String s )
	{
		final String regex = "\\{\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\}";
		final Pattern pattern = Pattern.compile( regex );
		final Matcher matcher = pattern.matcher( s );

		final ArrayList< int[] > tmp = new ArrayList< int[] >();
		while ( matcher.find() )
		{
			final int[] resolution = new int[] { Integer.parseInt( matcher.group( 1 ) ), Integer.parseInt( matcher.group( 2 ) ), Integer.parseInt( matcher.group( 3 ) ) };
			tmp.add( resolution );
		}
		final int[][] resolutions = new int[ tmp.size() ][];
		for ( int i = 0; i < resolutions.length; ++i )
			resolutions[ i ] = tmp.get( i );

		return resolutions;
	}

	public static File createNewPartitionFile( final File xmlSequenceFile ) throws IOException
	{
		final String seqFilename = xmlSequenceFile.getAbsolutePath();
		if ( !seqFilename.endsWith( ".xml" ) )
			throw new IllegalArgumentException();
		final String baseFilename = seqFilename.substring( 0, seqFilename.length() - 4 );
		for ( int i = 0; i < Integer.MAX_VALUE; ++i )
		{
			final File hdf5File = new File( String.format( "%s-%d.h5", baseFilename, i ) );
			if ( ! hdf5File.exists() )
				if ( hdf5File.createNewFile() )
					return hdf5File;
		}
		throw new RuntimeException( "could not generate new partition filename" );
	}
}
