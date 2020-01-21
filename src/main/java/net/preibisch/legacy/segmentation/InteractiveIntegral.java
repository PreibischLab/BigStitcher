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
package net.preibisch.legacy.segmentation;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import fiji.tool.SliceListener;
import fiji.tool.SliceObserver;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.plugin.PlugIn;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.type.numeric.integer.LongType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.util.Util;

/**
 * Gui for Integral: An interactive tool for determining the required radius and peak threshold
 * 
 * @author Stephan Preibisch, Marwan Zouinkhi
 */
public class InteractiveIntegral extends Integral implements PlugIn
{
	final int scrollbarSize = 1000;

	int radius1 = 1;
	int radius2 = 3;
	float threshold = 0.0001f;
	double min, max;

	int radiusMin = 1;
	int radiusMax = 29;
	int radiusInit1 = 300;
	int radiusInit2 = 400;

	float thresholdMin = 0.0001f;
	float thresholdMax = 1f;
	int thresholdInit = 500;
	
	SliceObserver sliceObserver;
	ImagePlus imp;
	int channel = 0;
	Rectangle rectangle;
	Image<FloatType> source, sliceImage;
	Image<LongType> integralImage;
	
	ArrayList<SimplePeak> peaks;
	
	Color originalColor = new Color( 0.8f, 0.8f, 0.8f );
	Color inactiveColor = new Color( 0.95f, 0.95f, 0.95f );
	boolean isComputing = false;
	boolean isStarted = false;
	boolean enableRadius2 = false;
	
	boolean lookForMinima = false;
	boolean lookForMaxima = true;
	
	public static enum ValueChange { RADIUS, THRESHOLD, SLICE, MINMAX, ALL }
	
	boolean isFinished = false;
	boolean wasCanceled = false;
	public boolean isFinished() { return isFinished; }
	public boolean wasCanceld() { return wasCanceled; }
	public void setInitialRadii( int r1, int r2 ) 
	{
		if ( r2 <= r1 )
			r2 = r1 + 2;
		
		radius1 = r1; 
		radiusInit1 = computeScrollbarPositionFromValue( radius1, radiusMin, radiusMax, scrollbarSize );
		radius2 = r2; 
		radiusInit2 = computeScrollbarPositionFromValue( radius2, radiusMin, radiusMax, scrollbarSize );
	}
	public void setInitialRadius( final int r1 ) 
	{
		setInitialRadii( r1, computeRadius2( r1 ) ); 
	}
	public int getRadius1() { return radius1; }
	public int getRadius2() { return radius2; }
	public double getThreshold() { return threshold; }
	public void setThreshold( final float value ) 
	{ 
		threshold = value;
		final double log1001 = Math.log10( scrollbarSize + 1);
		thresholdInit = (int)Math.round( 1001-Math.pow(10, -(((threshold - thresholdMin)/(thresholdMax-thresholdMin))*log1001) + log1001 ) );
	}
	public boolean getRadius2WasAdjusted() { return enableRadius2; }
	public boolean getLookForMaxima() { return lookForMaxima; }
	public boolean getLookForMinima() { return lookForMinima; }
	public void setLookForMaxima( final boolean lookForMaxima ) { this.lookForMaxima = lookForMaxima; }
	public void setLookForMinima( final boolean lookForMinima ) { this.lookForMinima = lookForMinima; }
	
	public void setRadiusMax( final int radiusMax ) { this.radiusMax = radiusMax; }
	
	// for the case that it is needed again, we can save one conversion
	public Image<FloatType> getConvertedImage() { return source; }
	
	public InteractiveIntegral( final ImagePlus imp, final int channel ) 
	{ 
		this.imp = imp;
		this.channel = channel;
	}
	public InteractiveIntegral( final ImagePlus imp ) { this.imp = imp; }
	public InteractiveIntegral() {}

	public void setMinIntensityImage( final double min ) { this.min = min; }
	public void setMaxIntensityImage( final double max ) { this.max = max; }

