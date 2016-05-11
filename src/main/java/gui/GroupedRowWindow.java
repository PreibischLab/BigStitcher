package gui;

import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;

public interface GroupedRowWindow
{
	public Collection<List< BasicViewDescription< ? extends BasicViewSetup > >> selectedRowsGroups();
	public List<List< ViewId >> selectedRowsViewIdGroups();
}
