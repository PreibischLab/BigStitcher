package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.TransformationTools;
import algorithm.lucaskanade.LucasKanadeParameters;
import gui.StitchingUIHelper;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
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
		final List< Pair< Group< BasicViewDescription< ? extends BasicViewSetup > >, Group< BasicViewDescription< ? extends BasicViewSetup > > > > pairs = filteringAndGrouping.getComparisons();

		// calculate
		final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
				pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
				filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
				dsFactors );

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
			long[] ds)
	{
		// TODO: implement
		return false;
	}

	public static boolean processInterestPoint(
			SpimData2 data,
			SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping,
			PairwiseStitchingParameters params)
	{
		// TODO: implement
		return false;
	}

}
