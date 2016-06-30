package gui.overlay;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;


import algorithm.StitchingResults;
import algorithm.globalopt.PairwiseStitchingResult;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.Pair;



public class LinkOverlay implements OverlayRenderer, TransformListener< AffineTransform3D >
{
	private StitchingResults stitchingResults;
	private AbstractSpimData< ? > spimData;
	
	private final AffineTransform3D viewerTransform;

	/** screen pixels [x,y,z] **/
	private Color getColor( final double[] gPos, double r, double maxr, double minr)
	{
		double range = maxr - minr;
		return Color.getHSBColor( (float) ((r-minr)/range), 1.0f, 1.0f );
	}

	public LinkOverlay( StitchingResults res, AbstractSpimData< ? > spimData)
	{
		this.stitchingResults = res;
		this.spimData = spimData;
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
		final double[] lPos1 = new double[ 3 ];
		final double[] lPos2 = new double[ 3 ];
		final double[] gPos1 = new double[ 3 ];
		final double[] gPos2 = new double[ 3 ];
		
		double maxr = 0.0;
		double minr = Double.MAX_VALUE;
		for (PairwiseStitchingResult sr : stitchingResults.getPairwiseResults().values())
		{
			maxr = Math.max( maxr, sr.r() );
			minr = Math.min( minr, sr.r() );
		}
		
		for (Pair< ViewId, ViewId > p : stitchingResults.getPairwiseResults().keySet())
		{
			long[] sizeA = new long[spimData.getSequenceDescription().getViewDescriptions().get( p.getA() ).getViewSetup().getSize().numDimensions()];
			long[] sizeB = new long[spimData.getSequenceDescription().getViewDescriptions().get( p.getB() ).getViewSetup().getSize().numDimensions()];
			spimData.getSequenceDescription().getViewDescriptions().get( p.getA() ).getViewSetup().getSize().dimensions( sizeA );
			spimData.getSequenceDescription().getViewDescriptions().get( p.getB() ).getViewSetup().getSize().dimensions( sizeB );
			
			//ViewTransform vt1 = spimData.getViewRegistrations().getViewRegistration( p.getA() ).getTransformList().get( 1 );
			AffineTransform3D vt1 = spimData.getViewRegistrations().getViewRegistration( p.getA() ).getModel();
			//ViewTransform vt2 = spimData.getViewRegistrations().getViewRegistration( p.getB() ).getTransformList().get( 1 );
			AffineTransform3D vt2 = spimData.getViewRegistrations().getViewRegistration( p.getB() ).getModel();
			
			final AffineTransform3D transform = new AffineTransform3D();
			transform.preConcatenate( viewerTransform );
			
			for(int i = 0; i < 3; i++)
			{
				lPos1[i] = vt1.get( i, 3 );
				lPos2[i] = vt2.get( i, 3 );
				
				sizeA[i] *= vt1.get( i, i );
				sizeB[i] *= vt2.get( i, i );
				
				// start from middle of view
				lPos1[i] += sizeA[i] / 2;
				lPos2[i] += sizeB[i] / 2;
			}
			
			transform.apply( lPos1, gPos1 );
			transform.apply( lPos2, gPos2 );
						
			graphics.setColor( getColor( gPos1, stitchingResults.getPairwiseResults().get( p ).r(), maxr, minr ) );
			
			graphics.drawLine((int) gPos1[0],(int) gPos1[1],(int) gPos2[0],(int) gPos2[1] );
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{}
}
