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
package net.preibisch.mvrecon.fiji.spimdata.explorer.registration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import net.preibisch.mvrecon.fiji.spimdata.explorer.SelectedViewDescriptionListener;

public class RegistrationExplorer< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > >
	implements SelectedViewDescriptionListener< AS >
{
	final String xml;
	final JFrame frame;
	final RegistrationExplorerPanel panel;
	final FilteredAndGroupedExplorer< AS, X > viewSetupExplorer;
	
	public RegistrationExplorer( final String xml, final X io, final FilteredAndGroupedExplorer< AS, X > viewSetupExplorer )
	{
		this.xml = xml;
		this.viewSetupExplorer = viewSetupExplorer;

		frame = new JFrame( "Registration Explorer" );
		panel = new RegistrationExplorerPanel( viewSetupExplorer.getPanel().getSpimData().getViewRegistrations(), this );
		frame.add( panel, BorderLayout.CENTER );

		frame.setSize( panel.getPreferredSize() );

		frame.pack();
		frame.setVisible( true );
		
		// Get the size of the screen
		final Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

		// Move the window
		frame.setLocation( ( dim.width - frame.getSize().width ) / 2, ( dim.height - frame.getSize().height ) * 3 / 4  );

		// this call also triggers the first update of the registration table
		viewSetupExplorer.addListener( this );
	}

	public JFrame frame() { return frame; }

	@Override
	public void save() {}

	@Override
	public void selectedViewDescriptions( final List<List< BasicViewDescription< ? extends BasicViewSetup > >> viewDescriptions )
	{
		List<BasicViewDescription< ? extends BasicViewSetup >> vdsFlat = new ArrayList<>();
		for (List<BasicViewDescription< ? extends BasicViewSetup >> vdsI : viewDescriptions)
			vdsFlat.addAll( vdsI );
		panel.updateViewDescriptions( vdsFlat );
		System.out.println( viewDescriptions );
	}

	@Override
	public void quit()
	{
		frame.setVisible( false );
		frame.dispose();
	}

	public RegistrationExplorerPanel panel() { return panel; }

	@Override
	public void updateContent( final AS data )
	{
		//panel.getTableModel().update( data.getViewRegistrations() );
		//panel.getTableModel().fireTableDataChanged();
	}

}
