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

import ij.gui.GenericDialog;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.RenderingHints;

public class MyMultiLineLabel extends Canvas
{
	private static final long serialVersionUID = 1L;

	protected String[] lines;
	protected int num_lines;
	protected int margin_width = 6;
	protected int margin_height = 6;
	protected int line_height;
	protected int line_ascent;
	protected int[] line_widths;
	protected int min_width, max_width;
    
	final GenericDialog gd;
	final Panel panel;
	
    // Breaks the specified label up into an array of lines.
    public MyMultiLineLabel( final GenericDialog gd, final Panel panel, final String[] label )
    {
        this( gd, panel, label, 0 );
    }

    public MyMultiLineLabel( final GenericDialog gd, final Panel panel, final String[] label, final int minimumWidth )
    {
        num_lines = label.length;
        lines = label;
        line_widths = new int[ num_lines ];
        min_width = minimumWidth;
        this.gd = gd;
        this.panel = panel;
    }
    
    public String[] getLines() { return lines; }
    public void setText( final String[] lines )
    {
    	for ( int i = 0; i < this.lines.length; ++i )
    	{
    		if ( i >= lines.length )
    			this.lines[ i ] = " ";
    		else
    			this.lines[ i ] = lines[ i ];
    	}
    	
    	updateLabel();
    }
    
    public void updateLabel()
    {
    	this.update( this.getGraphics() );
    }

    /* Adds a message consisting of one or more lines of text,
    which will be displayed using the specified font and color. */
    public static MyMultiLineLabel addMessage( final GenericDialog gd, final String[] text, final Font font, final Color color )
    {
    	final Panel panel = new Panel();
    	final MyMultiLineLabel l = new MyMultiLineLabel( gd, panel, text );
    	
    	l.setFont( font );
    	l.setForeground( color );

    	panel.add( l );
    	gd.addPanel( panel );
    	
    	return l;
    }

    // Figures out how wide each line of the label
    // is, and how wide the widest line is.
    protected void measure()
    {
        final FontMetrics fm = this.getFontMetrics( this.getFont() );
        // If we don't have font metrics yet, just return.
        if (fm == null) return;
        
        line_height = fm.getHeight();
        line_ascent = fm.getAscent();
        max_width = 0;
        
        for( int i = 0; i < num_lines; i++ )
        {
            line_widths[ i ] = fm.stringWidth( lines[ i ] );
            if ( line_widths[ i ] > max_width )
            	max_width = line_widths[i];
        }
    }
    

    public void setFont( final Font f )
    {
        super.setFont( f );
        measure();
        repaint();
    }


    // This method is invoked after our Canvas is first created
    // but before it can actually be displayed.  After we've
    // invoked our superclass's addNotify() method, we have font
    // metrics and can successfully call measure() to figure out
    // how big the label is.
    public void addNotify()
    {
        super.addNotify();
        measure();
    }
    

    // Called by a layout manager when it wants to
    // know how big we'd like to be.  
    public Dimension getPreferredSize()
    {
        return new Dimension( Math.max( min_width, max_width + 2*margin_width ), 
                     num_lines * line_height + 2*margin_height );
    }
    

    // Called when the layout manager wants to know
    // the bare minimum amount of space we need to get by.
    public Dimension getMinimumSize()
    {
        return new Dimension(Math.max(min_width, max_width), num_lines * line_height);
    }
    
    // Draws the label
    public void paint( final Graphics g )
    {
        int x, y;
        final Dimension d = this.getSize();
        if ( !ij.IJ.isLinux() ) setAntialiasedText( g );
        y = line_ascent + (d.height - num_lines * line_height)/2;
        for( int i = 0; i < num_lines; i++, y += line_height )
        {
            x = margin_width;
            g.drawString( lines[ i ], x, y );
        }
    }

    void setAntialiasedText( final Graphics g )
    {
    	final Graphics2D g2d = (Graphics2D)g;
    	g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

}
