package gui.popup;

import java.awt.Component;
import java.awt.Choice;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import org.apache.xml.serialize.XHTMLSerializer;

import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataFilteringAndGrouping;
import algorithm.GroupedViewAggregator.ActionType;
import algorithm.globalopt.TransformationTools;
import algorithm.lucaskanade.LucasKanadeParameters;
import fiji.util.gui.GenericDialogPlus;
import gui.StitchingExplorerPanel;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.resave.ProgressWriterIJ;
import spim.fiji.plugin.util.GUIHelper;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointdetection.methods.downsampling.DownsampleTools;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class CalculatePCPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8664967345630864576L;

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public static final String[] ds = { "1", "2", "4", "8", "16", "32", "64"};
	public static final String[] methods = {"Phase Correlation", "Iterative Intensity Based (Lucas-Kanade)"};
	public static final long[] dsDefault = {4, 4, 2};

	public CalculatePCPopup()
	{
		super( "Calculate Pairwise Shift" );
		this.addActionListener( new MyActionListener() );
	}

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.stitchingResults = res;
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	public static long[] askForDownsampling(AbstractSpimData< ? > data, boolean is2d)
	{
		// get first non-missing viewDescription
		final Optional<? extends BasicViewDescription< ? > > firstPresent = 
				data.getSequenceDescription().getViewDescriptions().values().stream().filter( v -> v.isPresent() ).findFirst();

		final VoxelDimensions voxelDims;
		if (firstPresent.isPresent() && firstPresent.get().getViewSetup().hasVoxelSize())
			voxelDims = firstPresent.get().getViewSetup().getVoxelSize();
		else
			voxelDims = new FinalVoxelDimensions( "pixels", new double[] {1, 1, 1} );

		String[] dsChoicesXY = new String[ds.length + (is2d ? 0 : 2)];
		System.arraycopy( ds, 0, dsChoicesXY, 0, ds.length );
		if (!is2d)
		{
			dsChoicesXY[dsChoicesXY.length-2] = "Match Z Resolution (less downsampling)";
			dsChoicesXY[dsChoicesXY.length-1] = "Match Z Resolution (more downsampling)";
		}

//		DownsampleTools.downsampleFactor( downsampleXY, downsampleZ, v );

		GenericDialog gd = new GenericDialog( "Downsampling Options" );

		final long[] dsPreset;
		final String[] dsStrings;
		final boolean isHDF5 = MultiResolutionImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() );
		if (isHDF5)
		{
			dsStrings = DownsampleTools.availableDownsamplings( data, firstPresent.get() );
			gd.addMessage( "Precomputed Downsamplings (x, y, z):", GUIHelper.largefont, GUIHelper.good );
			for (String dsString : dsStrings)
				gd.addMessage( dsString, GUIHelper.smallStatusFont, GUIHelper.neutral );
			dsPreset = closestPresentDownsampling( dsStrings, dsDefault );
		}
		else
		{
			gd.addMessage( "No Precomputed Downsamplings", GUIHelper.largefont, GUIHelper.warning );
			gd.addMessage( "Consider re-saving as HDF5 for better performance.", GUIHelper.smallStatusFont, GUIHelper.neutral );
			dsPreset = dsDefault.clone();
			dsStrings = new String[]{"1, 1, 1"};
		}

		gd.addMessage( "Specify Downsampling", GUIHelper.largefont, GUIHelper.neutral );
		if (isHDF5)
		{
			gd.addMessage( "Choices that are pre-computed will be labeled in green", GUIHelper.smallStatusFont, GUIHelper.good );
			gd.addMessage( "Choices that require additional downsampling will be labeled in orange", GUIHelper.smallStatusFont, GUIHelper.warning );
		}

		gd.addChoice( "Downsample_in_X", dsChoicesXY, Long.toString( dsPreset[0] ));
		final Choice xChoice = (Choice) gd.getChoices().get( gd.getChoices().size()-1 );
		gd.addChoice( "Downsample_in_Y", dsChoicesXY, Long.toString( dsPreset[1] ) );
		final Choice yChoice = (Choice) gd.getChoices().get( gd.getChoices().size()-1 );
		final Choice zChoice;
		if ( !is2d )
		{
			gd.addChoice( "Downsample_in_Z", ds, Long.toString( dsPreset[2] ) );
			zChoice = (Choice) gd.getChoices().get( gd.getChoices().size()-1 );
		}
		else
			zChoice = null;

		if (isHDF5)
		{
			xChoice.addItemListener( e -> choiceCallback( xChoice, yChoice, zChoice, dsStrings ));
			yChoice.addItemListener( e -> choiceCallback( xChoice, yChoice, zChoice, dsStrings ));
			if (zChoice != null)
				zChoice.addItemListener( e -> choiceCallback( xChoice, yChoice, zChoice, dsStrings ));
			choiceCallback( xChoice, yChoice, zChoice, dsStrings );
		}


		gd.showDialog();
		
		if (gd.wasCanceled())
			return null;


		int dsXIdx = gd.getNextChoiceIndex();
		int dsYIdx = gd.getNextChoiceIndex();
		long dsZ = is2d ? 1 : Long.parseLong( gd.getNextChoice() );

		
		
		long dsX;
		long dsY;
		if (!is2d)
		{
			if (dsXIdx >= ds.length)
				dsXIdx = -1 * (dsXIdx - ds.length);
			if (dsYIdx >= ds.length)
				dsYIdx = -1 * (dsYIdx - ds.length);
			dsX = DownsampleTools.downsampleFactor( dsXIdx, (int) dsZ, voxelDims );
			dsY = DownsampleTools.downsampleFactor( dsYIdx, (int) dsZ, voxelDims );
		}
		else
		{
			dsX = Long.parseLong( ds[dsXIdx] );
			dsY = Long.parseLong( ds[dsYIdx] );
		}

		return new long[] {dsX, dsY, dsZ};
	}

	public static void choiceCallback(Choice x, Choice y, Choice z, String[] available)
	{
		boolean goodChoice = false;
		try
		{
			long selectedItemX = Long.parseLong( x.getSelectedItem() );
			long selectedItemY = Long.parseLong( y.getSelectedItem() );
			long selectedItemZ = 1;
			if (z != null)
				selectedItemZ = Long.parseLong( z.getSelectedItem() );

			for (String availableI : available)
			{
				long[] parseDownsampleChoice = DownsampleTools.parseDownsampleChoice( availableI );
				if (selectedItemX == parseDownsampleChoice[0] && selectedItemY == parseDownsampleChoice[1] && selectedItemZ == parseDownsampleChoice[2])
				{
					goodChoice = true;
					break;
				}
			}
		}
		catch (Exception e)
		{
		}

		x.setForeground( goodChoice ? GUIHelper.good : GUIHelper.warning);
		y.setForeground( goodChoice ? GUIHelper.good : GUIHelper.warning);
		if (z != null)
			z.setForeground( goodChoice ? GUIHelper.good : GUIHelper.warning);
	}

	public static boolean allSmaller(long[] a, long[] b)
	{
		for (int i = 0; i<a.length; i++)
			if (a[i] > b[i])
				return false;
		return true;
	}

	public static double distanceLog(long[] a, long[] b)
	{
		double logD = 0;
		for (int i = 0; i<a.length; i++)
			logD += Math.pow( Math.log( a[i] ) / Math.log( 2 ) - Math.log( b[i] ) / Math.log( 2 ), 2);
		return Math.sqrt( logD );
		
	}

	public static long[] closestPresentDownsampling(String[] dsStrings, long[] maximalDesiredDownsampling)
	{
		int closestIdx = 0;
		double closestDist = Double.MAX_VALUE;
		for (int i=0; i<dsStrings.length; i++)
		{
			long[] dsChoiceI = DownsampleTools.parseDownsampleChoice( dsStrings[i] );
			if (!allSmaller( dsChoiceI, maximalDesiredDownsampling ))
				continue;
			double d = distanceLog( dsChoiceI, maximalDesiredDownsampling );
			if (d < closestDist)
			{
				closestDist = d;
				closestIdx = i;
			}
		}
		return DownsampleTools.parseDownsampleChoice( dsStrings[closestIdx] );
	}


	public class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? > panelFG = (FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? >) panel;
					final SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( panel.getSpimData() );

					// use whatever is selected in panel as filters
					filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );

					// get the grouping from panel and compare Tiles
					panelFG.getTableModel().getGroupingFactors().forEach( g -> filteringAndGrouping.addGroupingFactor( g ));
					filteringAndGrouping.addComparisonAxis( Tile.class );

					// compare by Channel if channels were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Channel.class ))
						filteringAndGrouping.addComparisonAxis( Channel.class );

					// compare by Illumination if illums were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Illumination.class ))
						filteringAndGrouping.addComparisonAxis( Illumination.class );

					// ask user what to do with grouped views
					// use AVERAGE as pre-set choice for illuminations
					// (this will cause no overhead if only one illum is present)
					final HashMap< Class<? extends Entity>, ActionType > illumDefaultAggregation = new HashMap<>();
					illumDefaultAggregation.put( Illumination.class, ActionType.AVERAGE );

					filteringAndGrouping.askUserForGroupingAggregator(illumDefaultAggregation);
					if (filteringAndGrouping.getDialogWasCancelled())
						return;

					// 2d check:
					// since the ImgLoader will give us 3D views,
					// we get all the views that are larger than 1 in every dimension
					// (if they do not have sizes yet, we assume them to be 3D)
					// if no view satisfies this check, we treat dataset as 2d
					// (this only affects whether the downsample z choicebox will be displayed or not )
					List< BasicViewDescription< ? > > all3DVds = filteringAndGrouping.getFilteredViews().stream().filter( vd -> {
						if (!vd.getViewSetup().hasSize())
							return true;
						Dimensions dims = vd.getViewSetup().getSize();
						boolean all3D = true;
						for (int d = 0; d<dims.numDimensions(); d++)
							if (dims.dimension( d ) == 1)
								all3D = false;
						return all3D;
					}).collect( Collectors.toList() );

					boolean is2d = all3DVds.size() == 0;

					final long[] downSamplingFactors = askForDownsampling( panel.getSpimData(), is2d );
					if (downSamplingFactors == null)
						return;

					final GenericDialog gd = new GenericDialog( "Pairwise Alignment Method" );

					gd.addChoice( "alignment_method", methods, methods[0] );
					gd.addCheckbox( "clear_previous_results", true );

					gd.showDialog();

					if(gd.wasCanceled())
						return;

					final boolean resetResults = gd.getNextBoolean();
					final int methodIdx = gd.getNextChoiceIndex();
					
					

					

					final List< Pair< Group< BasicViewDescription< ? extends BasicViewSetup > >, Group< BasicViewDescription< ? extends BasicViewSetup > > > > pairs 
						= filteringAndGrouping.getComparisons();

					final ArrayList< PairwiseStitchingResult< ViewId > > results;

					if (methodIdx == 0) // do phase correlation
					{
						PairwiseStitchingParameters params = PairwiseStitchingParameters.askUserForParameters();
						if ( params == null )
							return;

						results = TransformationTools.computePairs(
								pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
								filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
								downSamplingFactors );
					}
					else if (methodIdx == 1) // do Lucas-Kanade
					{
						LucasKanadeParameters params = LucasKanadeParameters.askUserForParameters();
						if ( params == null )
							return;

						results = TransformationTools.computePairsLK(
								pairs, params, filteringAndGrouping.getSpimData().getViewRegistrations(), 
								filteringAndGrouping.getSpimData().getSequenceDescription(), filteringAndGrouping.getGroupedViewAggregator(),
								downSamplingFactors, new ProgressWriterIJ());
					}
					else
					{
						results = null; // should not happen, just to stop compiler from complaining
					}


					// user wants us to clear previous results
					// we will clear results for all selected pairs
					if (resetResults)
					{
						// this is just a cast of pairs to Group<ViewId>
						final List< ValuePair< Group< ViewId >, Group< ViewId > > > castPairs = pairs.stream().map( p -> {
							final Group< ViewId > vidGroupA = new Group<>( p.getA().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
							final Group< ViewId > vidGroupB = new Group<>( p.getB().getViews().stream().map( v -> (ViewId) v ).collect( Collectors.toSet() ) );
							return new ValuePair<>( vidGroupA, vidGroupB );
						}).collect( Collectors.toList() );

						for (ValuePair< Group< ViewId >, Group< ViewId > > pair : castPairs)
						{
							// try to remove a -> b and b -> a, just to make sure
							stitchingResults.getPairwiseResults().remove( pair );
							stitchingResults.getPairwiseResults().remove( new ValuePair<>( pair.getB(), pair.getA() ) );
						}
					}

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						if (psr == null)
							continue;

						stitchingResults.setPairwiseResultForPair( psr.pair(), psr );
					}

					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": DONE." );

					// ask user if they want to switch to preview mode
					if (panel instanceof StitchingExplorerPanel)
					{
						final int choice = JOptionPane.showConfirmDialog( (Component) panel, "Pairwise shift calculation done. Switch to preview mode?", "Preview Mode", JOptionPane.YES_NO_OPTION );
						if (choice == JOptionPane.YES_OPTION)
						{
							((StitchingExplorerPanel< ?, ? >) panel).setSavedFilteringAndGrouping( filteringAndGrouping );
							((StitchingExplorerPanel< ?, ? >) panel).togglePreviewMode();
						}
					}
				}
			} ).start();


		}
	}
}
