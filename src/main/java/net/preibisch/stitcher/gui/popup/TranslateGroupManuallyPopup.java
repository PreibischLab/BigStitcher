package net.preibisch.stitcher.gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ApplyTransformationPopup;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;
import net.preibisch.stitcher.gui.TranslateGroupManuallyPanel;
import net.preibisch.stitcher.gui.popup.TogglePreviewPopup.MyActionListener;

public class TranslateGroupManuallyPopup extends JMenuItem implements ExplorerWindowSetable
{

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	public TranslateGroupManuallyPopup()
	{
		super( "Manually translate Views" );
		this.addActionListener( new MyActionListener() );
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

			
			
			if (!SpimData2.class.isInstance( panel.getSpimData() ))
			{
				IOFunctions.println( "Current dataset is not SpimData2, cannot open " + this.getClass().getSimpleName() );
				return;
			}
			
			final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );
			
			final JFrame theFrame = new JFrame( "Move Views" );			
			TranslateGroupManuallyPanel tgmp = new TranslateGroupManuallyPanel( (SpimData2) panel.getSpimData(), viewIds, panel.bdvPopup(), theFrame);
			
			((FilteredAndGroupedExplorerPanel< AbstractSpimData<?>, ? >) panel).addListener(  tgmp );
			
			theFrame.add( tgmp );
			theFrame.pack();
			theFrame.setVisible( true );
			
			theFrame.addWindowListener( new WindowAdapter() 
			{
				@Override
				public void windowClosing(WindowEvent e)
				{
					System.out.println( "closing" );
					((FilteredAndGroupedExplorerPanel< ?, ? >) panel).getListeners().remove( tgmp );
				}
			});
		}
	}
	
	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

}
