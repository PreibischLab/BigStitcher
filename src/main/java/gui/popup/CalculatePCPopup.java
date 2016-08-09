package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.GroupedViewAggregator;
import algorithm.GroupedViewAggregator.ActionType;
import algorithm.PairwiseStitching;
import algorithm.PairwiseStitchingParameters;
import algorithm.StitchingResults;
import algorithm.TransformTools;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStitchingResult;
import algorithm.globalopt.PairwiseStrategyTools;
import algorithm.globalopt.TransformationTools;
import gui.GroupedRowWindow;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class CalculatePCPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8664967345630864576L;
	
	private StitchingResults stitchingResults;
	private ExplorerWindow<? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	public static final String[] ds = { "1", "2", "4", "8" };
	
	public CalculatePCPopup()
	{
		super( "Calculate Pairwise Shift" );
		this.addActionListener( new MyActionListener() );
	}

	@Override
	public void setStitchingResults(StitchingResults res) { this.stitchingResults = res; }

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	public class MyActionListener implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e)
		{
			final AbstractSpimData< ? > d =  panel.getSpimData();
			final AbstractSequenceDescription< ?, ?, ? > sd = d.getSequenceDescription();
			final ViewRegistrations vr = d.getViewRegistrations();

			// take together all views where the all attributes are the same except channel (i.e. group the channels)
			// they are now represented by the channel of the first ID (e.g. channelId=0)
			final ArrayList< GroupedViews > viewIds = new ArrayList<>();
						
			for (List<ViewId> vidl : ((GroupedRowWindow)panel).selectedRowsViewIdGroups())
				viewIds.add( new GroupedViews( vidl ) );
			
			ArrayList< String > channelNames = new ArrayList<>();
			channelNames.add( "average all" );
			
			// TODO: this (and following code) assumes that all channels are present for all tiles
			// --> handle MissingViews!
			GroupedViews gv = viewIds.get( 0 );
			for (ViewId vid : gv.getViewIds())
			{
				channelNames.add( sd.getViewDescriptions().get( vid ).getViewSetup().getAttribute( Channel.class ).getName() );
			}
			
			boolean is2d = sd.getViewDescriptions().get( gv ).getViewSetup().getSize().numDimensions() == 2;
			
			GenericDialog gd = new GenericDialog("Stitching options");
			gd.addChoice( "channel to use",channelNames.toArray( new String[0] ), "average all" );
			gd.addChoice( "downsample x", ds, ds[0] );
			gd.addChoice( "downsample y", ds, ds[0] );
			if (!is2d) { gd.addChoice( "downsample z", ds, ds[0] ); }
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			String channel = gd.getNextChoice();
			
			long [] downSamplingFactors = !is2d ? new long[3] : new long[2];
			downSamplingFactors[0] = Integer.parseInt( gd.getNextChoice() );
			downSamplingFactors[1] = Integer.parseInt( gd.getNextChoice() );
			if (!is2d) { downSamplingFactors[2] = Integer.parseInt( gd.getNextChoice() ); }
			
			PairwiseStitchingParameters params = PairwiseStitchingParameters.askUserForParameters();
			if (params == null)
				return;
			
			//final ArrayList< ViewId > viewIdsSelectedChannel = new ArrayList<>();
			
			int channelIdxInGroup = channelNames.indexOf( channel ) - 1;			
			boolean doGrouped = channelIdxInGroup < 0 ;
			
			/*
			// get only one channel from grouped views
			if ( !doGrouped ) {
				for (GroupedViews g : viewIds)
				{
					viewIdsSelectedChannel.add( g.getViewIds().get( channelIdxInGroup ) );
				}
			}
			// keep GroupedViews
			else
			{
				viewIdsSelectedChannel.addAll( viewIds );
			}
			*/
						
			// find all pairwise matchings that we need to compute
			final HashMap< ViewId, Dimensions > vd = new HashMap<>();
			final HashMap< ViewId, AbstractTranslation > vl = new HashMap<>();

			for ( final ViewId viewId : viewIds )
			{
				vd.put( viewId, sd.getViewDescriptions().get( viewId ).getViewSetup().getSize() );
				vl.put( viewId, TransformTools.getInitialTranslation( vr.getViewRegistration( viewId ), is2d , new AffineTransform3D()) );
			}

			final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles(
					vd, vl, viewIds );

			// compute them
			
			GroupedViewAggregator groupedViewAggregator = new GroupedViewAggregator();
			
			if (doGrouped)
			{
				groupedViewAggregator.addAction( ActionType.PICK_BRIGHTEST, Illumination.class, null );
				groupedViewAggregator.addAction( ActionType.AVERAGE, Channel.class, null );
			}
			else
			{
				groupedViewAggregator.addAction( ActionType.PICK_BRIGHTEST, Illumination.class, null );
				Channel c = sd.getViewDescriptions().get( viewIds.get( 0 ) ).getViewSetup().getAttribute( Channel.class );
				groupedViewAggregator.addAction( ActionType.PICK_SPECIFIC, Channel.class, c );
			}
			
			final ArrayList< PairwiseStitchingResult<ViewId> > results = 
					TransformationTools.computePairs( pairs,
												params,
												d.getViewRegistrations(),
												d.getSequenceDescription(), 
												groupedViewAggregator,
												downSamplingFactors );

			
			// update StitchingResults with Results
			for (final PairwiseStitchingResult <ViewId > psr : results)
			{
				// find the ViewId of the GroupedViews that the results belong to
				ViewId gvA = null;
				ViewId gvB = null;
				for (GroupedViews g : viewIds){
					if (g.getViewIds().contains( psr.pair().getA())) {gvA = g;}
					if (g.getViewIds().contains( psr.pair().getB())) {gvB = g;}
				}
				
				stitchingResults.setPairwiseResultForPair( new ValuePair<>( gvA, gvB ), psr );						
			}
			
		}
		
		
	}

}
