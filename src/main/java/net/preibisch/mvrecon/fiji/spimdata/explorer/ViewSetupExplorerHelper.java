package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import bdv.BigDataViewer;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.DisplayMode;
import bdv.viewer.VisibilityAndGrouping;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.ARGBType;
import net.preibisch.mvrecon.fiji.spimdata.explorer.bdv.ScrollableBrightnessDialog;
import net.preibisch.mvrecon.fiji.spimdata.explorer.util.ColorStream;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class ViewSetupExplorerHelper {
	
	private static long colorOffset = 0;
	
	public static void updateBDV(final BigDataViewer bdv, final boolean colorMode, final AbstractSpimData< ? > data,
			BasicViewDescription< ? extends BasicViewSetup > firstVD,
			final Collection< List< BasicViewDescription< ? extends BasicViewSetup >> > selectedRows)
	{
		// we always set the fused mode
		setFusedModeSimple( bdv, data );

		if ( selectedRows == null || selectedRows.size() == 0 )
			return;

		if ( firstVD == null )
			firstVD = selectedRows.iterator().next().iterator().next();

		// always use the first timepoint
		final TimePoint firstTP = firstVD.getTimePoint();
		bdv.getViewer().setTimepoint( getBDVTimePointIndex( firstTP, data ) );

		final boolean[] active = new boolean[ data.getSequenceDescription().getViewSetupsOrdered().size() ];

		// set selected views active
		// also check whether at least one "group" of views is a real group (not just a single, wrapped, view) 
		boolean anyGrouped = false;		
		for ( final List<BasicViewDescription< ? >> vds : selectedRows )
		{
			if (vds.size() > 1)
				anyGrouped = true;

			for (BasicViewDescription< ? > vd : vds)
				if ( vd.getTimePointId() == firstTP.getId() )
					active[ getBDVSourceIndex( vd.getViewSetup(), data ) ] = true;
		}

		if ( selectedRows.size() > 1 && colorMode )
		{
			// we have grouped views
			// a.t.m. we can only group by tiles, therefore we just color by Tile
			if (anyGrouped)
			{
				Set< Class< ? extends Entity > > factors = new HashSet<>();
				factors.add( Tile.class );
				colorByFactors( bdv, data, factors );
			}
			else
				colorSources( bdv.getSetupAssignments().getConverterSetups(), colorOffset );
		}
		else
			whiteSources( bdv.getSetupAssignments().getConverterSetups() );

		setVisibleSources( bdv.getViewer().getVisibilityAndGrouping(), active );

		ScrollableBrightnessDialog.updateBrightnessPanels( bdv );
	}
	
	/**
	 * color the views displayed in bdv according to one or more Entity classes
	 * @param bdv - the BigDataViewer instance
	 * @param data - the SpimData
	 * @param groupingFactors - the Entity classes to group by (each distinct combination of instances will receive its own color)
	 */
	public static void colorByFactors(BigDataViewer bdv, AbstractSpimData< ? > data, Set<Class<? extends Entity>> groupingFactors)
	{
		List<BasicViewDescription< ? > > vds = new ArrayList<>();
		Map<BasicViewDescription< ? >, ConverterSetup> vdToCs = new HashMap<>();
		
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			Integer timepointId = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( bdv.getViewer().getState().getCurrentTimepoint()).getId();
			BasicViewDescription< ? > vd = data.getSequenceDescription().getViewDescriptions().get( new ViewId( timepointId, cs.getSetupId() ) );
			vds.add( vd );
			vdToCs.put( vd, cs );
		}
		
		List< Group< BasicViewDescription< ? > > > vdGroups = Group.combineBy( vds, groupingFactors );
		
		// nothing to group
		if (vdGroups.size() < 1)
			return;
		
		// one group -> white
		if (vdGroups.size() == 1)
		{
			FilteredAndGroupedExplorerPanel.whiteSources(bdv.getSetupAssignments().getConverterSetups());
			return;
		}

		List<ArrayList<ConverterSetup>> groups =  new ArrayList<>();

		for (Group< BasicViewDescription< ? > > lVd : vdGroups)
		{
			ArrayList< ConverterSetup > lCs = new ArrayList<>();
			for (BasicViewDescription< ? > vd : lVd)
				lCs.add( vdToCs.get( vd ) );
			groups.add( lCs );
		}

		Iterator< ARGBType > colorIt = ColorStream.iterator();
		for (int i = 0; i<colorOffset; ++i)
			colorIt.next();

		for (ArrayList< ConverterSetup > csg : groups)
		{
			ARGBType color = colorIt.next();
			for (ConverterSetup cs : csg)
				cs.setColor( color );
		}
	}
	
	public static void setFusedModeSimple( final BigDataViewer bdv, final AbstractSpimData< ? > data )
	{
		if ( bdv == null )
			return;

		if ( bdv.getViewer().getVisibilityAndGrouping().getDisplayMode() != DisplayMode.FUSED )
		{
			final boolean[] active = new boolean[ data.getSequenceDescription().getViewSetupsOrdered().size() ];
			active[ 0 ] = true;
			setVisibleSources( bdv.getViewer().getVisibilityAndGrouping(), active );
			bdv.getViewer().getVisibilityAndGrouping().setDisplayMode( DisplayMode.FUSED );
		}
	}
	
	public static void colorSources( final List< ConverterSetup > cs, final long j )
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ColorStream.get( i + j ) ) );
	}

	public static void whiteSources( final List< ConverterSetup > cs )
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ARGBType.rgba( 255, 255, 255, 0 ) ) );
	}

	public static void setVisibleSources( final VisibilityAndGrouping vag, final boolean[] active )
	{
		for ( int i = 0; i < active.length; ++i )
			vag.setSourceActive( i, active[ i ] );
	}

	public static int getBDVTimePointIndex( final TimePoint t, final AbstractSpimData< ? > data )
	{
		final List< TimePoint > list = data.getSequenceDescription().getTimePoints().getTimePointsOrdered();

		for ( int i = 0; i < list.size(); ++i )
			if ( list.get( i ).getId() == t.getId() )
				return i;

		return 0;
	}

	public static int getBDVSourceIndex( final BasicViewSetup vs, final AbstractSpimData< ? > data )
	{
		final List< ? extends BasicViewSetup > list = data.getSequenceDescription().getViewSetupsOrdered();
		
		for ( int i = 0; i < list.size(); ++i )
			if ( list.get( i ).getId() == vs.getId() )
				return i;

		return 0;
	}

}
