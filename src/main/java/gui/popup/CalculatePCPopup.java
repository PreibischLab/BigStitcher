package gui.popup;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import algorithm.GroupedViewAggregator.ActionType;
import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.lucaskanade.LucasKanadeParameters;
import gui.StitchingExplorerPanel;
import gui.StitchingResultsSettable;
import gui.StitchingUIHelper;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.io.IOFunctions;
import spim.fiji.plugin.Calculate_Pairwise_Shifts;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class CalculatePCPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8664967345630864576L;
	
	public enum Method{
		PHASECORRELATION,
		LUCASKANADE
	}

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	private boolean simple;
	private boolean wizardMode;
	private Method method;
	

	public CalculatePCPopup(String description, boolean simple, Method method, boolean wizardMode)
	{
		super( description );
		this.simple = simple;
		this.method = method;
		this.wizardMode = wizardMode;
		this.addActionListener( new MyActionListener() );
	}

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

					if (!SpimData2.class.isInstance( panel.getSpimData() ) )
					{
						IOFunctions.println(new Date( System.currentTimeMillis() ) + "ERROR: expected SpimData2, but got " + panel.getSpimData().getClass().getSimpleName());
						return;
					}

					@SuppressWarnings("unchecked")
					FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
					SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< SpimData2 >( (SpimData2) panel.getSpimData() );

					if (simple)
					{
						// use whatever is selected in panel as filters
						filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );
					}
					else
					{
						filteringAndGrouping.askUserForFiltering( panelFG );
						if (filteringAndGrouping.getDialogWasCancelled())
							return;
					}

					if (simple)
					{
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
					else
					{
						filteringAndGrouping.addComparisonAxis( Tile.class );
						filteringAndGrouping.askUserForGrouping( panelFG );
						if (filteringAndGrouping.getDialogWasCancelled())
							return;
					}

					// ask user what to do with grouped views
					// use AVERAGE as pre-set choice for illuminations
					// (this will cause no overhead if only one illum is present)
					final HashMap< Class<? extends Entity>, ActionType > illumDefaultAggregation = new HashMap<>();
					illumDefaultAggregation.put( Illumination.class, ActionType.AVERAGE );

					if (simple)
						filteringAndGrouping.askUserForGroupingAggregator(illumDefaultAggregation);
					else
						filteringAndGrouping.askUserForGroupingAggregator();

					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					boolean allViews2D = StitchingUIHelper.allViews2D( filteringAndGrouping.getFilteredViews() );
					long[] dsFactors = StitchingUIHelper.askForDownsampling( panel.getSpimData(), allViews2D );
					if (dsFactors == null)
						return;

					PairwiseStitchingParameters params = null;
					LucasKanadeParameters LKParams = null;
					if (method == Method.PHASECORRELATION)
						params = simple ? new PairwiseStitchingParameters() : PairwiseStitchingParameters.askUserForParameters();
					if (method == Method.LUCASKANADE)
						LKParams = LucasKanadeParameters.askUserForParameters();

					if (method == Method.PHASECORRELATION)
						Calculate_Pairwise_Shifts.processPhaseCorrelation( (SpimData2) panel.getSpimData(), filteringAndGrouping, params, dsFactors );
					if (method == Method.LUCASKANADE)
						Calculate_Pairwise_Shifts.processLucasKanade( (SpimData2) panel.getSpimData(), filteringAndGrouping, LKParams, dsFactors );


					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": DONE." );

					if (wizardMode)
					{
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
				}

			} ).start();


		}
	}
}
