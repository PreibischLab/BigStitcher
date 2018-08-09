package net.preibisch.stitcher.gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;


import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ApplyTransformationPopup;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.FusionPopup.MyActionListener;
import net.preibisch.stitcher.plugin.Fast_Translation_Fusion;

public class FastFusionPopup extends JMenuItem implements ExplorerWindowSetable
{

	ExplorerWindow< ?, ? > panel;
	
	public FastFusionPopup()
	{
		super( "Fast Image Fusion (Translation-only) ..." );

		this.addActionListener( new MyActionListener() );
	}

	
	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow<  ?, ? > panel)
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

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = new ArrayList<>();
					viewIds.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );
					Collections.sort( viewIds );

					if ( Fast_Translation_Fusion.fuse( (SpimData2)panel.getSpimData(), viewIds ) )
					{
						panel.updateContent(); // update interestpoint and registration panel if available
						panel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}

	}

}
