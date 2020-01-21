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
package net.preibisch.mvrecon.fiji.spimdata.explorer.interestpoint;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collection;
import java.util.HashMap;

import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import bdv.viewer.ViewerPanel;
import mpicbg.spim.data.sequence.ViewId;

public class InterestPointOverlay implements OverlayRenderer, TransformListener< AffineTransform3D >
{
	public static interface InterestPointSource
	{
		public HashMap< ? extends ViewId, ? extends Collection< ? extends RealLocalizable > > getLocalCoordinates( final int timepointIndex );
		public void getLocalToGlobalTransform( final ViewId viewId, final int timepointIndex, final AffineTransform3D transform );
	}

	private final Collection< ? extends InterestPointSource > interestPointSources;

	private final AffineTransform3D viewerTransform;

	private final ViewerPanel viewer;

	private Color col = Color.green.darker();

	public void setColor( final Color col ) { this.col = col; }

	/** screen pixels [x,y,z] **/
	private Color getColor( final double[] gPos )
	{
		if ( Math.abs( gPos[ 2 ] ) < 3 )
			return Color.red;

		int alpha = 255 - (int)Math.round( Math.abs( gPos[ 2 ] ) );

		if ( alpha < 64 )
			alpha = 64;

		return new Color( col.getRed(), col.getGreen(), col.getBlue(), alpha );
	}

	private double getPointSize( final double[] gPos )
	{
		return 3.0;
	}

	public InterestPointOverlay( final ViewerPanel viewer, final Collection< ? extends InterestPointSource > interestPointSources )
	{
		this.viewer = viewer;
		this.interestPointSources = interestPointSources;
		viewerTransform = new AffineTransform3D();
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		viewerTransform.set( transform );
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		final Graphics2D graphics = ( Graphics2D ) g;
		final int t = viewer.getState().getCurrentTimepoint();
		final double[] lPos = new double[ 3 ];
		final double[] gPos = new double[ 3 ];
		final AffineTransform3D transform = new AffineTransform3D();

		for ( final InterestPointSource pointSource : interestPointSources )
		{
			final HashMap< ? extends ViewId, ? extends Collection< ? extends RealLocalizable > > coordinates = pointSource.getLocalCoordinates( t );

			for ( final ViewId viewId : coordinates.keySet() )
			{
				pointSource.getLocalToGlobalTransform( viewId, t, transform );
				transform.preConcatenate( viewerTransform );

				for ( final RealLocalizable p : coordinates.get( viewId ) )
				{
					p.localize( lPos );
					transform.apply( lPos, gPos );
					final double size = getPointSize( gPos );
					final int x = ( int ) ( gPos[ 0 ] - 0.5 * size );
					final int y = ( int ) ( gPos[ 1 ] - 0.5 * size );
					final int w = ( int ) size;
					graphics.setColor( getColor( gPos ) );
					graphics.fillOval( x, y, w, w );
				}
			}
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{}
}
