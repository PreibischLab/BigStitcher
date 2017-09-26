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
package net.preibisch.stitcher.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.table.AbstractTableModel;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;

public class StitchingExplorerTableModel< AS extends AbstractSpimData< ? > > extends AbstractTableModel
{
	private static final long serialVersionUID = -6526338840427674269L;

	protected ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elements = null;
	
	final StitchingExplorerPanel< AS, ? > panel;
	final ArrayList< String > columnNames;

	public StitchingExplorerTableModel( final StitchingExplorerPanel< AS, ? > panel )
	{
		this.panel = panel;
		columnNames = new ArrayList< String >();
		columnNames.add( "Timepoint" );
		columnNames.add( "View Id" );
		columnNames.addAll( panel.getSpimData().getSequenceDescription().getViewSetupsOrdered().get( 0 ).getAttributes().keySet() );
	}

	protected ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elements()
	{
		final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > elementsNew = new ArrayList< BasicViewDescription< ? extends BasicViewSetup > >();

		for ( final TimePoint t : panel.getSpimData().getSequenceDescription().getTimePoints().getTimePointsOrdered() )
			for ( final BasicViewSetup v : panel.getSpimData().getSequenceDescription().getViewSetupsOrdered() )
			{
				final ViewId viewId = new ViewId( t.getId(), v.getId() );
				final BasicViewDescription< ? > viewDesc = panel.getSpimData().getSequenceDescription().getViewDescriptions().get( viewId );

				if ( viewDesc.isPresent() )
					elementsNew.add( viewDesc );
			}

		if ( this.elements == null || this.elements.size() != elementsNew.size() )
			this.elements = elementsNew;

		return elements;
	}

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
	
	public ArrayList< BasicViewDescription< ? extends BasicViewSetup > > getElements() { return elements(); }

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
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
		final BasicViewDescription< ? extends BasicViewSetup > vd = elements().get( row );

		if ( column == 0 )
			return vd.getTimePoint().getId();
		else if ( column == 1 )
			return vd.getViewSetupId();
		else
		{
			final Entity e = vd.getViewSetup().getAttributes().get( columnNames.get( column ) );

			if ( e instanceof NamedEntity )
				return ((NamedEntity)e).getName() + " (id = " + e.getId() + ")";
			else
				return e.getId() + " (no name available)";
		}
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}
}
