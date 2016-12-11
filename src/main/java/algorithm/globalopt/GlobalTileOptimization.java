package algorithm.globalopt;

import java.util.ArrayList;
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
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
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
		
		
		
		// create strong links for all pairs with valid pairwise results
		List<Link<C>> strongLinks = new ArrayList<>();
		for (PairwiseStitchingResult< C > res : pairwiseResults)
		{
			// only consider Pairs that were also selected
			if (res.r() > params.correlationT && views.contains( res.pair().getA()) && views.contains( res.pair().getB()))
			{
				strongLinks.add( new Link< C >( res.pair().getA(), res.pair().getB(), res.getTransform(), LinkType.STRONG ) );
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
					AffineGet mapBack = TransformTools.mapBackTransform( initialTransforms.get( views.get( i ) ),
							initialTransforms.get( views.get( j ) ));
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
			Pair< TileConfiguration, Map< C, Tile< TranslationModel3D > > > tc = prepareTileConfiguration( new TranslationModel3D(), null, strongLinks, fixedViews, null, params, null );
			Map< C, AffineGet > optimizeResult1 = optimize(numDimensions, tc.getA(), tc.getB(), params, null );
		
		
			// we have no weak links, return first round result
			// TODO: move connected components, so that they do not overlap
			if (weakLinks.size() == 0)
				return optimizeResult1;
		
			Pair< TileConfiguration, Map< C, Tile< TranslationModel3D > > > tc2 = prepareTileConfiguration( new TranslationModel3D(), views, weakLinks, fixedViews, connectedComponents, null, optimizeResult1 );
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
			final GlobalOptimizationParameters params,
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
		
			double[] shift = new double[numD];
			tile.getModel().applyInPlace( shift );
			
//			if (initialTransforms != null && initialTransforms.containsKey( viewId ))
//				shift = VectorUtil.getVectorSum( shift, initialTransforms.get( viewId ) );
			
			AffineTransform3D t = new AffineTransform3D();
			t.set( tile.getModel().getMatrix( null ) );
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
				(!initialTransforms.containsKey( pair.getFirst() ) && !initialTransforms.containsKey( pair.getSecond() )))
		{
			for (int i = 0; i < p.length; ++i)
			{
				pair.getShift().applyInverse( p[i], pb[i] );
				pointsA.add( new Point( p[i] ) );
				pointsB.add( new Point( pb[i] ) );
			}
			
		}
		// we already know the location of the first point in its (grouped) tile
		else if (initialTransforms.containsKey( pair.getFirst() ) && !initialTransforms.containsKey( pair.getSecond() ))
		{
			for (int i = 0; i < p.length; ++i)
			{
				initialTransforms.get( pair.getFirst() ).apply( p[i], pa[i] );
				pair.getShift().applyInverse( p[i], pb[i] );
				pointsA.add( new Point( pa[i] ) );
				pointsB.add( new Point( pb[i] ) );
			}
		} 
		// we know the location of the second point
		else if (initialTransforms.containsKey( pair.getSecond() ) && !initialTransforms.containsKey( pair.getFirst() ))
		{
	
			for (int i = 0; i < p.length; ++i)
			{
				pair.getShift().apply( p[i], pa[i] );
				initialTransforms.get( pair.getFirst() ).apply( p[i], pb[i] );				
				pointsA.add( new Point( pa[i] ) );
				pointsB.add( new Point( pb[i] ) );
			}

		}
		// do not add a point match if both points are in pre-registered groups
		else
			return;

		/*
		System.out.println( p1.getL().length );
		System.out.println( p1.getW().length );
		System.out.println( p2.getL().length );
		System.out.println( p2.getW().length );
		
		System.out.println( Util.printCoordinates( pair.getInverseShift() ));
		*/

		for (int i = 0; i < pointsA.size(); ++i)
			pm.add( new PointMatch( pointsA.get( i ) , pointsB.get( i ) ) );

		// TODO: workaround until mpicbg is fixed with pull request #30
		//pm.add( new PointMatch( p1.clone() , p2.clone() ) );

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
	
	public static void removeWeakestLink(TileConfiguration tc)
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

		worstTile1.removeConnectedTile( worstTile2 );
		worstTile2.removeConnectedTile( worstTile1 );	
		System.out.println( "removed link from " + worstTile1 + " to " + worstTile2 );
		
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
		
		// TEST one connected component
		List<Link<Integer>> myList = new ArrayList<>();
		myList.add(new Link<Integer>(1, 2, new Translation3D( new double[] {-2, 2, 1}), LinkType.STRONG));
		myList.add(new Link<Integer>(1, 3, new Translation3D( new double[] {1, 2, 0}), LinkType.STRONG));
		myList.add(new Link<Integer>(2, 3, new Translation3D( new double[] {3, 25, -1}), LinkType.STRONG));
		
		
		Pair< TileConfiguration, Map< Integer, Tile< TranslationModel3D > > > prepareTileConfiguration = 
				prepareTileConfiguration( 	new TranslationModel3D(), 
									null,
									myList, 
									null, 
									null, 
									new GlobalOptimizationParameters(),
									null );
		
		Map< Integer, AffineGet > optimize = optimize(3, prepareTileConfiguration.getA(), prepareTileConfiguration.getB(), new GlobalOptimizationParameters(), null);
	
		System.out.println( optimize );
		
		if (true)
			return;
		
		// TEST two connected components
		myList = new ArrayList<>();
		myList.add(new Link<Integer>(1, 2, new Translation3D( new double[] {2, 1, 0}), LinkType.STRONG));
		myList.add(new Link<Integer>(4, 5, new Translation3D( new double[] {2, 1, 0}), LinkType.STRONG));
		myList.add(new Link<Integer>(1, 7, new Translation3D( new double[] {0, 2, 0}), LinkType.STRONG));
		myList.add(new Link<Integer>(2, 7, new Translation3D( new double[] {-2, 1, 0}), LinkType.STRONG));
		
		prepareTileConfiguration = 
				prepareTileConfiguration( 	new TranslationModel3D(), 
									null,
									myList, 
									null, 
									null, 
									new GlobalOptimizationParameters(),
									null );
		
		optimize = optimize( 3, prepareTileConfiguration.getA(), prepareTileConfiguration.getB(), new GlobalOptimizationParameters(), null );

		System.out.println( optimize );
		
		// ADD Tile with weak link to the above
		// weak links between all views not linked by strong links
		myList = new ArrayList<>();
		
		

		
		myList.add(new Link<Integer>(2, 3, new Translation3D( new double[] {2, 0, 0}), LinkType.WEAK));
		myList.add(new Link<Integer>(2, 6, new Translation3D( new double[] {2, 2, 0}), LinkType.WEAK));
		
		myList.add(new Link<Integer>(3, 4, new Translation3D( new double[] {2, 0, 0}), LinkType.WEAK));
		
		
		
		myList.add(new Link<Integer>(4, 6, new Translation3D( new double[] {-2, 2, 0}), LinkType.WEAK));

		
		
		myList.add(new Link<Integer>(4, 7, new Translation3D( new double[] {-6, 2, 0}), LinkType.WEAK));
		myList.add(new Link<Integer>(5, 7, new Translation3D( new double[] {-8, 2, 0}), LinkType.WEAK));
		
		List< Set< Integer > > groups = new ArrayList<>();
		Set< Integer > group1 = new HashSet<>();
		group1.add( 1 );
		group1.add( 2 );
		group1.add( 7 );
		Set< Integer > group2 = new HashSet<>();
		group2.add( 4 );
		group2.add( 5 );
		groups.add( group1 );
		groups.add( group2 );
				
		
		List<Integer> views = new ArrayList<>();
		views.add( 1 );
		views.add( 2 );
		views.add( 3 );
		views.add( 4 );
		views.add( 5 );
		views.add( 6 );
		views.add( 7 );
		
		/*
		// try +err
		double[] err2 = VectorUtil.getVectorDiff(  new double[] {2,0,0} , optimize.get( 2 ));
		optimize.put( 2, VectorUtil.getVectorSum( optimize.get( 2 ), err2) );
		double[] err4 = VectorUtil.getVectorDiff(  new double[] {0,0,0} , optimize.get( 4 ) );
		optimize.put( 4, VectorUtil.getVectorSum( optimize.get( 4 ), err4) );
		double[] err5 = VectorUtil.getVectorDiff( new double[] {2,0,0}, optimize.get( 5 ) );
		optimize.put( 5, VectorUtil.getVectorSum( optimize.get( 5 ), err5) );
		*/
		
		prepareTileConfiguration = 
				prepareTileConfiguration( 	new TranslationModel3D(), 
									views,
									myList, 
									null, 
									groups, 
									new GlobalOptimizationParameters(),
									optimize );
		optimize = optimize(3,  prepareTileConfiguration.getA(), prepareTileConfiguration.getB(), new GlobalOptimizationParameters(), optimize);

		/*
		optimize.put(2, VectorUtil.getVectorDiff( err2, optimize.get( 2 )));
		optimize.put(4, VectorUtil.getVectorDiff( err4, optimize.get( 4 )));
		optimize.put(5, VectorUtil.getVectorDiff( err5, optimize.get( 5 ) ));
		*/
		
		System.out.println( optimize );
	
	}
	
	
}
