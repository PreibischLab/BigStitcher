package algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.TimePoint;

public class SpimDataTools {
	
	public static List<BasicViewDescription<?>> getFilteredViewDescriptions(AbstractSequenceDescription<?, ?, ?> seq,
													Map<Class<? extends Entity>, List<? extends Entity>> filters)
	{
		ArrayList<BasicViewDescription<?>> res = new ArrayList<>();
		for (BasicViewDescription<?> vd : seq.getViewDescriptions().values()){
			boolean matches = true;
			for (Class<? extends Entity> cl : filters.keySet()){
				
				if (cl.equals(TimePoint.class)){
					if (! filters.get(cl).contains(vd.getTimePoint()))
						matches = false;
				}				
				
				Entity e = vd.getViewSetup().getAttribute(cl);
				if(! filters.get(cl).contains(e))
					matches = false;
			}
			if (matches)
				res.add(vd);
		}	
		
		return res;
	}
	
	public static List<List<BasicViewDescription< ? >>> groupByAttributes(List<BasicViewDescription< ? >> vds, Set<Class<? extends Entity>> groupingFactors)
	{
		Map<List<Entity>, List<BasicViewDescription< ? >>> res = new HashMap<>();
		
		for (BasicViewDescription< ? > vd : vds)
		{
			List<Entity> key = new ArrayList<>();
			
			if (! groupingFactors.contains(TimePoint.class)){
				key.add(vd.getTimePoint());
			}
			
			for (Entity e : vd.getViewSetup().getAttributes().values()){
				if (! groupingFactors.contains(e.getClass()))
					key.add(e);
			}
			
			if (! res.containsKey(key))
				res.put(key, new ArrayList<BasicViewDescription< ? >>());
			
			res.get(key).add(vd);
		}
		
		return new ArrayList<List<BasicViewDescription< ? >>>(res.values());		
	}

}
