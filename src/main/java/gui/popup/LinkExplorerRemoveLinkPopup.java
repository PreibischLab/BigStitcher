package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import gui.LinkExplorerPanel;
import gui.StitchingExplorerPanel;
import gui.StitchingResultsSettable;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class LinkExplorerRemoveLinkPopup extends JMenuItem implements StitchingResultsSettable, ExplorerWindowSetable
{
	private LinkExplorerPanel panel;
	private StitchingResults results;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ?> stitchingExplorer;
	
	public LinkExplorerRemoveLinkPopup(LinkExplorerPanel panel)
	{
		super("Remove Link");
		this.panel = panel;
		this.addActionListener( new MyActionListener());
	}
	
	public class MyActionListener implements ActionListener
	{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				final Pair< Group<ViewId>, Group<ViewId> > pair = panel.getModel().getActiveLinks().get( panel.getTable().getSelectedRow() );
				results.removePairwiseResultForPair( pair );
				((StitchingExplorerPanel< ?, ? >)stitchingExplorer).updateBDVPreviewMode();
				
				panel.selectedViewDescriptions( new ArrayList<>(((GroupedRowWindow)stitchingExplorer).selectedRowsGroups()) );
				panel.getModel().fireTableDataChanged();
			}

	}

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		results = res;		
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.stitchingExplorer = panel;
		return this;
	}
	
}
