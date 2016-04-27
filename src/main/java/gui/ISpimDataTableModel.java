package gui;

import java.util.List;

import javax.swing.table.TableModel;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import spim.fiji.spimdata.explorer.ExplorerWindow;

public interface ISpimDataTableModel<AS extends AbstractSpimData<?>> extends TableModel {
	
	public ExplorerWindow< AS, ? > getPanel();
	
	void clearSortingFactors();

	void addSortingFactor(Class<? extends Entity> factor);

	void clearGroupingFactors();

	void addGroupingFactor(Class<? extends Entity> factor);

	void clearFilters();

	void addFilter(Class<? extends Entity> cl, List<? extends Entity> instances);
	
	public List<List< BasicViewDescription< ?  >> > getElements();
	
	public void sortByColumn( final int column );

}