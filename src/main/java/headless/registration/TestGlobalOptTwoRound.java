package headless.registration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.TransformationTools;
import ij.ImageJ;
import input.FractalSpimDataGenerator;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.multithreading.SimpleMultiThreading;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.interestpointregistration.global.GlobalOpt;
import spim.process.interestpointregistration.global.GlobalOptIterative;
import spim.process.interestpointregistration.global.GlobalOptTwoRound;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import spim.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.strong.ImageCorrelationPointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.weak.MetaDataWeakLinkFactory;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

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
		final List< ? extends Pair< Group< ? extends ViewId >, Group< ? extends ViewId > > > pairs = (List< ? extends Pair< Group< ? extends ViewId >, Group< ? extends ViewId > > >) filteringAndGrouping.getComparisons();
		final ArrayList< PairwiseStitchingResult< ViewId > > pairwiseResults = TransformationTools.computePairs(
				(List<Pair<Group<ViewId>, Group<ViewId>>>) pairs,
				params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
				filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
				dsFactors );

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
				new MetaDataWeakLinkFactory( spimData.getViewRegistrations() ),
				new ConvergenceStrategy( Double.MAX_VALUE ),
				fixed,
				Group.toViewIdGroups( views ) );

		computeResults.forEach( ( k, v) -> {
			System.out.println( Group.pvid( k ) + ": " + Util.printCoordinates( v.getTranslation() ) );
		});

	}
}
