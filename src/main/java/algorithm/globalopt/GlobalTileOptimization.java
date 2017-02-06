package algorithm.globalopt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import algorithm.TransformTools;
import algorithm.VectorUtil;
import algorithm.globalopt.Link.LinkType;
import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;

import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;

public class GlobalTileOptimization
{
	
	/**
	 * @param numDimensions
	 * @param views identifieres of the "views" to be stitched
	 * @param fixedViews identifieres of the fixed views
	 * @param initialTranslations approximate initial translations for each view
	 * @param pairwiseResults collection of available calculated pairwise shifts
	 * @param params 
	 * @return a mapping of view identifieres to locations in global space
	 */
	public static < C extends Comparable< C >> Map<C, AffineGet> twoRoundGlobalOptimization(
			final int numDimensions,
			final List< ? extends C > views,
			final Collection< C > fixedViews,
			final Map<C, AffineGet> initialTransforms,
			final Collection<PairwiseStitchingResult< C >> pairwiseResults,
			final GlobalOptimizationParameters params)
	{
		
		Collections.sort( views );
		
		// create strong links for all pairs with valid pairwise results
		List<Link<C>> strongLinks = new ArrayList<>();
		for (PairwiseStitchingResult< C > res : pairwiseResults)
		{
			// only consider Pairs that were also selected
			if (res.r() > params.correlationT && views.contains( res.pair().getA()) && views.contains( res.pair().getB()))
			{
				strongLinks.add( new Link< C >( res.pair().getA(), res.pair().getB(), res.getTransform(), LinkType.STRONG ) );
//				System.out.println( "added strong link between " + ((ViewId) res.pair().getA()).getViewSetupId() + " and " + ((ViewId) res.pair().getB()).getViewSetupId() + ": " + res.getTransform() );
			}
		}
		
		List< Set< C > > connectedComponents = Link.getConnectedComponents( strongLinks, LinkType.STRONG );
		
		// create weak links between every pair of views that are not both in a conn.comp.
		// TODO: we should only connect tiles within a reasonable radius to each other (for performance reasons)
		List<Link<C>> weakLinks = new ArrayList<>();
		for (int i = 0; i < views.size(); i++)
		{
			for(int j = i+1; j < views.size(); j++)
			{
				
				if(!(anyContains( views.get( i), connectedComponents ) && 
						anyContains( views.get( j), connectedComponents ) ) )
				{
					AffineGet mapBack = TransformTools.mapBackTransform( initialTransforms.get( views.get( j ) ),
							initialTransforms.get( views.get( i ) ));
					
					System.out.println( "added weak link between " + views.get( i ) + " and " + views.get( j ) + ": " + mapBack );
//					System.out.println( "added weak link between " + ((ViewId)views.get( i )).getViewSetupId() + " and " + ((ViewId)views.get( j )).getViewSetupId() + ": " + mapBack );
					weakLinks.add( new Link< C >( views.get( i ), views.get( j ), mapBack, LinkType.WEAK ) );
				}
				
			}
		}		
		
		
		// TODO: 
		/*
		if (numDimensions == 2){
			Pair< TileConfiguration, Map< C, Tile< TranslationModel2D > > > tc = prepareTileConfiguration( new TranslationModel2D(), null, strongLinks, fixedViews, null, params, null );
			Map< C, AffineGet > optimizeResult1 = optimize(numDimensions, tc.getA(), tc.getB(), params, null );
		
		
			// we have no weak links, return first round result
			// TODO: move connected components, so that they do not overlap
			if (weakLinks.size() == 0)
				return optimizeResult1;
		
			Pair< TileConfiguration, Map< C, Tile< TranslationModel2D > > > tc2 = prepareTileConfiguration( new TranslationModel2D(), views, weakLinks, fixedViews, connectedComponents, null, optimizeResult1 );
			Map< C, AffineGet > optimizeResult2 = optimize(numDimensions, tc2.getA(), tc2.getB(), new GlobalOptimizationParameters( 0.0, Double.MAX_VALUE, Double.MAX_VALUE ), optimizeResult1 );
			
			return optimizeResult2;
		}
		else
		{
		
		*/
			Pair< TileConfiguration, Map< C, Tile< TranslationModel3D > > > tc = prepareTileConfiguration( new TranslationModel3D(), null, strongLinks, fixedViews, null, null );
			Map< C, AffineGet > optimizeResult1 = optimize(numDimensions, tc.getA(), tc.getB(), params, null );
			
			optimizeResult1.forEach( (x, y) -> System.out.println( x + ": " + y ) );
		
		
			// we have no weak links, return first round result
			// TODO: move connected components, so that they do not overlap
			if (weakLinks.size() == 0)
				return optimizeResult1;
		
			Pair< TileConfiguration, Map< C, Tile< TranslationModel3D > > > tc2 = prepareTileConfiguration( new TranslationModel3D(), views, weakLinks, fixedViews, connectedComponents, optimizeResult1 );
			Map< C, AffineGet > optimizeResult2 = optimize(numDimensions, tc2.getA(), tc2.getB(), new GlobalOptimizationParameters( 0.0, Double.MAX_VALUE, Double.MAX_VALUE ), optimizeResult1 );
			
			return optimizeResult2;
		//}
		
	}

	
	
