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
package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.registration.RegistrationExplorer;

public class RegistrationExplorerPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;

	FilteredAndGroupedExplorerPanel< ?, ? > panel;
	RegistrationExplorer< ?, ? > re = null;

	public RegistrationExplorerPopup()
	{
		super( "Registration Explorer (on/off)" );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = (FilteredAndGroupedExplorerPanel< ?, ? >)panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( re == null || !re.frame().isVisible() )
					{
						re = instanceFor( (FilteredAndGroupedExplorerPanel)panel );
					}
					else
					{
						re.quit();
						re = null;
					}
				}
			}).start();
		}
	}

	private static final < AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > RegistrationExplorer< AS, X > instanceFor( final FilteredAndGroupedExplorerPanel< AS, X > panel )
	{
		return new RegistrationExplorer< AS, X >( panel.xml(), panel.io(), panel.explorer() );
	}
}
