package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.TransformationTools;
import algorithm.globalopt.GroupedViews;
import gui.StitchingResultsSettable;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

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
					
					SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > filteringAndGrouping = 
							SpimDataFilteringAndGrouping.askUserForGrouping( (FilteredAndGroupedExplorerPanel< ? extends AbstractSpimData< ? >, ? > ) panel);
					
					
					PairwiseStitchingParameters params = PairwiseStitchingParameters.askUserForParameters();
					
					long[] dsFactors = CalculatePCPopup.askForDownsampling( false );
					
					
					List< Pair< ViewId, ViewId > > pairs = filteringAndGrouping.getComparisons().stream().map( 
							( c ) -> new ValuePair<ViewId, ViewId>(new GroupedViews( c.getA() ), new GroupedViews( c.getB() )) ).collect( Collectors.toList() );
					
					
					final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
							pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
							filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
							dsFactors );

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						// find the ViewId of the GroupedViews that the results
						// belong to
						Set<ViewId> gvA = psr.pair().getA();
						Set<ViewId> gvB = psr.pair().getB();
						

						stitchingResults.setPairwiseResultForPair( new ValuePair< >( gvA, gvB ), psr );
					}
					
					
				}
			}).run();
		}
	}
}
