package net.preibisch.stitcher.gui.bdv;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;

import bdv.BigDataViewer;
import bdv.cache.CacheControl;
import bdv.viewer.ViewerPanel;
import bdv.viewer.overlay.MultiBoxOverlayRenderer;
import bdv.viewer.overlay.ScaleBarOverlayRenderer;
import bdv.viewer.render.MultiResolutionRenderer;
import bdv.viewer.state.ViewerState;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.PainterThread;
import net.imglib2.ui.RenderTarget;

public class BDVFlyThrough
{
	final public static ArrayList< AffineTransform3D > viewerTransforms = new ArrayList<>();
	public static boolean skipDialog = false;
	public static String defaultPath = "";
	public static int interpolateSteps = 100;

	public static void addCurrentViewerTransform( final BigDataViewer bdv )
	{
		AffineTransform3D currentViewerTransform = bdv.getViewer().getDisplay().getTransformEventHandler().getTransform().copy();
		viewerTransforms.add( currentViewerTransform );
		IOFunctions.println( "Added transform: " + currentViewerTransform  + ", #transforms=" + viewerTransforms.size() );
	}

	public static void clearAllViewerTransform()
	{
		viewerTransforms.clear();
		IOFunctions.println( "Cleared all transforms." );
	}

	public static void record( final BigDataViewer bdv, final boolean showScaleBar, final boolean showBoxes )
	{
		if ( !skipDialog )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "Select directory for movie" );
			gd.addDirectoryField( "Movie directory", defaultPath );
			gd.addNumericField( "Interpolation steps between keypoints", interpolateSteps, 0 );
	
			gd.showDialog();
			if ( gd.wasCanceled())
				return;
	
			defaultPath = gd.getNextString();
			interpolateSteps = (int)Math.round( gd.getNextNumber() );
		}

		IOFunctions.println( "Recording images for " + viewerTransforms.size() + " transforms, interpolated with " + interpolateSteps + " steps in between to directory " + defaultPath );


		final ViewerPanel viewer = bdv.getViewer();
		final ViewerState renderState = viewer.getState();
		final int canvasW = viewer.getDisplay().getWidth();
		final int canvasH = viewer.getDisplay().getHeight();

		final int width = canvasW;
		final int height = canvasH;
		
		final AffineTransform3D affine = new AffineTransform3D();
		renderState.getViewerTransform( affine );
		affine.set( affine.get( 0, 3 ) - canvasW / 2, 0, 3 );
		affine.set( affine.get( 1, 3 ) - canvasH / 2, 1, 3 );
		affine.scale( ( double ) width / canvasW );
		affine.set( affine.get( 0, 3 ) + width / 2, 0, 3 );
		affine.set( affine.get( 1, 3 ) + height / 2, 1, 3 );
		renderState.setViewerTransform( affine );

		final ScaleBarOverlayRenderer scalebar = showScaleBar ? new ScaleBarOverlayRenderer() : null;
		final MultiBoxOverlayRenderer boxRender = showBoxes ? new MultiBoxOverlayRenderer( width, height ) : null;

		final MyRenderTarget target = new MyRenderTarget( width, height );
		final MultiResolutionRenderer renderer = new MultiResolutionRenderer(
				target, new PainterThread( null ), new double[] { 1 }, 0, false, 1, null, false,
				viewer.getOptionValues().getAccumulateProjectorFactory(), new CacheControl.Dummy() );

		final ArrayList< AffineTransform3D > transforms = interpolateTransforms( viewerTransforms, interpolateSteps );

		IJ.showProgress( 0.0 );

		for ( int i = 0; i < transforms.size(); ++i )
		{
			IOFunctions.println( (i+1) + "/" + transforms.size() + ": " + transforms.get( i ) );

			renderState.setViewerTransform( transforms.get( i ) );

			renderer.requestRepaint();
			renderer.paint( renderState );

			if ( scalebar != null )
			{
				final Graphics2D g2 = target.bi.createGraphics();
				g2.setClip( 0, 0, width, height );
				scalebar.setViewerState( renderState );
				scalebar.paint( g2 );
			}

			if ( boxRender != null )
			{
				final Graphics2D g2 = target.bi.createGraphics();
				g2.setClip( 0, 0, width, height );
				boxRender.setViewerState( renderState );
				boxRender.paint( g2 );
			}

			try
			{
				ImageIO.write( target.bi, "png", new File( String.format( "%s/img-%03d.png", new File( "./movie" ), i ) ) );
			}
			catch ( IOException e )
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			IJ.showProgress( (double)(i+1)/(double)transforms.size() );
		}

		IJ.showProgress( 1.0 );

		viewer.setCurrentViewerTransform( transforms.get( 0 ) );

		IOFunctions.println( "Done" );
	}

	public static ArrayList< AffineTransform3D > interpolateTransforms( final ArrayList< AffineTransform3D > steps, final int interpolateSteps )
	{
		if ( steps.size() == 1 )
			return steps;
		else
		{
			final ArrayList< AffineTransform3D > interpolated = new ArrayList<>();

			for ( int i = 0; i < steps.size() - 1; ++i )
			{
				final AffineTransform3D first = steps.get( i );
				final AffineTransform3D second = steps.get( i + 1 );

				final double[] a = first.getRowPackedCopy();
				final double[] b = second.getRowPackedCopy();
				
				for ( int j = 0; j <= interpolateSteps; ++j )
				{
					final double ratioA = 1.0 - (double)j/(double)interpolateSteps;
					final double ratioB = (double)j/(double)interpolateSteps;

					final double[] c = new double[ a.length ];

					for ( int k = 0; k < a.length; ++k )
						c[ k ] = a[ k ] * ratioA + b[ k ] * ratioB;

					final AffineTransform3D interp = new AffineTransform3D();
					interp.set( c );
					interpolated.add( interp );
				}
			}

			return interpolated;
		}
	}

	static class MyRenderTarget implements RenderTarget
	{
		BufferedImage bi;

		final int width;
		final int height;

		public MyRenderTarget( final int width, final int height )
		{
			this.width = width;
			this.height = height;
		}

		@Override
		public BufferedImage setBufferedImage( final BufferedImage bufferedImage )
		{
			bi = bufferedImage;
			return null;
		}

		@Override
		public int getWidth()
		{
			return width;
		}

		@Override
		public int getHeight()
		{
			return height;
		}
	}
}