	@Override
	public void run( String arg )
	{
		if ( imp == null )
			imp = WindowManager.getCurrentImage();
		
		if ( imp.getType() == ImagePlus.COLOR_RGB || imp.getType() == ImagePlus.COLOR_256 )
		{			
			IJ.log( "Color images are not supported, please convert to 8, 16 or 32-bit grayscale" );
			return;
		}
		
		// copy the ImagePlus into an ArrayImage<FloatType> for faster access
		source = convertToFloat( imp, channel, 0 );
		sliceImage = source.getImageFactory().createImage( new int[]{ source.getDimension( 0 ), source.getDimension( 1 ) } );
		
		// compute min/max
		if ( Double.isNaN( min ) || Double.isNaN( max ) || Double.isInfinite( min ) || Double.isInfinite( max ) || min == max )
		{
			FloatType min = new FloatType();
			FloatType max = new FloatType();
	
			DOM.computeMinMax( source, min, max );
	
			this.min = min.get();
			this.max = max.get();
		}

		// compute the integral image
		integralImage = computeIntegralImage( source );
		
		// show the interactive kit
		displaySliders();

		// add listener to the imageplus slice slider
		sliceObserver = new SliceObserver( imp, new ImagePlusListener() );

		// compute first version
		updatePreview( ValueChange.ALL );		
		isStarted = true;
	}
	
	/**
	 * Updates the Preview with the current parameters (radius, threshold, roi, slicenumber)
	 * 
	 * @param change - what did change
	 */
	protected void updatePreview( final ValueChange change )
	{		
		
		// compute the Difference Of Mean if necessary
		if ( peaks == null || change == ValueChange.RADIUS || change == ValueChange.SLICE || change == ValueChange.ALL )
		{
			int slice = (imp.getCurrentSlice()-1)/imp.getNChannels();
	        
			final int s1 = radius1*2 + 1;
			final int s2 = radius2*2 + 1;
			
			computeDifferencOfMeanSlice( integralImage, sliceImage, slice, s1, s1, s1, s2, s2, s2, (float)min, (float)max );

	        //ImageJFunctions.show( sliceImage );
	        
			peaks = findPeaks( sliceImage, thresholdMin );
			//System.out.println("t=" + threshold + " -> " + peaks.size() );
		}
		
		// extract peaks to show
		Overlay o = imp.getOverlay();
		
		if ( o == null )
		{
			o = new Overlay();
			imp.setOverlay( o );
		}
		
		o.clear();
		
		if ( peaks != null )
		{
			int avgSize = (radius1 + radius2 + 1)/2;
			
			for ( final SimplePeak peak : peaks )
			{	
				if ( ( peak.isMax && lookForMaxima ) || ( peak.isMin && lookForMinima ) )
				{
					final float x = peak.location[ 0 ]; 
					final float y = peak.location[ 1 ];
					
					if ( Math.abs( peak.intensity ) > threshold )
					{
						final OvalRoi or = new OvalRoi( Util.round( x - avgSize ), Util.round( y - avgSize ), radius1+radius2+1, radius1+radius2+1 );
						
						if ( peak.isMax )
							or.setStrokeColor( Color.green );
						else if ( peak.isMin )
							or.setStrokeColor( Color.red );
						
						o.add( or );
					}
				}
			}
		}
		imp.updateAndDraw();
		
		isComputing = false;
	}
	
