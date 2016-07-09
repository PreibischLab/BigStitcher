package gui;

import ij.ImageJ;
import input.FractalSpimDataGenerator;
import input.GenerateSpimData;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import spim.fiji.spimdata.explorer.ViewSetupExplorer;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;

public class FilteredAndGroupedExplorer< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > >
{
	final JFrame frame;
	FilteredAndGroupedExporerPanel<AS, X> panel;
	
	public FilteredAndGroupedExplorer( final AS data, final String xml, final X io )
	{
		frame = new JFrame( "Stitching Explorer" );
		panel = new FilteredAndGroupedExporerPanel< AS, X >( this, data, xml, io );

		frame.add( panel, BorderLayout.CENTER );
		frame.setSize( panel.getPreferredSize() );

		frame.addWindowListener(
				new WindowAdapter()
				{
					@Override
					public void windowClosing( WindowEvent evt )
					{
						quit();
					}
				});

		frame.pack();
		frame.setVisible( true );

		// set the initial focus to the table
		panel.table.requestFocus();
	}
	
	public void quit()
	{
		for ( final SelectedViewDescriptionListener< AS > l : panel.getListeners() )
			l.quit();

		panel.getListeners().clear();
		
		frame.setVisible( false );
		frame.dispose();

		StitchingExplorerPanel.currentInstance = null;
	}
	
	public AS getSpimData() { return panel.getSpimData(); }
	public FilteredAndGroupedExporerPanel< AS, X > getPanel() { return panel; }
	public JFrame getFrame() { return frame; }
	public void addListener( final SelectedViewDescriptionListener< AS > listener ) { panel.addListener( listener ); }
	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners() { return panel.getListeners(); }

	public static void main( String[] args )
	{
		//new ImageJ();
		//new ViewSetupExplorer<>( GenerateSpimData.grid3x2(), null, null );
		
		
		// shift and scale the fractal
		final AffineTransform3D m = new AffineTransform3D();
		double scale = 200;
		m.set( scale, 0.0f, 0.0f, 0.0f, 
			   0.0f, scale, 0.0f, 0.0f,
			   0.0f, 0.0f, scale, 0.0f);
		
		final AffineTransform3D mShift = new AffineTransform3D();
		double shift = 100;
		mShift.set( 1.0f, 0.0f, 0.0f, shift, 
					0.0f, 1.0f, 0.0f, shift,
					0.0f, 0.0f, 1.0f, shift
					);
		final AffineTransform3D mShift2 = new AffineTransform3D();
		double shift2x = 1200;
		double shift2y = 300;
		mShift2.set( 1.0f, 0.0f, 0.0f, shift2x, 
					0.0f, 1.0f, 0.0f, shift2y,
					0.0f, 0.0f, 1.0f, 0.0f
					);
		
		final AffineTransform3D mShift3 = new AffineTransform3D();
		double shift3x = 500;
		double shift3y = 1300;
		mShift3.set( 1.0f, 0.0f, 0.0f, shift3x, 
					0.0f, 1.0f, 0.0f, shift3y,
					0.0f, 0.0f, 1.0f, 0.0f
					);
		
		
		AffineTransform3D m2 = m.copy();
		AffineTransform3D m3 = m.copy();
		m.preConcatenate( mShift );
		m2.preConcatenate( mShift2 );
		m3.preConcatenate( mShift3 );
		
		Interval start = new FinalInterval( new long[] {-399,-399,0},  new long[] {0, 0,1});
		List<Interval> intervals = FractalSpimDataGenerator.generateTileList( 
				start, 7, 6, 0.2f );
		
		List<RealLocalizable> falseStarts = FractalSpimDataGenerator.getTileMins(
														FractalSpimDataGenerator.generateTileList( start, 7, 6, 0.30f ));
		
		FractalSpimDataGenerator fsdg = new FractalSpimDataGenerator( 3 );
		fsdg.addFractal( m );
		fsdg.addFractal( m2 );
		fsdg.addFractal( m3 );
		
		SpimData sd = fsdg.generateSpimData( intervals , falseStarts);

		
		
		//new FilteredAndGroupedExplorer< SpimData, XmlIoSpimData >( GenerateSpimData.grid3x2(), null, null );
		new FilteredAndGroupedExplorer< SpimData, XmlIoSpimData >( sd, null, null );
	}
}
