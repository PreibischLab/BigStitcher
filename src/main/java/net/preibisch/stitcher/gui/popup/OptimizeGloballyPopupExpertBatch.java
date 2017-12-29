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
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.algorithm.globalopt.GlobalOptStitcher;
import net.preibisch.stitcher.algorithm.globalopt.GlobalOptimizationParameters;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;
import net.preibisch.stitcher.gui.overlay.DemoLinkOverlay;

public class OptimizeGloballyPopupExpertBatch extends JMenuItem
		implements ExplorerWindowSetable
{
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	private boolean expertMode;

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	public OptimizeGloballyPopupExpertBatch( boolean expertMode )
	{
		super( expertMode ? "Expert Mode" : "Simple Mode" );
		this.expertMode = expertMode;
		this.addActionListener( new MyActionListener() );
	}

	private class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			new Thread( new Runnable()
			{

				@Override
				public void run()
				{
					try
					{
						if (!SpimData2.class.isInstance( panel.getSpimData() ) )
						{
							IOFunctions.println(new Date( System.currentTimeMillis() ) + "ERROR: expected SpimData2, but got " + panel.getSpimData().getClass().getSimpleName());
							return;
						}

						final GlobalOptimizationParameters params = expertMode ? GlobalOptimizationParameters.askUserForParameters() : new GlobalOptimizationParameters();
						if ( params == null )
							return;

						final SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping;
						final boolean isSavedFaG = ( ( (StitchingExplorerPanel< ?, ? >) panel ).getSavedFilteringAndGrouping() != null );
						if ( !isSavedFaG )
						{
							FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
							filteringAndGrouping = new SpimDataFilteringAndGrouping< SpimData2 >(
									(SpimData2) panel.getSpimData() );

							if (expertMode)
							{
								filteringAndGrouping.askUserForFiltering( panelFG );
								if ( filteringAndGrouping.getDialogWasCancelled() )
									return;

								filteringAndGrouping.askUserForGrouping( panelFG );
								if ( filteringAndGrouping.getDialogWasCancelled() )
									return;
							}
							else
							{
								// use whatever is selected in panel as filters
								filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );

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
						}
						else
						{
							// FIXME: there is some generics work to be done,
							// obviously
							filteringAndGrouping = (SpimDataFilteringAndGrouping< SpimData2 >) ( (StitchingExplorerPanel< ?, ? >) panel ).getSavedFilteringAndGrouping();
						}

						final ArrayList< Pair< Group< ViewId >, Group< ViewId > > > removedInconsistentPairs = new ArrayList<>();

						GlobalOptStitcher.processGlobalOptimization( (SpimData2) panel.getSpimData(), filteringAndGrouping, params, removedInconsistentPairs, !expertMode );

						GlobalOptStitcher.removeInconsistentLinks( removedInconsistentPairs, ((SpimData2) panel.getSpimData()).getStitchingResults().getPairwiseResults() );

						final DemoLinkOverlay demoOverlay;

						if ( !StitchingExplorerPanel.class.isInstance( panel ) )
							demoOverlay = null;
						else
							demoOverlay = (( StitchingExplorerPanel<?,?> )panel).getDemoLinkOverlay();

						if ( demoOverlay != null )
						{
							synchronized ( demoOverlay )
							{
								demoOverlay.getInconsistentResults().clear();
								demoOverlay.getInconsistentResults().addAll( removedInconsistentPairs );
							}
						}
					}
					finally
					{
						// remove saved filtering and grouping once we are done here
						// regardless of whether optimization was successful or not
						( (StitchingExplorerPanel< ?, ? >) panel ).setSavedFilteringAndGrouping( null );
					}

					panel.bdvPopup().updateBDV();

				}
			} ).start();

		}

	}

}
