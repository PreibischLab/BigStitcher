package net.preibisch.stitcher.gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import bdv.BigDataViewer;
import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import javassist.bytecode.analysis.Executor;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBoxes;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.pointspreadfunctions.PointSpreadFunctions;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.algorithm.illuminationselection.BrightestViewSelection;
import net.preibisch.stitcher.algorithm.illuminationselection.IlluminationSelectionPreviewGUI;
import net.preibisch.stitcher.algorithm.illuminationselection.ViewSelection;

public class SelectIlluminationPopup extends JMenuItem implements ExplorerWindowSetable
{
	

	private FilteredAndGroupedExplorerPanel< ?, ? > panel;
	
	public SelectIlluminationPopup()
	{
		super( "Select Best Illuminations" );
		this.addActionListener( new MyActionListener() );
	}
	
	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = (FilteredAndGroupedExplorerPanel< ?, ? >)panel;
		return this;
	}

	public static ViewSelection< ViewId > getViewSelectionResult(GenericDialog gd, AbstractSequenceDescription< ?, ?, ? > sd)
	{
		String choice = gd.getNextChoice();
		if (choice.equals( "Pick brightest" ))
			return new BrightestViewSelection( sd );
		else
			return null;
		
	}
	
	public static void addViewSelectionQuery(GenericDialog gd)
	{
		final String[] choices = new String[] {"Pick brightest"};
		gd.addChoice( "Selection Method", choices, choices[0] );
	}
	
	private class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			
			new Thread(new Runnable()
			{
				
				@Override
				public void run()
				{

					if (!SpimData2.class.isInstance( panel.getSpimData() ) )
					{
						IOFunctions.println(new Date( System.currentTimeMillis() ) + "ERROR: expected SpimData2, but got " + panel.getSpimData().getClass().getSimpleName());
						return;
					}

					final BigDataViewer bdv = panel.bdvPopup().getBDV();
					final Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > selected = ((GroupedRowWindow)panel).selectedRowsGroups();

					SpimData2 filteredSpimData = processIlluminationSelection( 
							(SpimData) panel.getSpimData(),
							selected.stream().reduce( new ArrayList<>(), (x,y) -> {x.addAll( y ); return x;}),
							true,
							bdv != null,
							bdv );

					if (filteredSpimData != null)
					{
						panel.setSpimData( filteredSpimData );
						panel.updateContent();
					}

					// TODO: close and re-open BDV
				}

			}).start();
			
			
		}
		
	}
	
	public static SpimData2 processIlluminationSelection(
			SpimData data,
			Collection< ? extends BasicViewDescription< ? > > selected,
			boolean showOnlySelectedOption,
			boolean showPreviewOption,
			BigDataViewer bdvForPreview
			)
	{
		final GenericDialogPlus gdpParams = new GenericDialogPlus( "Illumination Selection" );
		if (showOnlySelectedOption)
			gdpParams.addCheckbox( "Process only selection", false );
		if (showPreviewOption)
			gdpParams.addCheckbox( "Show selection results before applying", true );
		addViewSelectionQuery( gdpParams );

		gdpParams.showDialog();

		if (gdpParams.wasCanceled())
			return null;

		// in the default case, we only process selection
		final boolean limitToSelection = showOnlySelectedOption ? gdpParams.getNextBoolean() : true;
		// in the default case, we do NOT preview
		final boolean previewResults = showPreviewOption ? gdpParams.getNextBoolean() : false;
		final ViewSelection< ViewId > viewSelection = getViewSelectionResult( gdpParams, data.getSequenceDescription() );

		final SpimDataFilteringAndGrouping< AbstractSpimData< ? > > grouping =
				new SpimDataFilteringAndGrouping< AbstractSpimData<?> >(data);
		grouping.addGroupingFactor( Illumination.class );


		if (limitToSelection)
		{
			grouping.addFilters( selected );
		}

		// get grouped views and filter out missing views
		final List< Group< BasicViewDescription< ? > > > groupedViews = grouping.getGroupedViews( true );
		groupedViews.forEach( g -> SpimData2.filterMissingViews( data, g.getViews() ) );

		// multithreaded best illuination determination
		List< Callable< ViewId > > tasks = new ArrayList<>();
		List< ViewId > bestViews = new ArrayList<>();
		ExecutorService service = Executors.newFixedThreadPool(Math.max( 2, Runtime.getRuntime().availableProcessors() ));

		for (final Group<? extends ViewId > group : groupedViews)
			tasks.add( new Callable< ViewId >()
			{
				@Override
				public ViewId call() throws Exception
				{
					return viewSelection.getBestView( group.getViews() );
				}
			} );
		List< Future< ViewId > > futures;
		try
		{
			futures = service.invokeAll( tasks );
			for (Future< ViewId > f : futures)
				bestViews.add( f.get() );
		}
		catch ( InterruptedException | ExecutionException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		service.shutdown();

		final List< List< BasicViewDescription< ? > > > groupedViewsList = groupedViews.stream().map( g -> {
			final ArrayList<BasicViewDescription< ? >> views = new ArrayList<>(g.getViews());
			Collections.sort( views );
			return views;
		} ).collect(Collectors.toList());

		if (previewResults)
		{
			List< ViewId > bestViewsFromGUI = new IlluminationSelectionPreviewGUI().previewWithGUI( groupedViewsList, bestViews, bdvForPreview);
			if (bestViewsFromGUI == null)
			{
				System.out.println( "Illumination selection aborted by user." );
				return null;
			}
			else
				bestViews = bestViewsFromGUI;
		}

		final Set<ViewId> missingViews = new HashSet<>();
		for (final Group< ? extends ViewId > group : groupedViews)
			for (final ViewId vid : group)
				if (!(bestViews.contains( vid )))
					missingViews.add( vid );
		missingViews.forEach( System.out::println );

		SpimData2 dataNew = getCopyWithMissingViews( data, missingViews );
		return dataNew;

	}

	public static SpimData2 getCopyWithMissingViews(SpimData data, Collection< ? extends ViewId > missingViews)
	{
		return getCopyWithMissingViews( data, missingViews, false );
	}

	public static SpimData2 getCopyWithMissingViews(SpimData data, Collection< ? extends ViewId > missingViews, boolean ignoreOldMissingViews)
	{
		final File basePath = data.getBasePath();
		final ViewRegistrations viewRegistrations = data.getViewRegistrations();
		
		final SequenceDescription sdOld = data.getSequenceDescription();
		
		final List<ViewId> missingViewsList = new ArrayList<>( missingViews );
		
		if (!ignoreOldMissingViews && sdOld.getMissingViews() != null )
			sdOld.getMissingViews().getMissingViews().forEach( mv -> missingViewsList.add( mv ) );
		
		final SequenceDescription sequenceDescription = 
				new SequenceDescription( 
						sdOld.getTimePoints(),
						sdOld.getViewSetupsOrdered(),
						sdOld.getImgLoader(),
						new MissingViews( missingViewsList ) );
		
		final ViewInterestPoints viewsInterestPoints = SpimData2.class.isInstance( data ) ? ((SpimData2)data).getViewInterestPoints() : new ViewInterestPoints();
		if (!(SpimData2.class.isInstance( data )))
			viewsInterestPoints.createViewInterestPoints( data.getSequenceDescription().getViewDescriptions() );
		
		final BoundingBoxes boundingBoxes = SpimData2.class.isInstance( data ) ? ((SpimData2)data).getBoundingBoxes() : new BoundingBoxes();
		final PointSpreadFunctions psfs = SpimData2.class.isInstance( data ) ? ((SpimData2)data).getPointSpreadFunctions() : new PointSpreadFunctions();
		final StitchingResults stitchingResults = SpimData2.class.isInstance( data ) ? ((SpimData2)data).getStitchingResults() : new StitchingResults();
				
		final SpimData2 dataNew = new SpimData2( basePath, sequenceDescription, viewRegistrations, viewsInterestPoints, boundingBoxes, psfs, stitchingResults );
		return dataNew;
	}

	static String getViewDescriptionStringWithoutIllum(BasicViewDescription< ? > vd)
	{
		return "TP: " + vd.getTimePointId() + 
				", Tile " + vd.getViewSetup().getAttribute( Tile.class ).getId() +
				", Angle " + vd.getViewSetup().getAttribute( Angle.class ).getId() +
				", Channel " + vd.getViewSetup().getAttribute( Channel.class ).getId();
	}
}
