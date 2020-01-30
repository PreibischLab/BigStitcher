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
package net.preibisch.mvrecon.fiji.plugin.apply;

import bdv.BigDataViewer;
import bdv.viewer.ViewerFrame;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;

import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.mvrecon.vecmath.Transform3D;

public class BigDataViewerTransformationWindow
{
	final AffineTransform3D t;
	final protected Timer timer;
	
	protected boolean isRunning = true;
	protected boolean wasCancelled = false;
	protected boolean ignoreScaling = true;

	public BigDataViewerTransformationWindow( final BigDataViewer bdv )
	{
		this.t = new AffineTransform3D();
		final Frame frame = new Frame( "Current Global Transformation" );
		frame.setSize( 400, 200 );

		/* Instantiation */
		final GridBagLayout layout = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();

		final Label text1 = new Label( "1.00000   0.00000   0.00000   0.00000", Label.CENTER );
		final Label text2 = new Label( "0.00000   1.00000   0.00000   0.00000", Label.CENTER );
		final Label text3 = new Label( "0.00000   0.00000   1.00000   0.00000", Label.CENTER );

		text1.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text2.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );
		text3.setFont( new Font( Font.MONOSPACED, Font.PLAIN, 14 ) );

		final Button apply = new Button( "Apply Transformation" );
		final Button cancel = new Button( "Cancel" );
		final Checkbox ignoreScale = new Checkbox( "Ignore scaling factor from BigDataViewer", ignoreScaling );

		/* Location */
		frame.setLayout( layout );

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;

		frame.add( text1, c );

		++c.gridy;
		frame.add( text2, c );

		++c.gridy;
		frame.add( text3, c );

		c.insets = new Insets( 20,0,0,0 );
		++c.gridy;
		frame.add( ignoreScale, c );

		c.insets = new Insets( 20,0,0,0 );
		++c.gridy;
		frame.add( apply, c );

		c.insets = new Insets( 0,0,0,0 );
		++c.gridy;
		frame.add( cancel, c );

		apply.addActionListener( new ApplyButtonListener( frame, bdv ) );
		cancel.addActionListener( new CancelButtonListener( frame, bdv ) );
		ignoreScale.addItemListener( new ItemListener(){ public void itemStateChanged( final ItemEvent arg0 ) { ignoreScaling = ignoreScale.getState(); } });

		frame.setVisible( true );

		timer = new Timer();
		timer.schedule( new BDVChecker( bdv, text1, text2, text3 ), 500 );
	}

	public AffineTransform3D getTransform() { return t; }
	public boolean isRunning() { return isRunning; }
	public boolean wasCancelled() { return wasCancelled; }

	protected void close( final Frame parent, final BigDataViewer bdv )
	{
		if ( parent != null )
			parent.dispose();

		isRunning = false;
	}

	protected class CancelButtonListener implements ActionListener
	{
		final Frame parent;
		final BigDataViewer bdv;

		public CancelButtonListener( final Frame parent, final BigDataViewer bdv )
		{
			this.parent = parent;
			this.bdv = bdv;
		}
		
		@Override
		public void actionPerformed( final ActionEvent arg0 ) 
		{ 
			wasCancelled = true;
			close( parent, bdv );
		}
	}

	protected class BDVChecker extends TimerTask
	{
		final BigDataViewer bdv;
		final Label text1, text2, text3;

		public BDVChecker(
				final BigDataViewer bdv,
				final Label text1,
				final Label text2,
				final Label text3 )
		{
			this.bdv = bdv;
			this.text1 = text1;
			this.text2 = text2;
			this.text3 = text3;
		}

		@Override
		public void run()
		{
			if ( isRunning )
			{
				if ( bdv != null )
					bdv.getViewer().getState().getViewerTransform( t );

				t.set( 0, 0, 3 );
				t.set( 0, 1, 3 );
				t.set( 0, 2, 3 );

				if ( ignoreScaling )
				{
					final double[] m = new double[ 16 ];
					int i = 0;
					for ( int row = 0; row < 3; ++row )
						for ( int col = 0; col < 4; ++col )
							m[ i++ ] = t.get( row, col );
	
					m[ 15 ] = 1;
	
					final Transform3D trans = new Transform3D( m );	
					trans.setScale( 1 );
					trans.get( m );

					i = 0;
					for ( int row = 0; row < 3; ++row )
						for ( int col = 0; col < 4; ++col )
							t.set( m[ i++ ], row, col );
				}

				final DecimalFormat df = new DecimalFormat( "0.00000" );

				text1.setText(
						df.format( t.get( 0, 0 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 0, 1 ) ).substring( 0, 7 ) + "   " +
						df.format( t.get( 0, 2 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 0, 3 ) ).substring( 0, 7 ) );

				text2.setText(
						df.format( t.get( 1, 0 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 1, 1 ) ).substring( 0, 7 ) + "   " +
						df.format( t.get( 1, 2 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 1, 3 ) ).substring( 0, 7 ) );

				text3.setText(
						df.format( t.get( 2, 0 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 2, 1 ) ).substring( 0, 7 ) + "   " +
						df.format( t.get( 2, 2 ) ).substring( 0, 7 ) + "   " + df.format( t.get( 2, 3 ) ).substring( 0, 7 ) );

				// Reschedule myself (new instance is required, why?)
				timer.schedule( new BDVChecker( bdv,text1, text2, text3 ), 500 );
			}
		}
		
	}
	
	protected class ApplyButtonListener implements ActionListener
	{
		final Frame parent;
		final BigDataViewer bdv;

		public ApplyButtonListener( final Frame parent, final BigDataViewer bdv )
		{
			this.parent = parent;
			this.bdv = bdv;
		}
		
		@Override
		public void actionPerformed( final ActionEvent arg0 ) 
		{ 
			wasCancelled = false;
			close( parent, bdv );
		}
	}

	public static void disposeViewerWindow( final BigDataViewer bdv )
	{
		try
		{
			SwingUtilities.invokeAndWait( new Runnable()
			{
				@Override
				public void run()
				{
					final ViewerFrame frame = bdv.getViewerFrame();
					final WindowEvent windowClosing = new WindowEvent( frame, WindowEvent.WINDOW_CLOSING );
					frame.dispatchEvent( windowClosing );
				}
			} );
		}
		catch ( final Exception e )
		{}
	}

}
