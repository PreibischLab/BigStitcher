/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2023 Big Stitcher developers.
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
package net.preibisch.stitcher.gui;

import ij.ImageJ;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import bdv.BigDataViewer;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.apply.BigDataViewerTransformationWindow;
import net.preibisch.mvrecon.fiji.plugin.util.MultiWindowLayoutHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorer;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import net.preibisch.stitcher.input.GenerateSpimData;
import net.preibisch.stitcher.plugin.BigStitcher;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

public class StitchingExplorer< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > extends FilteredAndGroupedExplorer< AS, X >
{

	private AS data;
	private String xml;
	private X io;
	private Mode currentMode;

	private JButton bStitching, bMV;

	public enum Mode{
		STITCHING,
		MULTIVIEW
	}
	
	
	public StitchingExplorer( final AS data, final String xml, final X io )
	{
		this.data = data;
		this.xml = xml;
		this.io = io;
		currentMode = Mode.STITCHING;

		frame = new JFrame( "Stitching Explorer" );

		JPanel buttons = new JPanel( new BorderLayout() );
		//buttons.setLayout( new BoxLayout( buttons, BoxLayout.LINE_AXIS ) );

		this.bStitching = new JButton( "Stitching" );
		buttons.add( bStitching, BorderLayout.WEST );
		bStitching.addActionListener( (e) -> switchMode(Mode.STITCHING));

		this.bMV = new JButton( "Multiview" );
		buttons.add( bMV, BorderLayout.EAST );
		bMV.addActionListener( (e) -> switchMode(Mode.MULTIVIEW));

		updateButtons();

		final JPanel header = new JPanel( new BorderLayout() );
		final JLabel l = new JLabel( "Press F1 for help" );
		l.setForeground( Color.DARK_GRAY );
		l.setBorder( new EmptyBorder( 0, 0, 0, 10 ) );
		l.setFont( l.getFont().deriveFont( Font.ITALIC ) );
		header.add( l, BorderLayout.EAST );

		header.add( buttons, BorderLayout.WEST );

		panel = new StitchingExplorerPanel< AS, X >( this, data, xml, io , true);

		frame.add( header, BorderLayout.NORTH );
		frame.add( panel, BorderLayout.CENTER );
		frame.setSize( panel.getPreferredSize() );

		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing( WindowEvent evt )
					{
						quit();
					}
				});

		frame.pack();
		frame.setVisible( true );

		// move explorer window and log to initial positions
		MultiWindowLayoutHelper.moveToScreenFraction( frame, ViewSetupExplorer.xPos, ViewSetupExplorer.yPos );
		MultiWindowLayoutHelper.moveToScreenFraction( MultiWindowLayoutHelper.getIJLogWindow(), ViewSetupExplorer.xPosLog, ViewSetupExplorer.yPosLog );

		// set the initial focus to the table
		panel.table.requestFocus();

		if ( panel.getTableModel().getRowCount() == 1 )
		{
			IOFunctions.println( "Only one tile, starting in MultiView mode." );
			switchMode( Mode.MULTIVIEW );
		}
	}

	public void updateButtons()
	{
		if ( currentMode == Mode.STITCHING )
		{
			bStitching.setForeground( Color.RED );
			bStitching.setFont( bStitching.getFont().deriveFont( Font.BOLD ) );
			bMV.setForeground( Color.BLACK );
			bMV.setFont( bMV.getFont().deriveFont( Font.PLAIN ) );
		}
		else
		{
			bMV.setForeground( Color.RED );
			bMV.setFont( bMV.getFont().deriveFont( Font.BOLD ) );
			bStitching.setForeground( Color.BLACK );
			bStitching.setFont( bStitching.getFont().deriveFont( Font.PLAIN ) );
		}
	}

	public void switchMode(Mode mode)
	{
		// we are already in the desired mode
		if (mode == currentMode)
			return;

		frame.setTitle( mode == Mode.STITCHING ? "Stitching Explorer" : "Multiview Explorer" );
		
		// TODO: is there a smarter way than closing and reopening BDV?
		boolean bdvWasOpen = panel.bdvPopup().bdvRunning();
		BigDataViewer bdvExisting = null;
		if (bdvWasOpen)
		{
			bdvExisting = panel.bdvPopup().getBDV();
			
			if (mode == Mode.MULTIVIEW)
			{
				bdvExisting.getViewer().removeTransformListener( ((StitchingExplorerPanel< AS, X >) panel).linkOverlay );
				bdvExisting.getViewer().getDisplay().removeOverlayRenderer( ((StitchingExplorerPanel< AS, X >) panel).linkOverlay );
				((StitchingExplorerPanel< AS, X >) panel).quitLinkExplorer();
				FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( bdvExisting );
			}
//			new Thread( () -> {panel.bdvPopup().closeBDV();} ).start();			
		}
			
		
		// remove old panel
		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();
		panel.getListeners().clear();
		StitchingExplorerPanel.currentInstance = null;		
		frame.remove( panel );
		
		// SpimData may have been updated -> pass that to new panel
		data = panel.getSpimData();
		
		// make new panel
		panel = mode == Mode.STITCHING ? new StitchingExplorerPanel<AS, X>(  this, data, xml, io , false) : new ViewSetupExplorerPanel<AS, X>(  this, data, xml, io , false );
		frame.add( panel, BorderLayout.CENTER );
		frame.setSize( panel.getPreferredSize() );
		frame.pack();

		
		if (bdvWasOpen)
			panel.bdvPopup().setBDV( bdvExisting );
//			for (ActionListener a : panel.bdvPopup().getActionListeners())
//				a.actionPerformed(( new ActionEvent( this, ActionEvent.ACTION_PERFORMED, null )));
		
		// set the initial focus to the table
		panel.table.requestFocus();
		
		frame.requestFocus();
		currentMode = mode;

		updateButtons();

		// set the focus to the table, otherwise key-bindings do not work right away
		panel.table.requestFocus();
	}
	
	public void quit()
	{
		// close BDV if open
		try
		{
			new Thread(() -> {
				if ( panel.bdvPopup().bdvRunning() )
					panel.bdvPopup().closeBDV();
			}).start();
		}
		catch( Exception e ) {};

		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();

		panel.getListeners().clear();

		frame.setVisible( false );
		frame.dispose();


		StitchingExplorerPanel.currentInstance = null;

		// by default go back to the LoadParseQuery window now
		new BigStitcher().run();
	}
	

	public static void main( String[] args )
	{
		new ImageJ();
		//new ViewSetupExplorer<>( GenerateSpimData.grid3x2(), null, null );
		new StitchingExplorer< SpimData2, XmlIoSpimData2 >( SpimData2.convert( GenerateSpimData.grid3x2()), null, null );
	}
}
