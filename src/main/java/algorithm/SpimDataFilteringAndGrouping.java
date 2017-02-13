package algorithm;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JLabel;

import algorithm.GroupedViewAggregator.ActionType;
import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.SpimDataTools;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.GroupedRowWindow;

public class SpimDataFilteringAndGrouping < AS extends AbstractSpimData< ? > >
{
	
	static List<Class<? extends Entity>> entityClasses = new ArrayList<>();
	static 
	{
		entityClasses.add( TimePoint.class );
		entityClasses.add( Channel.class );
		entityClasses.add( Illumination.class );
		entityClasses.add( Angle.class );
		entityClasses.add( Tile.class );
	}
	
	Set<Class<? extends Entity>> groupingFactors; // attributes by which views are grouped first
	Set<Class<? extends Entity>> axesOfApplication; // axes of application -> we want to process within single instances (e.g. each TimePoint separately)
	Set<Class<? extends Entity>> axesOfComparison; // axes we want to compare
	Map<Class<? extends Entity>, List<? extends Entity>> filters; // filters for the different attributes
	AS data;
	GroupedViewAggregator gva;
	
	public SpimDataFilteringAndGrouping(AS data)
	{
		groupingFactors = new HashSet<>();
		axesOfApplication = new HashSet<>();
		axesOfComparison = new HashSet<>();
		filters = new HashMap<>();
		this.data = data;
		gva = new GroupedViewAggregator();
	}
	
	public AS getSpimData()
	{
		return data;
	}
	
	public GroupedViewAggregator getGroupedViewAggregator()
	{
		return gva;
	}
	
