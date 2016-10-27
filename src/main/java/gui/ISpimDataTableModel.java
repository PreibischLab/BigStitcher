package gui;

import java.util.List;

import javax.swing.table.TableModel;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import spim.fiji.spimdata.explorer.ExplorerWindow;

public interface ISpimDataTableModel<AS extends AbstractSpimData<?>> extends TableModel {
	
	public enum SpecialColumnType{
		INTEREST_POINT_COLUMN,
		VIEW_REGISTRATION_COLUMN
	}
	
	public int getSpecialColumn(SpecialColumnType type);
	
	public ExplorerWindow< AS, ? > getPanel();
	
	public void clearSortingFactors();

	public void addSortingFactor(Class<? extends Entity> factor);

	public void clearGroupingFactors();

	public void addGroupingFactor(Class<? extends Entity> factor);

	public void clearFilters();

	public void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances);
	
	public List<List< BasicViewDescription< ?  >> > getElements();
	
	public void sortByColumn( final int column );
	
	public void setColumnClasses(List< Class< ? extends Entity > > columnClasses);

}