/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
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
package net.preibisch.stitcher.gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import bdv.BigDataViewer;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.gui.overlay.DemoLinkOverlay;

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
