/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2024 Big Stitcher developers.
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
package net.preibisch.stitcher.plugin;


import java.awt.Button;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URI;
import java.net.URISyntaxException;

import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

import ij.ImageJ;
import ij.plugin.PlugIn;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.gui.StitchingExplorer;

@Plugin(type = Command.class, menuPath = "Plugins>BigStitcher>BigStitcher")
public class BigStitcher implements Command, PlugIn
{
	boolean newDataset = false;

	@Override
	public void run( String arg )
	{
		run();
	}

	@Override
	public void run( )
	{
		final LoadParseQueryXML result = new EasterEggLoadParseQueryXML();

		result.addButton( "Define a new dataset", new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				((TextField)result.getGenericDialog().getStringFields().firstElement()).setText( "define" );
				Button ok = result.getGenericDialog().getButtons()[ 0 ];

				ActionEvent ae =  new ActionEvent( ok, ActionEvent.ACTION_PERFORMED, "");
				Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(ae);
			}
		});

		if ( !result.queryXML( "BigStitcher", "", false, false, false, false, false ) && !newDataset )
			return;

		final SpimData2 data = result.getData();
		final URI xml = result.getXMLURI();
		final XmlIoSpimData2 io = result.getIO();

		final StitchingExplorer< SpimData2 > explorer =
				new StitchingExplorer< >( data, xml, io );

		explorer.getFrame().toFront();
	}

	public static void setupTesting()
	{
		IOFunctions.printIJLog = true;
		new net.imagej.ImageJ();
		new ImageJ();

		//if ( System.getProperty("os.name").toLowerCase().contains( "mac" ) )
		//	GenericLoadParseQueryXML.defaultXMLURI = "/Users/preibischs/SparkTest/Stitching/dataset.xml";
	}

	public static void main( String[] args )
	{
		setupTesting();
		new BigStitcher().run( );
	}
}
