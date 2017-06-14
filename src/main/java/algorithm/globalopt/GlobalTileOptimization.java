package algorithm.globalopt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import com.google.common.collect.Iterators;

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
import mpicbg.models.SimilarityModel3D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;

import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;
import spim.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;

@Deprecated
public class GlobalTileOptimization
{
	
	/*
	 * @param model an instance of the model we wish to fit to data
	 * @param viewSets identifieres of the sets of "views" to be stitched
	 * @param fixedViews identifieres of the fixed views
	 * @param initialTranslations approximate initial translations for each view
	 * @param pairwiseResults collection of available calculated pairwise shifts
	 * @param params  parameters for global optimization
	 * @return a mapping of view identifieres to locations in global space
	 */
	@Deprecated
	public static < C extends Comparable< C >, M extends AbstractAffineModel3D<M>> Map<Set<C>, AffineGet> twoRoundGlobalOptimization(
			final M model,
			final List< Set< C > > viewSets,
			final Collection< Set<C> > fixedViews,
			final Map<C, AffineGet> initialTransforms,
			final Map<C, Dimensions> viewDimensions, 
			final Collection<PairwiseStitchingResult< C >> pairwiseResults,
			final GlobalOptimizationParameters params)
	{
		
		
		// 1) create strong links for all pairs with valid pairwise results
		List<Link< Set<C> >> strongLinks = new ArrayList<>();
		for (PairwiseStitchingResult< C > res : pairwiseResults)
		{
			// only consider Pairs that were selected and that have high enough correlation
			if (res.r() > params.correlationT && viewSets.contains( res.pair().getA()) && viewSets.contains( res.pair().getB()))
			{
				strongLinks.add( new Link< Set<C> >( res.pair().getA().getViews(), res.pair().getB().getViews(), res.getTransform(), LinkType.STRONG ) );
				//System.out.println( "added strong link between " + ((ViewId) res.pair().getA()).getViewSetupId() + " and " + ((ViewId) res.pair().getB()).getViewSetupId() + ": " + res.getTransform() );
			}
		}
		
		// 2) find connected components in strong link graph
		List< Set< Set<C> > > connectedComponents = Link.getConnectedComponents( strongLinks, LinkType.STRONG );
		
		
		// 3) create weak links between every pair of views that are not both in the same conn.comp.
		List<Link<Set<C>>> weakLinks = new ArrayList<>();
		for (int i = 0; i < viewSets.size(); i++)
		{
			for(int j = i+1; j < viewSets.size(); j++)
			{
				int indexI = -1;
				int counter = 0;
				for (Set< Set<C> > cc : connectedComponents)
				{
					if ( cc.contains( viewSets.get( i ) ) )
						indexI = counter;
					++counter;
				}
				
				int indexJ = -1;
				counter = 0;
				for (Set< Set<C> > cc : connectedComponents)
				{
					if ( cc.contains( viewSets.get( j ) ) )
						indexJ = counter;
					++counter;
				}

				if( (indexI == -1 && indexJ == -1) || (indexI != indexJ))
				{
					
					// check overlap if requested and only add weak links between overlapping view sets 
					if (params.useOnlyOverlappingPairs && viewDimensions != null)
					{
						final List<Dimensions> dimsA = new ArrayList<>();
						final List<Dimensions> dimsB = new ArrayList<>();
						final List<AffineTransform3D> transformsA = new ArrayList<>();
						final List<AffineTransform3D> transformsB = new ArrayList<>();
						
						// get view transformations and dimensions for view pair
						for (final C v : viewSets.get( i ))
						{
							dimsA.add( viewDimensions.get( v ) );
							AffineTransform3D at = new AffineTransform3D();
							at.set( initialTransforms.get( v ).getRowPackedCopy() );
							transformsA.add( at );
						}
						for (final C v : viewSets.get( j ))
						{
							dimsB.add( viewDimensions.get( v ) );
							AffineTransform3D at = new AffineTransform3D();
							at.set( initialTransforms.get( v ).getRowPackedCopy() );
							transformsB.add( at );
						}
						
						// check for overlap with a SimpleBoundingBoxOverlap
						boolean overlap = SimpleBoundingBoxOverlap.overlaps( SimpleBoundingBoxOverlap.getBoundingBox( dimsA, transformsA ), 
								SimpleBoundingBoxOverlap.getBoundingBox( dimsB, transformsB ));
						
						if (!overlap)
							continue;
					
					}
					
					// we use the first views in set to determine the map back transform for the weak link
					// TODO: handle the case of different transforms in the view sets
					AffineGet mapBack = TransformTools.mapBackTransform( initialTransforms.get( viewSets.get( j ).iterator().next() ),
							initialTransforms.get( viewSets.get( i ).iterator().next() ));
					
					//System.out.println( "added weak link between " + viewSets.get( i ) + " and " + viewSets.get( j ) + ": " + mapBack );
					//System.out.println( "added weak link between " + ((ViewId)views.get( i )).getViewSetupId() + " and " + ((ViewId)views.get( j )).getViewSetupId() + ": " + mapBack );
					weakLinks.add( new Link< Set<C> >( viewSets.get( i ), viewSets.get( j ), mapBack, LinkType.WEAK ) );
				}
				
			}
		}		
		
		
		// TODO: handle the 2D case? maybe we should do this outside and just call this method with a 2d model instance
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
		
		// 4) build TileConfiguration using the strong links and optimize
		final Pair< TileConfiguration, Map< Set< C >, Tile< M > > > tcFirstPass = prepareTileConfiguration( model.copy(), null,
				strongLinks, fixedViews, null, null );
		final Map< Set< C >, AffineGet > optimizeResultFirstPass = optimize( tcFirstPass.getA(), tcFirstPass.getB(), params, null );

		// optimizeResultFirstPass.forEach( (x, y) -> System.out.println( x + ": " + y ) );

		// if we have no weak links or the user does not want to do a second round, return first round result
		if ( weakLinks.size() == 0 || !params.doTwoRound )
			return optimizeResultFirstPass;

		
		// 5) build TileCOnfiguration from weak links and optimize
		final Pair< TileConfiguration, Map< Set< C >, Tile< M > > > tcSecondPass = prepareTileConfiguration( model.copy(), viewSets,
				weakLinks, fixedViews, connectedComponents, optimizeResultFirstPass );
		final Map< Set< C >, AffineGet > optimizeResultSecondPass = optimize( tcSecondPass.getA(), tcSecondPass.getB(), new GlobalOptimizationParameters(
				0.0, Double.MAX_VALUE, Double.MAX_VALUE, false, params.useOnlyOverlappingPairs ), optimizeResultFirstPass );

		return optimizeResultSecondPass;
		
	}

	
	/*
	 * build TileConfiguration for 2-round global optimization
	 * @param model
	 * @param views
	 * @param links
	 * @param fixedViews
	 * @param groups
	 * @param firstPassTransforms
	 * @return
	 */
	@Deprecated
	public static < M extends AbstractAffineModel3D< M >, C> Pair<TileConfiguration, Map<Set<C>, Tile<M>>>  prepareTileConfiguration(
			final M model,
			final Collection< Set<C> > views,
			final List< Link<Set<C>> > links,
			final Collection< Set<C> > fixedViews,
			final List< Set< Set<C> > > groups,
			final Map<Set<C>, AffineGet> firstPassTransforms)
	{
				
		final List< Set<C> > actualViews;
		
		// no list of views was given, assemble from links
		if (views == null){
			final HashSet< Set<C> > tmpSet = new HashSet<>();
			for ( Link<Set<C>> l : links )
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
		 
		//Collections.sort( actualViews );
		
		// assign ViewIds to the individual Tiles (either one tile per view or one tile per group)
		final Map< Set<C>, Tile< M > > map = assignViewsToTiles( model, actualViews, groups );
		
		
		// assign the pointmatches to all the tiles
		for ( Link<Set<C>> link : links )
			addPointMatches( link, map.get(link.getFirst() ), map.get( link.getSecond() ), firstPassTransforms);

		// add and fix tiles as defined in the GlobalOptimizationType
		final TileConfiguration tc = addAndFixTiles( actualViews, map, fixedViews);

		return new ValuePair<>(tc, map);
	}
	
	public static <M extends AbstractAffineModel3D<M>, C > Map<C, AffineGet> optimize(
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
			t.set( ( tile.getModel() ).getMatrix( null ) );
			
			if (initialTransforms != null && initialTransforms.containsKey( viewId ))
				t.concatenate( initialTransforms.get( viewId ) );
			
			resMap.put( viewId, t);
			
		}

		return resMap;	
	}
	
	
	protected static < M extends AbstractAffineModel3D< M >, C> HashMap< C, Tile< M > > assignViewsToTiles(
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
	
	protected static <C, M extends AbstractAffineModel3D< M >> void addPointMatches( 
			final Link<Set<C>> link, 
			final Tile<M> tileA, 
			final Tile<M> tileB,
			final Map<Set<C>, AffineGet> firstPassTransforms
			)
	{
		final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();
		final List<Point> pointsA = new ArrayList<>();
		final List<Point> pointsB = new ArrayList<>();
		
		// we use the vertices of the unit cube and their transformations as point matches 
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
		
		
		// case 1: we have no first pass results (e.g. we are preparing for the first pass)
		if (firstPassTransforms == null || 
				!(firstPassTransforms.containsKey( link.getFirst() ) || firstPassTransforms.containsKey( link.getSecond() )))
		{
			
			for (int i = 0; i < p.length; ++i)
			{
				link.getShift().applyInverse( pb[i], p[i] );
				pointsA.add( new Point( p[i] ) );
				pointsB.add( new Point( pb[i] ) );
			}
		}
		
		// case 2: we already know the location of the first point in its (grouped) tile
		// TODO: we will use the point match from first to second even if we have first pass results for second, is this okay?
		// else if (firstPassTransforms.containsKey( link.getFirst() ) && !firstPassTransforms.containsKey( link.getSecond() ))
		else if (firstPassTransforms.containsKey( link.getFirst() ) )
		{
			for (int i = 0; i < p.length; ++i)
			{
				firstPassTransforms.get( link.getFirst() ).applyInverse(  pa[i], p[i] );
				link.getShift().applyInverse( pb[i], pa[i] );
				pointsA.add( new Point( p[i] ) );
				pointsB.add( new Point( pb[i] ) );	
			}
			
			
		} 
		
		// case 3: we use the first pass result from second view group 
		//else if (firstPassTransforms.containsKey( link.getSecond() ) && !firstPassTransforms.containsKey( link.getFirst() ))
		else
		{
	
			for (int i = 0; i < p.length; ++i)
			{
				
				firstPassTransforms.get( link.getSecond() ).applyInverse( pb[i], p[i] );
				link.getShift().apply( pb[i], pa[i] );
				pointsA.add( new Point( pa[i] ) );
				pointsB.add( new Point( p[i] ) );
			}

		}
		
		// create PointMatches and connect Tiles
		for (int i = 0; i < pointsA.size(); ++i)
			pm.add( new PointMatch( pointsA.get( i ) , pointsB.get( i ) ) );

		tileA.addMatches( pm );
		tileB.addMatches( PointMatch.flip( pm ) );
		tileA.addConnectedTile( tileB );
		tileB.addConnectedTile( tileA );
	}
	
	protected static < M extends Model< M >, C> TileConfiguration addAndFixTiles(
			final List< Set<C> > views,
			final Map< Set<C>, Tile< M > > map,
			final Collection< Set<C> > fixedViews)
	{
		// if no fixed tiles are given, fix the tile of the first view
		final Collection< Set<C> > fixedViewsActual;
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
		
		for ( final Set<C> viewId : views )
		{
			final Tile< M > tile = map.get( viewId );

			// if one of the views that maps to this tile is fixed, fix this tile if it is not already fixed
			if ( fixedViewsActual.contains( viewId ) && !tc.getFixedTiles().contains( tile ) )
				tc.fixTile( tile );

			// add it if it is not already there
			tiles.add( tile );
		}	

		// now add connected tiles to the TileConfiguration
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
		
		List<Set<Integer>> views = new ArrayList<>();
		for (int i : new int[] {1,2,3,4})
		{
			HashSet< Integer > hashSet = new HashSet<Integer>();
			hashSet.add( i );
			views.add( hashSet );
		}
		
		List<Set< Integer>> fixedViews = new ArrayList<>();
		HashSet< Integer > firstView = new HashSet<Integer>();
		firstView.add( 1 );
		fixedViews.add( firstView );
		
		Map<Integer, AffineGet> initialTransforms = new HashMap<>();
		initialTransforms.put( 1, new Translation3D( 0, 0, 0 ) );
		initialTransforms.put( 2, new Translation3D( 0.75, 0, 0 ) );
		initialTransforms.put( 3, new Translation3D( 1.5, 0, 0 ) );
		initialTransforms.put( 4, new Translation3D( 2.25, 0, 0 ) );
		
		Map<Integer, Dimensions> dims = new HashMap<>();
		dims.put( 1, new FinalDimensions( 1,1,1 ) );
		dims.put( 2, new FinalDimensions( 1,1,1 ) );
		dims.put( 3, new FinalDimensions( 1,1,1 ) );
		dims.put( 4, new FinalDimensions( 1,1,1 ) );
		
		
		
		List<PairwiseStitchingResult< Integer >> pairwiseResults = new ArrayList<>();
		
		Group< Integer > s1 = new Group<Integer>(2);
		Group< Integer > s2 = new Group<Integer>(3);
		
		
		pairwiseResults.add( new PairwiseStitchingResult<Integer>( new ValuePair<>(s1, s2 ), null, new Translation3D(1,0,0), 1.0 ) );
		
		final GlobalOptimizationParameters params = new GlobalOptimizationParameters();
		params.useOnlyOverlappingPairs = true;
		
		Map< Set<Integer>, AffineGet > res = twoRoundGlobalOptimization( new TranslationModel3D(), views, fixedViews, initialTransforms, dims, pairwiseResults, params );
		
		res.forEach( ( x, y ) -> System.out.println( x + ": " + y ));
	
	}
	
	
	public static <V, T> Map<Pair<Set<V>, Set<V>>, T> expandToLinksSubset(Collection<Set<V>> views, Map<Pair<Set<V>, Set<V>>, T> links, BiPredicate< Set<V>, Set<V> > check)
	{
		Map<Pair<Set<V>, Set<V>>, T> res = new HashMap<>();
		Iterator< Set< V > > it1 = views.iterator();
		while ( it1.hasNext() )
		{
			Set< V > viewSet1 = it1.next();
			Iterator< Set< V > > it2 = views.iterator();
			while (it2.hasNext())
			{
				Set< V > viewSet2 = it2.next();
				for (Pair<Set<V>, Set<V>> linkPair : links.keySet())
				{
					if (check.test( viewSet1, linkPair.getA()) && check.test( viewSet2, linkPair.getB()))
					{
						res.put( new ValuePair< Set<V>, Set<V> >( viewSet1, viewSet2 ), links.get( linkPair ) );
					}
				}
			}
			
		}
		
		return res;
		
	}
	
	// check if first Set is a subset of second Set
	public class SubsetCheck <V> implements BiPredicate< Set<V>, Set<V> >
	{

		@Override
		public boolean test(Set< V > t, Set< V > u)
		{
			return u.containsAll( t );
		}
		
	}
	
	// check if second Set contains any of the elements of first Set (they intersect)
	public class IntersectCheck <V> implements BiPredicate< Set<V>, Set<V> >
	{

		@Override
		public boolean test(Set< V > t, Set< V > u)
		{
			for (V ti : t)
				if (u.contains( ti ))
					return true;
			return false;
		}
		
	}
}
