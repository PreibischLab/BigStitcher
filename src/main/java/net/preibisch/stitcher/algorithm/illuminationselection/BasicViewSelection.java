package net.preibisch.stitcher.algorithm.illuminationselection;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;

public abstract class BasicViewSelection <V extends ViewId> implements ViewSelection< V >
{
	protected AbstractSequenceDescription< ?, ?, ? > sd;
	
	public BasicViewSelection(AbstractSequenceDescription< ?, ?, ? > sd)
	{
		this.sd = sd;
	}
	
	public BasicViewSelection(AbstractSpimData< AbstractSequenceDescription<?,?,?> > data)
	{
		this.sd = data.getSequenceDescription();
	}

}
