package net.preibisch.stitcher.gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.arrangement.FlipAxes;

public class FlipAxesPopup extends JMenuItem implements ExplorerWindowSetable {

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public FlipAxesPopup()
	{
		super( "Flip Axes ..." );
		this.addActionListener( new FlipAxesPopupActionListener() );
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	private class FlipAxesPopupActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			final SpimData spimData = (SpimData)panel.getSpimData();
			final List< ViewId > views = ((GroupedRowWindow) panel).selectedRowsViewIdGroups().stream().reduce(new ArrayList<>(),  (a,b) -> {a.addAll( b ); return a;} );

			final Map<ViewId, Dimensions> dims = new HashMap<>();
			views.forEach( v -> dims.put( v, spimData.getSequenceDescription().getViewDescriptions() .get( v ).getViewSetup().getSize()) );
			final boolean[] flipAxes = new boolean[3];
			GenericDialog gd = new GenericDialog( "Flip Parameters" );
			gd.addCheckbox( "Flip X", true );
			gd.addCheckbox( "Flip Y", false );
			gd.addCheckbox( "Flip Z", false );

			gd.showDialog();
			if (gd.wasCanceled())
				return;

			flipAxes[0] = gd.getNextBoolean();
			flipAxes[1] = gd.getNextBoolean();
			flipAxes[2] = gd.getNextBoolean();

			FlipAxes.applyFlipToData( spimData.getViewRegistrations(), dims, views, flipAxes );

			panel.updateContent();
			panel.bdvPopup().updateBDV();
		}

	}

}
