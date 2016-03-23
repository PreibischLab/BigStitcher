package gui;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;

public interface SelectedViewDescriptionListener< AS extends AbstractSpimData< ? > >
{
	public void seletedViewDescription( BasicViewDescription< ? extends BasicViewSetup > viewDescription );
	public void updateContent( final AS data );
	public void save();
	public void quit();
}