	public static < M extends Model< M > , C extends Comparable< C >> Pair<TileConfiguration, Map<C, Tile<M>>>  prepareTileConfiguration(
			final M model,
			final Collection< ? extends C > views,
			final List< Link<C> > links,
			final Collection< C > fixedViews,
			final List< Set< C > > groups,
			final Map<C, AffineGet> initialTransforms)
	{
				
		final List< C > actualViews;
		
		// no list of views was given, assemble from links
		if (views == null){
			final HashSet< C > tmpSet = new HashSet<>();
			for ( Link<C> l : links )
			{
				tmpSet.add( l.getFirst() );
				tmpSet.add( l.getSecond() );
			}
			actualViews = new ArrayList<>();
			actualViews.addAll( tmpSet );
		}
		else
		{
			// use the views that were given
			actualViews = new ArrayList<>();
			actualViews.addAll( views );
		}
		 
		Collections.sort( actualViews );
		
		// assign ViewIds to the individual Tiles (either one tile per view or one tile per group)
		final Map< C, Tile< M > > map = assignViewsToTiles( model, actualViews, groups );
		
		
		// assign the pointmatches to all the tiles
		for ( Link<C> link : links )
			addPointMatches( link, map.get(link.getFirst() ), map.get( link.getSecond() ), initialTransforms);

		// add and fix tiles as defined in the GlobalOptimizationType
		final TileConfiguration tc = addAndFixTiles( actualViews, map, fixedViews);

		return new ValuePair<>(tc, map);
	}
	
	public static <M extends AbstractAffineModel3D<M>, C extends Comparable< C >> Map<C, AffineGet> optimize(
			int numD,
			TileConfiguration tc, 
			Map<C, Tile<M>> map,
			GlobalOptimizationParameters params,
			final Map<C, AffineGet> initialTransforms)
	{
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

				tc.optimize( params.absoluteThreshold, 10000, 200 );				

				IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Global optimization of " + 
						tc.getTiles().size());
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


		Map<C, AffineGet> resMap = new HashMap<>();
		
		// TODO: move this outside of method 
		for ( final C viewId : map.keySet() )
		{
			final Tile< M > tile = map.get( viewId );
		
//			double[] shift = new double[numD];
//			tile.getModel().applyInPlace( shift );
//			shift = VectorUtil.getVectorSum( shift, initialTransforms.get( viewId ) );
			
			AffineTransform3D t = new AffineTransform3D();
			t.set( tile.getModel().getMatrix( null ) );
			
			if (initialTransforms != null && initialTransforms.containsKey( viewId ))
				t.concatenate( initialTransforms.get( viewId ) );
			
			resMap.put( viewId, t);
			
		}

