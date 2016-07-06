package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.DownsampleTools;
import gui.popup.TestPopup.MyActionListener;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class TestDownsamplePopup extends JMenuItem implements ExplorerWindowSetable
{

	public TestDownsamplePopup() 
	{
		super( "Test Downsampling..." );
		this.addActionListener( new MyActionListener() );
	}
	
	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	private class MyActionListener implements ActionListener{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}
			
			BasicViewDescription< ? > vd = panel.firstSelectedVD();
			RandomAccessibleInterval< UnsignedShortType > rable = DownsampleTools.openAndDownsample( panel.getSpimData().getSequenceDescription().getImgLoader(), vd, new long[] {2,2,2} );
			ImageJFunctions.show( rable );
			
		}
		
	}
}
