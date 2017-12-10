package net.preibisch.stitcher.gui.popup;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

import javax.swing.JComponent;
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

}
