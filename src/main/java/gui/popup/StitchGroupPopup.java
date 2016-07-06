package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.StitchingResults;
import algorithm.TransformTools;
import algorithm.globalopt.GlobalOpt;
import algorithm.globalopt.GlobalOptimizationParameters;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStitchingResult;
import algorithm.globalopt.PairwiseStrategyTools;
import algorithm.globalopt.TransformationTools;
import gui.GroupedRowWindow;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class StitchGroupPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 2850312360148574353L;
	public static final String[] ds = { "1", "2", "4", "8" };

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	StitchingResults res;
	
	public StitchGroupPopup()
	{
		super( "Stitch group..." );
		this.addActionListener( new MyActionListener() );
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
			
			GlobalOptimizationParameters params = new GlobalOptimizationParameters();
			
			final AbstractSpimData< ? > d =  panel.getSpimData();
			final AbstractSequenceDescription< ?, ?, ? > sd = d.getSequenceDescription();
			final ViewRegistrations vr = d.getViewRegistrations();

			final boolean is2d = false;

			// take together all views where the all attributes are the same except channel (i.e. group the channels)
			// they are now represented by the channel of the first ID (e.g. channelId=0)
			final ArrayList< GroupedViews > viewIds = new ArrayList<>();
						
			for (List<ViewId> vidl : ((GroupedRowWindow)panel).selectedRowsViewIdGroups())
				viewIds.add( new GroupedViews( vidl ) );
			
			ArrayList< String > channelNames = new ArrayList<>();
			channelNames.add( "average all" );
			
			GroupedViews gv = viewIds.get( 0 );
			for (ViewId vid : gv.getViewIds())
			{
				channelNames.add( sd.getViewDescriptions().get( vid ).getViewSetup().getAttribute( Channel.class ).getName() );
			}
			
			GenericDialog gd = new GenericDialog("Stitching options");
			gd.addChoice( "channel to use",channelNames.toArray( new String[0] ), "average all" );
			gd.addCheckbox( "subpixel accuracy", true );
			gd.addChoice( "downsample x", ds, ds[0] );
			gd.addChoice( "downsample y", ds, ds[0] );
			gd.addChoice( "downsample z", ds, ds[0] );
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			String channel = gd.getNextChoice();
			final boolean doSubpixel = gd.getNextBoolean();
			
			long [] downSamplingFactors = new long[3];
			downSamplingFactors[0] = Integer.parseInt( gd.getNextChoice() );
			downSamplingFactors[1] = Integer.parseInt( gd.getNextChoice() );
			downSamplingFactors[2] = Integer.parseInt( gd.getNextChoice() );
			
			final ArrayList< ViewId > viewIdsSelectedChannel = new ArrayList<>();
			
			int channelIdxInGroup = channelNames.indexOf( channel ) - 1;			
			boolean doGrouped = channelIdxInGroup < 0 ;
			
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
			

			// define fixed tiles
			final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
			fixedViews.add( viewIdsSelectedChannel.get( 0 ) );

			// define groups (no checks in between Tiles of a group, they are transformed together)
			final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();

			// find all pairwise matchings that we need to compute
			final HashMap< ViewId, Dimensions > vd = new HashMap<>();
			final HashMap< ViewId, AbstractTranslation > vl = new HashMap<>();

			for ( final ViewId viewId : viewIdsSelectedChannel )
			{
				vd.put( viewId, sd.getViewDescriptions().get( viewId ).getViewSetup().getSize() );
				vl.put( viewId, TransformTools.getInitialTranslation( vr.getViewRegistration( viewId ), is2d , downSamplingFactors) );
			}

			final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles(
					vd, vl, viewIdsSelectedChannel,
					fixedViews, groupedViews );
					
			// compute them
			final ArrayList< PairwiseStitchingResult > results = TransformationTools.computePairs( pairs, 5, doSubpixel, d.getViewRegistrations(), (BasicImgLoader) d.getSequenceDescription().getImgLoader(), doGrouped, downSamplingFactors );

			
			// update StitchingResults with Results
			// TODO: for all in group
			for (final PairwiseStitchingResult psr : results)
			{
				Pair< ViewId, ViewId > key = 
						psr.pair().getA().compareTo( psr.pair().getB() ) < 0 ? psr.pair() : new ValuePair<>(psr.pair().getB(), psr.pair().getA());
				res.getPairwiseResults().put( key, psr );						
			}
			
			// add correspondences
			
			for ( final ViewId v : fixedViews )
				System.out.println( "Fixed: " + v );

			// global opt
			final HashMap< ViewId, Tile< TranslationModel3D > > models =
					GlobalOpt.compute( new TranslationModel3D(), results, fixedViews, groupedViews , params);
			
			for (ViewId vid : models.keySet())
			{
				double[] tr = models.get( vid ).getModel().getTranslation();
				AffineTransform3D at = new AffineTransform3D();
				at.set( new double []  {1.0, 0.0, 0.0, tr[0],
										0.0, 1.0, 0.0, tr[1],
										0.0, 0.0, 1.0, tr[2]} );
				ViewTransform vt = new ViewTransformAffine( "Translation", at);
				
				// find the GroupedViews that contains vid, update Registrations for all viewIDs in group
				for (ViewId groupVid : viewIds){
					if (((GroupedViews) groupVid).getViewIds().contains( vid ))
					{
						for (ViewId vid2 : ((GroupedViews) groupVid).getViewIds()){
							d.getViewRegistrations().getViewRegistration( vid2 ).getTransformList().set( 1, vt );
							d.getViewRegistrations().getViewRegistration( vid2 ).updateModel();
						}
					}
				}
				
				
			}
			
			panel.bdvPopup().updateBDV();
			
		}
		
	}

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.res = res;		
	}

}
