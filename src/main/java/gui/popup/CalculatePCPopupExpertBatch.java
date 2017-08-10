package gui.popup;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.TransformationTools;
import gui.StitchingExplorerPanel;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class CalculatePCPopupExpertBatch extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.stitchingResults = res;		
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	public CalculatePCPopupExpertBatch()
	{
		super( "Calculate Pairwise Shift (Expert/Batch mode)" );
		this.addActionListener( new MyActionListener() );
	}

	public class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{

					FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? > panelFG = (FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? >) panel;
					SpimDataFilteringAndGrouping< AbstractSpimData< ? > > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( panel.getSpimData() );

					filteringAndGrouping.askUserForFiltering( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					filteringAndGrouping.askUserForGrouping( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					filteringAndGrouping.askUserForGroupingAggregator();
					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					final GenericDialog gd = new GenericDialog( "Parameters for Stitching" );
					PairwiseStitchingParameters.addQueriesToGD( gd );
					gd.addCheckbox( "clear_previous_results", true );

					gd.showDialog();

					if(gd.wasCanceled())
						return;

					final PairwiseStitchingParameters params = PairwiseStitchingParameters.getParametersFromGD( gd );
					final boolean resetResults = gd.getNextBoolean();

					if ( params == null )
						return;

					long[] dsFactors = CalculatePCPopup.askForDownsampling( panel.getSpimData(), false );
					if (dsFactors == null)
						return;

					List< Pair< Group< BasicViewDescription< ? extends BasicViewSetup > >, Group< BasicViewDescription< ? extends BasicViewSetup > > > > pairs = filteringAndGrouping.getComparisons();

					final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
							pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
							filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
							dsFactors );

					// user wants us to clear previous results
					// we will clear results for all selected pairs
					if (resetResults)
					{
						// this is just a cast of pairs to Group<ViewId>
						final List< ValuePair< Group< ViewId >, Group< ViewId > > > castPairs = pairs.stream().map( p -> {
							final Group< ViewId > vidGroupA = new Group<>( p.getA().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
							final Group< ViewId > vidGroupB = new Group<>( p.getB().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
							return new ValuePair<>( vidGroupA, vidGroupB );
						}).collect( Collectors.toList() );

						for (ValuePair< Group< ViewId >, Group< ViewId > > pair : castPairs)
						{
							// try to remove a -> b and b -> a, just to make sure
							stitchingResults.getPairwiseResults().remove( pair );
							stitchingResults.getPairwiseResults().remove( new ValuePair<>( pair.getB(), pair.getA() ) );
						}
					}

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						if (psr == null)
							continue;

						stitchingResults.setPairwiseResultForPair(psr.pair(), psr );
					}

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": DONE." );

					// ask user if they want to switch to preview mode
					if (panel instanceof StitchingExplorerPanel)
					{
						final int choice = JOptionPane.showConfirmDialog( (Component) panel, "Pairwise shift calculation done. Switch to preview mode?", "Preview Mode", JOptionPane.YES_NO_OPTION );
						if (choice == JOptionPane.YES_OPTION)
						{
							((StitchingExplorerPanel< ?, ? >) panel).setSavedFilteringAndGrouping( filteringAndGrouping );
							((StitchingExplorerPanel< ?, ? >) panel).togglePreviewMode();
						}
					}
				}
			}).run();
		}
	}
}
