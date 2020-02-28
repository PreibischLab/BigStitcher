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
package net.preibisch.stitcher.algorithm;


import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionGUI;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimDataTools;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.stitcher.algorithm.GroupedViewAggregator.ActionType;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;

public class SpimDataFilteringAndGrouping< AS extends AbstractSpimData< ? > >  extends SpimDataFilteringAndGroupingFunctions<AS>
{
	public SpimDataFilteringAndGrouping(AS data) {
		super(data);
	}
	
	// convenience method if we do not know selected views
		public SpimDataFilteringAndGrouping< AS> askUserForFiltering()
		{
			// select all
			return askUserForFiltering( data.getSequenceDescription().getViewDescriptions().values() );
		}

		// convenience method if have a panel (which can give us selected views)
		public SpimDataFilteringAndGrouping< AS> askUserForFiltering(FilteredAndGroupedExplorerPanel< AS, ?> panel)
		{
			List< BasicViewDescription< ? extends BasicViewSetup > > views;
			
			if (panel instanceof GroupedRowWindow)
			{
				Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > selectedRowsGroups = ((GroupedRowWindow)panel).selectedRowsGroups();
				views = selectedRowsGroups.stream().reduce( new ArrayList<>(), (x, y) -> {x.addAll(y); return x;} );
			}
			else
				views = panel.selectedRows();
			
			SpimDataFilteringAndGrouping< AS > res = askUserForFiltering( views );
			return res;
			
		}

	public SpimDataFilteringAndGrouping< AS> askUserForFiltering(Collection<? extends BasicViewDescription< ? extends BasicViewSetup > > views)
	{

		GenericDialogPlus gdp1 = new GenericDialogPlus( "Select Views To Process" );
		
		final String msg = ( "<html><strong>Select wether you want to process all instances of an attribute <br>"
				+ " or just the currently selected Views</strong> </html>" ) ;
		FileListDatasetDefinitionGUI.addMessageAsJLabel(msg, gdp1);
		
		String[] viewSelectionChoices = new String[] {"all", "selected"};
		
		for (Class<? extends Entity> cl : entityClasses)
		{
			boolean allSelected = (getInstancesOfAttribute(views, cl ).containsAll(SpimDataTools.getInstancesOfAttribute( data.getSequenceDescription(), cl )));
			gdp1.addChoice( cl.getSimpleName(), viewSelectionChoices, allSelected ? viewSelectionChoices[0] : viewSelectionChoices[1] );			
		}
		
		gdp1.showDialog();
		if (gdp1.wasCanceled())
		{
			dialogWasCancelled = true;
			return this;
		}
		
		for (Class<? extends Entity> cl : entityClasses)
		{
			boolean useCurrent = gdp1.getNextChoiceIndex() == 1;
			if (useCurrent)
				addFilter( cl, new ArrayList<>(getInstancesOfAttribute(views, cl )) );
		}
		
		return this;
	
	}

