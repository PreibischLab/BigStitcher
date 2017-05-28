package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
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

	public static final String[] ds = { "1", "2", "4", "8" };

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
					
					
					FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? > panelFG = (FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? >) panel;
					SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( panel.getSpimData() );
					
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
					
					/*
					final AbstractSpimData< ? > d = panel.getSpimData();
					final AbstractSequenceDescription< ?, ?, ? > sd = d.getSequenceDescription();
					final ViewRegistrations vr = d.getViewRegistrations();

					// take together all views where the all attributes are the
					// same except channel (i.e. group the channels)
					// they are now represented by the channel of the first ID
					// (e.g. channelId=0)
					final ArrayList< GroupedViews > viewIds = new ArrayList< >();

					for ( List< ViewId > vidl : ( (GroupedRowWindow) panel ).selectedRowsViewIdGroups() )
						viewIds.add( new GroupedViews( vidl ) );

					Collections.sort( viewIds );

					ArrayList< String > channelNames = new ArrayList< >();
					channelNames.add( "average all" );

					List< Entity > channels = SpimDataTools.getInstancesOfAttribute( sd, Channel.class );
					for ( Entity en : channels )
						channelNames.add( NamedEntity.class.isInstance( en ) ? ( (NamedEntity) en ).getName()
								: Integer.toString( en.getId() ) );

					ArrayList< String > illuminationNames = new ArrayList< >();
					illuminationNames.add( "pick brightest" );

					List< Entity > illums = SpimDataTools.getInstancesOfAttribute( sd, Illumination.class );
					for ( Entity en : illums )
						illuminationNames.add( NamedEntity.class.isInstance( en ) ? ( (NamedEntity) en ).getName()
								: Integer.toString( en.getId() ) );

					GroupedViews gv = viewIds.get( 0 );
					boolean is2d = sd.getViewDescriptions().get( gv ).getViewSetup().getSize().numDimensions() == 2;
					// boolean is2d = false;

					
										
					GenericDialog gd = new GenericDialog( "Stitching options" );
					gd.addChoice( "channel to use", channelNames.toArray( new String[0] ), "average all" );
					gd.addChoice( "illumination to use", illuminationNames.toArray( new String[0] ), "pick brightest" );
					
					
					gd.showDialog();

					if ( gd.wasCanceled() )
						return;

					String channel = gd.getNextChoice();
					String illum = gd.getNextChoice();
					

					*/
					
					final long[] downSamplingFactors = askForDownsampling( panel.getSpimData(), is2d );
					if (downSamplingFactors == null)
						return;

					PairwiseStitchingParameters params = PairwiseStitchingParameters.askUserForParameters();
					if ( params == null )
						return;

					// final ArrayList< ViewId > viewIdsSelectedChannel = new
					// ArrayList<>();

					/*
					int channelIdxInGroup = channelNames.indexOf( channel ) - 1;
					boolean doChannelAverage = channelIdxInGroup < 0;

					int illumIdxInGroup = illuminationNames.indexOf( illum ) - 1;
					boolean doIllumBrightest = illumIdxInGroup < 0;

					 */
					/*
					 * // get only one channel from grouped views if (
					 * !doGrouped ) { for (GroupedViews g : viewIds) {
					 * viewIdsSelectedChannel.add( g.getViewIds().get(
					 * channelIdxInGroup ) ); } } // keep GroupedViews else {
					 * viewIdsSelectedChannel.addAll( viewIds ); }
					 */

					List< Pair< Group< BasicViewDescription< ? extends BasicViewSetup > >, Group< BasicViewDescription< ? extends BasicViewSetup > > > > pairs 
						= filteringAndGrouping.getComparisons();

					final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
							pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
							filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
							downSamplingFactors );

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						
						if (psr == null)
							continue;
						
						// find the ViewId of the GroupedViews that the results
						// belong to
						Set<ViewId> gvA = (Set< ViewId >) psr.pair().getA().getViews();
						Set<ViewId> gvB = (Set< ViewId >) psr.pair().getB().getViews();
						

						stitchingResults.setPairwiseResultForPair( new ValuePair< >( gvA, gvB ), psr );
					}
					
					/*
					
					// find all pairwise matchings that we need to compute
					final HashMap< ViewId, Dimensions > vd = new HashMap< >();
					final HashMap< ViewId, TranslationGet > vl = new HashMap< >();

					for ( final ViewId viewId : viewIds )
					{
						vd.put( viewId, sd.getViewDescriptions().get( viewId ).getViewSetup().getSize() );
						vl.put( viewId, TransformTools.getInitialTransforms( vr.getViewRegistration( viewId ), is2d,
								new AffineTransform3D() ).getB() );
					}

					final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles( vd, vl,
							viewIds );

					// compute them

					GroupedViewAggregator groupedViewAggregator = new GroupedViewAggregator();

					// decide how to handle illuminations
					if ( doIllumBrightest )
						groupedViewAggregator.addAction( ActionType.PICK_BRIGHTEST, Illumination.class, null );
					else
						groupedViewAggregator.addAction( ActionType.PICK_SPECIFIC, Illumination.class,
								(Illumination) illums.get( illumIdxInGroup ) );

					// decide how to handle channels
					if ( doChannelAverage )
						groupedViewAggregator.addAction( ActionType.AVERAGE, Channel.class, null );
					else
						groupedViewAggregator.addAction( ActionType.PICK_SPECIFIC, Channel.class,
								(Channel) channels.get( channelIdxInGroup ) );

					final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
							pairs, params, d.getViewRegistrations(), d.getSequenceDescription(), groupedViewAggregator,
							downSamplingFactors );

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						// find the ViewId of the GroupedViews that the results
						// belong to
						Set<ViewId> gvA = new HashSet<>();
						Set<ViewId> gvB = new HashSet<>();
						for ( GroupedViews g : viewIds )
						{
							if ( g.getViewIds().containsAll( psr.pair().getA() ) )
							{
								gvA.addAll( g.getViewIds() );
							}
							if ( g.getViewIds().containsAll( psr.pair().getB() ) )
							{
								gvB.addAll( g.getViewIds() );
							}
						}

						stitchingResults.setPairwiseResultForPair( new ValuePair< >( gvA, gvB ), psr );
					}
					
					*/
				}
			} ).start();

		}
	}
}
