package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JMenuItem;

import algorithm.StitchingResults;
import gui.LinkExplorerPanel;
import gui.StitchingResultsSettable;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class LinkExplorerRemoveLinkPopup extends JMenuItem implements StitchingResultsSettable
{
	private LinkExplorerPanel panel;
	private StitchingResults results;
	
	public LinkExplorerRemoveLinkPopup(LinkExplorerPanel panel)
	{
		super("Remove Link");
		this.panel = panel;
		this.addActionListener( new ActionListener()
		{
			
			@Override
			public void actionPerformed(ActionEvent e)
			{
				Pair< ViewId, ViewId > pair = panel.getModel().getActiveLinks().get( panel.getTable().getSelectedRow() );
				results.removePairwiseResultForPair( pair );
				//panel.getParent().updateBDVPreviewMode();
				//panel.getModel().fireTableDataChanged();
			}
		} );
	}

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		results = res;		
	}
	
}
