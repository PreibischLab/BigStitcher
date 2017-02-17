package gui.popup;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;

import bdv.BigDataViewer;
import gui.PreviewRegularGridPanel;
import gui.overlay.DemoLinkOverlay;
import gui.popup.TestPopup.MyActionListener;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class DemoLinkOverlayPopup extends JMenuItem implements ExplorerWindowSetable {

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	DemoLinkOverlay overlay;
	boolean active = false;

	public DemoLinkOverlayPopup(DemoLinkOverlay overlay)
	{
		super( "Toggle Demo Link Overlay" );
		this.addActionListener( new MyActionListener() );
		this.overlay = overlay;
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	
	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			active = !active;
			
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			overlay.isActive = active;
			
			BigDataViewer bdv = panel.bdvPopup().getBDV();
			
			if (bdv == null)
			{
				IOFunctions.println( "BigDataViewer is not open. Please start it to access this funtionality." );
				return;
			}
	
			// remove if it is already there
			bdv.getViewer().removeTransformListener( overlay );
			bdv.getViewer().getDisplay().removeTransformListener( overlay );
			
			
			if (active)
			{
				bdv.getViewer().addTransformListener( overlay );
				bdv.getViewer().getDisplay().addOverlayRenderer( overlay );
			}
			
			
			
		
		}
	}
}
