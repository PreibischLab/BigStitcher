package gui.popup;

import java.awt.Component;
import java.awt.Choice;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.apache.xml.serialize.XHTMLSerializer;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.GroupedViewAggregator.ActionType;
import algorithm.globalopt.TransformationTools;
import algorithm.lucaskanade.LucasKanadeParameters;
import fiji.util.gui.GenericDialogPlus;
import gui.StitchingExplorerPanel;
import gui.StitchingResultsSettable;
import gui.StitchingUIHelper;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointdetection.methods.downsampling.DownsampleTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class CalculatePCPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8664967345630864576L;

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public CalculatePCPopup()
	{
		super( "Calculate Pairwise Shift" );
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
					final FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? > panelFG = (FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? >) panel;
					final SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( panel.getSpimData() );

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

					// ask user what to do with grouped views
					// use AVERAGE as pre-set choice for illuminations
					// (this will cause no overhead if only one illum is present)
					final HashMap< Class<? extends Entity>, ActionType > illumDefaultAggregation = new HashMap<>();
					illumDefaultAggregation.put( Illumination.class, ActionType.AVERAGE );

					filteringAndGrouping.askUserForGroupingAggregator(illumDefaultAggregation);
					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					// 2d check:
					// since the ImgLoader will give us 3D views,
					// we get all the views that are larger than 1 in every dimension
					// (if they do not have sizes yet, we assume them to be 3D)
					// if no view satisfies this check, we treat dataset as 2d
					// (this only affects whether the downsample z choicebox will be displayed or not )
					List< BasicViewDescription< ? > > all3DVds = filteringAndGrouping.getFilteredViews().stream().filter( vd -> {
						if (!vd.getViewSetup().hasSize())
							return true;
						Dimensions dims = vd.getViewSetup().getSize();
						boolean all3D = true;
						for (int d = 0; d<dims.numDimensions(); d++)
							if (dims.dimension( d ) == 1)
								all3D = false;
						return all3D;
					}).collect( Collectors.toList() );

					boolean is2d = all3DVds.size() == 0;

					final long[] downSamplingFactors = StitchingUIHelper.askForDownsampling( panel.getSpimData(), is2d );
					if (downSamplingFactors == null)
						return;

					final GenericDialog gd = new GenericDialog( "Pairwise Alignment Method" );

					gd.addChoice( "alignment_method", StitchingUIHelper.methods, StitchingUIHelper.methods[0] );
					gd.addCheckbox( "clear_previous_results", true );

					gd.showDialog();

					if(gd.wasCanceled())
						return;

					final boolean resetResults = gd.getNextBoolean();
					final int methodIdx = gd.getNextChoiceIndex();
					
					

					

					final List< Pair< Group< BasicViewDescription< ? extends BasicViewSetup > >, Group< BasicViewDescription< ? extends BasicViewSetup > > > > pairs 
						= filteringAndGrouping.getComparisons();

					final ArrayList< PairwiseStitchingResult< ViewId > > results;

					if (methodIdx == 0) // do phase correlation
					{
						PairwiseStitchingParameters params = PairwiseStitchingParameters.askUserForParameters();
						if ( params == null )
							return;

						results = TransformationTools.computePairs(
								pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
								filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
								downSamplingFactors );
					}
					else if (methodIdx == 1) // do Lucas-Kanade
					{
						LucasKanadeParameters params = LucasKanadeParameters.askUserForParameters();
						if ( params == null )
							return;

						results = TransformationTools.computePairsLK(
								pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
								filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
								downSamplingFactors, new ProgressWriterIJ());
					}
					else
					{
						results = null; // should not happen, just to stop compiler from complaining
					}


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

						stitchingResults.setPairwiseResultForPair( psr.pair(), psr );
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
			} ).start();


		}
	}
}
