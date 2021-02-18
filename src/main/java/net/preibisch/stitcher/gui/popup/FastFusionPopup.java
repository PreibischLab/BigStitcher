/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2021 Big Stitcher developers.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;


import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ApplyTransformationPopup;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.plugin.Fast_Translation_Fusion;
import net.preibisch.stitcher.plugin.Fast_Translation_Fusion.FastFusionParameters;

public class FastFusionPopup extends JMenuItem implements ExplorerWindowSetable
{

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	public FastFusionPopup()
	{
		super( "Fast Image Fusion (Translation-only) ..." );

		this.addActionListener( new MyActionListener() );
	}

	
	@Override
	public JComponent setExplorerWindow(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final List< ViewId > viewIds = new ArrayList<>();
					viewIds.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );
					Collections.sort( viewIds );


					FastFusionParameters params = Fast_Translation_Fusion.queryParameters( (SpimData2)panel.getSpimData(), viewIds );

					if (params == null)
						return;

					if ( Fast_Translation_Fusion.fuse( (SpimData2)panel.getSpimData(), viewIds, params ) )
					{
						panel.updateContent(); // update interestpoint and registration panel if available
						panel.bdvPopup().updateBDV();
					}
				}
			} ).start();
		}

	}

}