	public SpimDataFilteringAndGrouping< AS> askUserForGrouping()
	{
		// use the current filtering as preset
		return askUserForGrouping( SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), getFilters() ), new ArrayList<>(), new HashSet<>() );
	}

	public SpimDataFilteringAndGrouping< AS> askUserForGrouping( FilteredAndGroupedExplorerPanel< AS, ?> panel)
	{
		List< BasicViewDescription< ? extends BasicViewSetup > > views;

		if (panel instanceof GroupedRowWindow)
		{
			Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > selectedRowsGroups = ((GroupedRowWindow)panel).selectedRowsGroups();
			views = selectedRowsGroups.stream().reduce( new ArrayList<>(), (x, y) -> {x.addAll(y); return x;} );
		}
		else
			views = panel.selectedRows();

		final HashSet< Class<? extends Entity> > comparisonsRequested = new HashSet<>();
		if (StitchingExplorerPanel.class.isInstance( panel ) )
		{
			if (!panel.channelsGrouped())
				comparisonsRequested.add( Channel.class );
			if (!panel.illumsGrouped())
				comparisonsRequested.add( Illumination.class );
			if (!panel.tilesGrouped())
				comparisonsRequested.add( Tile.class );
		}
		return askUserForGrouping( views, panel.getTableModel().getGroupingFactors(), comparisonsRequested);
	}

	public SpimDataFilteringAndGrouping< AS> askUserForGrouping( 
					Collection<? extends BasicViewDescription< ? > > views,
					Collection<Class<? extends Entity>> groupingFactors,
					Collection<Class<? extends Entity>> comparisionFactors)
	{
		GenericDialogPlus gdp2 = new GenericDialogPlus( "Select How to Process Views" );
		
		final String msg = ( "<html><strong>Select how to process the different attributes </strong> <br>"
				+ "<strong>COMPARE:</strong> calculate pairwise shift between instances <br>"
				+ "<strong>GROUP:</strong> combine all instances into one view<br>"
				+ "<strong>TREAT INDIVIDUALLY:</strong> process instances one after the other, but do not compare or group <br> </html>");
		FileListDatasetDefinitionGUI.addMessageAsJLabel(msg, gdp2);

		String[] computeChoices = new String[] {"compare", "group", "treat individually"};
		for (Class<? extends Entity> cl : entityClasses)
		{
			boolean isGrouping = groupingFactors.contains( cl );
			boolean isFilterOrSingleton = getFilters().keySet().contains( cl ) || getInstancesOfAttribute(views, cl ).size() <= 1;
			boolean isComparison = comparisionFactors.contains( cl );
			int idx = isGrouping ? 1 : isComparison || !isFilterOrSingleton ? 0 : 2;
			gdp2.addChoice( "How_to_treat_" + cl.getSimpleName() + "s:", computeChoices, computeChoices[idx] );
		}

		gdp2.showDialog();
		if (gdp2.wasCanceled())
		{
			dialogWasCancelled = true;
			return this;
		}

		for (Class<? extends Entity> cl : entityClasses)
		{
			int selection = gdp2.getNextChoiceIndex();
			if (selection == 0)
				addComparisonAxis( cl );
			else if (selection == 1)
				addGroupingFactor( cl );
			else
				addApplicationAxis( cl );
		}

		return this;
	}

	/**
	 * ask user how to aggregate grouped views (for all the entity classes we group by)
	 * if a default choice for a class is provided, the user will not be asked for that class
	 * @param defaultChoices pre-set choices for specific classes (NB: may not be 'pick specific')
	 * @return self
	 */
	public SpimDataFilteringAndGrouping< AS> askUserForGroupingAggregator(final Map<Class<? extends Entity>, ActionType> defaultChoices)
	{
		// ask what to do with grouped views
		GenericDialogPlus gdp = new GenericDialogPlus( "Select How to Treat Grouped Views" );		

//		FileListDatasetDefinition.addMessageAsJLabel("<html><strong>Select which instances of attributes to use in Grouped Views </strong> <br></html>", gdp3);
		gdp.addMessage( "Please specify how to deal with grouped Views.", new Font( Font.SANS_SERIF, Font.BOLD, 14 ), GUIHelper.neutral );

		// filter first
		final List<BasicViewDescription< ? > > ungroupedElements =
						SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), getFilters());
		// then group
		final List< Group< BasicViewDescription< ?  > >> groupedElements = 
						Group.combineBy( ungroupedElements, getGroupingFactors());

		boolean dialogNecessary = false;
		for (Class<? extends Entity> cl : getGroupingFactors())
		{
			if (defaultChoices != null && defaultChoices.containsKey( cl ))
				continue;

			List<String> selection = new ArrayList<>();
			selection.add( "Average " + cl.getSimpleName() +"s" );
//			selection.add(  "pick brightest" );
			List< ? extends Entity > instancesInAllGroups = getInstancesInAllGroups( groupedElements, cl );

			// we only have one instance of entity, do not ask for aggregation in that case
			if (instancesInAllGroups.size() < 2)
				continue;
			// we have more than one instance of any entity -> we have to display dialog
			dialogNecessary = true;

			instancesInAllGroups.forEach( ( e ) -> 
			{
				if (e instanceof NamedEntity)
					selection.add( "use " + cl.getSimpleName() + " " + ((NamedEntity)e).getName());
				else
					selection.add( "use " + cl.getSimpleName() + " " + Integer.toString( e.getId() ));
			});
			
			String[] selectionArray = selection.toArray( new String[selection.size()] );
			gdp.addChoice( cl.getSimpleName() + "s:", selectionArray, selectionArray[0] );
		}

		if (dialogNecessary)
			gdp.showDialog();
			if (gdp.wasCanceled())
			{
				dialogWasCancelled = true;
				return this;
			}

		for (Class<? extends Entity> cl : getGroupingFactors())
		{
			if (defaultChoices != null && defaultChoices.containsKey( cl ))
			{
				getGroupedViewAggregator().addAction(defaultChoices.get( cl ), cl, null);
				continue;
			}

			List< ? extends Entity > instancesInAllGroups = getInstancesInAllGroups( groupedElements, cl );

			// we have only one instance -> "average" (i.e. just keep the one view)
			if (instancesInAllGroups.size() < 2)
			{
				getGroupedViewAggregator().addAction( ActionType.AVERAGE, cl, null );
				continue;
			}

			int nextChoiceIndex = gdp.getNextChoiceIndex();
			if (nextChoiceIndex == 0)
				getGroupedViewAggregator().addAction( ActionType.AVERAGE, cl, null );
//			else if (nextChoiceIndex == 1)
//				getGroupedViewAggregator().addAction( ActionType.PICK_BRIGHTEST, cl, null );
			else
				getGroupedViewAggregator().addAction( ActionType.PICK_SPECIFIC, cl, instancesInAllGroups.get( nextChoiceIndex - 1 ) );
		}
		
		return this;
	}

	public SpimDataFilteringAndGrouping< AS> askUserForGroupingAggregator()
	{
		return askUserForGroupingAggregator( new HashMap<>() );
	}
}
