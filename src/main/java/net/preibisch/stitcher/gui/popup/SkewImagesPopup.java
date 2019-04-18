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
import net.preibisch.stitcher.arrangement.SkewImages;

public class SkewImagesPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static String[] axesChoice = new String[] {"X", "Y", "Z"};
	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	public SkewImagesPopup()
	{
		super( "(De)Skew Images ..." );
		this.addActionListener( new SkewImagesPopupActionListener() );
	}
	
	private class SkewImagesPopupActionListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent e)
		{
			final SpimData spimData = (SpimData)panel.getSpimData();
			final List< ViewId > views = ((GroupedRowWindow) panel).selectedRowsViewIdGroups().stream().reduce(new ArrayList<>(),  (a,b) -> {a.addAll( b ); return a;} );

			final Map<ViewId, Dimensions> dims = new HashMap<>();
			views.forEach( v -> dims.put( v, spimData.getSequenceDescription().getViewDescriptions() .get( v ).getViewSetup().getSize()) );
			GenericDialog gd = new GenericDialog( "(De)Skew Parameters" );
			
			gd.addChoice( "Skew Direction", axesChoice, axesChoice[0] );
			gd.addChoice( "Skew Along Which Axis", axesChoice, axesChoice[2] );
			gd.addSlider( "Angle", -90, 90, 45 );

			gd.showDialog();
			if (gd.wasCanceled())
				return;

			final int direction = gd.getNextChoiceIndex();
			final int skewAxis = gd.getNextChoiceIndex();
			final double angle = gd.getNextNumber() / 180 * Math.PI;

			SkewImages.applySkewToData( spimData.getViewRegistrations(), dims, views, direction, skewAxis, angle );

			panel.updateContent();
			panel.bdvPopup().updateBDV();
		}
	}
}