	/**
	 * Instantiates the panel for adjusting the paramters
	 */
	protected void displaySliders()
	{
		final Frame frame = new Frame( "Adjust Difference-of-Mean Values" );
		frame.setSize( 400, 330 );
		
		/* Instantiation */		
		final GridBagLayout layout = new GridBagLayout();
		final GridBagConstraints c = new GridBagConstraints();
				
		final Scrollbar radius1 = new Scrollbar ( Scrollbar.HORIZONTAL, radiusInit1, 10, 0, 10 + scrollbarSize );		
	    this.radius1 = Math.round( computeValueFromScrollbarPosition( radiusInit1, radiusMin, radiusMax, scrollbarSize) ); 
	    
	    final Scrollbar threshold = new Scrollbar ( Scrollbar.HORIZONTAL, thresholdInit, 10, 0, 10 + scrollbarSize );
	    final float log1001 = (float)Math.log10( scrollbarSize + 1);
	    
	    this.threshold = thresholdMin + ( (log1001 - (float)Math.log10(1001-thresholdInit))/log1001 ) * (thresholdMax-thresholdMin);
	    
	    this.radius2 = computeRadius2( this.radius1 );
	    final int radius2init = computeScrollbarPositionFromValue( this.radius2, radiusMin, radiusMax, scrollbarSize ); 
		final Scrollbar radius2 = new Scrollbar ( Scrollbar.HORIZONTAL, radius2init, 10, 0, 10 + scrollbarSize );
		
	    final Label radiusText1 = new Label( "Radius 1 = " + this.radius1, Label.CENTER );
	    final Label radiusText2 = new Label( "Radius 2 = " + this.radius2, Label.CENTER );
	    	    
	    final Label thresholdText = new Label( "Threshold = " + this.threshold, Label.CENTER );
	    final Button button = new Button( "Done" );
	    final Button cancel = new Button( "Cancel" );

	    final Checkbox radius2Enable = new Checkbox( "Enable Manual Adjustment of Radius 2 ", enableRadius2 );
	    final Checkbox min = new Checkbox( "Look for Minima (red)", lookForMinima );
	    final Checkbox max = new Checkbox( "Look for Maxima (green)", lookForMaxima );
	    
	    /* Location */
	    frame.setLayout( layout );
	    
	    c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
	    frame.add ( radius1, c );

	    ++c.gridy;
	    frame.add( radiusText1, c );

	    ++c.gridy;
	    frame.add ( radius2, c );

	    ++c.gridy;
	    frame.add( radiusText2, c );
	    
	    ++c.gridy;
	    c.insets = new Insets(0,65,0,65);
	    frame.add( radius2Enable, c );
	    
		++c.gridy;
	    c.insets = new Insets(10,0,0,0);
	    frame.add ( threshold, c );
	    c.insets = new Insets(0,0,0,0);

	    ++c.gridy;
	    frame.add( thresholdText, c );

	    ++c.gridy;
	    c.insets = new Insets(0,130,0,75);
	    frame.add( min, c );

	    ++c.gridy;
	    c.insets = new Insets(0,125,0,75);
	    frame.add( max, c );

	    ++c.gridy;
	    c.insets = new Insets(10,150,0,150);
	    frame.add( button, c );

	    ++c.gridy;
	    c.insets = new Insets(10,150,0,150);
	    frame.add( cancel, c );

	    /* Configuration */
	    radius1.addAdjustmentListener( new Radius1Listener( radiusText1, radiusMin, radiusMax, scrollbarSize, radius1, radius2, radiusText2 ) );
	    radius2.addAdjustmentListener( new Radius2Listener( radiusMin, radiusMax, scrollbarSize, radius2, radiusText2 ) );
	    threshold.addAdjustmentListener( new ThresholdListener( thresholdText, thresholdMin, thresholdMax ) );
	    button.addActionListener( new FinishButtonListener( frame, false ) );
	    cancel.addActionListener( new FinishButtonListener( frame, true ) );
		min.addItemListener( new MinListener() );
		max.addItemListener( new MaxListener() );
		radius2Enable.addItemListener( new EnableListener( radius2, radiusText2 ) );
		
		if ( !enableRadius2 )
			radius2Enable.setEnabled( false );
		
	    frame.addWindowListener( new FrameListener( frame ) );
		
		frame.setVisible( true );
		
		originalColor = radius2.getBackground();
		radius2.setBackground( inactiveColor );
	    radiusText1.setFont( radiusText1.getFont().deriveFont( Font.BOLD ) );
	    thresholdText.setFont( thresholdText.getFont().deriveFont( Font.BOLD ) );
	}

	protected class EnableListener implements ItemListener
	{
		final Scrollbar radius2;
		final Label radiusText2;
		
		public EnableListener( final Scrollbar radius2, final Label radiusText2 )
		{
			this.radiusText2 = radiusText2;
			this.radius2 = radius2;
		}
		
