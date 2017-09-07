package spim.fiji.plugin;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.TransformationTools;
import algorithm.lucaskanade.LucasKanadeParameters;
import gui.StitchingUIHelper;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.interestpointregistration.TransformationModelGUI;
import spim.fiji.plugin.interestpointregistration.parameters.BasicRegistrationParameters;
import spim.fiji.plugin.interestpointregistration.parameters.GroupParameters.InterestpointGroupingType;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.boundingbox.BoundingBoxMaximalGroupOverlap;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Calculate_Pairwise_Shifts implements PlugIn
{

	@Override
	public void run(String arg)
	{

		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "for pairwise shift calculation", true, true, true, true, true ) )
			return;
		
		final SpimData2 data = result.getData();
		final SpimDataFilteringAndGrouping< SpimData2 > grouping = new SpimDataFilteringAndGrouping<>( data );

		// suggest the default grouping by channels and illuminations
		final HashSet< Class <? extends Entity> > groupingFactors = new HashSet<>();
		groupingFactors.add( Illumination.class );
		groupingFactors.add( Channel.class );
		grouping.askUserForGrouping(data.getSequenceDescription().getViewDescriptions().values(), groupingFactors);
		grouping.askUserForGroupingAggregator();
		
		final boolean is2d = StitchingUIHelper.allViews2D( grouping.getFilteredViews() );
		final long[] ds = StitchingUIHelper.askForDownsampling( data, is2d );
		
		
	}
	
	public static void main(String[] args)
	{
		new Calculate_Pairwise_Shifts().run( "Test ..." );
	}
	
	public static boolean processPhaseCorrelation(
			SpimData2 data,
			SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping,
			PairwiseStitchingParameters params,
			long[] dsFactors)
	{
		// getpairs to compare
		List< ? extends Pair< Group< ? extends ViewId >, Group< ? extends ViewId > > > pairs = (List< ? extends Pair< Group< ? extends ViewId >, Group< ? extends ViewId > > >) filteringAndGrouping.getComparisons();

		// calculate
		final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
				(List< Pair< Group< ViewId >, Group< ViewId > > >) pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
				filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
				dsFactors );

		// remove old results

		// this is just a cast of pairs to Group<ViewId>
		final List< ValuePair< Group< ViewId >, Group< ViewId > > > castPairs = pairs.stream().map( p -> {
			final Group< ViewId > vidGroupA = new Group<>( p.getA().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
			final Group< ViewId > vidGroupB = new Group<>( p.getB().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
			return new ValuePair<>( vidGroupA, vidGroupB );
		}).collect( Collectors.toList() );

		for (ValuePair< Group< ViewId >, Group< ViewId > > pair : castPairs)
		{
			// try to remove a -> b and b -> a, just to make sure
			data.getStitchingResults().getPairwiseResults().remove( pair );
			data.getStitchingResults().getPairwiseResults().remove( new ValuePair<>( pair.getB(), pair.getA() ) );
		}

		// update StitchingResults with Results
		for ( final PairwiseStitchingResult< ViewId > psr : results )
		{
			if (psr == null)
				continue;

			data.getStitchingResults().setPairwiseResultForPair(psr.pair(), psr );
		}

		return true;
	}

	public static boolean processLucasKanade(
			SpimData2 data,
			SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping,
			LucasKanadeParameters params,
			long[] dsFactors)
	{
		// getpairs to compare
		List< ? extends Pair< Group< ? extends ViewId >, Group< ? extends ViewId > > > pairs = (List< ? extends Pair< Group< ? extends ViewId >, Group< ? extends ViewId > > >) filteringAndGrouping
				.getComparisons();

		// calculate
		final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairsLK(
				(List< Pair< Group< ViewId >, Group< ViewId > > >) pairs,
				params,
				filteringAndGrouping.getSpimData().getViewRegistrations(),
				filteringAndGrouping.getSpimData().getSequenceDescription(),
				filteringAndGrouping.getGroupedViewAggregator(),
				dsFactors,
				new ProgressWriterIJ());

		// remove old results
		// this is just a cast of pairs to Group<ViewId>
		final List< ValuePair< Group< ViewId >, Group< ViewId > > > castPairs = pairs.stream().map( p -> {
			final Group< ViewId > vidGroupA = new Group<>(
					p.getA().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
			final Group< ViewId > vidGroupB = new Group<>(
					p.getB().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
			return new ValuePair<>( vidGroupA, vidGroupB );
		} ).collect( Collectors.toList() );

		for ( ValuePair< Group< ViewId >, Group< ViewId > > pair : castPairs )
		{
			// try to remove a -> b and b -> a, just to make sure
			data.getStitchingResults().getPairwiseResults().remove( pair );
			data.getStitchingResults().getPairwiseResults().remove( new ValuePair<>( pair.getB(), pair.getA() ) );
		}

		// update StitchingResults with Results
		for ( final PairwiseStitchingResult< ViewId > psr : results )
		{
			if ( psr == null )
				continue;

			data.getStitchingResults().setPairwiseResultForPair( psr.pair(), psr );
		}

		return true;
	}


	public static boolean processInterestPoint(final SpimData2 data,
			final SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping)
	{
		// by default the registration suggests what is selected in the dialog
		// (and was passed to filteringAndGrouping)
		Interest_Point_Registration.defaultGroupTiles = filteringAndGrouping.getGroupingFactors()
				.contains( Tile.class );
		Interest_Point_Registration.defaultGroupIllums = filteringAndGrouping.getGroupingFactors()
				.contains( Illumination.class );
		Interest_Point_Registration.defaultGroupChannels = filteringAndGrouping.getGroupingFactors()
				.contains( Channel.class );

		// which timepoints are part of the data (we dont necessarily need them,
		// but basicRegistrationParameters GUI wants them)
		List< ? extends ViewId > viewIds = filteringAndGrouping.getFilteredViews();
		final List< TimePoint > timepointToProcess = SpimData2.getAllTimePointsSorted( data, viewIds );
		final int nAllTimepoints = data.getSequenceDescription().getTimePoints().size();

		// query basic registration parameters
		final BasicRegistrationParameters brp = new Interest_Point_Registration().basicRegistrationParameters(
				timepointToProcess, nAllTimepoints, true, data, (List< ViewId >) viewIds );
		if ( brp == null )
			return false;

		// query algorithm parameters
		GenericDialog gd = new GenericDialog( "Registration Parameters" );

		gd.addMessage( "Algorithm parameters [" + brp.pwr.getDescription() + "]",
				new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		gd.addMessage( "" );

		brp.pwr.presetTransformationModel( new TransformationModelGUI( 0 ) );
		brp.pwr.addQuery( gd );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return false;
		if ( !brp.pwr.parseDialog( gd ) )
			return false;

		// get all possible group pairs
		List< ? extends Pair< ? extends Group< ? extends ViewId >, ? extends Group< ? extends ViewId > > > pairs = filteringAndGrouping
				.getComparisons();

		// remove old results
		// this is just a cast of pairs to Group<ViewId>
		final List< ValuePair< Group< ViewId >, Group< ViewId > > > castPairs = pairs.stream().map( p -> {
			final Group< ViewId > vidGroupA = new Group<>(
					p.getA().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
			final Group< ViewId > vidGroupB = new Group<>(
					p.getB().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
			return new ValuePair<>( vidGroupA, vidGroupB );
		} ).collect( Collectors.toList() );

		for ( ValuePair< Group< ViewId >, Group< ViewId > > pair : castPairs )
		{
			// try to remove a -> b and b -> a, just to make sure
			data.getStitchingResults().getPairwiseResults().remove( pair );
			data.getStitchingResults().getPairwiseResults().remove( new ValuePair<>( pair.getB(), pair.getA() ) );
		}

		// remove non-overlapping comparisons
		final List< Pair< Group< ViewId >, Group< ViewId > > > removedPairs = TransformationTools
				.filterNonOverlappingPairs( (List< Pair< Group< ViewId >, Group< ViewId > > >) pairs,
						data.getViewRegistrations(), data.getSequenceDescription() );
		removedPairs
				.forEach( p -> System.out.println( "Skipping non-overlapping pair: " + p.getA() + " -> " + p.getB() ) );

		for ( final Pair< Group< ViewId >, Group< ViewId > > pair : (List< Pair< Group< ViewId >, Group< ViewId > > >) pairs )
		{
			// all views in group pair
			final HashSet< ViewId > vids = new HashSet< ViewId >();
			vids.addAll( pair.getA().getViews() );
			vids.addAll( pair.getB().getViews() );

			// simple PairwiseSetup with just two groups (fully connected to
			// each other)
			final PairwiseSetup< ViewId > setup = new PairwiseSetup< ViewId >( new ArrayList<>( vids ),
					new HashSet<>( Arrays.asList( pair.getA(), pair.getB() ) ) )
			{

				@Override
				protected List< Pair< ViewId, ViewId > > definePairsAbstract()
				{
					// all possible links between groups
					final List< Pair< ViewId, ViewId > > res = new ArrayList<>();
					for ( final ViewId vidA : pair.getA() )
						for ( final ViewId vidB : pair.getB() )
							res.add( new ValuePair< ViewId, ViewId >( vidA, vidB ) );
					return res;
				}

				@Override
				public List< ViewId > getDefaultFixedViews()
				{
					// first group will remain fixed -> we get the transform to
					// align second group to this target
					return new ArrayList<>( pair.getA().getViews() );
				}
			};

			// prepare setup
			setup.definePairs();
			setup.detectSubsets();

			// get copies of view registrations (as they will be modified) and
			// interest points
			final Map< ViewId, ViewRegistration > registrationMap = new HashMap<>();
			final Map< ViewId, ViewInterestPointLists > ipMap = new HashMap<>();
			for ( ViewId vid : vids )
			{
				final ViewRegistration vrOld = data.getViewRegistrations().getViewRegistration( vid );
				final ViewInterestPointLists iplOld = data.getViewInterestPoints().getViewInterestPointLists( vid );
				registrationMap.put( vid, new ViewRegistration( vid.getTimePointId(), vid.getViewSetupId(),
						new ArrayList<>( vrOld.getTransformList() ) ) );
				ipMap.put( vid, iplOld );
			}

			// run the registration for this pair, skip saving results if it did not work
			if ( !new Interest_Point_Registration().processRegistration( setup, brp.pwr,
					InterestpointGroupingType.DO_NOT_GROUP, 0.0, pair.getA().getViews(), null, null, registrationMap,
					ipMap, brp.labelMap, false ) )
				continue;

			// get newest Transformation of groupB (the accumulative transform
			// determined by registration)
			final ViewTransform vtB = registrationMap.get( pair.getB().iterator().next() ).getTransformList().get( 0 );

			final AffineTransform3D result = new AffineTransform3D();
			result.set( vtB.asAffine3D().getRowPackedCopy() );
			IOFunctions.println( "resulting transformation: " + Util.printCoordinates( result.getRowPackedCopy() ) );

			// NB: in the global optimization, the final transform of a view
			// will be VR^-1 * T * VR (T is the optimization result)
			// the rationale behind this is that we can use "raw (pixel)
			// coordinate" transforms T (the typical case when stitching)
			//
			// since we get results T' in world coordinates here, we calculate
			// VR * T' * VR^-1 as the result here
			// after the optimization, we will get VR^-1 * VR * T' * VR^-1 * VR
			// = T' (i.e. the result will remain in world coordinates)
			final AffineTransform3D oldVT = data.getViewRegistrations()
					.getViewRegistration( pair.getB().iterator().next() ).getModel();
			result.concatenate( oldVT );
			result.preConcatenate( oldVT.copy().inverse() );

			// get Overlap Bounding Box, which we need for stitching results
			final List< List< ViewId > > groupListsForOverlap = new ArrayList<>();
			groupListsForOverlap.add( new ArrayList<>( pair.getA().getViews() ) );
			groupListsForOverlap.add( new ArrayList<>( pair.getB().getViews() ) );
			BoundingBoxMaximalGroupOverlap< ViewId > bbDet = new BoundingBoxMaximalGroupOverlap< ViewId >(
					groupListsForOverlap, data.getSequenceDescription(), data.getViewRegistrations() );
			BoundingBox bbOverlap = bbDet.estimate( "Max Overlap" );

			// TODO: meaningful quality criterion (e.g. inlier ratio ), not just
			// 1.0

			final double oldTransformHash = PairwiseStitchingResult.calculateHash(
					data.getViewRegistrations().getViewRegistration( pair.getA().getViews().iterator().next() ),
					data.getViewRegistrations().getViewRegistration( pair.getA().getViews().iterator().next() ) );
			data.getStitchingResults().getPairwiseResults().put( pair,
					new PairwiseStitchingResult<>( pair, bbOverlap, result, 1.0, oldTransformHash ) );
		}

		return true;
	}

}
