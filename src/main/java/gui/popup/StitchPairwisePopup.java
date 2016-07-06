package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;


import algorithm.StitchingResults;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStitchingResult;
import algorithm.globalopt.TransformationTools;
import gui.GroupedRowWindow;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class StitchPairwisePopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;
	
	public static final String[] ds = { "1", "2", "4", "8" };

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	StitchingResults results;

	public StitchPairwisePopup()
	{
		super( "Stitch pairwise..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}


	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}
			

			AbstractSequenceDescription< ?, ?, ? > sd = panel.getSpimData().getSequenceDescription();
			

			final List< ViewId > viewIds = panel.selectedRowsViewId();
			
			if (viewIds.size() != 2){
				JOptionPane.showMessageDialog(
						null,
						"You need to select two images to perform pairwise stitching" );
			}
			
			ArrayList< String > channelNames = new ArrayList<>();
			channelNames.add( "average all" );
			GroupedViews gv = new GroupedViews( ((GroupedRowWindow)panel).selectedRowsViewIdGroups().get( 0 ));
			for (ViewId vid : gv.getViewIds())
			{
				channelNames.add( sd.getViewDescriptions().get( vid ).getViewSetup().getAttribute( Channel.class ).getName() );
			}
			
			GenericDialog gd = new GenericDialog("Pairwise stitching options");
			gd.addStringField("number of PCM peaks to check", "5");
			gd.addCheckbox("Subpixel accuracy", true);
			gd.addChoice( "channel to use",channelNames.toArray( new String[0] ), "average all" );
			gd.addChoice( "downsample x", ds, ds[0] );
			gd.addChoice( "downsample y", ds, ds[0] );
			gd.addChoice( "downsample z", ds, ds[0] );
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			int nPeaks = Integer.parseInt(gd.getNextString());
			boolean doSubpixel = gd.getNextBoolean();
			String channel = gd.getNextChoice();
			
			long [] downSamplingFactors = new long[3];
			downSamplingFactors[0] = Integer.parseInt( gd.getNextChoice() );
			downSamplingFactors[1] = Integer.parseInt( gd.getNextChoice() );
			downSamplingFactors[2] = Integer.parseInt( gd.getNextChoice() );
			
			int channelIdxInGroup = channelNames.indexOf( channel ) - 1;
			
			ViewId vid0 = viewIds.get( 0 );
			ViewId vid1 = viewIds.get( 1 );
			
			final ViewRegistrations vrs = panel.getSpimData().getViewRegistrations();
			final ViewRegistration v0 = vrs.getViewRegistration( vid0 );
			final ViewRegistration v1 = vrs.getViewRegistration( vid1 );
			
			final Pair<double[], Double> stitchingResult;
			
			// average all channels
			if ( channelIdxInGroup < 0 )
			{
				// TODO: handle 2-d
				stitchingResult = TransformationTools.computeStitching( vid0, vid1, v0, v1, nPeaks, doSubpixel, panel.getSpimData().getSequenceDescription().getImgLoader(), true, downSamplingFactors );
			}
			else
			// use only selected channel
			{
				vid0 = ((GroupedViews) viewIds.get( 0 )).getViewIds().get( channelIdxInGroup );
				vid1 = ((GroupedViews) viewIds.get( 1 )).getViewIds().get( channelIdxInGroup );
				stitchingResult = TransformationTools.computeStitching( vid0, vid1, v0, v1, nPeaks, doSubpixel, panel.getSpimData().getSequenceDescription().getImgLoader(), false, downSamplingFactors );

			}			
						

			System.out.println("integer shift: " + Util.printCoordinates(stitchingResult.getA()));
			System.out.print("cross-corr: " + stitchingResult.getB());
			
			
			// update the registration of the second tile to:
			// registration of first tile + calculated shift
			AffineGet tile1Reg = vrs.getViewRegistration( viewIds.get( 0 ) ).getTransformList().get( 1 ).asAffine3D();
			AffineTransform3D shiftTransform = new AffineTransform3D();
			shiftTransform.set( new double[]   {1.0, 0.0, 0.0, tile1Reg.get( 0, 4 ) + stitchingResult.getA()[0],
												0.0, 1.0, 0.0, tile1Reg.get( 1, 4 ) + stitchingResult.getA()[1],
												0.0, 0.0, 1.0, tile1Reg.get( 2, 4 ) + stitchingResult.getA()[2]} );
			
			// apply registration to all views in GroupedViews
			for (ViewId vid : ((GroupedViews) viewIds.get( 1 )).getViewIds())
			{
				vrs.getViewRegistration( vid ).getTransformList().set( 1, new ViewTransformAffine( "Translation", shiftTransform ) );
				vrs.getViewRegistration( vid ).updateModel();
			}

			// update global stitching results if we have them available
			if (results != null)
			{
				Pair<ViewId, ViewId> vidPair = new ValuePair<>( vid0, vid1 );
				results.getPairwiseResults().put(vidPair , new PairwiseStitchingResult( vidPair, stitchingResult.getA(), stitchingResult.getB() ) );
			}
			panel.bdvPopup().updateBDV();
			
			
		}
	}


	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.results = res;		
	}
}
