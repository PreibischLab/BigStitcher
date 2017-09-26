package net.preibisch.stitcher.headless.registration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import ij.ImageJ;

import mpicbg.models.AbstractModel;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOpt;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOptIterative;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOptTwoRound;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.strong.ImageCorrelationPointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.weak.MetaDataWeakLinkFactory;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;
import net.preibisch.stitcher.algorithm.PairwiseStitchingParameters;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.algorithm.globalopt.TransformationTools;
import net.preibisch.stitcher.gui.StitchingExplorer;
import net.preibisch.stitcher.input.FractalSpimDataGenerator;

public class TestGlobalOptTwoRound
{
	public static void main( String[] args )
	{
		new ImageJ();
		final SpimData2 spimData = FractalSpimDataGenerator.createVirtualSpimData();

		final List< ViewDescription > views = new ArrayList<>();

		// select only one illumination
		for ( final ViewDescription vs : spimData.getSequenceDescription().getViewDescriptions().values() )
		{
			if ( vs.getViewSetup().getIllumination().getId() == 0 )
			{
				if ( vs.getViewSetupId() <= 34 || vs.getViewSetupId() == 44 || vs.getViewSetupId() == 46 )
				{
					System.out.println( vs.getViewSetupId() );
					views.add( vs );
				}
			}
		}

		SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping =
				new SpimDataFilteringAndGrouping<>( spimData );

		filteringAndGrouping.addComparisonAxis( Tile.class );
		filteringAndGrouping.addFilters( views );

		final PairwiseStitchingParameters params = new PairwiseStitchingParameters( 0.1, 5, true, false );

		final long[] dsFactors = new long[]{ 4, 4, 1 };

		// the result are shifts relative to the current registration of the dataset!
		// that's because we find overlapping areas in global coordinates for which we run the Stitching
		final List< ? extends Pair< ? extends Group< ? extends ViewId >, ? extends Group< ? extends ViewId > > > pairs = filteringAndGrouping.getComparisons();
		final ArrayList< PairwiseStitchingResult< ViewId > > pairwiseResults = TransformationTools.computePairs(
				(List<Pair<Group<ViewId>, Group<ViewId>>>) pairs,
				params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
				filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
				dsFactors );

		// add the second illumination and group them together with the first
		final Collection< Group< ViewId > > groupsIn = new ArrayList<>();
		System.out.println();

		for ( final ViewDescription vs : spimData.getSequenceDescription().getViewDescriptions().values() )
		{
			if ( vs.getViewSetup().getIllumination().getId() == 1 )
			{
				if ( vs.getViewSetupId() <= 35 || vs.getViewSetupId() == 45 || vs.getViewSetupId() == 47 )
				{
					views.add( vs );
					final Group< ViewId > group = new Group< ViewId >( vs );
					for ( final ViewDescription vd : views )
						if ( vd.getViewSetupId() == vs.getViewSetupId() - 1 )
							group.getViews().add( vd );

					groupsIn.add( group );
					System.out.println( "Group: " + group );
				}
			}
		}

		final ArrayList< ViewId > fixed = new ArrayList<>();
		fixed.add( views.get( 0 ) );

		final IterativeConvergenceStrategy cs = new SimpleIterativeConvergenceStrategy( 10.0, 2.0, 10.0 );
		final PointMatchCreator pmc = new ImageCorrelationPointMatchCreator( pairwiseResults, 0.8 );

		/*
		final HashMap< ViewId, mpicbg.models.Tile< TranslationModel3D > > computeResults = GlobalOptIterative.compute(
				new TranslationModel3D(),
				pmc,
				cs,
				new MaxErrorLinkRemoval(),
				fixed,
				Group.toViewIdGroups( views ) );

		computeResults.forEach( ( k, v) -> {
			System.out.println( Group.pvid( k ) + ": " + Util.printCoordinates( v.getModel().getTranslation() ) );
		});
		*/

		final HashMap< ViewId, AffineTransform3D > computeResults = GlobalOptTwoRound.compute(
				new TranslationModel3D(),
				pmc,
				cs,
				new MaxErrorLinkRemoval(),
				new MetaDataWeakLinkFactory( spimData.getViewRegistrations().getViewRegistrations(), new SimpleBoundingBoxOverlap<>( spimData ) ),
				new ConvergenceStrategy( Double.MAX_VALUE ),
				fixed,
				groupsIn ); //Group.toViewIdGroups( views ) );

		computeResults.forEach( ( k, v) -> {
			System.out.println( Group.pvid( k ) + ": " + Util.printCoordinates( v.getTranslation() ) );
		});

		for ( final ViewId viewId : computeResults.keySet() )
		{
			final AffineTransform3D transform = computeResults.get( viewId );
			final ViewRegistration vr = spimData.getViewRegistrations().getViewRegistrations().get( viewId );

			final ViewTransform vt = new ViewTransformAffine( "two-round global opt", transform );
			vr.preconcatenateTransform( vt );
			vr.updateModel();
		}

		final StitchingExplorer< SpimData2, XmlIoSpimData2 > explorer =
				new StitchingExplorer< SpimData2, XmlIoSpimData2 >( spimData, null, null );

		explorer.getFrame().toFront();
	}
}
