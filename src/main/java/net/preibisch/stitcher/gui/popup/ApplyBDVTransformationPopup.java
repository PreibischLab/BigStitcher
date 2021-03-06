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
import javax.swing.JMenuItem;
import bdv.BigDataViewer;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.state.SourceState;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class ApplyBDVTransformationPopup extends JMenuItem implements ExplorerWindowSetable {
	
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public ApplyBDVTransformationPopup() 
	{
		super( "Apply BDV Transformation to Data" );
		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
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

			BigDataViewer bdv = panel.bdvPopup().getBDV();
			
			if (bdv == null)
			{
				IOFunctions.println( "BigDataViewer is not open. Please start it to access this functionality." );
				return;
			}			
			
			
			for (int i = 0; i < bdv.getViewer().getVisibilityAndGrouping().numSources(); ++i)
			{
				Integer tpId = bdv.getViewer().getState().getCurrentTimepoint();
				SourceState<?> s = bdv.getViewer().getVisibilityAndGrouping().getSources().get( i );
				
				// get manual transform
				AffineTransform3D tAffine = new AffineTransform3D();
				((TransformedSource< ? >)s.getSpimSource()).getFixedTransform( tAffine );
				
				// get old transform
				ViewRegistration vr = panel.getSpimData().getViewRegistrations().getViewRegistration( new ViewId(tpId, i ));
				AffineGet old = vr.getTransformList().get( 1 ).asAffine3D();
				
				// update transform in ViewRegistrations
				AffineTransform3D newTransform = new AffineTransform3D();
				newTransform.set( old.get( 0, 3 ) + tAffine.get( 0, 3 ), 0, 3 );
				newTransform.set( old.get( 1, 3 ) + tAffine.get( 1, 3 ), 1, 3 );
				newTransform.set( old.get( 2, 3 ) + tAffine.get( 2, 3 ), 2, 3 );
				
				ViewTransform newVt = new ViewTransformAffine( "Translation", newTransform );				
				vr.getTransformList().set( 1, newVt );
				vr.updateModel();
				
				// reset manual transform
				((TransformedSource< ? >)s.getSpimSource()).setFixedTransform( new AffineTransform3D() );
				bdv.getViewer().requestRepaint();
			}
			
			panel.bdvPopup().updateBDV();			
			
		}
	}

}
