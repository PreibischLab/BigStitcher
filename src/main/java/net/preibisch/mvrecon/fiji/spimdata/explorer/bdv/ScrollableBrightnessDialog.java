/*
 * #%L
 * BigDataViewer core classes with minimal dependencies
 * %%
 * Copyright (C) 2012 - 2016 Tobias Pietzsch, Stephan Saalfeld, Stephan Preibisch,
 * Jean-Yves Tinevez, HongKee Moon, Johannes Schindelin, Curtis Rueden, John Bogovic
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package net.preibisch.mvrecon.fiji.spimdata.explorer.bdv;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.util.InputActionBindings;

import bdv.BigDataViewer;
import bdv.BigDataViewerActions;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.SetupAssignments;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;


/**
 * Adjust brightness and colors for individual (or groups of) {@link BasicViewSetup setups} and having a scrollpane if necessary
 *
 * @author Stephan Preibisch
 */
public class ScrollableBrightnessDialog extends BrightnessDialog
{
	private static final long serialVersionUID = -2021647829067995526L;

	public static void setAsBrightnessDialog( final BigDataViewer bdv )
	{
		final InputActionBindings inputActionBindings = bdv.getViewerFrame().getKeybindings();
		final ActionMap am = inputActionBindings.getConcatenatedActionMap();
		//final ToggleDialogAction tda = (ToggleDialogAction)am.getParent().get( BigDataViewerActions.BRIGHTNESS_SETTINGS ); // the old one

		am.getParent().put(
				BigDataViewerActions.BRIGHTNESS_SETTINGS,
				new ToggleDialogActionBrightness(
						BigDataViewerActions.BRIGHTNESS_SETTINGS,
						new ScrollableBrightnessDialog( bdv.getViewerFrame(), bdv.getSetupAssignments() ) ) );
	}

	public static void updateBrightnessPanels( final BigDataViewer bdv )
	{
		// without running this in a new thread can lead to a deadlock, not sure why
		
		new Thread( new Runnable()
		{
			
			@Override
			public void run()
			{
				try
				{
					SwingUtilities.invokeAndWait( new Runnable()
					{
						@Override
						public void run()
						{
							if ( bdv == null )
								return;

							final InputActionBindings inputActionBindings = bdv.getViewerFrame().getKeybindings();

							if ( inputActionBindings == null )
								return;

							final ActionMap am = inputActionBindings.getConcatenatedActionMap();

							if ( am == null )
								return;

							final Action dialog = am.getParent().get( BigDataViewerActions.BRIGHTNESS_SETTINGS );

							if ( dialog == null || !ToggleDialogActionBrightness.class.isInstance( dialog ) )
								return;

							((ToggleDialogActionBrightness)dialog).updatePanels();
						}
					} );
				} catch ( InvocationTargetException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch ( InterruptedException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}).start();
	}

	final MinMaxPanels minMaxPanels;
	final ColorsPanel colorsPanel;

	public ScrollableBrightnessDialog( final Frame owner, final SetupAssignments setupAssignments )
	{
		super( owner, setupAssignments );

		this.setSize( this.getSize().width + 5, this.getHeight() + 20 );

		final Container content = getContentPane();

		this.minMaxPanels = (MinMaxPanels)content.getComponent( 0 );
		this.colorsPanel = (ColorsPanel)content.getComponent( 1 );

		content.removeAll();

		JPanel panel = new JPanel( new BorderLayout() );

		panel.add( minMaxPanels, BorderLayout.NORTH );
		panel.add( colorsPanel, BorderLayout.SOUTH );

		JScrollPane jspane = new JScrollPane( panel,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
				jspane.getVerticalScrollBar().setBlockIncrement(5);
				jspane.getVerticalScrollBar().setAutoscrolls(true);
				jspane.getHorizontalScrollBar().setAutoscrolls(true);

		content.add( jspane );

		this.addWindowListener( new WindowListener()
		{
			@Override
			public void windowOpened( WindowEvent e ){}

			@Override
			public void windowIconified( WindowEvent e ){}

			@Override
			public void windowDeiconified( WindowEvent e ){}

			@Override
			public void windowDeactivated( WindowEvent e ) {}

			@Override
			public void windowClosing( WindowEvent e ) {}

			@Override
			public void windowClosed( WindowEvent e ) {}

			@Override
			public void windowActivated( WindowEvent e ) { updatePanels(); }
		} );

		this.validate();
	}

	public void updatePanels()
	{
		colorsPanel.recreateContent();
		minMaxPanels.recreateContent();

		validate();
	}
}
