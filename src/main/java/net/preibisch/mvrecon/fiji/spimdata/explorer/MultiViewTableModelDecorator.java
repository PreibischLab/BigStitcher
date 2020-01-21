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
package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.event.TableModelListener;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;

public class MultiViewTableModelDecorator < AS extends AbstractSpimData< ? > > implements ISpimDataTableModel< AS >
{
	private ISpimDataTableModel<AS> decorated;
	final ArrayList< String > columnNames;
	
	final int registrationColumn, interestPointsColumn, psfColumn;
	final ViewRegistrations viewRegistrations;
	final ViewInterestPoints viewInterestPoints;
	final PointSpreadFunctions pointSpradFunctions;
	
	public MultiViewTableModelDecorator(ISpimDataTableModel<AS> decorated) {
		this.decorated = decorated;
		this.columnNames = new ArrayList<>();
		
		columnNames.add( "#Registrations" );

		registrationColumn = decorated.getColumnCount() + columnNames.size();
		viewRegistrations = decorated.getPanel().getSpimData().getViewRegistrations();

		if ( SpimData2.class.isInstance( decorated.getPanel().getSpimData() ) )
		{
			final SpimData2 data2 = (SpimData2)decorated.getPanel().getSpimData();
			columnNames.add( "#InterestPoints" );

			interestPointsColumn = decorated.getColumnCount() + columnNames.size();
			viewInterestPoints = data2.getViewInterestPoints();

			columnNames.add( "PSF" );
			psfColumn = decorated.getColumnCount() + columnNames.size();
			pointSpradFunctions = data2.getPointSpreadFunctions();
		}
		else
		{
			viewInterestPoints = null;
			interestPointsColumn = -1;

			pointSpradFunctions = null;
			psfColumn = -1;
		}
	}
	
	@Override
	public int getRowCount() {
		return decorated.getRowCount();
	}

	@Override
	public int getColumnCount() {
		// TODO implement for real
		return decorated.getColumnCount() + columnNames.size();
	}

	@Override
	public String getColumnName(int columnIndex) {
		// TODO Auto-generated method stub
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnName(columnIndex);
		else
			return columnNames.get( columnIndex - decorated.getColumnCount() );
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		if (columnIndex < decorated.getColumnCount())
			return decorated.getColumnClass(columnIndex);
		else if ( columnIndex == psfColumn )
			return Boolean.class;
		else
			return String.class;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		
		// pass on to decorated
		if (columnIndex < decorated.getColumnCount())
			return decorated.getValueAt(rowIndex, columnIndex);

		final List< BasicViewDescription< ? > > vds = getElements().get( rowIndex );

		if ( vds.size() == 1 )
		{
			final BasicViewDescription< ? extends BasicViewSetup > vd = vds.get( 0 );

			if ( vd.isPresent() )
			{
				if ( columnIndex == registrationColumn )
					return this.viewRegistrations.getViewRegistration( vd ).getTransformList().size();
				else if ( columnIndex == interestPointsColumn && viewInterestPoints != null )
					return viewInterestPoints.getViewInterestPointLists( vd ).getHashMap().keySet().size();
				else if ( columnIndex == psfColumn )
					return pointSpradFunctions.getPointSpreadFunctions().containsKey( vd );
			}

			if ( columnIndex == psfColumn )
				return false;
			else
				return "missing";
		}
		else
		{
			int minReg = Integer.MAX_VALUE;
			int minIP = Integer.MAX_VALUE;

			int maxReg = -Integer.MAX_VALUE;
			int maxIP = -Integer.MAX_VALUE;

			boolean hasPSF = true;

			for ( final BasicViewDescription< ? > vd : vds )
			{
				if ( vd.isPresent() )
				{
					if ( columnIndex == registrationColumn )
					{
						final int numR = this.viewRegistrations.getViewRegistration( vd ).getTransformList().size();
						minReg = Math.min( minReg, numR );
						maxReg = Math.max( maxReg, numR );
					}
					else if ( columnIndex == interestPointsColumn )
					{
						if ( viewInterestPoints != null )
						{
							final int numIP = viewInterestPoints.getViewInterestPointLists( vd ).getHashMap().keySet().size();
							minIP = Math.min( minIP, numIP );
							maxIP = Math.max( maxIP, numIP );
						}
						else
						{
							minIP = maxIP = 0;
						}
					}
					else if ( columnIndex == psfColumn )
						hasPSF &= pointSpradFunctions.getPointSpreadFunctions().containsKey( vd );
				}
				else
				{
					hasPSF = false;
				}
			}

			if ( columnIndex == registrationColumn )
			{
				if ( minReg == maxReg )
					return minReg;
				else if( minReg == Integer.MAX_VALUE )
					return "missing";
				else
					return minReg + "-" + maxReg;
			}
			else if ( columnIndex == interestPointsColumn )
			{
				if ( minIP == maxIP )
					return minIP;
				else if( minIP == Integer.MAX_VALUE )
					return "missing";
				else
					return minIP + "-" + maxIP;
			}
			else if ( columnIndex == psfColumn )
			{
				return hasPSF;
			}
			else
				return "missing";
		}
	}

	@Override
	public void addTableModelListener(TableModelListener l) {decorated.addTableModelListener(l);}

	@Override
	public void removeTableModelListener(TableModelListener l) {decorated.removeTableModelListener(l);}

	@Override
	public void clearSortingFactors() {decorated.clearSortingFactors();}

	@Override
	public void addSortingFactor(Class<? extends Entity> factor) {decorated.addSortingFactor(factor);}

	@Override
	public void clearGroupingFactors() {decorated.clearGroupingFactors();}

	@Override
	public void addGroupingFactor(Class<? extends Entity> factor) {decorated.addGroupingFactor(factor);}

	@Override
	public void clearFilters() {decorated.clearFilters();}

	@Override
	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances) {decorated.addFilter(cl, instances);}

	@Override
	public List<List<BasicViewDescription<?>>> getElements() { return decorated.getElements(); }

	@Override
	public void sortByColumn(int column) {
		if (column < decorated.getColumnCount())
			decorated.sortByColumn(column);		
	}

	@Override
	public ExplorerWindow<AS, ?> getPanel() { return decorated.getPanel(); }

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex)
	{
		decorated.setValueAt( aValue, rowIndex, columnIndex );		
	}

	@Override
	public int getSpecialColumn(ISpimDataTableModel.SpecialColumnType type)
	{
		if (type == SpecialColumnType.INTEREST_POINT_COLUMN)
			return interestPointsColumn;
		else if (type == SpecialColumnType.VIEW_REGISTRATION_COLUMN)
			return registrationColumn;
		else
			return -1;
	}

	@Override
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses)
	{
		decorated.setColumnClasses( columnClasses );
		
	}

	@Override
	public Set< Class< ? extends Entity > > getGroupingFactors(){return decorated.getGroupingFactors();}

	@Override
	public Map< Class< ? extends Entity >, List< ? extends Entity > > getFilters()
	{
		return decorated.getFilters();
	}

	@Override
	public void updateElements() { decorated.updateElements(); }


}
