package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import algorithm.PairwiseStitching;
import algorithm.TransformTools;
import algorithm.globalopt.GroupedViews;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import spim.fiji.ImgLib2Temp.Pair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class StitchPairwisePopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

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

			int tp = viewIds.get( 0 ).getTimePointId();
			
			ArrayList< String > channelNames = new ArrayList<>();
			channelNames.add( "average all" );
			GroupedViews gv = (GroupedViews) viewIds.get( 0 );
			for (ViewId vid : gv.getViewIds())
			{
				channelNames.add( sd.getViewDescriptions().get( vid ).getViewSetup().getAttribute( Channel.class ).getName() );
			}
			
			GenericDialog gd = new GenericDialog("Pairwise stitching options");
			gd.addStringField("number of PCM peaks to check", "5");
			gd.addCheckbox("Subpixel accuracy", true);
			gd.addChoice( "channel to use",channelNames.toArray( new String[0] ), "average all" );
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			int nPeaks = Integer.parseInt(gd.getNextString());
			boolean doSubpixel = gd.getNextBoolean();
			String channel = gd.getNextChoice();
			
			int channelIdxInGroup = channelNames.indexOf( channel ) - 1;
			
			if ( channelIdxInGroup < 0 )
			{
				//TODO: implement it
				System.out.println( "Averaging not implemented yet." );
				return;
			}
			
			
			ViewId vid0 = ((GroupedViews) viewIds.get( 0 )).getViewIds().get( channelIdxInGroup );
			ViewId vid1 = ((GroupedViews) viewIds.get( 1 )).getViewIds().get( channelIdxInGroup );
			
			
			ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			
			// TODO: what is a smart way to find out the type here
			final RandomAccessibleInterval<UnsignedShortType> img1 = (RandomAccessibleInterval<UnsignedShortType>) sd.getImgLoader().getSetupImgLoader(vid0.getViewSetupId()).getImage(tp, null);
			final RandomAccessibleInterval<UnsignedShortType> img2 = (RandomAccessibleInterval<UnsignedShortType>) sd.getImgLoader().getSetupImgLoader(vid1.getViewSetupId()).getImage(tp, null);

			final ViewRegistrations vrs = panel.getSpimData().getViewRegistrations();
			final ViewRegistration v0 = vrs.getViewRegistration( vid0 );
			final ViewRegistration v1 = vrs.getViewRegistration( vid1 );

			// TODO: Test if 2d, and if then reduce dimensionality and ask for a 2d translation
			AbstractTranslation t1 = TransformTools.getInitialTranslation( v0, false );
			AbstractTranslation t2 = TransformTools.getInitialTranslation( v1, false );

			final Pair< double[], Double > result = PairwiseStitching.getShift( img1, img2, t1, t2, nPeaks, doSubpixel, null, service );

			System.out.println("integer shift: " + Util.printCoordinates(result.getA()));
			System.out.print("cross-corr: " + result.getB());
			
			
			// update the registration of the second tile to:
			// registration of first tile + calculated shift
			AffineGet tile1Reg = vrs.getViewRegistration( viewIds.get( 0 ) ).getTransformList().get( 1 ).asAffine3D();
			AffineTransform3D shiftTransform = new AffineTransform3D();
			shiftTransform.set( new double[]   {1.0, 0.0, 0.0, tile1Reg.get( 0, 4 ) + result.getA()[0],
												0.0, 1.0, 0.0, tile1Reg.get( 1, 4 ) + result.getA()[1],
												0.0, 0.0, 1.0, tile1Reg.get( 2, 4 ) + result.getA()[2]} );
			
			// apply registration to all views in GroupedViews
			for (ViewId vid : ((GroupedViews) viewIds.get( 1 )).getViewIds())
			{
				vrs.getViewRegistration( vid ).getTransformList().set( 1, new ViewTransformAffine( "Translation", shiftTransform ) );
				vrs.getViewRegistration( vid ).updateModel();
			}

			panel.bdvPopup().updateBDV();
			
			/*
			RandomAccessibleInterval<FloatType> pcm = PhaseCorrelation2.calculatePCM(img1, img2, new ArrayImgFactory<FloatType>(), new FloatType(), 
					new ArrayImgFactory<ComplexFloatType>(), new ComplexFloatType(), service);
			
			//ImageJFunctions.show(pcm);		
			PhaseCorrelationPeak2 shiftPeak = PhaseCorrelation2.getShift(pcm, img1, img2, nPeaks, null, doSubpixel, service);
			System.out.println("integer shift: " + Util.printCoordinates(shiftPeak.getShift()));
			System.out.print("cross-corr: " + shiftPeak.getCrossCorr());
			if (shiftPeak.getSubpixelShift() != null){
				System.out.println("subpixel shift: " + Util.printCoordinates(shiftPeak.getSubpixelShift()));
			} else {
				System.out.println("could not calculate subpixel shift.");
			}
			*/
			
		}
	}
}
