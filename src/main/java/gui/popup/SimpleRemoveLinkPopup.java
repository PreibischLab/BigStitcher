package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import gui.StitchingResultsSettable;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class SimpleRemoveLinkPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	StitchingResults results;
	
	@Override
	public void setStitchingResults(StitchingResults res){this.results = res;}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}
	
	public SimpleRemoveLinkPopup()
	{
		super("Remove Link");
		this.addActionListener( new ActionListener()
		{
			
			@Override
			public void actionPerformed(ActionEvent e)
			{
				final List< ViewId > viewIds = panel.selectedRowsViewId();
				
				if (viewIds.size() != 2){
					JOptionPane.showMessageDialog(
							null,
							"You need to select two images to remove Link" );
					return;
				}
				
				ViewId vid1 = viewIds.get( 0 );
				ViewId vid2 = viewIds.get( 1 );
				
				results.removePairwiseResultForPair( new ValuePair<>( vid1, vid2 ) );				
			}
		} );
	}

	
}
