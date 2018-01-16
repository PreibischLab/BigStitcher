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
package net.preibisch.stitcher.gui.popup.icp;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Detection;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Registration;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.gui.StitchingUIHelper;

public class RefineWithICPPopup extends JMenu implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 1L;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public RefineWithICPPopup( String description )
	{
		super( description );

		final JMenuItem simpleICP = new JMenuItem( "Wizard ..." );
		final JMenuItem advancedICP = new JMenuItem( "Expert ..." );

		simpleICP.addActionListener( new ICPListener( false ) );
		advancedICP.addActionListener( new ICPListener( true ) );

		this.add( simpleICP );
		this.add( advancedICP );
	}

	@Override
	public JComponent setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;

		return this;
	}

	public class ICPListener implements ActionListener
	{
		final boolean expert;

		public ICPListener( final boolean expert ) { this.expert = expert; }

		@Override
		public void actionPerformed( ActionEvent e )
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

			if (!GroupedRowWindow.class.isInstance( panel ))
			{
				IOFunctions.println( "Only supported for GroupedRowWindow panels: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( () ->
			{
				// get selected groups, filter missing views, get all present and selected vids
				final SpimData2 data = (SpimData2) panel.getSpimData();
				@SuppressWarnings("unchecked")
				FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
				SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< SpimData2 >( (SpimData2) panel.getSpimData() );

				if (!expert)
				{
					// use whatever is selected in panel as filters
					filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );
				}
				else
				{
					filteringAndGrouping.askUserForFiltering( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;
				}
	
				if (!expert)
				{
					// get the grouping from panel and compare Tiles
					panelFG.getTableModel().getGroupingFactors().forEach( g -> filteringAndGrouping.addGroupingFactor( g ));
					filteringAndGrouping.addComparisonAxis( Tile.class );
	
					// compare by Channel if channels were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Channel.class ))
						filteringAndGrouping.addComparisonAxis( Channel.class );
	
					// compare by Illumination if illums were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Illumination.class ))
						filteringAndGrouping.addComparisonAxis( Illumination.class );
				}
				else
				{
					filteringAndGrouping.addComparisonAxis( Tile.class );
					filteringAndGrouping.askUserForGrouping( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;
				}

				if ( StitchingUIHelper.allViews2D( filteringAndGrouping.getFilteredViews() ) )
				{
					IOFunctions.println( "ICP refinement is currenty not supported for 2D: " + this.getClass().getSimpleName() );
					return;
				}

				boolean groupTiles = filteringAndGrouping.getGroupingFactors().contains( Tile.class ); // always false?
				boolean groupIllums = filteringAndGrouping.getGroupingFactors().contains( Illumination.class );
				boolean groupChannels = filteringAndGrouping.getGroupingFactors().contains( Channel.class );

				if ( expert )
				{
					
				}
				else
				{
					
				}
			}).start();
		}
		
	}
}
