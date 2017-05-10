package algorithm.illuminationselection;

import java.util.Collection;

public interface ViewSelection <V>
{
	public V getBestView(Collection<? extends V> views);
}
