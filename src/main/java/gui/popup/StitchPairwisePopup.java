package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.phasecorrelation.PhaseCorrelation2;
import net.imglib2.algorithm.phasecorrelation.PhaseCorrelationPeak2;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import spim.fiji.plugin.Max_Project;
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

			final List< ViewId > viewIds = panel.selectedRowsViewId();
			
			if (viewIds.size() != 2){
				JOptionPane.showMessageDialog(
						null,
						"You need to select two images to perform pairwise stitching" );
			}

			int tp = 0;
			
			GenericDialog gd = new GenericDialog("Pairwise stitching options");
			gd.addStringField("number of PCM peaks to check", "5");
			gd.addCheckbox("Subpixel accuracy", true);
			gd.showDialog();
			
			if (gd.wasCanceled())
				return;
			
			int nPeaks = Integer.parseInt(gd.getNextString());
			boolean doSubpixel = gd.getNextBoolean();
			
			
			ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
			
			
			// TODO: what is a smart way to find out the type here
			RandomAccessibleInterval<UnsignedShortType> img1 = (RandomAccessibleInterval<UnsignedShortType>) panel.getSpimData().getSequenceDescription().getImgLoader().getSetupImgLoader(viewIds.get(0).getViewSetupId()).getImage(tp, null);
			RandomAccessibleInterval<UnsignedShortType> img2 = (RandomAccessibleInterval<UnsignedShortType>) panel.getSpimData().getSequenceDescription().getImgLoader().getSetupImgLoader(viewIds.get(1).getViewSetupId()).getImage(tp, null);
						
						
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
			
			
		}
	}
}
