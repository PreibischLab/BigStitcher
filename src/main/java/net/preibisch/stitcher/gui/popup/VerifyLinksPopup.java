package net.preibisch.stitcher.gui.popup;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.StitchingResultsSettable;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public class VerifyLinksPopup extends JMenu implements ExplorerWindowSetable, StitchingResultsSettable
{
	private ExplorerWindow< ?, ? > panel;
	private TogglePreviewPopup interactiveExplorer;
	private SimpleRemoveLinkPopup parameterBasedRemoval;

	public VerifyLinksPopup()
	{
		super("Verify Pairwise Links");
		this.interactiveExplorer = new TogglePreviewPopup();
		this.parameterBasedRemoval = new SimpleRemoveLinkPopup();

		this.add( interactiveExplorer );
		this.add( parameterBasedRemoval );
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		interactiveExplorer.setExplorerWindow( panel );
		parameterBasedRemoval.setExplorerWindow( panel );
		return this;
	}

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		parameterBasedRemoval.setStitchingResults( res );
	}

}
