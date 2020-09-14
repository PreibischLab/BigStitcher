package net.preibisch.stitcher.gui.popup;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.gui.ReadTileConfigurationPanel;

public class ReadTileConfigurationPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 8420300257587465114L;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	public ReadTileConfigurationPopup()
	{
		super( "Read Locations From File" );

		addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				new Thread(new Runnable()
				{
					@Override
					public void run()
					{
						if ( panel == null )
						{
							IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
							return;
						}

						JFrame frame = new JFrame( "Read Locations from File" );

						final ReadTileConfigurationPanel tcPanel = new ReadTileConfigurationPanel( panel.getSpimData(), panel.bdvPopup(), frame );
						frame.add( tcPanel, BorderLayout.CENTER );
						frame.setSize( tcPanel.getPreferredSize() );

						frame.addWindowListener( new WindowAdapter()
						{
							@Override
							public void windowClosing( WindowEvent evt )
							{
								tcPanel.quit();
							}
						} );

						frame.pack();
						frame.setVisible( true );
						frame.requestFocus();
					}
				}).start();
			}
		} );
	}

}
