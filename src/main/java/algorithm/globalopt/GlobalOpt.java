package algorithm.globalopt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;

import net.imglib2.util.Util;
import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.Affine3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.fiji.ImgLib2Temp.Pair;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class GlobalOpt
{
	/**
	 * Computes a global optimization based on the corresponding points
	 * 
	 * @param registrationType - to determine which tiles are fixed
	 * @param subset - to get the correspondences
	 * @return - list of Tiles containing the final transformation models
	 */
	public static < M extends Model< M > > HashMap< ViewId, Tile< M > > compute(
			final M model,
			final List< PairwiseStitchingResult<ViewId> > pairs,
			final Collection< ViewId > fixedViews,
			final List< ? extends List< ViewId > > groups,
			final GlobalOptimizationParameters params)
	{
		// assemble all views and corresponding points
		final HashSet< ViewId > tmpSet = new HashSet< ViewId >();
		for ( PairwiseStitchingResult<ViewId> pair : pairs )
		{
			tmpSet.add( pair.pair().getA() );
			tmpSet.add( pair.pair().getB() );
		}

		final List< ViewId > views = new ArrayList< ViewId >();
		views.addAll( tmpSet );
		Collections.sort( views );

		// assign ViewIds to the individual Tiles (either one tile per view or one tile per timepoint)
		final HashMap< ViewId, Tile< M > > map = assignViewsToTiles( model, views, groups );

		// assign the pointmatches to all the tiles
		for ( PairwiseStitchingResult pair : pairs )
			// only add pointmatch if correlation is high enough
			if (pair.r() > params.correlationT)
				GlobalOpt.addPointMatches( pair, map.get( pair.pair().getA() ), map.get( pair.pair().getB() ) );

		// add and fix tiles as defined in the GlobalOptimizationType
		final TileConfiguration tc = addAndFixTiles( views, map, fixedViews, groups );
		
		if ( tc.getTiles().size() == 0 )
		{
			IOFunctions.println( "There are no connected tiles, cannot do an optimization. Quitting." );
			return null;
		}
		
		// now perform the global optimization
		boolean finished = false;
		
		while (!finished){
			try 
			{
				int unaligned = tc.preAlign().size();
				if ( unaligned > 0 )
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): pre-aligned all tiles but " + unaligned );
				else
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): prealigned all tiles" );
	
				tc.optimize( 10, 10000, 200 );				
				
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + 
					tc.getTiles().size() +  " view-tiles (Model=" + model.getClass().getSimpleName()  + "):" );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Avg Error: " + tc.getError() + "px" );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Min Error: " + tc.getMinError() + "px" );
				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "):    Max Error: " + tc.getMaxError() + "px" );
			}
			catch (NotEnoughDataPointsException e)
			{
				IOFunctions.println( "Global optimization failed: " + e );
				e.printStackTrace();
			}
			catch (IllDefinedDataPointsException e)
			{
				IOFunctions.println( "Global optimization failed: " + e );
				e.printStackTrace();
			}
			
			finished = true;			
			
			// re-do if errors are too big
			double avgErr = tc.getError();
			double maxErr = tc.getMaxError();			
			if ( ( ( avgErr*params.relativeThreshold < maxErr && maxErr > 0.95 ) || avgErr > params.absoluteThreshold ) )
			{
				finished = false;
				removeWeakestLink( tc );				
			}
							
		}
		
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Transformation Models:" );

		// TODO: We assume it is Affine3D here
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );
			String output = "ViewId=" + viewId.getViewSetupId() + ": " + printAffine3D( (Affine3D<?>)tile.getModel() );
			IOFunctions.println( output  );
		}
		
		return map;
	}
	
	public static void removeWeakestLink(TileConfiguration tc)
	{
		double worstDistance = Double.MIN_VALUE;
		Tile<?> worstTile1 = null;
		Tile<?> worstTile2 = null;
		
		for (Tile<?> t : tc.getTiles())
		{
			for (PointMatch pm : t.getMatches())
			{
				if (pm.getDistance() > worstDistance)
				{
					worstDistance = pm.getDistance();
								
					worstTile1 = t;
					worstTile2 = t.findConnectedTile( pm );
				}
			}
		}
		
		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );
		
	}

	public static String printAffine3D( final Affine3D< ? > model )
	{
		final double[][] m = new double[ 3 ][ 4 ];
		model.toMatrix( m );
		
		return m[0][0] + "," + m[0][1] + "," + m[0][2] + "," + m[0][3] + "," + 
				m[1][0] + "," + m[1][1] + "," + m[1][2] + "," + m[1][3] + "," + 
				m[2][0] + "," + m[2][1] + "," + m[2][2] + "," + m[2][3];
	}
	
	protected static < M extends Model< M > > TileConfiguration addAndFixTiles(
			final List< ViewId > views,
			final HashMap< ViewId, Tile< M > > map,
			final Collection< ViewId > fixedViews,
			final List< ? extends List< ViewId > > groups )
	{
		// create a new tileconfiguration organizing the global optimization
		final TileConfiguration tc = new TileConfiguration();
		
		// assemble a list of all tiles and set them fixed if desired
		final HashSet< Tile< M > > tiles = new HashSet< Tile< M > >();
		
		for ( final ViewId viewId : views )
		{
			final Tile< M > tile = map.get( viewId );

			// if one of the views that maps to this tile is fixed, fix this tile if it is not already fixed
			if ( fixedViews.contains( viewId ) && !tc.getFixedTiles().contains( tile ) )
			{
				if ( groups != null && groups.size() > 0 )
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing timepoint-tile (timepointId = " + viewId.getTimePointId() + ")" );
				else
					IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Fixing view-tile (viewSetupId = " + viewId.getViewSetupId() + ")" );
				tc.fixTile( tile );
			}

			// add it if it is not already there
			tiles.add( tile );
		}	
		
		// now add connected tiles to the tileconfiguration
		for ( final Tile< M > tile : tiles )
			if ( tile.getConnectedTiles().size() > 0 )
				tc.addTile( tile );
		
		return tc;
	}
	
	protected static < M extends Model< M > > HashMap< ViewId, Tile< M > > assignViewsToTiles(
			final M model,
			final List< ViewId > views,
			final List< ? extends List< ViewId > > groups )
	{
		final HashMap< ViewId, Tile< M > > map = new HashMap< ViewId, Tile< M > >();

		if ( groups != null && groups.size() > 0 )
		{
			//
			// there is one tile per group only
			//

			// remember those who are not part of a group
			final HashSet< ViewId > remainingViews = new HashSet< ViewId >();
			remainingViews.addAll( views );

			// for all groups find the viewIds that belong to this timepoint
			for ( final List< ViewId > viewIds : groups )
			{
				// one tile per timepoint
				final Tile< M > tileGroup = new Tile< M >( model.copy() );

				// all viewIds of one group map to the same tile (see main method for test, that works)
				for ( final ViewId viewId : viewIds )
				{
					map.put( viewId, tileGroup );

					// TODO: merge groups that share tiles
					if ( !remainingViews.contains( viewId ) )
						throw new RuntimeException(
								"ViewSetupID:" + viewId.getViewSetupId() + ", timepointId: " + viewId.getTimePointId() +
								" not part of two sets of groups, this is not supported." ); 

					remainingViews.remove( viewId );
				}
			}

			// add all remaining views
			for ( final ViewId viewId : remainingViews )
				map.put( viewId, new Tile< M >( model.copy() ) );
		}
		else
		{
			// there is one tile per view
			for ( final ViewId viewId : views )
				map.put( viewId, new Tile< M >( model.copy() ) );
		}
		
		return map;
	}

	protected static void addPointMatches( final PairwiseStitchingResult pair, final Tile<?> tileA, final Tile<?> tileB)
	{
		final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();

		// the transformations that map each tile into the relative global coordinate system (that's why the "-")
		final Point p1 = new Point( new double[ pair.relativeVector().length ] );
		final Point p2 = new Point( pair.negativeRelationVector() );

		System.out.println( p1.getL().length );
		System.out.println( p1.getW().length );
		System.out.println( p2.getL().length );
		System.out.println( p2.getW().length );
		
		System.out.println( Util.printCoordinates( pair.negativeRelationVector() ));

		pm.add( new PointMatch( p1 , p2 ) );

		tileA.addMatches( pm );
		tileB.addMatches( PointMatch.flip( pm ) );
		tileA.addConnectedTile( tileB );
		tileB.addConnectedTile( tileA );
	}
	
	public static void main( String[] args )
	{
		// multiple keys can map to the same value
		final HashMap< ViewId, Tile< AffineModel3D > > map = new HashMap<ViewId, Tile<AffineModel3D>>();
		
		final AffineModel3D m1 = new AffineModel3D();
		final AffineModel3D m2 = new AffineModel3D();

		final Tile< AffineModel3D > tile1 = new Tile<AffineModel3D>( m1 );
		final Tile< AffineModel3D > tile2 = new Tile<AffineModel3D>( m2 );
		
		final ViewId v11 = new ViewId( 1, 1 );
		final ViewId v21 = new ViewId( 2, 1 );
		final ViewId v12 = new ViewId( 1, 2 );
		final ViewId v22 = new ViewId( 2, 2 );
		
		map.put( v11, tile1 );
		map.put( v21, tile2 );

		map.put( v12, tile1 );
		map.put( v22, tile2 );
		
		m1.set( 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 );
		m2.set( 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2 );

		System.out.println( map.get( v11 ).getModel() );
		System.out.println( map.get( v21 ).getModel() );
		
		System.out.println( map.get( v12 ).getModel() );
		System.out.println( map.get( v22 ).getModel() );		
	}
}
