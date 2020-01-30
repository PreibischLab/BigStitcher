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
package net.preibisch.mvrecon.fiji.plugin.util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;

import javax.swing.JFrame;

import ij.IJ;
import ij.ImageJ;
import ij.WindowManager;

public class MultiWindowLayoutHelper
{

	/**
	 * move a component (such as a JFrame Window) to x and y, expressed as fractions of the screen width and height
	 * of the screen the component is on. The top-left corner of the component is moved to the desired location.
	 * @param component the component to move
	 * @param x desired left position (fraction of screen width)
	 * @param y desired top position (fraction of screen height)
	 */
	public static void moveToScreenFraction(Component component, double x, double y)
	{
		if (component == null)
			return;

		// ensure we stay within the screen
		x = Math.min( Math.max( x, 0 ), 1 );
		y = Math.min( Math.max( y, 0 ), 1 );

		// get the bounds of the screen we are on (without insets := toolbars, etc.)
		final Rectangle screenRect = component.getGraphicsConfiguration().getBounds();
		final Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets( component.getGraphicsConfiguration() );
		final Rectangle boundsWithoutInsets = new Rectangle(
				screenRect.x + screenInsets.left,
				screenRect.y + screenInsets.top,
				screenRect.width - screenInsets.left - screenInsets.right,
				screenRect.height - screenInsets.top - screenInsets.bottom );

		final Dimension compontenSize = component.getSize();

		final int newX = (int) Math.min(boundsWithoutInsets.x + x * boundsWithoutInsets.width, (boundsWithoutInsets.x + boundsWithoutInsets.width) - compontenSize.getWidth());
		final int newY = (int) Math.min(boundsWithoutInsets.y + y * boundsWithoutInsets.height, (boundsWithoutInsets.y + boundsWithoutInsets.height) - compontenSize.getHeight());

		component.setLocation( newX, newY );
	}

	/**
	 * get the ImageJ log window (if present)
	 * @return the log window component or null if the log is not open;
	 */
	public static Component getIJLogWindow()
	{
		// this is how ImageJ itself does it
		final Component log = WindowManager.getWindow("Log");
		return log;
	}

	public static void main(String[] args)
	{
		final JFrame theFrame = new JFrame(GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0].getDefaultConfiguration());
		theFrame.setSize( 100, 100 );
		theFrame.pack();
		theFrame.setVisible( true );

		moveToScreenFraction(theFrame, 0.5, 0.0);
		new ImageJ();
		IJ.log( "AAA" );
		moveToScreenFraction( getIJLogWindow(), 0, 0.8 );
	}
}
