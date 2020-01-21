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
package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JMenuItem;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.plugin.Apply_Transformation;
import net.preibisch.mvrecon.fiji.plugin.apply.ApplyParameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

public class ApplyTransformationPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public ApplyTransformationPopup()
	{
		super( "Apply Transformation(s) ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}
	
	public static final List< ViewId > getSelectedViews(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		return getSelectedViews( panel, true );
	}
	public static final List< ViewId > getSelectedViews(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel,
			final boolean filterMissing)
	{
		final List< ViewId > viewIds = new ArrayList<>();
		if (GroupedRowWindow.class.isInstance( panel ))
			((GroupedRowWindow)panel).selectedRowsViewIdGroups().forEach( vidsI -> viewIds.addAll( vidsI ) );
		else
			viewIds.addAll(panel.selectedRowsViewId());

		if (filterMissing)
			SpimData2.filterMissingViews( panel.getSpimData(), viewIds );
		return viewIds;
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

			if ( !SpimData.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData data = (SpimData)panel.getSpimData();

					final ArrayList< ViewId > viewIds = new ArrayList<>();
					viewIds.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), viewIds );

					final Apply_Transformation t = new Apply_Transformation();
		
					final ApplyParameters params = t.queryParams( data, viewIds );
		
					if ( params == null )
						return;
				
					final Map< ViewDescription, Pair< double[], String > > modelLinks;
				
					// query models and apply them
					if ( params.defineAs == 0 ) // matrix
						modelLinks = t.queryString( data, viewIds, params );
					else if ( params.defineAs == 1 ) //Rotation around axis
						modelLinks = t.queryRotationAxis( data, viewIds, params );
					else // Interactively using the BigDataViewer
						modelLinks = t.queryBigDataViewer( data, viewIds, params );
				
					if ( modelLinks == null )
						return;
				
					t.applyModels( data, params.minResolution, params.applyTo, modelLinks );
		
					// update registration panel if available
					panel.updateContent();
					panel.bdvPopup().updateBDV();
				}
			} ).start();
		}
	}
}
