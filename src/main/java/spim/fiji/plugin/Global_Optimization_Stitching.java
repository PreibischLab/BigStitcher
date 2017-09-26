package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

import algorithm.SpimDataFilteringAndGrouping;
import algorithm.globalopt.GlobalOptimizationParameters;
import gui.popup.OptimizeGloballyPopupExpertBatch;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class Global_Optimization_Stitching implements PlugIn
{

	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "for global optimization", true, true, true, true, true ) )
			return;

		final SpimData2 data = result.getData();
		ArrayList< ViewId > selectedViews = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final SpimDataFilteringAndGrouping< SpimData2 > grouping = new SpimDataFilteringAndGrouping<>( data );
		grouping.addFilters( selectedViews.stream().map( vid -> data.getSequenceDescription().getViewDescription( vid ) ).collect( Collectors.toList() ) );

		// Defaults for grouping
		// the default grouping by channels and illuminations
		final HashSet< Class< ? extends Entity > > defaultGroupingFactors = new HashSet<>();
		defaultGroupingFactors.add( Illumination.class );
		defaultGroupingFactors.add( Channel.class );
		// the default comparision by tiles
		final HashSet< Class< ? extends Entity > > defaultComparisonFactors = new HashSet<>();
		defaultComparisonFactors.add( Tile.class );

		grouping.askUserForGrouping( 
				selectedViews.stream().map( vid -> data.getSequenceDescription().getViewDescription( vid ) ).collect( Collectors.toList() ),
				defaultGroupingFactors,
				defaultComparisonFactors );

		GlobalOptimizationParameters params = GlobalOptimizationParameters.askUserForParameters();

		if (!OptimizeGloballyPopupExpertBatch.processGlobalOptimization( data, grouping, params, false ))
			return;

		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );

	}

	public static void main(String[] args)
	{
		new Global_Optimization_Stitching().run( "" );
	}

}
