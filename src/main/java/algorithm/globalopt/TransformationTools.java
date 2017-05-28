package algorithm.globalopt;


import input.GenerateSpimData;
import spim.fiji.spimdata.SpimDataTools;
import spim.fiji.spimdata.ViewSetupUtils;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import algorithm.DownsampleTools;
import algorithm.GroupedViewAggregator;
import algorithm.PairwiseStitching;
import algorithm.PairwiseStitchingParameters;
import algorithm.TransformTools;
import algorithm.GroupedViewAggregator.ActionType;
import bdv.BigDataViewer;
import gui.popup.DisplayOverlapTestPopup;
import ij.IJ;
import input.GenerateSpimData;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.pointdescriptor.LocalCoordinateSystemPointDescriptor;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaders;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.Threads;
import spim.process.fusion.ImagePortion;
import spim.process.fusion.boundingbox.overlap.IterativeBoundingBoxDetermination;
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
		
		
		// FIXME: remove cast of sd, change constructor?
		IterativeBoundingBoxDetermination< ViewId > bbDet = new IterativeBoundingBoxDetermination<ViewId>( (SequenceDescription) sd, vrs );
		
		// get Overlap Bounding Box
		final List<List<ViewId>> views = new ArrayList<>();
		views.add( new ArrayList<>(viewIdsA.getViews()) );
		views.add( new ArrayList<>(viewIdsB.getViews()) );
		BoundingBox bbOverlap = bbDet.getMaxOverlapBoundingBox( views );
		
		//System.out.println( "Overlap BB: " + Util.printInterval( bbOverlap ) );
				
		
		List<RandomAccessibleInterval< FloatType >> raiOverlaps = new ArrayList<>();
		
		for (List< ViewId > tileViews : views)
		{
			// wrap every vid in list
			List<List< ViewId >> wrapped = tileViews.stream().map( v -> {
				ArrayList< ViewId > wrp = new ArrayList<ViewId>();
				wrp.add( v );
				return wrp;} ).collect( Collectors.toList() );
			
			List< RandomAccessibleInterval< FloatType > > openFused = 
					DisplayOverlapTestPopup.openVirtuallyFused( sd, vrs, wrapped, bbOverlap, downsampleDbl );
			
			
			RandomAccessibleInterval< FloatType > raiI = gva.aggregate( 
					openFused, 
					tileViews,
					sd );
			
			raiOverlaps.add(raiI);
		}
		
		
		RandomAccessibleInterval< FloatType > img1 = raiOverlaps.get(0);
		RandomAccessibleInterval< FloatType > img2 = raiOverlaps.get(1);
		
		final Pair< double[], Double > result = PairwiseStitching.getShift( img1, img2, new Translation( img1.numDimensions() ), new Translation( img1.numDimensions() ), params, service );
	
		if (result == null)
			return null;		
		

		for (int i = 0; i< result.getA().length; ++i)
		{
			result.getA()[i] *= downsampleFactors[i];
			
		}
		
		/*
			// add shift between view group 1 bbox and overlap
			result.getA()[i] += bbOverlap.realMin( i ) - viewsABoundingBox.realMin( i );
		}
		
		*/
		
		
		// TODO: return the bounding Box here
		
		System.out.println("integer shift: " + Util.printCoordinates(result.getA()));
		System.out.print("cross-corr: " + result.getB());

		
		//service.shutdown();
		
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
		
		// FIXME: remove cast of sd, change constructor?
		IterativeBoundingBoxDetermination< ViewId > bbDet = new IterativeBoundingBoxDetermination<ViewId>( (SequenceDescription) sd, vrs );
				
		// get Overlap Bounding Box
		final List<List<ViewId>> views = new ArrayList<>();
		views.add( new ArrayList<>(viewIdsA.getViews()) );
		views.add( new ArrayList<>(viewIdsB.getViews()) );
		BoundingBox bbOverlap = bbDet.getMaxOverlapBoundingBox( views );
		
		if (bbOverlap == null)
			return null;
		
		
		
		/*
		// TODO: check if overlapping, else return immediately
		// TODO: can we ensure we have a ImgLoader here (BDV wraps ImgLoader into a BasicImgLoader??)
		if (ImgLoader.class.isInstance( sd.getImgLoader() ))
		{
			
			Dimensions dimsA = ((ImgLoader)sd.getImgLoader()).getSetupImgLoader( viewIdsA.getViewSetupId() ).getImageSize( viewIdsA.getTimePointId() );
			Dimensions dimsB = ((ImgLoader)sd.getImgLoader()).getSetupImgLoader( viewIdsB.getViewSetupId() ).getImageSize( viewIdsB.getTimePointId() );
			
			if (dimsA != null && dimsB != null)
			{
				Pair< AffineGet, TranslationGet > trA = TransformTools.getInitialTransforms( vA, dimsA.numDimensions() == 2, dsCorrectionT );
				Pair< AffineGet, TranslationGet > trB = TransformTools.getInitialTransforms( vB, dimsB.numDimensions() == 2, dsCorrectionT );
				
				if (!PairwiseStrategyTools.overlaps( dimsA, dimsB, trA.getB(), trB.getB() ))
					return null;
			}
		}

		
		*/
			
		final RandomAccessibleInterval<T> img1;
		final RandomAccessibleInterval<T> img2;

		
		/*
		if (gva != null && GroupedViews.class.isInstance( viewIdsA ))
		{
		*/
			img1 = gva.aggregate( viewIdsA, sd, downsampleFactors, dsCorrectionT );	
			img2 = gva.aggregate( viewIdsB, sd, downsampleFactors, dsCorrectionT );
			/*
		}
		else
		{
			img1 = DownsampleTools.openAndDownsample( sd.getImgLoader(), viewIdsA, downsampleFactors, dsCorrectionT );
			img2 = DownsampleTools.openAndDownsample( sd.getImgLoader(), viewIdsB, downsampleFactors, dsCorrectionT );
		}
	*/
		
		if (img1 == null || img2 == null)
		{
			IJ.log( "WARNING: Tried to open missing View when computing Stitching for " + viewIdsA + " and " + 
						viewIdsB + ". No link between those could be determined");
			return null;
		}
		
		
		boolean is2d = img1.numDimensions() == 2;
		
		
		// TODO: Test if 2d, and if then reduce dimensionality and ask for a 2d translation
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
		
		// shift
		//for (int i = 0; i< result.getA().length; ++i)
		//	result.getA()[i] = vB.getModel().get( i, 3 ) - result.getA()[i];
		
		System.out.println("integer shift: " + Util.printCoordinates(result.getA()));
		System.out.print("cross-corr: " + result.getB());

		
		//service.shutdown();
		
		return new ValuePair< Pair<double[],Double>, RealInterval >( result, bbOverlap );
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

					// TODO: check for ViewRegistration "equality" here and use fused views if they differ					
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

					if (result != null)
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Compute pairwise: " + p.getA() + " <> " + p.getB() + ": r=" + result.getB() );
					
					return new ValuePair<>( p,  result );
				}
			});
		}

		final ArrayList< PairwiseStitchingResult< ViewId > > results = new ArrayList<>();

		try
		{
			// invokeAll() returns when all tasks are complete
			for ( final Future< Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > > future : serviceGlobal.invokeAll( tasks ) )
			{
				final Pair< Pair< Group< V >, Group< V > >, Pair<Pair< double[], Double >, RealInterval> > result = future.get();

				if (result.getB() == null)
					continue;
				
				final ViewRegistration vrA = vrs.getViewRegistration( result.getA().getA().iterator().next() );
				final ViewRegistration vrB = vrs.getViewRegistration( result.getA().getB().iterator().next() );
				
				// get non-translation transform between the initial localtions
				Pair< AffineGet, TranslationGet > initialTransformsA = TransformTools.getInitialTransforms( vrA, false, new AffineTransform3D() );
				Pair< AffineGet, TranslationGet > initialTransformsB = TransformTools.getInitialTransforms( vrB, false, new AffineTransform3D() );
				AffineGet mapBack = TransformTools.mapBackTransform( initialTransformsA.getA(), initialTransformsB.getA() );
	
				// correct translation determined by phase correlation by that transform
				// TODO: move this inside the shift determination method -> make this method agnostic of actual intensity-based method used
				AffineTransform3D resT = new AffineTransform3D();
				resT.translate( result.getB().getA().getA() );
				
				boolean nonTranslationsEqual = TransformTools.nonTranslationsEqual(vrA, vrB);
				
				if (nonTranslationsEqual)
					resT = resT.copy().preConcatenate( mapBack );

				// create Set pair identifying the pair
				
				/*
				Pair<Set<ViewId>, Set<ViewId>> setPair = new ValuePair< Set<ViewId>, Set<ViewId> >( new HashSet<>(), new HashSet<>() );
				if (result.getA().getA() instanceof GroupedViews)
				{ 
					setPair.getA().addAll( ((GroupedViews)result.getA().getA()).getViewIds() );
					setPair.getB().addAll( ((GroupedViews)result.getA().getB()).getViewIds() );
				}
				else
				{
					setPair.getA().add( result.getA().getA() );
					setPair.getA().add( result.getA().getB() );
				}
				*/
				
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
			IOFunctions.println( "Failed to compute min/max: " + e );
			e.printStackTrace();
			return null;
		}

		return results;
	}

	public static void main( String[] args )
	{
		final SpimData d = GenerateSpimData.grid3x2();
		final SequenceDescription sd = d.getSequenceDescription();
		final ViewRegistrations vr = d.getViewRegistrations();

		final boolean is2d = false;

		// select views to process
		final List< ViewId > rawViewIds = new ArrayList< ViewId >();
		rawViewIds.addAll( sd.getViewDescriptions().keySet() );
		Collections.sort( rawViewIds );

		// take together all views where the all attributes are the same except channel (i.e. group the channels)
		// they are now represented by the channel of the first ID (e.g. channelId=0)
		final List< GroupedViews > viewIds = GroupedViews.groupByChannel( rawViewIds, sd );

		// define fixed tiles
		final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
		fixedViews.add( viewIds.get( 0 ) );

		// define groups (no checks in between Tiles of a group, they are transformed together)
		final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();

		// find all pairwise matchings that we need to compute
		final HashMap< ViewId, Dimensions > vd = new HashMap<>();
		final HashMap< ViewId, TranslationGet > vl = new HashMap<>();
		
		final long[] downsamplingFactors = new long[] {1,1,1};

		for ( final ViewId viewId : viewIds )
		{
			vd.put( viewId, sd.getViewDescription( viewId ).getViewSetup().getSize() );
			vl.put( viewId, TransformTools.getInitialTransforms( vr.getViewRegistration( viewId ), is2d, new AffineTransform3D()).getB() );
		}

		final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles(
				vd, vl, viewIds,
				fixedViews, groupedViews );
				
		// compute them
		final ArrayList< PairwiseStitchingResult <ViewId>> results = computePairs( pairs,
																new PairwiseStitchingParameters(), 
																d.getViewRegistrations(),
																d.getSequenceDescription() ,
																null,
																downsamplingFactors);

		// add correspondences
		
		if (true)
			return;
		
		for ( final ViewId v : fixedViews )
			System.out.println( "Fixed: " + v );

		
		GlobalOptimizationParameters params = new GlobalOptimizationParameters();
		// global opt
		final HashMap< ViewId, Tile< TranslationModel3D > > models =
				GlobalOpt.compute( new TranslationModel3D(), results, fixedViews, groupedViews , params);

		/*
		// save the corresponding detections and output result
		for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult > p : result )
		{
			final InterestPointList listA = spimData.getViewInterestPoints().getViewInterestPointLists( p.getA().getA() ).getInterestPointList( "beads" );
			final InterestPointList listB = spimData.getViewInterestPoints().getViewInterestPointLists( p.getA().getB() ).getInterestPointList( "beads" );
			setCorrespondences( p.getB().getInliers(), p.getA().getA(), p.getA().getB(), "beads", "beads", listA, listB );

			System.out.println( p.getB().getFullDesc() );
		}
		

		// map-back model (useless as we fix the first one)
		final AffineTransform3D mapBack = computeMapBackModel(
				spimData.getSequenceDescription().getViewDescription( viewIds.get( 0 ) ).getViewSetup().getSize(),
				transformations.get( viewIds.get( 0 ) ).getModel(),
				models.get( viewIds.get( 0 ) ).getModel(),
				new RigidModel3D() );
		*/
	}
}
