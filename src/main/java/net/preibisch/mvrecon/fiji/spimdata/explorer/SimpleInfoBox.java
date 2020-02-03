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

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class SimpleInfoBox
{
	final JFrame frame;

	public SimpleInfoBox( final String title, final String text )
	{
		frame = new JFrame( title );

		final JTextArea textarea = new JTextArea( text );

		final JPanel panel = new JPanel();
		panel.add( textarea, BorderLayout.CENTER );
		final JScrollPane pane = new JScrollPane( panel );
		frame.add( pane, BorderLayout.CENTER );

		frame.pack();

		final Dimension d = pane.getSize();
		d.setSize( d.width + 20, d.height + 10 );
		pane.setSize( d );
		pane.setPreferredSize( d );
		frame.setPreferredSize( d );

		frame.pack();
		frame.setVisible( true );
	}
}
