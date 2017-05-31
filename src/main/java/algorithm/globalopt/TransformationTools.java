package algorithm.globalopt;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import algorithm.GroupedViewAggregator;
import algorithm.GroupedViewAggregator.ActionType;
import algorithm.PairwiseStitching;
import algorithm.PairwiseStitchingParameters;
import algorithm.TransformTools;
import gui.popup.DisplayOverlapTestPopup;
import ij.IJ;
import input.GenerateSpimData;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.boundingbox.BoundingBoxMaximalGroupOverlap;
import spim.process.fusion.boundingbox.overlap.IterativeBoundingBoxDetermination;
import spim.process.interestpointregistration.global.GlobalOpt;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.pointmatchcreating.ImageCorrelationPointMatchCreator;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class TransformationTools
{
	
	public static < T extends RealType< T > > Pair<Pair< double[], Double >, RealInterval> computeStitchingNonEqualTransformations(
			final Group<? extends ViewId> viewIdsA,
			final Group<? extends ViewId> viewIdsB,
			final ViewRegistrations vrs,
			final PairwiseStitchingParameters params,
			final AbstractSequenceDescription< ?,? extends BasicViewDescription<?>, ? > sd,
			final GroupedViewAggregator gva,
			final long[] downsampleFactors,
			final ExecutorService service )
	{

		final double[] downsampleDbl = new double[downsampleFactors.length];
		for (int d = 0; d < downsampleFactors.length; d++)
			downsampleDbl[d] = downsampleFactors[d];

		// get Overlap Bounding Box
		final List<List<ViewId>> views = new ArrayList<>();
		views.add( new ArrayList<>(viewIdsA.getViews()) );
		views.add( new ArrayList<>(viewIdsB.getViews()) );
		BoundingBoxMaximalGroupOverlap< ViewId > bbDet = new BoundingBoxMaximalGroupOverlap<ViewId>( views, sd, vrs );
		BoundingBox bbOverlap = bbDet.estimate( "Max Overlap" );

		
		List<RandomAccessibleInterval< FloatType >> raiOverlaps = new ArrayList<>();		
		for (List< ViewId > tileViews : views)
		{
			// wrap every view id (corresponding e.g. to different channels, illums,.. ) in list
			List<List< ViewId >> wrapped = tileViews.stream().map( v -> {
				ArrayList< ViewId > wrp = new ArrayList<ViewId>();
				wrp.add( v );
				return wrp;} ).collect( Collectors.toList() );

			// open all of them "virtually fused"
			List< RandomAccessibleInterval< FloatType > > openFused = 
					DisplayOverlapTestPopup.openVirtuallyFused( sd, vrs, wrapped, bbOverlap, downsampleDbl );

			// aggregate the group into one image
			RandomAccessibleInterval< FloatType > raiI = gva.aggregate( 
					openFused, 
					tileViews,
					sd );

			raiOverlaps.add(raiI);
		}

		// the overlap in both images
		final RandomAccessibleInterval< FloatType > img1 = raiOverlaps.get(0);
		final RandomAccessibleInterval< FloatType > img2 = raiOverlaps.get(1);
		
		// compute phase correlation shift (passing (0,0,..) translations prevents any overlap correction inside)
		final Pair< double[], Double > result = PairwiseStitching.getShift(
				img1,
				img2,
				new Translation( img1.numDimensions() ),
				new Translation( img1.numDimensions() ),
				params,
				service );

		if (result == null)
			return null;

		// correct for downsampling, but nothing else
		for (int i = 0; i< result.getA().length; ++i)
			result.getA()[i] *= downsampleFactors[i];

		System.out.println("shift: " + Util.printCoordinates(result.getA()));
		System.out.print("cross-corr: " + result.getB());

		return new ValuePair<>(result, bbOverlap);
	}
	
	public static < T extends RealType< T > > Pair<Pair< double[], Double >, RealInterval> computeStitching(
			final Group<? extends ViewId> viewIdsA,
			final Group<? extends ViewId> viewIdsB,
			final ViewRegistrations vrs,
			final PairwiseStitchingParameters params,
			final AbstractSequenceDescription< ?,? extends BasicViewDescription<?>, ? > sd,
			final GroupedViewAggregator gva,
			final long[] downsampleFactors,
			final ExecutorService service )
	{
		
		// the transformation that maps the downsampled image coordinates back to the original input(!) image space
		final AffineTransform3D dsCorrectionT = new AffineTransform3D();

		// get Overlap Bounding Box
		final List<List<ViewId>> views = new ArrayList<>();
		views.add( new ArrayList<>(viewIdsA.getViews()) );
		views.add( new ArrayList<>(viewIdsB.getViews()) );
		BoundingBoxMaximalGroupOverlap< ViewId > bbDet = new BoundingBoxMaximalGroupOverlap<ViewId>( views, sd, vrs );
		BoundingBox bbOverlap = bbDet.estimate( "Max Overlap" );

		// this should be caught outside of this method already, but check nonetheless
		if (bbOverlap == null)
			return null;

		// get one image per group
		final RandomAccessibleInterval<T> img1 = gva.aggregate( viewIdsA, sd, downsampleFactors, dsCorrectionT );	
		final RandomAccessibleInterval<T> img2 = gva.aggregate( viewIdsB, sd, downsampleFactors, dsCorrectionT );

		if (img1 == null || img2 == null)
		{
			IOFunctions.println( "WARNING: Tried to open missing View when computing Stitching for " + viewIdsA + " and " + 
						viewIdsB + ". No link between those could be determined");
			return null;
		}

		// get translations
		// TODO: is the 2d check here meaningful?
		boolean is2d = img1.numDimensions() == 2;
		Pair< AffineGet, TranslationGet > t1 = TransformTools.getInitialTransforms( vrs.getViewRegistration(viewIdsA.iterator().next()), is2d, dsCorrectionT );
		Pair< AffineGet, TranslationGet > t2 = TransformTools.getInitialTransforms( vrs.getViewRegistration(viewIdsB.iterator().next()), is2d, dsCorrectionT );

		final Pair< double[], Double > result;
		
		if (params.doLucasKanade)
			result = PairwiseStitching.getShiftLucasKanade( img1, img2, t1.getB(), t2.getB(), params, service );
		else
			result = PairwiseStitching.getShift( img1, img2, t1.getB(), t2.getB(), params, service );

		if (result == null)
			return null;
		
		
		for (int i = 0; i< result.getA().length; ++i)			
			result.getA()[i] *= downsampleFactors[i];
		
		t1.getA().apply( result.getA(), result.getA() );

		System.out.println("shift: " + Util.printCoordinates(result.getA()));
		System.out.print("cross-corr: " + result.getB());

		return new ValuePair< Pair<double[],Double>, RealInterval >( result, bbOverlap );
	}

	/**
	 * 
	 * @param pairs list of potentially overlapping pairs of view groups, this will be modified!
	 * @param vrs 
	 * @param sd
	 * @return list of the pairs that were removed
	 */
	public static <V extends ViewId> List< Pair< Group< V >, Group< V > > > filterNonOverlappingPairs(
			List< Pair<  Group< V >,  Group< V > > > pairs,
			final ViewRegistrations vrs,
			final AbstractSequenceDescription< ?, ? extends BasicViewDescription< ? >, ? > sd
			)
	{
		IterativeBoundingBoxDetermination< V > ibbd = new IterativeBoundingBoxDetermination< V >((SequenceDescription)sd, vrs);
		final List< Pair<  Group< V >,  Group< V > > > removedPairs = new ArrayList<>();
		
		for (int i = pairs.size() - 1; i >= 0; i--)
		{
			final List<Set<V>> pairAsGroups = new ArrayList<>();
			pairAsGroups.add( pairs.get( i ).getA().getViews() );
			pairAsGroups.add( pairs.get( i ).getB().getViews() );
			
			BoundingBox bb = ibbd.getMaxOverlapBoundingBox( pairAsGroups );
			
			if (bb == null)
			{
				removedPairs.add( pairs.get( i ) );
				pairs.remove( i );
			}
			
		}
		
		return removedPairs;
		
	}
	
	public static <V extends ViewId > ArrayList< PairwiseStitchingResult<ViewId> > computePairs( 	final List< Pair<  Group< V >,  Group< V > > > pairs, 
																		final PairwiseStitchingParameters params, 
																		final ViewRegistrations vrs,
																		final AbstractSequenceDescription< ?, ? extends BasicViewDescription< ? >, ? > sd, 
																		final GroupedViewAggregator gva,
																		final long[] downsamplingFactors)
	{
		// set up executor service
		final ExecutorService serviceGlobal = Executors.newFixedThreadPool( Math.max( 2, Runtime.getRuntime().availableProcessors() / 2 ) );
		final ArrayList< Callable< Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > > > tasks = new ArrayList<>();

		// remove non-overlapping comparisons
		final List< Pair< Group< V >, Group< V > > > removedPairs = filterNonOverlappingPairs( pairs, vrs, sd );
		removedPairs.forEach( p -> System.out.println( "Skipping non-overlapping pair: " + p.getA() + " -> " + p.getB() ) );
		
		final int nComparisions = pairs.size();
		AtomicInteger nCompleted = new AtomicInteger();
		
		IJ.showProgress( 0.0 );
		
		for ( final Pair< Group< V >, Group< V > > p : pairs )
		{
			tasks.add( new Callable< Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > >()
			{
				@Override
				public Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > call() throws Exception
				{
					Pair<Pair< double[], Double >, RealInterval> result = null;
					
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Compute pairwise: " + p.getA() + " <> " + p.getB() );

					final ExecutorService serviceLocal = Executors.newFixedThreadPool( Math.max( 2, Runtime.getRuntime().availableProcessors() / 4 ) );

					final ViewId firstVdA = p.getA().iterator().next();
					final ViewId firstVdB = p.getB().iterator().next();
					
					boolean nonTranslationsEqual = TransformTools.nonTranslationsEqual( vrs.getViewRegistration( firstVdA ), vrs.getViewRegistration( firstVdB ) );
					
					if (nonTranslationsEqual)
					{
						System.out.println( "non translations equal" );
						result = computeStitching(
								p.getA(),
								p.getB(),
								vrs,
								params,
								sd,
								gva,
								downsamplingFactors,
								serviceLocal );
					}
					else
					{
						result = computeStitchingNonEqualTransformations( 
								p.getA(),
								p.getB(),
								vrs,
								params,
								sd,
								gva,
								downsamplingFactors,
								serviceLocal );
						System.out.println( "non translations NOT equal, using virtually fused views for stitching" );
					}

					serviceLocal.shutdown();

					// show progress in ImageJ progress bar (TODO: should we really do this here or leave it GUI-independent?)
					int nCompletedI = nCompleted.incrementAndGet();
					IJ.showProgress( (double) nCompletedI / nComparisions );
					
					if (result != null)
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Compute pairwise: " + p.getA() + " <> " + p.getB() + ": r=" + result.getA().getB() );
					
					return new ValuePair<>( p,  result );
				}
			});
		}

		final ArrayList< PairwiseStitchingResult< ViewId > > results = new ArrayList<>();

		try
		{
			for ( final Future< Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > > future : serviceGlobal.invokeAll( tasks ) )
			{
				// wait for task to complete
				final Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > result = future.get();

				if (result.getB() == null)
					continue;
				
				final ViewRegistration vrA = vrs.getViewRegistration( result.getA().getA().iterator().next() );
				final ViewRegistration vrB = vrs.getViewRegistration( result.getA().getB().iterator().next() );
				
				// get non-translation transform between the initial location of groupA
				Pair< AffineGet, TranslationGet > initialTransformsA = TransformTools.getInitialTransforms( vrA, false, new AffineTransform3D() );

				// apply to shift vector
				// FIXME: this only works for scaling, we need to do something different about rotations, etc.
				boolean nonTranslationsEqual = TransformTools.nonTranslationsEqual(vrA, vrB);
				if (nonTranslationsEqual)
					initialTransformsA.getA().apply( result.getB().getA().getA(), result.getB().getA().getA() );

				AffineTransform3D resT = new AffineTransform3D();
				resT.translate( result.getB().getA().getA() );
				
				// TODO: translate does not properly update the matrix, but copy does -> we need a newer version of imglib2-realtransform
				resT = resT.copy();

				// TODO: can we get rid of this ugly cast
				Group< ViewId > groupA = new Group<ViewId>(result.getA().getA().getViews().stream().map( x -> (ViewId) x ).collect( Collectors.toList() ));
				Group< ViewId > groupB = new Group<ViewId>(result.getA().getB().getViews().stream().map( x -> (ViewId) x ).collect( Collectors.toList() ));

				// TODO: when does that really happen?
				if ( result.getB() != null)
					results.add( new PairwiseStitchingResult<>( new ValuePair<>(groupA, groupB), result.getB().getB(),  resT, result.getB().getA().getB() ) );
			}
		}
		catch ( final Exception e )
		{
			IOFunctions.println( "Failed to compute pairwise shift: " + e );
			e.printStackTrace();
			return null;
		}

		return results;
	}

	public static void main( String[] args )
	{
		final SpimData d = GenerateSpimData.grid3x2();
		final SequenceDescription sd = d.getSequenceDescription();

		// select views to process
		final List< ViewId > rawViewIds = new ArrayList< ViewId >();
		rawViewIds.addAll( sd.getViewDescriptions().keySet() );
		Collections.sort( rawViewIds );

		// take together all views where the all attributes are the same except channel (i.e. group the channels)
		final List< Group<ViewId> > viewIds = Group.groupByChannel( rawViewIds, sd );

		// define fixed tiles
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.addAll( viewIds.get( 0 ).getViews() );

		final long[] downsamplingFactors = new long[] {2,2,1};

		final List<Pair<Group<ViewId>, Group<ViewId>>> pairs = new ArrayList<>();
		for (int i = 0; i < viewIds.size(); i++)
			for (int j = i+1; j< viewIds.size(); j++)
				pairs.add( new ValuePair<>( viewIds.get( i ), viewIds.get( j ) ) );		
		
		final GroupedViewAggregator gva = new GroupedViewAggregator();
		gva.addAction( ActionType.AVERAGE, Channel.class, null );
		
		// compute pairwise shifts
		final ArrayList< PairwiseStitchingResult <ViewId>> results = computePairs( pairs,
																new PairwiseStitchingParameters(), 
																d.getViewRegistrations(),
																d.getSequenceDescription() ,
																gva,
																downsamplingFactors);

		results.forEach( r -> System.out.println( r.getTransform() ) );
		
		for ( final ViewId v : fixedViews )
			System.out.println( "Fixed: " + v );

		GlobalOpt.compute( 
				new TranslationModel3D(),
				new ImageCorrelationPointMatchCreator( results, 0.5 ),
				new ConvergenceStrategy( 5.0 ),
				fixedViews,
				viewIds );


	}
}
