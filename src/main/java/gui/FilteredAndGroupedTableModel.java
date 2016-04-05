package gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.AbstractTableModel;

import algorithm.SpimDataTools;
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
import mpicbg.spim.data.sequence.ViewSetup;
import spim.fiji.spimdata.explorer.ExplorerWindow;

public class FilteredAndGroupedTableModel < AS extends AbstractSpimData< ? > > extends AbstractTableModel
{
	private static final long serialVersionUID = -6526338840427674269L;

	protected List< List< BasicViewDescription< ? > >> elements = null;
	
	final ExplorerWindow< AS, ? > panel;
	//final ArrayList< String > columnNames;
	Set<Class<? extends Entity>> groupingFactors;
	Map<Class<? extends Entity>, List<? extends Entity>> filters;
	ArrayList<Class<? extends Entity>> columnClasses;

	public void clearGroupingFactors() {
		groupingFactors.clear();
		updateColumnClasses();
	}
	
	public void addGroupingFactor(Class<? extends Entity> factor ) {
		groupingFactors.add(factor);
		updateColumnClasses();
	}
	
	public void clearFilters() {
		filters.clear();
		updateColumnClasses();		
	}
	
	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances){
		filters.put(cl, instances);
		updateColumnClasses();
		fireTableDataChanged();
	}
	
	public ArrayList<Class<? extends Entity>> defaultColumnClasses()
	{
		ArrayList<Class <? extends Entity>> res = new ArrayList<>();
		res.add(TimePoint.class);
		res.add(ViewSetup.class);
		res.add(Angle.class);
		res.add(Channel.class);
		res.add(Illumination.class);
		res.add(Tile.class);		
		return res;
	}
	
	public FilteredAndGroupedTableModel( final ExplorerWindow< AS, ? > panel )
	{
		groupingFactors = new HashSet<>();
		filters = new HashMap<>();
		this.panel = panel;
		columnClasses = defaultColumnClasses();
		
		elements();
		updateColumnClasses();
		
		//columnNames.add( "Timepoint" );
		//columnNames.add( "View Id" );
		//columnNames.addAll( panel.getSpimData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getAttributes().keySet() );
	}
	
	
	/**
	 * set the classes of the columns to the default
	 * but remove columns where a single instance is filtered out
	 */
	protected void updateColumnClasses()
	{
		columnClasses = defaultColumnClasses();
		for (Class<? extends Entity> c : filters.keySet())
		{
			// number of columns cannot change?
//			if (columnClasses.contains(c) && filters.get(c).size() <= 1){
//				columnClasses.remove(c);
//			}
		}
	}

	protected List<List< BasicViewDescription< ? extends BasicViewSetup > >> elements()
	{
		final List<BasicViewDescription< ? > > ungroupedElements =
				SpimDataTools.getFilteredViewDescriptions( panel.getSpimData().getSequenceDescription(), filters);
		final List< List< BasicViewDescription< ?  > >> elementsNew = 
				SpimDataTools.groupByAttributes(ungroupedElements, groupingFactors);

		//if ( this.elements == null )
			this.elements = elementsNew;

		return elements;
	}

	/*
	 * TODO implement this
	public void sortByColumn( final int column )
	{
		Collections.sort( elements(), new Comparator< BasicViewDescription< ? extends BasicViewSetup > >()
		{
			@Override
			public int compare(
					BasicViewDescription<? extends BasicViewSetup> arg0,
					BasicViewDescription<? extends BasicViewSetup> arg1)
			{
				if ( column == 0 )
				{
					final int diff = arg0.getTimePointId() - arg1.getTimePointId();
					return diff == 0 ? arg0.getViewSetupId() - arg1.getViewSetupId() : diff;
				}
				else if ( column == 1 )
				{
					final int diff = arg0.getViewSetupId() - arg1.getViewSetupId();
					return diff == 0 ? arg0.getTimePointId() - arg1.getTimePointId() : diff;
				}
				else
				{
					final int diff1 = arg0.getViewSetup().getAttributes().get( columnNames.get( column ) ).getId() - arg1.getViewSetup().getAttributes().get( columnNames.get( column ) ).getId();
					final int diff2 = arg0.getViewSetupId() - arg1.getViewSetupId();
					
					return diff1 == 0 ? ( diff2 == 0 ? arg0.getTimePointId() - arg1.getTimePointId() : diff2 ) : diff1;
				}
			}
		});
		
		fireTableDataChanged();
	}
	*/
	
	public List<List< BasicViewDescription< ?  >> > getElements() { return elements(); }

	@Override
	public int getColumnCount() { return columnClasses.size(); }
	
	@Override
	public int getRowCount() { return elements().size(); }

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		return false;
	}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		final List<BasicViewDescription< ? extends BasicViewSetup >> vds = elements().get( row );

		Class <? extends Entity> c = columnClasses.get(column);
		final HashSet<String> entries = new HashSet<>();
		
		for (BasicViewDescription< ? extends BasicViewSetup > vd : vds)
		{
			if ( c == TimePoint.class )
				entries.add(vd.getTimePoint().getName());
			else if ( c == ViewSetup.class )
				entries.add(vd.getViewSetup().getName());
			else
			{
				for (Entity ei : vd.getViewSetup().getAttributes().values()){
					if (c.isInstance(ei)){
						if ( ei instanceof NamedEntity )
							entries.add(((NamedEntity)ei).getName());
						else
							entries.add(Integer.toString(ei.getId()));
					}
				}
			}
		}
		
		if (entries.size() < 1)
			return "";
		else
			return String.join(", ", entries);
		
		
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnClasses.get( column ).getSimpleName();
	}
}
