package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.TransformTools;
import algorithm.globalopt.TransformationTools;
import algorithm.globalopt.GroupedViews;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.TranslationGet;
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
					SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( panel.getSpimData() );

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
					if (resetResults)
					{
						stitchingResults.getPairwiseResults().clear();
					}

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						if (psr == null)
							continue;

						stitchingResults.setPairwiseResultForPair(psr.pair(), psr );
					}

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": DONE." );

				}
			}).run();
		}
	}
}
