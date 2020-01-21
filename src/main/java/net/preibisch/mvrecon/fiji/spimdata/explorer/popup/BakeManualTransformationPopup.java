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
import java.util.ArrayList;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import bdv.AbstractSpimSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

public class BakeManualTransformationPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 4627408819269954486L;

	ExplorerWindow< ?, ? > panel;

	public BakeManualTransformationPopup()
	{
		super( "Bake BDV manual transform" );

		this.addActionListener( this::actionPerformed );
	}

	@Override
	public JComponent setExplorerWindow( ExplorerWindow<? extends AbstractSpimData<? extends AbstractSequenceDescription<?, ?, ?>>, ?> panel )
	{
		this.panel = panel;
		return this;
	}

	private void actionPerformed( final ActionEvent e )
	{
		if ( panel == null )
		{
			IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
			return;
		}

		final ArrayList< ViewId > views = new ArrayList<>();
		views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

		// filter not present ViewIds
		SpimData2.filterMissingViews( panel.getSpimData(), views );

		final ViewRegistrations vr = panel.getSpimData().getViewRegistrations();
		ViewerState state = panel.bdvPopup().getBDV().getViewer().getState();
		for ( SourceState< ? > s : state.getSources() )
		{
			if ( s.getSpimSource() instanceof TransformedSource )
			{
				TransformedSource< ? > transformedSource = ( TransformedSource< ? > ) s.getSpimSource();
				if ( transformedSource.getWrappedSource() instanceof AbstractSpimSource )
				{
					int setupId = ( ( AbstractSpimSource< ? > ) transformedSource.getWrappedSource() ).getSetupId();

					AffineTransform3D manual = new AffineTransform3D();
					transformedSource.getFixedTransform( manual );

					for ( final ViewId viewId : views )
					{
						if ( viewId.getViewSetupId() == setupId )
						{
							final ViewRegistration v = vr.getViewRegistrations().get( viewId );
							final ViewTransform vt = new ViewTransformAffine( "baked bdv manual transform", manual );
							v.preconcatenateTransform( vt );
//							v.updateModel();
						}
					}
				}
				transformedSource.setFixedTransform( new AffineTransform3D() );
			}
		}

		panel.updateContent();
		panel.bdvPopup().updateBDV();
	}
}
