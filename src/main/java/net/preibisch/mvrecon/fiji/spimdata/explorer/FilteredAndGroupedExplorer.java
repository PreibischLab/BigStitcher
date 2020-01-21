/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
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
package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.ArrayList;

import javax.swing.JFrame;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;

public abstract class FilteredAndGroupedExplorer<AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS >>
{

	protected JFrame frame;
	protected FilteredAndGroupedExplorerPanel< AS, X > panel;


	public AS getSpimData()
	{ return panel.getSpimData(); }

	public FilteredAndGroupedExplorerPanel< AS, X > getPanel()
	{ return panel; }

	public JFrame getFrame()
	{ return frame; }

	public void addListener(final SelectedViewDescriptionListener< AS > listener)
	{ panel.addListener( listener ); }

	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners()
	{ return panel.getListeners(); }

}
