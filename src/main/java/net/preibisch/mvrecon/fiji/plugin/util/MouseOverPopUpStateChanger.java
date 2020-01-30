package net.preibisch.mvrecon.fiji.plugin.util;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JMenuItem;

public class MouseOverPopUpStateChanger implements ActionListener, MouseListener
{
	public static interface StateChanger
	{
		public void setSelectedState( final int state );
	}

	final JMenuItem[] items;
	final int myState;
	boolean hasMouseFocusWaiting = false;
	final StateChanger changer;

	public MouseOverPopUpStateChanger( final JMenuItem[] items, final int myState, final StateChanger changer )
	{
		this.items = items;
		this.myState = myState;
		this.changer = changer;
	}

	@Override
	public void actionPerformed( final ActionEvent e )
	{
		for ( int i = 0; i < items.length; ++i )
		{
			if ( i == myState )
			{
				items[ i ].setForeground( Color.RED );
				items[ i ].setFocusable( false );
			}
			else
				items[ i ].setForeground( Color.GRAY );
		}

		changer.setSelectedState( myState );

		this.hasMouseFocusWaiting = false;
	}

	@Override
	public void mouseEntered( MouseEvent e )
	{
		hasMouseFocusWaiting = true;

		new Thread( () ->
		{
			int countNull = 0;

			for ( int i = 0; i <= 7; ++i )
			{
				if ( items[ myState ].getMousePosition() == null )
					if ( i == 7 || ++countNull >= 2 )
						hasMouseFocusWaiting = false;

				if ( !hasMouseFocusWaiting )
					break;

				try { Thread.sleep( 100 ); } catch ( InterruptedException e1 ){}
			}

			if ( hasMouseFocusWaiting )
				this.actionPerformed( null );
		}).start();
	}

	@Override
	public void mouseExited( MouseEvent e )
	{
		hasMouseFocusWaiting = false;
	}

	@Override
	public void mouseClicked( MouseEvent e ) {}

	@Override
	public void mousePressed( MouseEvent e ) {}

	@Override
	public void mouseReleased( MouseEvent e ) {}
}