		@Override
		public void itemStateChanged( final ItemEvent arg0 )
		{
			if ( arg0.getStateChange() == ItemEvent.DESELECTED )
			{
				radiusText2.setFont( radiusText2.getFont().deriveFont( Font.PLAIN ) );
				radius2.setBackground( inactiveColor );
				enableRadius2 = false;
			}
			else if ( arg0.getStateChange() == ItemEvent.SELECTED  )
			{
				radiusText2.setFont( radiusText2.getFont().deriveFont( Font.BOLD ) );
				radius2.setBackground( originalColor );
				enableRadius2 = true;
			}
		}
	}
	
	protected class MinListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent arg0 )
		{
			boolean oldState = lookForMinima;
			
			if ( arg0.getStateChange() == ItemEvent.DESELECTED )				
				lookForMinima = false;			
			else if ( arg0.getStateChange() == ItemEvent.SELECTED  )
				lookForMinima = true;
			
			if ( lookForMinima != oldState )
			{
				while ( isComputing )
					SimpleMultiThreading.threadWait( 10 );
				
				updatePreview( ValueChange.MINMAX );
			}
		}
	}

	protected class MaxListener implements ItemListener
	{
		@Override
		public void itemStateChanged( final ItemEvent arg0 )
		{
			boolean oldState = lookForMaxima;
			
			if ( arg0.getStateChange() == ItemEvent.DESELECTED )				
				lookForMaxima = false;			
			else if ( arg0.getStateChange() == ItemEvent.SELECTED  )
				lookForMaxima = true;
			
			if ( lookForMaxima != oldState )
			{
				while ( isComputing )
					SimpleMultiThreading.threadWait( 10 );
				
				updatePreview( ValueChange.MINMAX );
			}
		}
	}

	protected class FinishButtonListener implements ActionListener
	{
		final Frame parent;
		final boolean cancel;
		
		public FinishButtonListener( final Frame parent, final boolean cancel )
		{
			this.parent = parent;
			this.cancel = cancel;
		}
		
		@Override
		public void actionPerformed( final ActionEvent arg0 ) 
		{
			wasCanceled = cancel;
			close( parent, sliceObserver, imp );
		}
	}
	
	protected class FrameListener extends WindowAdapter
	{
		final Frame parent;
		
		public FrameListener( Frame parent )
		{
			super();
			this.parent = parent;
		}
		
		@Override
        public void windowClosing (WindowEvent e) 
		{ 
			close( parent, sliceObserver, imp );
		}
	}
	
	protected final void close( final Frame parent, final SliceObserver sliceObserver, final ImagePlus imp )
	{
		if ( parent != null )
			parent.dispose();
		
		if ( sliceObserver != null )
			sliceObserver.unregister();
		
		if ( imp != null )
		{
			imp.getOverlay().clear();
			imp.updateAndDraw();
		}
		
		isFinished = true;
	}

	protected class Radius2Listener implements AdjustmentListener
	{
		final float min, max;
		final int scrollbarSize;
		
		final Scrollbar radiusScrollbar2;
		final Label radius2Label;
		
		public Radius2Listener( final float min, final float max, final int scrollbarSize, final Scrollbar radiusScrollbar2, final Label radius2Label )
		{
			this.min = min;
			this.max = max;
			this.scrollbarSize = scrollbarSize;
			
			this.radiusScrollbar2 = radiusScrollbar2;
			this.radius2Label = radius2Label;
		}
		
		@Override
		public void adjustmentValueChanged( final AdjustmentEvent event )
		{
			if ( enableRadius2 )
			{
				radius2 = Math.round( computeValueFromScrollbarPosition( event.getValue(), min, max, scrollbarSize ) );
								
				if ( radius2 <= radius1 )
				{
					radius2 = radius1 + 1;
					radiusScrollbar2.setValue( computeScrollbarPositionFromValue( radius2, min, max, scrollbarSize ) );
				}
				
				radius2Label.setText( "Radius 2 = " + radius2 );
				
				if ( !event.getValueIsAdjusting() )
				{
					while ( isComputing )
					{
						SimpleMultiThreading.threadWait( 10 );
					}
					updatePreview( ValueChange.RADIUS );
				}
				
			}
			else
			{
				// if no manual adjustment simply reset it
				radiusScrollbar2.setValue( computeScrollbarPositionFromValue( radius2, min, max, scrollbarSize ) );
			}
		}		
	}

	protected class Radius1Listener implements AdjustmentListener
	{
		final Label label;
		final float min, max;
		final int scrollbarSize;
		
		final Scrollbar radiusScrollbar1;
		final Scrollbar radiusScrollbar2;		
		final Label radiusText2;
		
		public Radius1Listener( final Label label, final float min, final float max, final int scrollbarSize, final Scrollbar radiusScrollbar1,  final Scrollbar radiusScrollbar2, final Label radiusText2  )
		{
			this.label = label;
			this.min = min;
			this.max = max;
			this.scrollbarSize = scrollbarSize;
			
			this.radiusScrollbar1 = radiusScrollbar1;
			this.radiusScrollbar2 = radiusScrollbar2;
			this.radiusText2 = radiusText2;
		}
		
		@Override
		public void adjustmentValueChanged( final AdjustmentEvent event )
		{
			radius1 = Math.round( computeValueFromScrollbarPosition( event.getValue(), min, max, scrollbarSize ) );			
			
			if ( !enableRadius2 )
			{
				radius2 = computeRadius2( radius1 );
				radiusText2.setText( "Radius 2 = " + radius2 );			    
				radiusScrollbar2.setValue( computeScrollbarPositionFromValue( radius2, min, max, scrollbarSize ) );
			}
			else if ( radius1 >= radius2 )
			{
				radius1 = radius2 - 2;
				radiusScrollbar1.setValue( computeScrollbarPositionFromValue( radius1, min, max, scrollbarSize ) );
			}
			
			label.setText( "Radius 1 = " + radius1 );
			
			if ( !event.getValueIsAdjusting() )
			{
				while ( isComputing )
				{
					SimpleMultiThreading.threadWait( 10 );
				}
				updatePreview( ValueChange.RADIUS );
			}
		}		
	}

	protected static float computeValueFromScrollbarPosition( final int scrollbarPosition, final float min, final float max, final int scrollbarSize )
	{
		return min + (scrollbarPosition/(float)scrollbarSize) * (max-min);
	}

	protected static int computeScrollbarPositionFromValue( final float radius, final float min, final float max, final int scrollbarSize )
	{
		return Util.round( ((radius - min)/(max-min)) * scrollbarSize );
	}

	protected class ThresholdListener implements AdjustmentListener
	{
		final Label label;
		final float min, max;
		final float log1001 = (float)Math.log10(1001);
		
		public ThresholdListener( final Label label, final float min, final float max )
		{
			this.label = label;
			this.min = min;
			this.max = max;
		}
		
		@Override
		public void adjustmentValueChanged( final AdjustmentEvent event )
		{			
			threshold = min + ( (log1001 - (float)Math.log10(1001-event.getValue()))/log1001 ) * (max-min);
			label.setText( "Threshold = " + threshold );

			if ( !isComputing )
			{
				updatePreview( ValueChange.THRESHOLD );
			}
			else if ( !event.getValueIsAdjusting() )
			{
				while ( isComputing )
				{
					SimpleMultiThreading.threadWait( 10 );
				}
				updatePreview( ValueChange.THRESHOLD );
			}
		}		
	}

	protected class ImagePlusListener implements SliceListener
	{
		@Override
		public void sliceChanged(ImagePlus arg0)
		{
			if ( isStarted )
			{
				while ( isComputing )
				{
					SimpleMultiThreading.threadWait( 10 );
				}
				updatePreview( ValueChange.SLICE );
			}
		}		
	}
	
	public static void main( String[] args )
	{
		//new InteractiveDoG().displaySliders();
			
		new ImageJ();
		
		ImagePlus imp = new ImagePlus( "/Users/preibischs/Documents/Microscopy/SPIM/HisYFP-SPIM/spim_TL18_Angle0_cropped.tif" );
		//ImagePlus imp = new Opener().openImage( "D:/Documents and Settings/Stephan/My Documents/Downloads/1-315--0.08-isotropic-subvolume/1-315--0.08-isotropic-subvolume.tif" );
		imp.show();
		
		imp.setSlice( 27 );		
		
		InteractiveIntegral ii = new InteractiveIntegral();
		ii.setInitialRadius( 2 );
		ii.run( null ); 	
			
	}
}
