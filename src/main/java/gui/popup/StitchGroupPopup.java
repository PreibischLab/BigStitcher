package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.TransformTools;
import algorithm.globalopt.GlobalOpt;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStitchingResult;
import algorithm.globalopt.PairwiseStrategyTools;
import algorithm.globalopt.TransformationTools;
import gui.popup.StitchPairwisePopup.MyActionListener;
import ij.gui.GenericDialog;
import input.GenerateSpimData;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class StitchGroupPopup extends JMenuItem implements ExplorerWindowSetable
{
	
	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

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
	
	public class MyActionListener<V extends BasicViewSetup, D extends BasicViewDescription< V >, AS extends AbstractSequenceDescription< V ,D ,? >> implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			final AbstractSpimData< AS > d = (AbstractSpimData< AS >) panel.getSpimData();
			final AS sd = d.getSequenceDescription();
			final ViewRegistrations vr = d.getViewRegistrations();

			final boolean is2d = false;
			final boolean doSubpixel = true;


			// take together all views where the all attributes are the same except channel (i.e. group the channels)
			// they are now represented by the channel of the first ID (e.g. channelId=0)
			final List< ViewId > viewIds = panel.selectedRowsViewId();
			
			ArrayList< String > channelNames = new ArrayList<>();
			channelNames.add( "average all" );
			GroupedViews gv = (GroupedViews) viewIds.get( 0 );
			for (ViewId vid : gv.getViewIds())
			{
				channelNames.add( sd.getViewDescriptions().get( vid ).getViewSetup().getAttribute( Channel.class ).getName() );
			}
			
			GenericDialog gd = new GenericDialog("Stitching options");
			gd.addChoice( "channel to use",channelNames.toArray( new String[0] ), "average all" );
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			String channel = gd.getNextChoice();
			
			int channelIdxInGroup = channelNames.indexOf( channel ) - 1;
			
			if ( channelIdxInGroup < 0 )
			{
				//TODO: implement it
				System.out.println( "Averaging not implemented yet." );
				return;
			}
			
			final ArrayList< ViewId > viewIdsSelectedChannel = new ArrayList<>();
			
			for (ViewId vid : viewIds)
			{
				GroupedViews g = (GroupedViews) vid;
				viewIdsSelectedChannel.add( g.getViewIds().get( channelIdxInGroup ) );
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
				vl.put( viewId, TransformTools.getInitialTranslation( vr.getViewRegistration( viewId ), is2d ) );
			}

			final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles(
					vd, vl, viewIdsSelectedChannel,
					fixedViews, groupedViews );
					
			// compute them
			final ArrayList< PairwiseStitchingResult > results = TransformationTools.computePairs( pairs, 5, doSubpixel, d.getViewRegistrations(), (ImgLoader) d.getSequenceDescription().getImgLoader() );

			// add correspondences
			
			for ( final ViewId v : fixedViews )
				System.out.println( "Fixed: " + v );

			// global opt
			final HashMap< ViewId, Tile< TranslationModel3D > > models =
					GlobalOpt.compute( new TranslationModel3D(), results, fixedViews, groupedViews );
			
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

}
