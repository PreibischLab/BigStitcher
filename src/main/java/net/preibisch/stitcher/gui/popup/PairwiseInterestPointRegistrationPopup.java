/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2024 Big Stitcher developers.
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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;
import net.preibisch.stitcher.gui.StitchingUIHelper;
import net.preibisch.stitcher.plugin.Calculate_Pairwise_Shifts;

public class PairwiseInterestPointRegistrationPopup extends JMenu implements ExplorerWindowSetable
{

	private static final long serialVersionUID = -396274656320474433L;
	ExplorerWindow< ? > panel;

	private JMenuItem withDetection;
	private JMenuItem withoutDetection;
	private JCheckBoxMenuItem expertGrouping;

	private boolean wizardMode;

	public PairwiseInterestPointRegistrationPopup(String description, boolean wizardMode )
	{
		super( description );
		this.addActionListener( new MyActionListener(false) );

		this.wizardMode = wizardMode;

		withDetection = new JMenuItem( "With new Interest Points" );
		withoutDetection = new JMenuItem( "With existing Interest Points" );
		expertGrouping = new JCheckBoxMenuItem( "Show expert grouping Options", false );

		withDetection.addActionListener( new MyActionListener( false ) );
		withoutDetection.addActionListener( new MyActionListener( true ) );

		this.add( withDetection );
		this.add( withoutDetection );
		this.addSeparator();
		this.add( expertGrouping );

		this.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected(MenuEvent e)
			{
				if (SpimData2.class.isInstance( panel.getSpimData() ))
				{
					final List< ViewId > selectedViews = ((GroupedRowWindow)panel).selectedRowsViewIdGroups().stream().reduce( new ArrayList<>(), (x,y) -> {x.addAll( y ); return x;} );
					boolean allHaveIPs = true;
					final ViewInterestPoints viewInterestPoints = ((SpimData2)panel.getSpimData()).getViewInterestPoints();
					for (ViewId vid : selectedViews)
						if (panel.getSpimData().getSequenceDescription().getMissingViews() != null &&
							!panel.getSpimData().getSequenceDescription().getMissingViews().getMissingViews().contains( vid ))
						{
							if (!viewInterestPoints.getViewInterestPoints().containsKey( vid ) ||
								viewInterestPoints.getViewInterestPoints().get( vid ).getHashMap().size() < 1)
							{
								allHaveIPs = false;
								break;
							}
						}
					withoutDetection.setEnabled( allHaveIPs );
				}
			}

			@Override
			public void menuDeselected(MenuEvent e){}

			@Override
			public void menuCanceled(MenuEvent e){}

		} );
	}

	@Override
	public JComponent setExplorerWindow( final ExplorerWindow< ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		private boolean existingInterestPoints;

		public MyActionListener(boolean existingInterestPoints)
		{
			this.existingInterestPoints = existingInterestPoints;
		}

		@Override
		public void actionPerformed(ActionEvent e)
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
				FilteredAndGroupedExplorerPanel< SpimData2 > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2 >) panel;
				SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping = 	new SpimDataFilteringAndGrouping<>( (SpimData2) panel.getSpimData() );

				if (!expertGrouping.isSelected())
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

				if (!expertGrouping.isSelected())
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

				boolean allViews2D = StitchingUIHelper.allViews2D( filteringAndGrouping.getFilteredViews() );
				if (allViews2D)
				{
					IOFunctions.println( "Interest point-based registration is currenty not supported for 2D: " + this.getClass().getSimpleName() );
					return;
				}


				if (Calculate_Pairwise_Shifts.processInterestPoint( data, filteringAndGrouping, existingInterestPoints ))
					if (wizardMode)
					{
						// ask user if they want to switch to preview mode
						if (panel instanceof StitchingExplorerPanel)
						{
							final int choice = JOptionPane.showConfirmDialog( (Component) panel, "Pairwise shift calculation done. Switch to preview mode?", "Preview Mode", JOptionPane.YES_NO_OPTION );
							if (choice == JOptionPane.YES_OPTION)
							{
								((StitchingExplorerPanel< ? >) panel).setSavedFilteringAndGrouping( filteringAndGrouping );
								((StitchingExplorerPanel< ? >) panel).togglePreviewMode(false);
							}
						}
					}

			}).start();

			panel.updateContent();
		}
	}

}