		return resMap;	
	}
	
	
	protected static < M extends Model< M >, C extends Comparable< C >> HashMap< C, Tile< M > > assignViewsToTiles(
			final M model,
			final List< C > views,
			final List< Set < C > > groups )
	{
		final HashMap< C, Tile< M > > map = new HashMap<>();

		if ( groups != null && groups.size() > 0 )
		{
			//
			// there is one tile per group only
			//

			// remember those who are not part of a group
			final HashSet< C > remainingViews = new HashSet<>();
			remainingViews.addAll( views );

			// for all groups find the viewIds that belong to this group
			for ( final Set< C > viewIds : groups )
			{
				// one tile per group
				final Tile< M > tileGroup = new Tile< M >( model.copy() );

				// all viewIds of one group map to the same tile (see main method for test, that works)
				for ( final C viewId : viewIds )
				{
					map.put( viewId, tileGroup );

					// TODO: merge groups that share tiles
					// we might just do that in calling methods, though
					if ( !remainingViews.contains( viewId ) )
						throw new RuntimeException(	" groups have to be non-intersecting" ); 

					remainingViews.remove( viewId );
				}
			}

			// add all remaining views
			for ( final C viewId : remainingViews )
				map.put( viewId, new Tile< M >( model.copy() ) );
		}
		else
		{
			// there is one tile per view
			for ( final C id : views )
				map.put( id, new Tile< M >( model.copy() ) );
		}
		
		return map;
	}
	
	protected static <C extends Comparable< C >> void addPointMatches( 
			final Link<C> pair, 
			final Tile<?> tileA, 
			final Tile<?> tileB,
			final Map<C, AffineGet> initialTransforms
			)
	{
		final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();
		final List<Point> pointsA = new ArrayList<>();
		final List<Point> pointsB = new ArrayList<>();
		
		final double[][] p = new double[][]{
			{ 0, 0, 0 },
			{ 1, 0, 0 },
			{ 0, 1, 0 },
			{ 1, 1, 0 },
			{ 0, 0, 1 },
			{ 1, 0, 1 },
			{ 0, 1, 1 },
			{ 1, 1, 1 }};

		final double[][] pa = new double[8][3];
		final double[][] pb = new double[8][3];
		
		
		// we do not know any initial location
		if (initialTransforms == null || 
				!(initialTransforms.containsKey( pair.getFirst() ) || initialTransforms.containsKey( pair.getSecond() )))
		{
			
			for (int i = 0; i < p.length; ++i)
			{
				pair.getShift().applyInverse( pb[i], p[i] );
				pointsA.add( new Point( p[i] ) );
				pointsB.add( new Point( pb[i] ) );
			}
		}
		
		// we already know the location of the first point in its (grouped) tile
		else if (initialTransforms.containsKey( pair.getFirst() ) && !initialTransforms.containsKey( pair.getSecond() ))
		{
			for (int i = 0; i < p.length; ++i)
			{
				initialTransforms.get( pair.getFirst() ).applyInverse( pa[i], p[i] );
				pair.getShift().applyInverse( pb[i], pa[i] );
				pointsA.add( new Point( p[i] ) );
				pointsB.add( new Point( pb[i] ) );	
			}
			
			
		} 
		
		// we know the location of the second point
		else if (initialTransforms.containsKey( pair.getSecond() ) && !initialTransforms.containsKey( pair.getFirst() ))
		{
	
			for (int i = 0; i < p.length; ++i)
			{
				
				initialTransforms.get( pair.getSecond() ).applyInverse( pb[i], p[i] );
				pair.getShift().apply( pb[i], pa[i] );
				pointsA.add( new Point( pa[i] ) );
				pointsB.add( new Point( p[i] ) );
			}

		}
		
		// do not add a point match if both points are in pre-registered groups
		else
			return;

		// create PointMatches and connect Tiles
		for (int i = 0; i < pointsA.size(); ++i)
			pm.add( new PointMatch( pointsA.get( i ) , pointsB.get( i ) ) );

		tileA.addMatches( pm );
		tileB.addMatches( PointMatch.flip( pm ) );
		tileA.addConnectedTile( tileB );
		tileB.addConnectedTile( tileA );
	}
	
	protected static < M extends Model< M >, C extends Comparable< C >> TileConfiguration addAndFixTiles(
			final List< C > views,
			final Map< C, Tile< M > > map,
			final Collection< C > fixedViews)
	{
		// if no fixed tiles are given, fix the tile of the first view
		final Collection< C > fixedViewsActual;
		if (fixedViews == null)
		{
			fixedViewsActual = new ArrayList<>();
			fixedViewsActual.add( views.get( 0 ) );
		}
		else
		{
			fixedViewsActual = fixedViews;
		}
		
		
		// create a new TileConfiguration organizing the global optimization
		final TileConfiguration tc = new TileConfiguration();

		// assemble a list of all tiles and set them fixed if desired
		final HashSet< Tile< M > > tiles = new HashSet< Tile< M > >();
		
		for ( final C viewId : views )
		{
			final Tile< M > tile = map.get( viewId );

			// if one of the views that maps to this tile is fixed, fix this tile if it is not already fixed
			if ( fixedViewsActual.contains( viewId ) && !tc.getFixedTiles().contains( tile ) )
				tc.fixTile( tile );

			// add it if it is not already there
			tiles.add( tile );
		}	

		// now add connected tiles to the tileconfiguration
		for ( final Tile< M > tile : tiles )
			if ( tile.getConnectedTiles().size() > 0 )
				tc.addTile( tile );

		return tc;
	}
	
	public static boolean removeWeakestLink(TileConfiguration tc)
	{
		double worstDistance = -Double.MAX_VALUE;
		Tile<?> worstTile1 = null;
		Tile<?> worstTile2 = null;
		
		for (Tile<?> t : tc.getTiles())
		{
			// we mustn't disconnect a tile entirely
			if (t.getConnectedTiles().size() <= 1)
				continue;
			
			for (PointMatch pm : t.getMatches())
			{
				
				if (/*worstTile1 == null || */ pm.getDistance() > worstDistance)
				{
					worstDistance = pm.getDistance();
					
					
					worstTile1 = t;
					worstTile2 = t.findConnectedTile( pm );
				}
				
				//System.out.println( pm.getDistance() + " " + worstDistance + " " + worstTile1 );
			}
		}

		if (worstTile1 == null)
		{
			System.err.println( "WARNING: can not remove any more links without disconnecting components" );
			return false;
		}
		
		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );	
		System.out.println( "removed link from " + worstTile1 + " to " + worstTile2 );
		return true;
	}
	
	private static <C> boolean anyContains( C element, List< Set< C > > list)
	{
		boolean found = false;
		for (Set<C> s : list)
			found |= s.contains( element );
		return found;
	}
	
	
	public static void main(String[] args)
	{
		
		List<Integer> views = Arrays.asList( new Integer[] {1,2,-1, 3} );
		List<Integer> fixedViews = Arrays.asList( new Integer[] {1} );
		
		Map<Integer, AffineGet> initialTransforms = new HashMap<>();
		initialTransforms.put( 1, new Translation3D( 0, 0, 0 ) );
		initialTransforms.put( 2, new Translation3D( 0.75, 0, 0 ) );
		initialTransforms.put( -1, new Translation3D( 1.5, 0, 0 ) );
		initialTransforms.put( 3, new Translation3D( 2.25, 0, 0 ) );
		
		List<PairwiseStitchingResult< Integer >> pairwiseResults = new ArrayList<>();
		pairwiseResults.add( new PairwiseStitchingResult<>( new ValuePair<>( 1, 2 ), new Translation3D(1,0,0), 1.0 ) );
		
		Map< Integer, AffineGet > res = twoRoundGlobalOptimization( 3, views, fixedViews, initialTransforms, pairwiseResults, new GlobalOptimizationParameters() );
		
		res.forEach( ( x, y ) -> System.out.println( x + ": " + y ));
	
	}
	
	
}
