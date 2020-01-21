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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.table.TableModel;

import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;

public interface ISpimDataTableModel<AS extends AbstractSpimData<?>> extends TableModel {
	
	public enum SpecialColumnType{
		INTEREST_POINT_COLUMN,
		VIEW_REGISTRATION_COLUMN
	}
	
	public int getSpecialColumn(SpecialColumnType type);
	
	public ExplorerWindow< AS, ? > getPanel();
	
	public Set<Class<? extends Entity>> getGroupingFactors();
	
	public void clearSortingFactors();

	public void addSortingFactor(Class<? extends Entity> factor);

	public void clearGroupingFactors();

	public void addGroupingFactor(Class<? extends Entity> factor);

	public void clearFilters();

	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances);

	public Map<Class<? extends Entity> , List<? extends Entity>> getFilters();

	public List<List< BasicViewDescription< ?  >> > getElements();

	public void updateElements();

	public void sortByColumn( final int column );
	
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses);

}
