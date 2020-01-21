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
package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import net.preibisch.mvrecon.fiji.plugin.util.MultiWindowLayoutHelper;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BasicBDVPopup;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

public class ViewSetupExplorer< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > extends FilteredAndGroupedExplorer< AS, X >
{
	public static final double xPos = 0.4;
	public static final double yPos = 0.4;
	public static final double xPosLog = 0.0;
	public static final double yPosLog = 0.8;

	public ViewSetupExplorer( final AS data, final String xml, final X io )
	{
		frame = new JFrame( "ViewSetup Explorer" );
		panel = new ViewSetupExplorerPanel< AS, X >( this, data, xml, io, true );

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
		MultiWindowLayoutHelper.moveToScreenFraction( frame, xPos, yPos );
		MultiWindowLayoutHelper.moveToScreenFraction( MultiWindowLayoutHelper.getIJLogWindow(), xPosLog, yPosLog );

		// set the initial focus to the table
		panel.table.requestFocus();
	}
	
	public void quit()
	{
		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();

		panel.getListeners().clear();

		frame.setVisible( false );
		frame.dispose();

		BasicBDVPopup bdvPopup = panel.bdvPopup();
		
		if ( bdvPopup.bdvRunning() )
			bdvPopup.closeBDV();

		ViewSetupExplorerPanel.currentInstance = null;
	}
}
