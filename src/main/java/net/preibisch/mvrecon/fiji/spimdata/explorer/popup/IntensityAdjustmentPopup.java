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
import java.util.Collections;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.Intensity_Adjustment;
import net.preibisch.mvrecon.fiji.plugin.RemoveIntensity_Adjustment;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class IntensityAdjustmentPopup extends JMenu implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 1L;

	ExplorerWindow< ?, ? > panel;

	protected static String[] types = new String[]{ "Compute ...", "List all", "Remove" };

	public IntensityAdjustmentPopup()
	{
		super( "Intensity Adjustment" );

		final JMenuItem compute = new JMenuItem( types[ 0 ] );
		final JMenuItem list = new JMenuItem( types[ 1 ] );
		final JMenuItem remove = new JMenuItem( types[ 2 ] );

		compute.addActionListener( new MyActionListener( 0 ) );
		list.addActionListener( new MyActionListener( 1 ) );
		remove.addActionListener( new MyActionListener( 2 ) );

		this.add( compute );
		this.add( list );
		this.add( remove );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		final int index; // 0 == Compute, 1 == Remove

		public MyActionListener( final int index )
		{
			this.index = index;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			final SpimData2 sd = (SpimData2)panel.getSpimData();

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );

					if ( index == 0 )
						new Intensity_Adjustment().intensityAdjustment( sd, viewIds );
					else if ( index == 1 )
					{
						String text = "Intensity Adjustments:\n";

						if ( sd.getIntensityAdjustments().getIntensityAdjustments().size() == 0 )
						{
							text += "None defined\n";
						}
						else
						{
							final ArrayList< ViewId > views = new ArrayList<>( sd.getIntensityAdjustments().getIntensityAdjustments().keySet() );
							Collections.sort( views );
							for ( final ViewId v : views )
								text += Group.pvid( v ) + ": " + sd.getIntensityAdjustments().getIntensityAdjustments().get( v ) + "\n";
						}

						IOFunctions.println( text );
					}
					else
						new RemoveIntensity_Adjustment().removeAdjustment( sd, viewIds );
				}
			} ).start();
		}
	}
}
