package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import algorithm.FilteredStitchingResults;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class SimpleRemoveLinkPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	StitchingResults results;
	
	@Override
	public void setStitchingResults(StitchingResults res){this.results = res;}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	public SimpleRemoveLinkPopup()
	{
		super("Filter Links by parameters ...");
		this.addActionListener( new ActionListener()
		{
			
			@Override
			public void actionPerformed(ActionEvent e)
			{
				final GenericDialog gd = new GenericDialog("Link Filters");
				
				gd.addCheckbox("filter by correlation coefficient", false);
				gd.addNumericField("min R", 0.0, 2);
				gd.addNumericField("max R", 1.0, 2);
				
				gd.addCheckbox("filter by shift in each dimension", false);
				gd.addNumericField("max shift in X", 0.0, 2);
				gd.addNumericField("max shift in Y", 0.0, 2);
				gd.addNumericField("max shift in Z", 0.0, 2);
				
				gd.addCheckbox("filter by total shift magnitude", false);
				gd.addNumericField("max displacement", 0.0, 2);
				
				gd.addCheckbox("remove only links between selected views", true);
				
				gd.showDialog();
				
				if (gd.wasCanceled())
					return;
				
				final boolean doCorrelationFilter = gd.getNextBoolean();
				final double minR = gd.getNextNumber();
				final double maxR = gd.getNextNumber();
				
				final boolean doAbsoluteShiftFilter = gd.getNextBoolean();
				final double[] maxShift = new double[]{gd.getNextNumber(), gd.getNextNumber(), gd.getNextNumber()};
				
				final boolean doMagnitudeFilter = gd.getNextBoolean();
				final double maxMag = gd.getNextNumber();
				
				final boolean onlySelectedLinks = gd.getNextBoolean();
				
				final List< List<ViewId> > selectedViewGroups = ((GroupedRowWindow)panel).selectedRowsViewIdGroups();
				
				final Set<Pair<Group<ViewId>, Group<ViewId>>> selectedPairs = new HashSet<>();
				for (int i = 0; i < selectedViewGroups.size(); i++)
					for (int j = i+1; j< selectedViewGroups.size() -1; j++)
					{
						// add both ways just to make sure
						selectedPairs.add(new ValuePair<>(new Group<>(selectedViewGroups.get(i)), new Group<>(selectedViewGroups.get(j))));
						selectedPairs.add(new ValuePair<>(new Group<>(selectedViewGroups.get(j)), new Group<>(selectedViewGroups.get(i))));
					}
				
				
				FilteredStitchingResults fsr = new FilteredStitchingResults(results);
				
				if (doCorrelationFilter)
					fsr.addFilter(new FilteredStitchingResults.CorrelationFilter(minR, maxR));
				
				if (doAbsoluteShiftFilter)
					fsr.addFilter(new FilteredStitchingResults.AbsoluteShiftFilter(maxShift));
				
				if (doMagnitudeFilter)
					fsr.addFilter(new FilteredStitchingResults.ShiftMagnitudeFilter(maxMag));
				
				if (onlySelectedLinks)
					fsr.applyToWrappedSubset(selectedPairs);
				else
					fsr.applyToWrappedAll();
			}
		} );
	}

	
}
