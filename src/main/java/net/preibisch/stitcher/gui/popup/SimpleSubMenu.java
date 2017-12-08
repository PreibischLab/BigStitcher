package net.preibisch.stitcher.gui.popup;

import java.util.Arrays;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class SimpleSubMenu extends JMenu implements ExplorerWindowSetable
{

	private static final long serialVersionUID = 1L;
	private final List<JMenuItem> children;
	
	public SimpleSubMenu(final String title, final JMenuItem ...children)
	{
		super(title);
		this.children = Arrays.asList( children );
		this.children.forEach( c -> this.add( c ) );
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		children.forEach( c -> {
			if (ExplorerWindowSetable.class.isInstance( c ))
				((ExplorerWindowSetable)c).setExplorerWindow( panel );
		} );
		return this;
	}

}
