package net.preibisch.stitcher.gui.bdv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import bdv.BigDataViewer;
import bdv.tools.brightness.ConverterSetup;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.type.numeric.ARGBType;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxMaximalGroupOverlap;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.stitcher.gui.StitchingExplorerPanel;
import net.preibisch.stitcher.gui.popup.BDVPopupStitching;

public class BDVVisibilityHandlerNeighborhood implements BDVVisibilityHandler
{
	private ExplorerWindow< ? extends AbstractSpimData< ? >, ? > panel;
	private long colorOffset;

	public BDVVisibilityHandlerNeighborhood(ExplorerWindow< ? extends AbstractSpimData< ? >, ? > panel, long colorOffset)
	{
		this.panel = panel;
		this.colorOffset = colorOffset;
	}

	@Override
	public void updateBDV()
	{
		// panel not set, do nothing
		if (panel == null)
			return;

		// BDV is not open, nothing to do
		if (panel.bdvPopup() == null || !panel.bdvPopup().bdvRunning())
			return;

		// get all selected views
		final Set< ViewId > selectedViewIds;
		if (GroupedRowWindow.class.isInstance( panel ))
		{
			selectedViewIds = ((GroupedRowWindow)panel).selectedRowsViewIdGroups().stream().collect(
					HashSet::new,
					HashSet::addAll,
					HashSet::addAll );
		}
		else
			selectedViewIds = new HashSet<>( panel.selectedRowsViewId() );

		final BigDataViewer bdv = panel.bdvPopup().getBDV();

		// first, re-color sources
		if (!panel.colorMode())
			BDVPopupStitching.colorByChannels( bdv, panel.getSpimData(), colorOffset );
		else
			StitchingExplorerPanel.colorSources( bdv.getSetupAssignments().getConverterSetups(), colorOffset );
		
		// get map vsId -> convertersetup
		final Map<Integer, ConverterSetup> setupToConverterSetup = new HashMap<>();
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
			setupToConverterSetup.put( cs.getSetupId(), cs );

		// current TP + idx
		final int currentTimepoint = bdv.getViewer().getState().getCurrentTimepoint();
		final int currentTPId = panel.getSpimData().getSequenceDescription().getTimePoints().getTimePointsOrdered().get( currentTimepoint ).getId();

		// all vids of current timepoint
		for (final ViewId vid : panel.getSpimData().getSequenceDescription().getViewDescriptions().keySet()
				.stream().filter( vi -> vi.getTimePointId() == currentTPId ).collect( Collectors.toList() ))
		{
			// we have this view selected -> no need to re-color
			if (selectedViewIds.contains( vid ))
				continue;

			// search for (approximate) overlap between all selected views & this view
			final ArrayList< Iterable< ViewId > > comparison = new ArrayList<>();
			comparison.add( selectedViewIds );
			comparison.add( new Group< ViewId >( vid ) );
			BoundingBox bbox = new BoundingBoxMaximalGroupOverlap< ViewId >(comparison, panel.getSpimData()).estimate( "" );

			// overlap found
			if (bbox != null)
			{
				ConverterSetup cs = setupToConverterSetup.get( vid.getViewSetupId() );
				if (cs != null)
				{
					// set setup to gray
					cs.setColor( new ARGBType( ARGBType.rgba( 100, 100, 100, 255 ) ) );
					bdv.getViewer().getVisibilityAndGrouping().setSourceActive(
							FilteredAndGroupedExplorerPanel.getBDVSourceIndex(
									panel.getSpimData().getSequenceDescription().getViewDescriptions().get( vid ).getViewSetup(),
									panel.getSpimData() ), true );
				}
			}
		}
	}

}
