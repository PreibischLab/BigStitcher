package algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jfree.ui.SortButtonRenderer;

import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;

public class SpimDataTools {

	public static List<Entity> getInstancesOfAttribute(AbstractSequenceDescription<?, ?, ?> seq, Class<? extends Entity> cl) {
		Set<Entity> res = new HashSet<>();
		for (BasicViewDescription<?> vd : seq.getViewDescriptions().values()) {

				if (cl.equals(TimePoint.class))
					res.add(vd.getTimePoint());
				else if (cl.equals(ViewSetup.class))
					res.add(vd.getViewSetup());
				else
					res.add(vd.getViewSetup().getAttribute(cl));
		}

		ArrayList<Entity> resSorted = new ArrayList<>(res);
		Entity.sortById(resSorted);
		return resSorted;
	}

	public static List<BasicViewDescription<?>> getFilteredViewDescriptions(AbstractSequenceDescription<?, ?, ?> seq,
			Map<Class<? extends Entity>, List<? extends Entity>> filters) {
		ArrayList<BasicViewDescription<?>> res = new ArrayList<>();
		for (BasicViewDescription<?> vd : seq.getViewDescriptions().values()) {
			boolean matches = true;
			for (Class<? extends Entity> cl : filters.keySet()) {

				if (cl.equals(TimePoint.class)) {
					if (!filters.get(cl).contains(vd.getTimePoint()))
						matches = false;
				}
				else
				{
					Entity e = vd.getViewSetup().getAttribute(cl);
					if (!filters.get(cl).contains(e))
						matches = false;
				}
			}
			if (matches)
				res.add(vd);
		}

		return res;
	}

	public static List<List<BasicViewDescription<?>>> groupByAttributes(List<BasicViewDescription<?>> vds,
			Set<Class<? extends Entity>> groupingFactors) {
		Map<List<Entity>, List<BasicViewDescription<?>>> res = new HashMap<>();

		for (BasicViewDescription<?> vd : vds) {
			List<Entity> key = new ArrayList<>();

			if (!groupingFactors.contains(TimePoint.class)) {
				key.add(vd.getTimePoint());
			}

			for (Entity e : vd.getViewSetup().getAttributes().values()) {
				if (!groupingFactors.contains(e.getClass()))
					key.add(e);
			}

			if (!res.containsKey(key))
				res.put(key, new ArrayList<BasicViewDescription<?>>());

			res.get(key).add(vd);
		}

		return new ArrayList<List<BasicViewDescription<?>>>(res.values());
	}

	/**
	 * get a Comparator to sort ViewDescriptions by the ID of the Attribute of
	 * class cl
	 * 
	 * @param cl
	 * @return
	 */
	public static Comparator<BasicViewDescription<?>> getVDComparator(final Class<? extends Entity> cl) {
		return new Comparator<BasicViewDescription<?>>() {

			@Override
			public int compare(BasicViewDescription<?> o1, BasicViewDescription<?> o2) {
				if (cl == ViewSetup.class)
					return o1.getViewSetupId() - o2.getViewSetupId();
				else if (cl == TimePoint.class)
					return o1.getTimePointId() - o2.getTimePointId();
				else
					return o1.getViewSetup().getAttribute(cl).getId() - o2.getViewSetup().getAttribute(cl).getId();
			}
		};
	}

	/**
	 * get a Comparator of Lists of ViewDescriptions the first elements of the
	 * lists are compared by the ID of their Attribute of class cl (it is a good
	 * idea to sort the lists themselves first)
	 * 
	 * @param cl
	 * @return
	 */
	public static Comparator<List<BasicViewDescription<?>>> getVDListComparator(final Class<? extends Entity> cl) {
		return new Comparator<List<BasicViewDescription<?>>>() {

			@Override
			public int compare(List<BasicViewDescription<?>> o1, List<BasicViewDescription<?>> o2) {
				return getVDComparator(cl).compare(o1.get(0), o2.get(0));
			}
		};
	}

}
