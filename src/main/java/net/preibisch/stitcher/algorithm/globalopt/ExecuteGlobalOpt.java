package net.preibisch.stitcher.algorithm.globalopt;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.algorithm.globalopt.GlobalOptimizationParameters.GlobalOptType;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;
import net.preibisch.stitcher.gui.overlay.DemoLinkOverlay;

public class ExecuteGlobalOpt implements Runnable
{
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	private boolean expertMode;
	private SpimDataFilteringAndGrouping<? extends AbstractSpimData<?> > savedFiltering;

	private GlobalOptimizationParameters params;
	private ArrayList< Pair< Group< ViewId >, Group< ViewId > > > removedInconsistentPairs = null;

	public ExecuteGlobalOpt(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel, 
			final boolean expertMode )
	{
		this( panel, expertMode, null );
	}

	public ExecuteGlobalOpt(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel,
			final boolean expertMode,
			final GlobalOptimizationParameters params )
	{
		this.panel = panel;
		this.expertMode = expertMode;
		this.savedFiltering = null;
		this.params = params;
	}

	public ExecuteGlobalOpt(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel,
			final SpimDataFilteringAndGrouping<? extends AbstractSpimData<?> > savedFiltering)
	{
		this( panel, savedFiltering, null );
	}

	public ExecuteGlobalOpt(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel,
			final SpimDataFilteringAndGrouping<? extends AbstractSpimData<?> > savedFiltering,
			final GlobalOptimizationParameters params )
	{
		this.panel = panel;
		this.expertMode = savedFiltering.requestExpertSettingsForGlobalOpt;
		this.savedFiltering = savedFiltering;
		this.params = params;
	}

	public ArrayList< Pair< Group< ViewId >, Group< ViewId > > > getRemovedInconsistentPairs()
	{
		return removedInconsistentPairs;
	}

	public GlobalOptimizationParameters getParams() { return params; }

	@Override
	public void run()
	{
		try
		{
			if (!SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println(new Date( System.currentTimeMillis() ) + "ERROR: expected SpimData2, but got " + panel.getSpimData().getClass().getSimpleName());
				return;
			}

			final boolean isSavedFaG = savedFiltering != null;

			if ( params == null )
			{
				params = expertMode ? GlobalOptimizationParameters.askUserForParameters(!isSavedFaG) : new GlobalOptimizationParameters( Double.MAX_VALUE, Double.MAX_VALUE, GlobalOptType.TWO_ROUND, false );
				if ( params == null )
					return;
			}

			final SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping;
			if ( !isSavedFaG )
			{
				FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
				filteringAndGrouping = new SpimDataFilteringAndGrouping< SpimData2 >(
						(SpimData2) panel.getSpimData() );

				if (expertMode && params.showExpertGrouping)
				{
					filteringAndGrouping.askUserForFiltering( panelFG );
					if ( filteringAndGrouping.getDialogWasCancelled() )
						return;

					filteringAndGrouping.askUserForGrouping( panelFG );
					if ( filteringAndGrouping.getDialogWasCancelled() )
						return;
				}
				else
				{
					// use whatever is selected in panel as filters
					filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );

					// get the grouping from panel and compare Tiles
					panelFG.getTableModel().getGroupingFactors().forEach( g -> filteringAndGrouping.addGroupingFactor( g ));
					filteringAndGrouping.addComparisonAxis( Tile.class );

					// compare by Channel if channels were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Channel.class ))
						filteringAndGrouping.addComparisonAxis( Channel.class );

					// compare by Illumination if illums were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Illumination.class ))
						filteringAndGrouping.addComparisonAxis( Illumination.class );

				}
			}
			else
			{
				// FIXME: there is some generics work to be done,
				// obviously
				filteringAndGrouping = (SpimDataFilteringAndGrouping< SpimData2 >) savedFiltering;
			}

			this.removedInconsistentPairs = new ArrayList<>();

			GlobalOptStitcher.processGlobalOptimization( (SpimData2) panel.getSpimData(), filteringAndGrouping, params, removedInconsistentPairs, !expertMode, params.applyResults );

			if ( params.applyResults )
			{
				GlobalOptStitcher.removeInconsistentLinks( removedInconsistentPairs, ((SpimData2) panel.getSpimData()).getStitchingResults().getPairwiseResults() );
	
				final DemoLinkOverlay demoOverlay;
	
				if ( !StitchingExplorerPanel.class.isInstance( panel ) )
					demoOverlay = null;
				else
					demoOverlay = (( StitchingExplorerPanel<?,?> )panel).getDemoLinkOverlay();
	
				if ( demoOverlay != null )
				{
					synchronized ( demoOverlay )
					{
						demoOverlay.getInconsistentResults().clear();
						demoOverlay.getInconsistentResults().addAll( removedInconsistentPairs );
					}
				}
			}
		}
		finally
		{
			if ( params.applyResults )
			{
				System.out.println( "resetting savedFilteringAndGrouping" );
				// remove saved filtering and grouping once we are done here
				// regardless of whether optimization was successful or not
				( (StitchingExplorerPanel< ?, ? >) panel ).setSavedFilteringAndGrouping( null );
			}
		}

		if ( params.applyResults )
		{
			panel.updateContent();
			panel.bdvPopup().updateBDV();
		}
	}
}
