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
package net.preibisch.mvrecon.process.cuda;

import com.sun.jna.Library;
import com.sun.jna.Native;

import fiji.util.gui.GenericDialogPlus;
import ij.IJ;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;

public class NativeLibraryTools
{
	public static String defaultDirectory = null;

	public static < L extends Library > L loadNativeLibrary( final Class< L > library )
	{
		final ArrayList< String > names = new ArrayList< String >();
		return loadNativeLibrary( names, library );
	}

	public static < L extends Library > L loadNativeLibrary( final String potentialName, final Class< L > library )
	{
		final ArrayList< String > names = new ArrayList< String >();
		names.add( potentialName );
		return loadNativeLibrary( names, library );
	}

	public static < L extends Library > L loadNativeLibrary( ArrayList< String > potentialNames, final Class< L > library )
	{
		final GenericDialogPlus gd = new GenericDialogPlus( "Specify path of native library" );

		final String directory;
		
		if ( defaultDirectory == null )
			directory = IJ.getDirectory( "ImageJ" );
		else
			directory = defaultDirectory;
		
		final File dir;
		
		if ( directory == null || directory.equals( "null/") )
			dir = new File( "" );
		else
			dir = new File( directory );
		
		gd.addDirectoryField( "CUDA_Directory", dir.getAbsolutePath(), 80 );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		return loadNativeLibrary( potentialNames, new File( defaultDirectory = gd.getNextString() ), library );
	}

	@SuppressWarnings("unchecked")
	public static < L extends Library > L loadNativeLibrary( ArrayList< String > potentialNames, final File dir, final Class< L > libraryClass )
	{
		if ( potentialNames == null )
			potentialNames = new ArrayList<String>();

		try
		{
			// it cannot be null
			if ( System.getProperty( "jna.library.path" ) == null )
				System.setProperty( "jna.library.path", "" );
			
			final ArrayList< String > ext = getLibraryExtensions();

			String exts = "";
			
			for ( int i = 0; i < ext.size(); ++i )
			{
				exts += "'" + ext.get( i ) + "'";
				if ( i != ext.size() -1 )
					exts += ", ";
			}

			IOFunctions.println( "Looking for native libraries ending with " + exts + " in directory: '" + dir.getAbsolutePath() + "' ... " );

			final String[] libs = dir.list( new FilenameFilter() {
				@Override
				public boolean accept( final File dir, final String name )
				{
					for ( final String e : ext )
						if ( name.toLowerCase().endsWith( e ) )
							return true;

					return false;
				}
			});

			if ( libs == null || libs.length == 0 )
			{
				IOFunctions.println( "No libraries found." );
				return null;
			}

			int index = 0;
			
			for ( int i = 0; i < libs.length; ++i )
				for ( final String s : potentialNames )
					if ( libs[ i ].toLowerCase().contains( s.toLowerCase() ) )
						index = i;

			final GenericDialogPlus gd = new GenericDialogPlus( "Select native library" );

			gd.addMessage( "Native_library_directory: '" + dir.getAbsolutePath() + "'", GUIHelper.mediumstatusfont );
			gd.addChoice( "Select_native_library_for_" + libraryClass.getSimpleName(), libs, libs[ index ] );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return null;
			
			final String fullPath = new File( dir.getAbsolutePath(), gd.getNextChoice() ).getAbsolutePath();
			IOFunctions.println( "Trying to load following library: " + fullPath );

			return (L)Native.loadLibrary( fullPath, libraryClass );
		}
		catch ( UnsatisfiedLinkError e )
		{
			IOFunctions.println( "Cannot load JNA library: " + e );
			return null;
		}
	}

	public static ArrayList< String > getLibraryExtensions()
	{
		final ArrayList< String > libs = new ArrayList<String>();
		
		if ( IJ.isWindows() )
		{
			libs.add( ".dll" );
		}
		else if ( IJ.isLinux() )
		{
			libs.add( ".so" );
			libs.add( ".lib" );
		}
		else if ( IJ.isMacOSX() || IJ.isMacintosh() )
		{
			libs.add( ".lib" );
			libs.add( ".so" );
			libs.add( ".dylib" );
		}
		
		return libs;
	}
	
	public static void main( String[] args )
	{
		CUDAStandardFunctions c = loadNativeLibrary( CUDAStandardFunctions.class );
		
		IOFunctions.println( "devices: " + c.getNumDevicesCUDA() );
		CUDATools.queryCUDADetails( c, false );
		CUDATools.queryCUDADetails( c, true );
	}
}
