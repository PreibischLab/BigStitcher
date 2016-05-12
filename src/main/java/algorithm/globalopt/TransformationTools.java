package algorithm.globalopt;

import input.GenerateSpimData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mpicbg.models.Tile;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import spim.fiji.ImgLib2Temp.Pair;
import algorithm.AveragedRandomAccessible;
import algorithm.DownsampleTools;
import algorithm.PairwiseStitching;
import algorithm.TransformTools;

public class TransformationTools
{
	public static < T extends RealType< T > > Pair< double[], Double > computeStitching(
			final ViewId viewIdA,
			final ViewId viewIdB,
			final ViewRegistration vA,
			final ViewRegistration vB,
			final int nPeaks,
			final boolean doSubpixel,
			final BasicImgLoader imgLoader,
			final boolean averageGrouped,
			final int[] downsampleFactors)
	{
		ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		final RandomAccessibleInterval<T> img1;
		final RandomAccessibleInterval<T> img2;
		if (averageGrouped)
		{
			// TODO: 2-d?
			AveragedRandomAccessible< T > avg1 = new AveragedRandomAccessible<>( 3 );
			AveragedRandomAccessible< T > avg2 = new AveragedRandomAccessible<>( 3 );
			
			Interval interval = null;
			
			for(int i = 0; i < ((GroupedViews) viewIdA).getViewIds().size(); i++)
			{
				ViewId tVid1 = ((GroupedViews) viewIdA).getViewIds().get( i );
				ViewId tVid2 = ((GroupedViews) viewIdB).getViewIds().get( i );
				RandomAccessibleInterval<T> tImg1 = DownsampleTools.openAndDownsample( imgLoader, tVid1, downsampleFactors );
						//(RandomAccessibleInterval<T>) imgLoader.getSetupImgLoader(tVid1.getViewSetupId()).getImage(tVid1.getTimePointId(), null);
				RandomAccessibleInterval<T> tImg2 = DownsampleTools.openAndDownsample( imgLoader, tVid2, downsampleFactors );
						//(RandomAccessibleInterval<T>) imgLoader.getSetupImgLoader(tVid2.getViewSetupId()).getImage(tVid2.getTimePointId(), null);
				
				// TODO: shifts between channels are not handled at the moment
				if (interval == null){
					interval = new FinalInterval( tImg1 );
				}
				
				//ImageJFunctions.show( tImg1 );
				
				avg1.addRAble( Views.extendZero( tImg1 ) );
				avg2.addRAble( Views.extendZero( tImg2 ) );
			}
			
			img1 = Views.interval( avg1, interval );	
			img2 = Views.interval( avg2, interval );
			
		}
		else
		{
			img1 = DownsampleTools.openAndDownsample( imgLoader, viewIdA, downsampleFactors );
			img2 = DownsampleTools.openAndDownsample( imgLoader, viewIdB, downsampleFactors );
		}

		// TODO: Test if 2d, and if then reduce dimensionality and ask for a 2d translation
		AbstractTranslation t1 = TransformTools.getInitialTranslation( vA, false, downsampleFactors);
		AbstractTranslation t2 = TransformTools.getInitialTranslation( vB, false, downsampleFactors );

		final Pair< double[], Double > result = PairwiseStitching.getShift( img1, img2, t1, t2, nPeaks, doSubpixel, null, service );

		if (result == null)
			return null;
		
		
		for (int i = 0; i< result.getA().length; ++i)
			result.getA()[i] *= downsampleFactors[i];		
		
		System.out.println("integer shift: " + Util.printCoordinates(result.getA()));
		System.out.print("cross-corr: " + result.getB());

		return result;
	}

	public static ArrayList< PairwiseStitchingResult > computePairs( 	final List< Pair< ViewId, ViewId > > pairs, 
																		final int nPeaks, final boolean doSubpixel, 
																		final ViewRegistrations vrs, 
																		final BasicImgLoader imgLoader, 
																		final boolean averageGrouped,
																		final int[] downsamplingFactors)
	{
		final ArrayList< PairwiseStitchingResult > results = new ArrayList<>();

		for ( final Pair< ViewId, ViewId > p : pairs )
		{
			System.out.println( "Compute pairwise: " + p.getA() + " <> " + p.getB() );
			final Pair< double[], Double > result = computeStitching( p.getA(), p.getB(), vrs.getViewRegistration( p.getA() ), vrs.getViewRegistration( p.getB() ), nPeaks, doSubpixel, imgLoader , averageGrouped, downsamplingFactors);
			
			if (result != null)
				results.add( new PairwiseStitchingResult( p, result.getA(), result.getB() ) );
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
		final HashMap< ViewId, AbstractTranslation > vl = new HashMap<>();
		
		final int[] downsamplingFactors = new int[] {1,1,1};

		for ( final ViewId viewId : viewIds )
		{
			vd.put( viewId, sd.getViewDescription( viewId ).getViewSetup().getSize() );
			vl.put( viewId, TransformTools.getInitialTranslation( vr.getViewRegistration( viewId ), is2d, downsamplingFactors) );
		}

		final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles(
				vd, vl, viewIds,
				fixedViews, groupedViews );
				
		// compute them
		final ArrayList< PairwiseStitchingResult > results = computePairs( pairs, 5, false, d.getViewRegistrations(), d.getSequenceDescription().getImgLoader() , false, downsamplingFactors);

		// add correspondences
		
		for ( final ViewId v : fixedViews )
			System.out.println( "Fixed: " + v );

		// global opt
		final HashMap< ViewId, Tile< TranslationModel3D > > models =
				GlobalOpt.compute( new TranslationModel3D(), results, fixedViews, groupedViews );

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
