package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class SimpleHyperlinkPopup extends JMenuItem implements ExplorerWindowSetable
{

	private static final long serialVersionUID = 1L;

	public SimpleHyperlinkPopup(String title, URI uri)
	{
		super(title);
		this.addActionListener( ev -> {
			if (Desktop.isDesktopSupported())
				if (Desktop.getDesktop().isSupported( Desktop.Action.BROWSE ))
					try { Desktop.getDesktop().browse( uri ); } catch ( IOException e ) { e.printStackTrace(); }
		});
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		return this;
	}

	public static JLabel createHyperlinkLabel(String text, URI uri)
	{
		final JLabel linkLabel = new JLabel( text );
		if (Desktop.isDesktopSupported())
			if (Desktop.getDesktop().isSupported( Desktop.Action.BROWSE ))
			{
				linkLabel.addMouseListener( new MouseAdapter()
				{
					@Override
					public void mouseClicked( final MouseEvent e )
					{
						try { Desktop.getDesktop().browse( uri ); } catch ( IOException ex ) { ex.printStackTrace(); }
					}

					@Override
					public void mouseEntered( final MouseEvent e )
					{
						linkLabel.setForeground( Color.BLUE );
						linkLabel.setCursor( new Cursor( Cursor.HAND_CURSOR ) );
					}

					@Override
					public void mouseExited( final MouseEvent e )
					{
						linkLabel.setForeground( Color.BLACK );
						linkLabel.setCursor( new Cursor( Cursor.DEFAULT_CURSOR ) );
					}
				} );
			}
		return linkLabel;
	}

}
