package gui.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.ui.OverlayRenderer;
import net.imglib2.ui.TransformListener;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;



public class LinkOverlay implements OverlayRenderer, TransformListener< AffineTransform3D >
{
	private StitchingResults stitchingResults;
	private AbstractSpimData< ? > spimData;	
	private final AffineTransform3D viewerTransform;	
	public boolean isActive;
	private ArrayList<Pair<Set<ViewId>, Set<ViewId>>> activeLinks;
	private ValuePair<Set<ViewId>, Set<ViewId>> selectedLink;
	private Set<ViewId> reference;
	
	public void clearActiveLinks()
	{
		activeLinks.clear();
		this.reference = null;
	}
	
	public void setActiveLinks(List<Pair<Set<ViewId>, Set<ViewId>>> vids, Set<ViewId> reference)
	{
		activeLinks.clear();
		activeLinks.addAll( vids );
		this.reference = reference;
	}
	
	public void setSelectedLink(Pair<Set<ViewId>, Set<ViewId>> link)
	{
		if (link == null)
			selectedLink = null;
		else
			selectedLink = new ValuePair< Set<ViewId>, Set<ViewId> >( link.getA(), link.getB() );
	}
	

	/** screen pixels [x,y,z] **/	
	private static Color getColor( final double corr, final double maxR, final double minR )
	{
		final double r = ( corr - minR ) / ( maxR - minR );
		
		final double red = r > 0.5 ? Math.max( 0, (1 - r) * 2 ): 1.0;
		final double green = r > 0.5 ? 1 : Math.max( 0, 2*r );

		return new Color( (float)red, (float)green, 0.0f, 1.0f);
	}

	public LinkOverlay( StitchingResults res, AbstractSpimData< ? > spimData)
	{
		this.stitchingResults = res;
		this.spimData = spimData;
		viewerTransform = new AffineTransform3D();
		isActive = false;
		activeLinks = new ArrayList<>();
		selectedLink = null;
	}

	@Override
	public void transformChanged( final AffineTransform3D transform )
	{
		viewerTransform.set( transform );
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		// dont do anything if the overlay was set to inactive or we have no Tile selected (no links to display)
		if (!isActive || activeLinks.size() == 0)
			return;
		
		final Graphics2D graphics = ( Graphics2D ) g;
		
		
		double maxr = 0.0;
		double minr = Double.MAX_VALUE;
		for (PairwiseStitchingResult<ViewId> sr : stitchingResults.getPairwiseResults().values())
		{
			maxr = Math.max( maxr, sr.r() );
			minr = Math.min( minr, sr.r() );
		}
		
		//System.out.println( activeLinks );
		
		for (Pair< Set<ViewId>, Set<ViewId> > p : stitchingResults.getPairwiseResults().keySet())
		{
			if (activeLinks.size() > 0 && !(activeLinks.contains( p )))
				continue;
			
			// local coordinates of views, without BDV transform 
			final double[] lPos1 = new double[ 3 ];
			final double[] lPos2 = new double[ 3 ];
			// global coordianates, after BDV transform
			final double[] gPos1 = new double[ 3 ];
			final double[] gPos2 = new double[ 3 ];
			
			long[] sizeA = new long[spimData.getSequenceDescription().getViewDescriptions().get( p.getA().iterator().next() ).getViewSetup().getSize().numDimensions()];
			long[] sizeB = new long[spimData.getSequenceDescription().getViewDescriptions().get( p.getB().iterator().next() ).getViewSetup().getSize().numDimensions()];
			spimData.getSequenceDescription().getViewDescriptions().get( p.getA().iterator().next() ).getViewSetup().getSize().dimensions( sizeA );
			spimData.getSequenceDescription().getViewDescriptions().get( p.getB().iterator().next() ).getViewSetup().getSize().dimensions( sizeB );
			
			// TODO: this uses the transform of the first view in the set, maybe do something better?
			AffineTransform3D vt1 = spimData.getViewRegistrations().getViewRegistration( p.getA().iterator().next() ).getModel();
			AffineTransform3D vt2 = spimData.getViewRegistrations().getViewRegistration( p.getB().iterator().next() ).getModel();
			
			final AffineTransform3D transform = new AffineTransform3D();
			transform.preConcatenate( viewerTransform );

			for(int i = 0; i < 3; i++)
			{
				// start from middle of view
				lPos1[i] += sizeA[i] / 2;
				lPos2[i] += sizeB[i] / 2;
			}

			vt1.apply( lPos1, lPos1 );
			vt1.apply( lPos2, lPos2 );
			stitchingResults.getPairwiseResults().get( p ).getTransform().apply( lPos2, lPos2 );

			transform.apply( lPos1, gPos1 );
			transform.apply( lPos2, gPos2 );
			
			// if we have an active link, color it white, else red->yellow->green depending on the correlation
			if (p.equals( selectedLink ))
				graphics.setColor( Color.WHITE );
			else
				graphics.setColor( getColor( stitchingResults.getPairwiseResults().get( p ).r(), maxr, minr ) );
			
			graphics.setStroke( new BasicStroke( 2.0f ) );
			graphics.drawLine((int) gPos1[0],(int) gPos1[1],(int) gPos2[0],(int) gPos2[1] );
			
		}
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{}
	
	public static void main(String[] args)
	{
		double maxR = 2;
		double minR = 1;
		
		for (double corr = 2.0; corr >= 1.0; corr -= 0.1) 
		{
			final double r = ( corr - minR ) / ( maxR - minR );
					
			final double red = r > 0.5 ? Math.max( 0, (1 - r) * 2 ): 1.0;
			final double green = r > 0.5 ? 1 : Math.max( 0, 2*r );
			System.out.println( red + " " + green );
		}
	}
}
