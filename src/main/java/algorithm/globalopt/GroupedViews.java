package algorithm.globalopt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

public class GroupedViews extends ViewId
{
	final List<? extends ViewId > group;

	/**
	 * A group of ViewIds that represents itself as the first ViewId
	 * 
	 * @param group
	 */
	public GroupedViews( final List<? extends ViewId > group )
	{
		super( group.get( 0 ).getTimePointId(), group.get( 0 ).getViewSetupId() );

		this.group = group;
	}


	public List<? extends ViewId > getViewIds() { return group; }

}
