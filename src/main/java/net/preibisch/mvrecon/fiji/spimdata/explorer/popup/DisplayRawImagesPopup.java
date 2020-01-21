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
import java.util.ArrayList;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.Display_View;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

import mpicbg.spim.data.sequence.ViewId;

public class DisplayRawImagesPopup extends JMenu implements ExplorerWindowSetable
{
	public static final int askWhenMoreThan = 5;
	private static final long serialVersionUID = 5234649262342301390L;

	ExplorerWindow< ?, ? > panel = null;

	public DisplayRawImagesPopup()
	{
		super( "Display Raw Image(s)" );

		final JMenuItem as32bit = new JMenuItem( "As 32-Bit ImageJ Stack" );
		final JMenuItem as16bit = new JMenuItem( "As 16-Bit ImageJ Stack" );

		as16bit.addActionListener( new MyActionListener( true ) );
		as32bit.addActionListener( new MyActionListener( false ) );

		this.add( as16bit );
		this.add( as32bit );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;

		return this;
	}

	public class MyActionListener implements ActionListener
	{
		final boolean as16bit;

		public MyActionListener( final boolean as16bit )
		{
			this.as16bit = as16bit;
		}

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
					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), views );


					if (
						views.size() > askWhenMoreThan &&
						JOptionPane.showConfirmDialog(
							null,
							"Are you sure to display " + views.size() + " views?",
							"Warning",
							JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
						return;

					IOFunctions.println(
							"Opening as" + ( as16bit ? " 16 bit" : "32 bit" ) + " using " +
							panel.getSpimData().getSequenceDescription().getImgLoader().getClass().getSimpleName() );

					for ( final ViewId view : views )
					{
						IOFunctions.println( "Loading timepoint: " + view.getTimePointId() + " ViewSetup: " + view.getViewSetupId() );
		
						final String name = "Timepoint: " + view.getTimePointId() + " ViewSetup: " + view.getViewSetupId();
			
						if ( as16bit )
							Display_View.display( panel.getSpimData(), view, 1, name );
						else
							Display_View.display( panel.getSpimData(), view, 0, name );
					}
				}
			} ).start();
		}
	}

}
