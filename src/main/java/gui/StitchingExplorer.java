package gui;

import ij.ImageJ;
import input.GenerateSpimData;

import java.awt.BorderLayout;
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
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import bdv.BigDataViewer;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
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
		
		JPanel buttons = new JPanel( );
		buttons.setLayout( new BoxLayout( buttons, BoxLayout.LINE_AXIS ) );
		
		JButton bStitching = new JButton( "Stitching" );
		buttons.add( bStitching );
		bStitching.addActionListener( (e) -> switchMode(Mode.STITCHING));
		
		
		JButton bMV = new JButton( "Multiview" );
		buttons.add( bMV );
		bMV.addActionListener( (e) -> switchMode(Mode.MULTIVIEW));
		
		
		
		panel = new StitchingExplorerPanel< AS, X >( this, data, xml, io );

		frame.add( buttons, BorderLayout.NORTH );
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

		// set the initial focus to the table
		panel.table.requestFocus();
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
		
		
		currentMode = mode;
	}
	
	public void quit()
	{
		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();

		panel.getListeners().clear();
		
		frame.setVisible( false );
		frame.dispose();

		StitchingExplorerPanel.currentInstance = null;
	}
	

	public static void main( String[] args )
	{
		new ImageJ();
		//new ViewSetupExplorer<>( GenerateSpimData.grid3x2(), null, null );
		new StitchingExplorer< SpimData, XmlIoSpimData >( GenerateSpimData.grid3x2(), null, null );
	}
}
