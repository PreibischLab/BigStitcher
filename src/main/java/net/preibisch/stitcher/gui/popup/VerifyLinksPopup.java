/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
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