	public void addGroupingFactor(Class<? extends Entity> factor ) {
		groupingFactors.add(factor);
	}
		
	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances){
		filters.put(cl, instances);
	}
	
	public void addApplicationAxis(Class<? extends Entity> axis ) {
		axesOfApplication.add(axis);
	}
	
	public void addComparisonAxis(Class<? extends Entity> axis ) {
		axesOfComparison.add(axis);
	}
	
	
	public void clearGroupingFactors()
	{
		groupingFactors.clear();
	}
	public void clearFilters()
	{
		filters.clear();
	}
	public void clearApplicationAxes()
	{
		axesOfApplication.clear();
	}
	public void clearComparisonAxes()
	{
		axesOfComparison.clear();
	}
	
	public Set< Class< ? extends Entity > > getGroupingFactors()
	{
		return groupingFactors;
	}


	public Set< Class< ? extends Entity > > getAxesOfApplication()
	{
		return axesOfApplication;
	}


	public Set< Class< ? extends Entity > > getAxesOfComparison()
	{
		return axesOfComparison;
	}


	public Map< Class< ? extends Entity >, List< ? extends Entity > > getFilters()
	{
		return filters;
	}
	
	public List<BasicViewDescription< ? > > getFilteredViews()
	{
		return SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), filters);
	}
	
	public List< List< BasicViewDescription< ?  > >> getGroupedViews(boolean filtered)
	{
		final List<BasicViewDescription< ? > > ungroupedElements =
				SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), filtered? filters : new HashMap<>());
		return SpimDataTools.groupByAttributes(ungroupedElements, groupingFactors);
	}
	
	public List<Pair<List< BasicViewDescription< ? extends BasicViewSetup > >, List< BasicViewDescription< ? extends BasicViewSetup >>>> getComparisons()
	{
		final List<Pair<List< BasicViewDescription< ? extends BasicViewSetup > >, List< BasicViewDescription< ? extends BasicViewSetup >>>> res = new ArrayList<>();
		
		// filter first
		final List<BasicViewDescription< ? > > ungroupedElements =
				SpimDataTools.getFilteredViewDescriptions( data.getSequenceDescription(), filters);
		// then group
		final List< List< BasicViewDescription< ?  > >> groupedElements = 
				SpimDataTools.groupByAttributes(ungroupedElements, groupingFactors);
		
		// go through possible group pairs
		for (int i = 0; i < groupedElements.size(); ++i)
			for(int j = i+1; j < groupedElements.size(); ++j)
			{
				// we will want to process the pair if:
				// the groups do not differ along an axis along which we want to treat elements individually (e.g. Angle)
				// but they differ along an axis that we want to register (e.g Tile)
				if (!groupsDifferByAny( groupedElements.get( i ), groupedElements.get( j ), axesOfApplication ) 
						&& groupsDifferByAny( groupedElements.get( i ), groupedElements.get( j ), axesOfComparison ))
					res.add(new ValuePair<>(groupedElements.get( i ), groupedElements.get( j )));
			}
	
		return res;		
	}
	
	private static boolean groupsDifferByAny(List< BasicViewDescription< ?  > > vds1, List< BasicViewDescription< ?  > > vds2, Set<Class<? extends Entity>> entities)
	{
		for (Class<? extends Entity> entity : entities)
		{
			for ( BasicViewDescription< ?  > vd1 : vds1)
				for ( BasicViewDescription< ?  > vd2 : vds2)
				{
					if (entity == TimePoint.class)
					{
						if (!vd1.getTimePoint().equals( vd2.getTimePoint() ))
							return true;
					}
					else
					{
						if (!vd1.getViewSetup().getAttribute( entity ).equals( vd2.getViewSetup().getAttribute( entity ) ) )
							return true;
					}
					
				}
		}
		
		return false;
	}


	public static Set<Entity> getInstancesOfAttributeGrouped(Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > vds, Class<? extends Entity> cl)
	{
		Set<Entity> res = new HashSet<>();
		for (List< BasicViewDescription< ? extends BasicViewSetup > > vdl1 : vds)
			for (BasicViewDescription< ? extends BasicViewSetup > vd : vdl1)
				if (cl == TimePoint.class)
					res.add( vd.getTimePoint() );
				else
					res.add( vd.getViewSetup().getAttribute( cl ) );
		return res;
	}
	
	public static List<? extends Entity> getInstancesInAllGroups(Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > vds, Class<? extends Entity> cl)
	{
		Set<Entity> res = new HashSet<>();
		Iterator< List< BasicViewDescription< ? extends BasicViewSetup > > > it = vds.iterator();
		for (BasicViewDescription< ? extends BasicViewSetup > vd : it.next())
			if (cl == TimePoint.class)
				res.add( vd.getTimePoint() );
			else
				res.add( vd.getViewSetup().getAttribute( cl ) );
		
		it.forEachRemaining( ( vdli ) -> 
		{
			Set<Entity> resI = new HashSet<>();
			for (BasicViewDescription< ? extends BasicViewSetup > vd : vdli)
				if (cl == TimePoint.class)
					resI.add( vd.getTimePoint() );
				else
					resI.add( vd.getViewSetup().getAttribute( cl ) );
			
			for (Entity e : res)
			{
				if(!resI.contains( e ))
					res.remove( e );
			}
		}
		);
		return new ArrayList<>(res);
	}
	
	
	public static <AS extends AbstractSpimData< ? > > SpimDataFilteringAndGrouping< AS> askUserForGrouping(FilteredAndGroupedExplorerPanel< AS, ?> panel)
	{
		
		
		SpimDataFilteringAndGrouping< AS > res = new SpimDataFilteringAndGrouping< AS >( panel.getSpimData() );
		
		Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > selectedRowsGroups = ((GroupedRowWindow)panel).selectedRowsGroups();		
		GenericDialogPlus gdp1 = new GenericDialogPlus( "Select Views To Process" );
		
		gdp1.addMessage( "<html><strong>Select wether you want to process all instances of an attribute <br>"
				+ " or just the currently selected Views</strong> <br>"
				+ "NOTE: use 'all' or a specific ID (TODO) if you plan to call this from a macro.</html>" ) ;
		
		String[] viewSelectionChoices = new String[] {"all", "selected"};
		
		for (Class<? extends Entity> cl : entityClasses)
		{
			boolean allSelected = (getInstancesOfAttributeGrouped( selectedRowsGroups, cl ).containsAll(SpimDataTools.getInstancesOfAttribute( panel.getSpimData().getSequenceDescription(), cl )));
			gdp1.addChoice( cl.getSimpleName(), viewSelectionChoices, allSelected ? viewSelectionChoices[0] : viewSelectionChoices[1] );			
		}
		
		gdp1.showDialog();
		if (gdp1.wasCanceled())
			return null;
		
		for (Class<? extends Entity> cl : entityClasses)
		{
			boolean useCurrent = gdp1.getNextChoiceIndex() == 1;
			if (useCurrent)
				res.addFilter( cl, new ArrayList<>(getInstancesOfAttributeGrouped( selectedRowsGroups, cl )) );
		}
		
		
		
		
		
		GenericDialogPlus gdp2 = new GenericDialogPlus( "Select How to Process Views" );
		
		gdp2.addMessage( "<html><strong>Select how to process the different attributes </strong> <br>"
				+ "<strong>COMPARE:</strong> calculate pairwise shift between instances <br>"
				+ "<strong>GROUP:</strong> combine all instances into one view<br>"
				+ "<strong>TREAT INDIVIDUALLY:</strong> process instances one after the other, but do not compare or group <br> </html>");

		
		System.err.println( panel.getTableModel().getFilters() );
		
		String[] computeChoices = new String[] {"compare", "group", "treat individually"};
		for (Class<? extends Entity> cl : entityClasses)
		{
			boolean isGrouping = panel.getTableModel().getGroupingFactors().contains( cl );
			boolean isFilterOrSingleton = panel.getTableModel().getFilters().keySet().contains( cl ) || getInstancesOfAttributeGrouped( selectedRowsGroups, cl ).size() <= 1;
			int idx = isGrouping ? 1 : isFilterOrSingleton ? 2 : 0;
			gdp2.addChoice( cl.getSimpleName(), computeChoices, computeChoices[idx] );
		}
		
		gdp2.showDialog();
		if (gdp2.wasCanceled())
			return null;
		
		for (Class<? extends Entity> cl : entityClasses)
		{
			int selection = gdp2.getNextChoiceIndex();
			if (selection == 0)
				res.addComparisonAxis( cl );
			else if (selection == 1)
				res.addGroupingFactor( cl );
			else
				res.addApplicationAxis( cl );
		}
		
		
		
		// ask what to do with grouped views
		
		GenericDialogPlus gdp3 = new GenericDialogPlus( "Select How to Treat Grouped Views" );
		
		gdp3.addMessage( "<html><strong>Select which instances of attributes to use in Grouped Views </strong> <br></html>");
		
		
		// filter first
		final List<BasicViewDescription< ? > > ungroupedElements =
						SpimDataTools.getFilteredViewDescriptions( panel.getSpimData().getSequenceDescription(), res.getFilters());
		// then group
		final List< List< BasicViewDescription< ?  > >> groupedElements = 
						SpimDataTools.groupByAttributes(ungroupedElements, res.getGroupingFactors());
		
		for (Class<? extends Entity> cl : res.getGroupingFactors())
		{
			List<String> selection = new ArrayList<>();
			selection.add( "average" );
			selection.add(  "pick prightest" );
			List< ? extends Entity > instancesInAllGroups = getInstancesInAllGroups( groupedElements, cl );
			instancesInAllGroups.forEach( ( e ) -> 
			{
				if (e instanceof NamedEntity)
					selection.add( ((NamedEntity)e).getName());
				else
					selection.add( Integer.toString( e.getId() ));
			});
			
			String[] selectionArray = selection.toArray( new String[selection.size()] );
			gdp3.addChoice( cl.getSimpleName(), selectionArray, selectionArray[0] );
		}
		
		gdp3.showDialog();
		if (gdp3.wasCanceled())
			return null;
		
		for (Class<? extends Entity> cl : res.getGroupingFactors())
		{
			List< ? extends Entity > instancesInAllGroups = getInstancesInAllGroups( groupedElements, cl );
			int nextChoiceIndex = gdp3.getNextChoiceIndex();
			if (nextChoiceIndex == 0)
				res.getGroupedViewAggregator().addAction( ActionType.AVERAGE, cl, null );
			else if (nextChoiceIndex == 1)
				res.getGroupedViewAggregator().addAction( ActionType.PICK_BRIGHTEST, cl, null );
			else
				res.getGroupedViewAggregator().addAction( ActionType.PICK_SPECIFIC, cl, instancesInAllGroups.get( nextChoiceIndex - 2 ) );
		}
		
		System.out.println("Filters: " + res.getFilters() );
		System.out.println("Grouping: " + res.getGroupingFactors() );
		System.out.println("Application: " + res.getAxesOfApplication() );
		System.out.println("Comparison: " + res.getAxesOfComparison() );
		
		System.out.println( res.getComparisons() );
		System.out.println( res.getGroupedViewAggregator() );
		
		return res;
	}
	
	
	

}
