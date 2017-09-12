package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.datasetmanager.grid.RegularTranformHelpers;
import spim.fiji.datasetmanager.grid.RegularTranformHelpers.GridPreset;
import spim.fiji.datasetmanager.grid.RegularTranformHelpers.RegularTranslationParameters;
import spim.fiji.plugin.queryXML.LoadParseQueryXML;
import spim.fiji.spimdata.SpimData2;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class Regular_Grid implements PlugIn
{
	public static final String[] choice = new String[]{
			"In rows (+x, +y)",
			"In rows (-x, +y)",
			"In rows (+x, -y)",
			"In rows (-x, -y)",
			"In columns (+y, +x)",
			"In columns (+y, -x)",
			"In columns (-y, +x)",
			"In columns (-y, -x)",
			"Snake (+x, +y)",
			"Snake (+y, +x)",
			"Snake (-x, +y)",
			"Snake (+y, -x)",
			"Snake (+x, -y)",
			"Snake (-y, +x)",
			"Snake (-x, -y)",
			"Snake (-y, -x)" };

	@Override
	public void run(String arg)
	{
		final LoadParseQueryXML result = new LoadParseQueryXML();
		if ( !result.queryXML( "for pairwise shift calculation", true, true, true, true, false ) )
			return;

		final SpimData2 data = result.getData();
		final ArrayList< ViewId > views = SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final int tp = views.get( 0 ).getTimePointId();

		final ArrayList< ViewDescription > selectedViews = new ArrayList<>();
		for ( final ViewId v : views )
			if ( v.getTimePointId() == tp )
				selectedViews.add( data.getSequenceDescription().getViewDescription( v ) );

		final GenericDialog gd = new GenericDialog( "Move to Grid options" );

		gd.addChoice( "Grid_type", choice, choice[ 0 ] );
		gd.addMessage( "" );

		gd.addNumericField( "Tiles_in_x", 2, 0 );
		gd.addNumericField( "Tiles_in_y", 3, 0 );
		gd.addNumericField( "Tiles_in_z", 1, 0 );

		gd.addMessage( "" );

		gd.addNumericField( "Overlap_in_x [%]", 10, 0 );
		gd.addNumericField( "Overlap_in_y [%]", 10, 0 );
		gd.addNumericField( "Overlap_in_z [%]", 10, 0 );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		final int type = gd.getNextChoiceIndex();
		final int tilesX = (int)Math.round( gd.getNextNumber() );
		final int tilesY = (int)Math.round( gd.getNextNumber() );
		final int tilesZ = (int)Math.round( gd.getNextNumber() );
		final double overlapX = gd.getNextNumber();
		final double overlapY = gd.getNextNumber();
		final double overlapZ = gd.getNextNumber();

		final GridPreset gp = RegularTranformHelpers.presets.get( type );

		System.out.print( type );

		RegularTranslationParameters params = new RegularTranslationParameters();
		params.nDimensions = 3;
		params.alternating = gp.alternating.clone();
		params.dimensionOrder = RegularTranformHelpers.getDimensionOrder( gp.dimensionOrder );
		params.increasing = gp.increasing.clone();
		params.overlaps = new double[]{ overlapX/100.0, overlapY/100.0, overlapZ/100.0 };
		params.nSteps = new int[]{ tilesX, tilesY, tilesZ };
		params.keepRotation = true;

		final HashSet< Class< ? extends Entity > > splitFactor = new HashSet<>();
		splitFactor.add( Angle.class );
		
		for ( final Group< ViewDescription > angleGroup : Group.splitBy( selectedViews, splitFactor ) )
		{
			final ArrayList< ViewDescription > perAngle = new ArrayList<>();
			perAngle.addAll( angleGroup.getViews() );

			Collections.sort( perAngle );

			final HashSet< Class< ? extends Entity > > groupingFactor = new HashSet<>();
			groupingFactor.add( Channel.class );
			groupingFactor.add( Illumination.class );

			RegularTranformHelpers.applyToSpimData( data, Group.combineBy( perAngle, groupingFactor ), params, true );
		}

		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );
	}


	public static void main(String[] args)
	{
		new Regular_Grid().run( "" );
	}
}
