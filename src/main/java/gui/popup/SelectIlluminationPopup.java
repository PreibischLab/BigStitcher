package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.SpimDataFilteringAndGrouping;
import algorithm.illuminationselection.BasicViewSelection;
import algorithm.illuminationselection.BrightestViewSelection;
import algorithm.illuminationselection.ViewSelection;
import fiji.util.gui.GenericDialogPlus;
import gui.popup.TestPopup.MyActionListener;
import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescriptionUtils;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class SelectIlluminationPopup extends JMenuItem implements ExplorerWindowSetable
{
	
	static Map<String, Class<? extends BasicViewSelection>> methods = new HashMap<>();
	static{
		methods.put( "Pick brightest", BrightestViewSelection.class );
	}

	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	public SelectIlluminationPopup()
	{
		super( "Select Best Illuminations" );
		this.addActionListener( new MyActionListener() );
	}
	
	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
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
			final GenericDialogPlus gdpParams = new GenericDialogPlus( "Illumination Selection" );
			gdpParams.addCheckbox( "Process only selection", false );
			gdpParams.addCheckbox( "Show selection results before applying", true );
			addViewSelectionQuery( gdpParams );
			
			gdpParams.showDialog();
			
			if (gdpParams.wasCanceled())
				return;
			
			final boolean limitToSelection = gdpParams.getNextBoolean();
			final boolean previewResults = gdpParams.getNextBoolean();
			final ViewSelection< ViewId > viewSelection = getViewSelectionResult( gdpParams, panel.getSpimData().getSequenceDescription() );			
			
			
			final SpimDataFilteringAndGrouping< AbstractSpimData< ? > > grouping =
					new SpimDataFilteringAndGrouping< AbstractSpimData<?> >(panel.getSpimData());
			grouping.addGroupingFactor( Illumination.class );
			
			/*
			grouping.addApplicationAxis( Angle.class );
			grouping.addApplicationAxis( TimePoint.class );
			grouping.addApplicationAxis( Tile.class );
			grouping.addApplicationAxis( Channel.class );
			*/
			
			if (limitToSelection)
			{
				final Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > selected = ((GroupedRowWindow)panel).selectedRowsGroups();
				grouping.addFilters( selected.stream().reduce( new ArrayList<>(), (x, y) -> {x.addAll(y); return x;} ) );
				/*
				grouping.addFilter( TimePoint.class, SpimDataFilteringAndGrouping.getInstancesInAllGroups( selected, TimePoint.class ));
				grouping.addFilter( Tile.class, SpimDataFilteringAndGrouping.getInstancesInAllGroups( selected, Tile.class ));
				grouping.addFilter( Channel.class, SpimDataFilteringAndGrouping.getInstancesInAllGroups( selected, Channel.class ));
				*/
			}
			
			final List< List< BasicViewDescription< ? > > > groupedViews = grouping.getGroupedViews( true );
			
			final List< ViewId > bestViews = new ArrayList<>();
			for (final List< ? extends ViewId > group : groupedViews)
				bestViews.add( viewSelection.getBestView( group ) );
				
			
			if (previewResults)
			{
				GenericDialogPlus gdp = new GenericDialogPlus("Preview Selected Illuminations");
			
				for (int i = 0; i < groupedViews.size(); i++)
				{
					List< BasicViewDescription< ? > > group = groupedViews.get( i );
					ViewId bestView = bestViews.get( i );
					
					String desc = getViewDescriptionStringWithoutIllum( (BasicViewDescription< ? > ) bestView);
					String[] choices = group.stream().map( v -> Integer.toString( v.getViewSetup().getAttribute( Illumination.class ).getId() ))
						.collect( Collectors.toList() ).toArray( new String[group.size()] );
					String bestIllum = Integer.toString( ( (BasicViewDescription< ? > ) bestView).getViewSetup().getAttribute( Illumination.class ).getId());
					
					gdp.addChoice( desc, choices, bestIllum );					
					
				}
				
				gdp.showDialog();
				
				if (gdp.wasCanceled())
					return;
				
				for (int i = 0; i < groupedViews.size(); i++)
				{
					int indexChoice = gdp.getNextChoiceIndex();
					bestViews.set( i, groupedViews.get( i ).get( indexChoice ) );
				}
				
			}
			
			//bestViews.forEach( System.out::println );
			
			final Set<ViewId> missingViews = new HashSet<>();
			
			for (final List< ? extends ViewId > group : groupedViews)
				for (final ViewId vid : group)
					if (!(bestViews.contains( vid )))
						missingViews.add( vid );
			
			missingViews.forEach( System.out::println );
			
			// TODO: are there realistic cases where we will not have a SpimData?
			if (SpimData.class.isInstance( panel.getSpimData() ))
			{
				SpimData data = (SpimData) panel.getSpimData();				
				SpimData2 dataNew = getCopyWithMissingViews( data, missingViews );				
				
				((FilteredAndGroupedExplorerPanel< ?, ? >)panel).setSpimData( dataNew );
				panel.updateContent();
				panel.saveXML();
			}
			else
			{
				System.err.println( "ERROR: Cannot replace data because it is not SpimData." );
			}
			
		}
		
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
		
		if (!ignoreOldMissingViews)
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
		final StitchingResults stitchingResults = SpimData2.class.isInstance( data ) ? ((SpimData2)data).getStitchingResults() : new StitchingResults();
				
		final SpimData2 dataNew = new SpimData2( basePath, sequenceDescription, viewRegistrations, viewsInterestPoints, boundingBoxes, stitchingResults );
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
