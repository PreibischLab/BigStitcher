package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import com.jgoodies.common.base.Strings;

import algorithm.GroupedViewAggregator;
import algorithm.GroupedViewAggregator.ActionType;
import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.SpimDataTools;
import algorithm.TransformTools;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStrategyTools;
import algorithm.globalopt.TransformationTools;
import fiji.util.gui.GenericDialogPlus;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.datasetmanager.FileListDatasetDefinition;
import spim.fiji.datasetmanager.FileListDatasetDefinitionUtil;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class CalculatePCPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8664967345630864576L;

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public static final String[] ds = { "1", "2", "4", "8", "16", "32", "64" };

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

	public static long[] askForDownsampling(AbstractSpimData< ? > data, boolean is2d)
	{
		// get first non-missing viewDescription
		final Optional<? extends BasicViewDescription< ? > > firstPresent = 
				data.getSequenceDescription().getViewDescriptions().values().stream().filter( v -> v.isPresent() ).findFirst();
		
		boolean askForManualDownsampling = true;
		final long[] downSamplingFactors = new long[] {1, 1, 1};
		
		// ask for precomputed levels if we have at least one present view and a MultiResolutionImgLoader
		if (firstPresent.isPresent())
		{
			if (MultiResolutionImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ))
			{
				// by default, we do not ask for manual ds if we have multiresolution data
				askForManualDownsampling = false;
				
				GenericDialog gd = new GenericDialog( "Use precomputed downsampling" );
				
				final MultiResolutionImgLoader mrImgLoader = (MultiResolutionImgLoader) data.getSequenceDescription().getImgLoader();
				final double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( firstPresent.get().getViewSetupId()).getMipmapResolutions();
				final String[] dsStrings = new String[mipmapResolutions.length];
				
				for (int i = 0; i<mipmapResolutions.length; i++)
				{
					final String fx = ((Long)Math.round( mipmapResolutions[i][0] )).toString(); 
					final String fy = ((Long)Math.round( mipmapResolutions[i][1] )).toString(); 
					final String fz = ((Long)Math.round( mipmapResolutions[i][2] )).toString();
					final String dsString = String.join( ", ", fx, fy, fz );
					dsStrings[i] = dsString;
				}
				
				gd.addChoice( "downsampling (x, y, z)", dsStrings, dsStrings[0] );
				gd.addCheckbox( "manually select downsampling", false );
				
				gd.showDialog();

				if ( gd.wasCanceled() )
					return null;
				
				final String dsChoice = gd.getNextChoice();
				final String[] choiceSplit = dsChoice.split( ", " );
				downSamplingFactors[0] = Long.parseLong( choiceSplit[0] );
				downSamplingFactors[1] = Long.parseLong( choiceSplit[1] );
				downSamplingFactors[2] = Long.parseLong( choiceSplit[2] );
				
				askForManualDownsampling = gd.getNextBoolean();
			
			}
		}
		
		if (!askForManualDownsampling)
			return downSamplingFactors;
		
		GenericDialogPlus gdp = new GenericDialogPlus( "Manual downsampling" );
		gdp.addChoice( "downsample x", ds, Long.toString( downSamplingFactors[0] ));
		gdp.addChoice( "downsample y", ds, Long.toString( downSamplingFactors[1] ) );
		if ( !is2d )
		{
			gdp.addChoice( "downsample z", ds, Long.toString( downSamplingFactors[2] ) );
		}
		
		gdp.showDialog();
		
		if (gdp.wasCanceled())
			return null;
			
			downSamplingFactors[0] = Integer.parseInt( gdp.getNextChoice() );
			downSamplingFactors[1] = Integer.parseInt( gdp.getNextChoice() );
			if ( !is2d )
			{
				downSamplingFactors[2] = Integer.parseInt( gdp.getNextChoice() );
			}
		
		
		return downSamplingFactors;
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
					filteringAndGrouping.askUserForGroupingAggregator();
					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					// TODO: do a meaningful is2d check
					boolean is2d = false;

					final long[] downSamplingFactors = askForDownsampling( panel.getSpimData(), is2d );
					if (downSamplingFactors == null)
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

					final List< Pair< Group< BasicViewDescription< ? extends BasicViewSetup > >, Group< BasicViewDescription< ? extends BasicViewSetup > > > > pairs 
						= filteringAndGrouping.getComparisons();

					final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
							pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
							filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
							downSamplingFactors );


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

						stitchingResults.setPairwiseResultForPair( psr.pair(), psr );
					}

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": DONE." );
				}
			} ).start();

		}
	}
}
