/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2017 Big Stitcher developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.stitcher.gui.popup.icp;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import ij.IJ;
import ij.gui.GenericDialog;
import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel3D;
import mpicbg.models.InterpolatedAffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Detection;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Registration;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.TransformationModelGUI;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters.AdvancedRegistrationParameters;
import net.preibisch.mvrecon.fiji.plugin.util.MouseOverPopUpStateChanger;
import net.preibisch.mvrecon.fiji.plugin.util.MouseOverPopUpStateChanger.StateChanger;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.Separator;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoG;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoGParameters;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.global.GlobalOpt;
import net.preibisch.mvrecon.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.PointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.global.pointmatchcreating.strong.InterestPointMatchCreator;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.MatcherPairwiseTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.PairwiseResult;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.AllToAll;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.Subset;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.GroupedInterestPoint;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.InterestPointGrouping;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.InterestPointGroupingMinDistance;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.overlap.SimpleBoundingBoxOverlap;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.icp.IterativeClosestPointPairwise;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.icp.IterativeClosestPointParameters;
import net.preibisch.stitcher.algorithm.SpimDataFilteringAndGrouping;
import net.preibisch.stitcher.gui.StitchingUIHelper;

public class RefineWithICPPopup extends JMenu implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 1L;

	public static enum ICPType{ TileRefine, ChromaticAbberation, Expert }

	public static String[] downsampling = new String[]{ "Downsampling 2/2/1", "Downsampling 4/4/2", "Downsampling 8/8/4", "Downsampling 16/16/8" };
	public static int defaultDownsampling = 1;

	public static String[] thresold = new String[]{ "Low Threshold (many points)", "Average Threshold", "High Threshold (few points)" };
	public static int defaultThreshold = 1;

	public static String[] distance = new String[]{ "Fine Adjustment (<1px)", "Normal Adjustment (<5px)", "Gross Adjustment (<20px, careful)" };
	public static int defaultDistance = 1;

	public static int defaultLabelDialog = 0;
	public static double defaultICPError = 5;
	public static int defaultModel = 2;
	public static boolean defaultRegularize = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public RefineWithICPPopup( String description )
	{
		super( description );

		final JMenuItem simpleICPtiles = new JMenuItem( "Simple (tile registration)" );
		final JMenuItem simpleICPchannels = new JMenuItem( "Simple (chromatic abberation)" );
		final JMenuItem advancedICP = new JMenuItem( "Expert ..." );

		simpleICPtiles.addActionListener( new ICPListener( ICPType.TileRefine ) );
		simpleICPchannels.addActionListener( new ICPListener( ICPType.ChromaticAbberation ) );
		advancedICP.addActionListener( new ICPListener( ICPType.Expert ) );

		this.add( simpleICPtiles );
		this.add( simpleICPchannels );
		this.add( advancedICP );

		this.add( new Separator() );

		final JMenuItem[] dsItems = new JMenuItem[ downsampling.length ];
		final StateChanger dsStateChanger = new StateChanger() { public void setSelectedState( int state ) { defaultDownsampling = state; } };

		for ( int i = 0; i < dsItems.length; ++i )
		{
			final JMenuItem item = new JMenuItem( downsampling[ i ] );

			if ( i == defaultDownsampling )
				item.setForeground( Color.RED );
			else
				item.setForeground( Color.GRAY );

			dsItems[ i ] = item;
		}

		for ( int i = 0; i < dsItems.length; ++i )
		{
			final JMenuItem item = dsItems[ i ];
			final MouseOverPopUpStateChanger cds = new MouseOverPopUpStateChanger( dsItems, i, dsStateChanger );

			item.addActionListener( cds );
			item.addMouseListener( cds );
			this.add( item );
		}

		this.add( new Separator() );

		final JMenuItem[] thrItems = new JMenuItem[ thresold.length ];
		final StateChanger thrStateChanger = new StateChanger() { public void setSelectedState( int state ) { defaultThreshold = state; } };

		for ( int i = 0; i < thrItems.length; ++i )
		{
			final JMenuItem item = new JMenuItem( thresold[ i ] );

			if ( i == defaultDownsampling )
				item.setForeground( Color.RED );
			else
				item.setForeground( Color.GRAY );

			thrItems[ i ] = item;
		}

		for ( int i = 0; i < thrItems.length; ++i )
		{
			final JMenuItem item = thrItems[ i ];
			final MouseOverPopUpStateChanger cds = new MouseOverPopUpStateChanger( thrItems, i, thrStateChanger );

			item.addActionListener( cds );
			item.addMouseListener( cds );
			this.add( item );
		}

		this.add( new Separator() );

		final JMenuItem[] distItems = new JMenuItem[ distance.length ];
		final StateChanger distStateChanger = new StateChanger()
		{ 
			public void setSelectedState( int state )
			{
				defaultDistance = state;

				if ( defaultDistance == 0 )
					defaultICPError = 1.0;
				else if ( defaultDistance == 1 )
					defaultICPError = 5.0;
				else
					defaultICPError = 20;
			}
		};

		for ( int i = 0; i < distItems.length; ++i )
		{
			final JMenuItem item = new JMenuItem( distance[ i ] );

			if ( i == defaultDownsampling )
				item.setForeground( Color.RED );
			else
				item.setForeground( Color.GRAY );

			distItems[ i ] = item;
		}

		for ( int i = 0; i < distItems.length; ++i )
		{
			final JMenuItem item = distItems[ i ];
			final MouseOverPopUpStateChanger cds = new MouseOverPopUpStateChanger( distItems, i, distStateChanger );

			item.addActionListener( cds );
			item.addMouseListener( cds );
			this.add( item );
		}

	}

	@Override
	public JComponent setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;

		return this;
	}

	public class ICPListener implements ActionListener
	{
		final ICPType icpType;

		public ICPListener( final ICPType icpType )
		{
			this.icpType = icpType;
		}

		@Override
		public void actionPerformed( ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			if (!GroupedRowWindow.class.isInstance( panel ))
			{
				IOFunctions.println( "Only supported for GroupedRowWindow panels: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( () ->
			{
				// get selected groups, filter missing views, get all present and selected vids
				final SpimData2 data = (SpimData2) panel.getSpimData();
				@SuppressWarnings("unchecked")
				FilteredAndGroupedExplorerPanel< SpimData2, ? > panelFG = (FilteredAndGroupedExplorerPanel< SpimData2, ? >) panel;
				SpimDataFilteringAndGrouping< SpimData2 > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< SpimData2 >( (SpimData2) panel.getSpimData() );

				// use whatever is selected in panel as filters
				filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );
	
				if ( StitchingUIHelper.allViews2D( filteringAndGrouping.getFilteredViews() ) )
				{
					IOFunctions.println( "ICP refinement is currenty not supported for 2D: " + this.getClass().getSimpleName() );
					return;
				}

				// filter not present ViewIds
				final ArrayList< ViewId > viewIds = new ArrayList<>();
				viewIds.addAll( filteringAndGrouping.getFilteredViews() );

				final List< ViewId > removed = SpimData2.filterMissingViews( data, viewIds );
				if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

				if ( viewIds.size() <= 1 )
				{
					IOFunctions.println( "Only " + viewIds.size() + " views selected, need at least two for this to make sense." );
					return;
				}

				//
				// get all parameters
				//
				final boolean groupTiles, groupIllums, groupChannels;
				final String label, transformationDescription;
				final double maxError;
				final AbstractModel< ? > transformationModel;

				if ( icpType == ICPType.Expert )
				{
					final GenericDialog gd = new GenericDialog( "Expert Refine by ICP" );

					final ArrayList< String > labels = getAllLabels( viewIds, data );
	
					if ( labels.size() == 0 )
					{
						IOFunctions.println( "No interest point defined, please detect interest point and re-run" );
						new Interest_Point_Detection().detectInterestPoints( data, viewIds );
						return;
					}

					final String[] labelChoice = new String[ labels.size() ];

					for ( int i = 0; i < labels.size(); ++i )
						labelChoice[ i ] = labels.get( i );

					if ( defaultLabelDialog >= labelChoice.length )
						defaultLabelDialog = 0;

					gd.addChoice( "Interest_Points", labelChoice, labelChoice[ defaultLabelDialog ] );
					gd.addNumericField( "ICP_maximum_error", defaultICPError, 2 );
					gd.addChoice( "Transformation model", TransformationModelGUI.modelChoice, TransformationModelGUI.modelChoice[ defaultModel ] );
					gd.addCheckbox( "Regularize_model", defaultRegularize );

					gd.addCheckbox( "Group_tiles", filteringAndGrouping.getGroupingFactors().contains( Tile.class ) );
					gd.addCheckbox( "Group_channels", filteringAndGrouping.getGroupingFactors().contains( Channel.class ) );
					gd.addCheckbox( "Group_illuminations", filteringAndGrouping.getGroupingFactors().contains( Illumination.class ) );

					gd.showDialog();
					if ( gd.wasCanceled() )
						return;

					label = labels.get( defaultLabelDialog = gd.getNextChoiceIndex() );
					maxError = gd.getNextNumber();
					TransformationModelGUI model = new TransformationModelGUI( defaultModel = gd.getNextChoiceIndex() );

					if ( defaultRegularize = gd.getNextBoolean() )
						if ( !model.queryRegularizedModel() )
							return;

					transformationModel = model.getModel();

					groupTiles = gd.getNextBoolean();
					groupChannels = gd.getNextBoolean();
					groupIllums = gd.getNextBoolean();

					transformationDescription = "Expert ICP Refinement";
				}
				else
				{
					if ( icpType == ICPType.TileRefine )
					{
						groupTiles = false;
						groupChannels = true;
						groupIllums = true;
						transformationDescription = "Tile ICP Refinement";
					}
					else // chromatic aberration
					{
						groupTiles = true;
						groupChannels = false;
						groupIllums = true;
						transformationDescription = "Chromatic Aberration Correction (ICP)";
					}

					label = "forICP_" + defaultDownsampling + "_" + defaultThreshold;

					// DoG
					if ( !presentForAll( label, viewIds, data ) )
					{
						// each channel get the same min/max intensity for the interestpoints
						final HashSet< Class<? extends Entity> > factors = new HashSet<>();
						factors.add( Channel.class );

						for ( final Group< ViewDescription > group : Group.splitBy( SpimData2.getAllViewDescriptionsSorted( data, viewIds ), factors ) )
						{
							IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Processing channel: " + group.getViews().iterator().next().getViewSetup().getChannel().getName() );

							final DoGParameters dog = new DoGParameters();
		
							dog.imgloader = data.getSequenceDescription().getImgLoader();
							dog.toProcess = new ArrayList< ViewDescription >();
							dog.toProcess.addAll( group.getViews() );

							switch ( defaultDownsampling )
							{
								case 0:
									dog.downsampleXY = 2;
									dog.downsampleZ = 1;
									break;
								case 1:
									dog.downsampleXY = 4;
									dog.downsampleZ = 2;
									break;
								case 2:
									dog.downsampleXY = 8;
									dog.downsampleZ = 4;
									break;
								case 3:
									dog.downsampleXY = 16;
									dog.downsampleZ = 8;
									break;
								default:
									dog.downsampleXY = 4;
									dog.downsampleZ = 2;
									break;
							}

							dog.sigma = 1.4;

							if ( defaultThreshold == 0 )
								dog.threshold = 0.00075;
							else if ( defaultThreshold == 1 )
								dog.threshold = 0.005;
							else //if ( defaultThreshold == 2 )
								dog.threshold = 0.01;

							dog.limitDetections = true;
							dog.maxDetections = 10000;
							dog.maxDetectionsTypeIndex = 0; // brightest

							dog.showProgress( 0, 1 );

							if ( group.getViews().size() > 1 )
							{
								final double[] minmax = minmax( data, dog.toProcess );
								dog.minIntensity = minmax[ 0 ];
								dog.maxIntensity = minmax[ 1 ];
							}

							final HashMap< ViewId, List< InterestPoint > > points = DoG.findInterestPoints( dog );
		
							InterestPointTools.addInterestPoints( data, label, points, "DoG, sigma=1.4, downsampleXY=" + dog.downsampleXY + ", downsampleZ=" + dog.downsampleZ );
						}
					}
					else
					{
						IOFunctions.println( "Interestpoint '" + label + "' already defined for all views, using those." );
					}

					transformationModel = new InterpolatedAffineModel3D< AffineModel3D, RigidModel3D >(
							new AffineModel3D(),
							new RigidModel3D(),
							0.1f );

					if ( defaultDistance == 0 )
						maxError = 1.0;
					else if ( defaultDistance == 1 )
						maxError = 5.0;
					else
						maxError = 20;
				}

				//
				// run the alignment
				//
				final IterativeClosestPointParameters icpp =
						new IterativeClosestPointParameters(
								transformationModel,
								maxError,
								100 );

				final Map< ViewId, String > labelMap = new HashMap<>();

				for ( final ViewId viewId : viewIds )
					labelMap.put( viewId, label );

				// load & transform all interest points
				final Map< ViewId, List< InterestPoint > > interestpoints =
						TransformationTools.getAllTransformedInterestPoints(
							viewIds,
							data.getViewRegistrations().getViewRegistrations(),
							data.getViewInterestPoints().getViewInterestPoints(),
							labelMap );

				// identify groups/subsets
				final Set< Group< ViewId > > groups = AdvancedRegistrationParameters.getGroups( data, viewIds, groupTiles, groupIllums, groupChannels, false );

				final PairwiseSetup< ViewId > setup = new AllToAll<>( viewIds, groups );
				IOFunctions.println( "Defined pairs, removed " + setup.definePairs().size() + " redundant view pairs." );
				IOFunctions.println( "Removed " + setup.removeNonOverlappingPairs( new SimpleBoundingBoxOverlap<>( data ) ).size() + " pairs because they do not overlap." );
				setup.reorderPairs();
				setup.detectSubsets();
				setup.sortSubsets();
				final ArrayList< Subset< ViewId > > subsets = setup.getSubsets();
				IOFunctions.println( "Identified " + subsets.size() + " subsets " );

				for ( final Subset< ViewId > subset : subsets )
				{
					// fix view(s)
					final List< ViewId > fixedViews = setup.getDefaultFixedViews();
					final ViewId fixedView = subset.getViews().iterator().next();
					fixedViews.add( fixedView );
					IOFunctions.println( "Removed " + subset.fixViews( fixedViews ).size() + " views due to fixing view tpId=" + fixedView.getTimePointId() + " setupId=" + fixedView.getViewSetupId() );

					HashMap< ViewId, mpicbg.models.Tile< ? extends AbstractModel< ? > > > models;

					if ( Interest_Point_Registration.hasGroups( subsets ) )
						models = groupedSubset( data, subset, interestpoints, labelMap, icpp, fixedViews );
					else
						models = pairSubset( data, subset, interestpoints, labelMap, icpp, fixedViews );

					if ( models == null )
						continue;

					// pre-concatenate models to spimdata2 viewregistrations (from SpimData(2))
					for ( final ViewId viewId : subset.getViews() )
					{
						final mpicbg.models.Tile< ? extends AbstractModel< ? > > tile = models.get( viewId );
						final ViewRegistration vr = data.getViewRegistrations().getViewRegistrations().get( viewId );

						TransformationTools.storeTransformation( vr, viewId, tile, null, transformationDescription );
					}
				}

				panel.updateContent();
				panel.bdvPopup().updateBDV();
			}).start();
		}
		
	}

	public static final HashMap< ViewId, mpicbg.models.Tile< ? extends AbstractModel< ? > > > pairSubset(
			final SpimData2 spimData,
			final Subset< ViewId > subset,
			final Map< ViewId, List< InterestPoint > > interestpoints,
			final Map< ViewId, String > labelMap,
			final IterativeClosestPointParameters icpp,
			final List< ViewId > fixedViews )
	{
		final List< Pair< ViewId, ViewId > > pairs = subset.getPairs();

		if ( pairs.size() <= 0 )
		{
			IOFunctions.println( "No image pair for comparison left, we need at least one pair for this to make sense." );
			return null;
		}

		for ( final Pair< ViewId, ViewId > pair : pairs )
			System.out.println( Group.pvid( pair.getA() ) + " <=> " + Group.pvid( pair.getB() ) );

		// compute all pairwise matchings
		final List< Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > > result =
				MatcherPairwiseTools.computePairs( pairs, interestpoints, new IterativeClosestPointPairwise< InterestPoint >( icpp ) );

		// clear correspondences
		MatcherPairwiseTools.clearCorrespondences( subset.getViews(), spimData.getViewInterestPoints().getViewInterestPoints(), labelMap );

		// add the corresponding detections and output result
		for ( final Pair< Pair< ViewId, ViewId >, PairwiseResult< InterestPoint > > p : result )
		{
			final ViewId vA = p.getA().getA();
			final ViewId vB = p.getA().getB();

			final InterestPointList listA = spimData.getViewInterestPoints().getViewInterestPoints().get( vA ).getInterestPointList( labelMap.get( vA ) );
			final InterestPointList listB = spimData.getViewInterestPoints().getViewInterestPoints().get( vB ).getInterestPointList( labelMap.get( vB ) );

			MatcherPairwiseTools.addCorrespondences( p.getB().getInliers(), vA, vB, labelMap.get( vA ), labelMap.get( vB ), listA, listB );

			IOFunctions.println( p.getB().getFullDesc() );
		}

		final ConvergenceStrategy cs = new ConvergenceStrategy( icpp.getMaxDistance() );
		final PointMatchCreator pmc = new InterestPointMatchCreator( result );

		// run global optimization
		return GlobalOpt.compute( (Model)icpp.getModel().copy(), pmc, cs, fixedViews, subset.getGroups() );
	}

	public static boolean presentForAll( final String label, final Collection< ? extends ViewId > viewIds, final SpimData2 data )
	{
		for ( final ViewId viewId : viewIds )
			if ( data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label ) == null )
				return false;

		return true;
	}

	public static ArrayList< String > getAllLabels( final Collection< ? extends ViewId > viewIds, final SpimData2 data )
	{
		final ViewId view1 = viewIds.iterator().next();

		final Set< String > labels = data.getViewInterestPoints().getViewInterestPointLists( view1 ).getHashMap().keySet();
		final ArrayList< String > presentLabels = new ArrayList<>();

		for ( final String label : labels )
			if ( presentForAll( label, viewIds, data ) )
				presentLabels.add( label );

		Collections.sort( presentLabels );

		return presentLabels;
	}

	public static HashMap< ViewId, mpicbg.models.Tile< ? extends AbstractModel< ? > > > groupedSubset(
			final SpimData2 spimData,
			final Subset< ViewId > subset,
			final Map< ViewId, List< InterestPoint > > interestpoints,
			final Map< ViewId, String > labelMap,
			final IterativeClosestPointParameters icpp,
			final List< ViewId > fixedViews )
	{
		final List< Pair< Group< ViewId >, Group< ViewId > > > groupedPairs = subset.getGroupedPairs();
		final Map< Group< ViewId >, List< GroupedInterestPoint< ViewId > > > groupedInterestpoints = new HashMap<>();
		final InterestPointGrouping< ViewId > ipGrouping = new InterestPointGroupingMinDistance<>( interestpoints );

		if ( groupedPairs.size() <= 0 )
		{
			IOFunctions.println( "No pair of grouped images for comparison left, we need at least one pair for this to make sense." );
			return null;
		}

		// which groups exist
		final Set< Group< ViewId > > groups = new HashSet<>();

		for ( final Pair< Group< ViewId >, Group< ViewId > > pair : groupedPairs )
		{
			groups.add( pair.getA() );
			groups.add( pair.getB() );

			System.out.print( "[" + pair.getA() + "] <=> [" + pair.getB() + "]" );

			if ( !groupedInterestpoints.containsKey( pair.getA() ) )
			{
				System.out.print( ", grouping interestpoints for " + pair.getA() );

				groupedInterestpoints.put( pair.getA(), ipGrouping.group( pair.getA() ) );
			}

			if ( !groupedInterestpoints.containsKey( pair.getB() ) )
			{
				System.out.print( ", grouping interestpoints for " + pair.getB() );

				groupedInterestpoints.put( pair.getB(), ipGrouping.group( pair.getB() ) );
			}

			System.out.println();
		}

		final List< Pair< Pair< Group< ViewId >, Group< ViewId > >, PairwiseResult< GroupedInterestPoint< ViewId > > > > resultGroup =
				MatcherPairwiseTools.computePairs( groupedPairs, groupedInterestpoints, new IterativeClosestPointPairwise< GroupedInterestPoint< ViewId > >( icpp ) );

		// clear correspondences and get a map linking ViewIds to the correspondence lists
		final Map< ViewId, List< CorrespondingInterestPoints > > cMap = MatcherPairwiseTools.clearCorrespondences( subset.getViews(), spimData.getViewInterestPoints().getViewInterestPoints(), labelMap );

		// add the corresponding detections and output result
		final List< Pair< Pair< ViewId, ViewId >, PairwiseResult< GroupedInterestPoint< ViewId > > > > resultG =
				MatcherPairwiseTools.addCorrespondencesFromGroups( resultGroup, spimData.getViewInterestPoints().getViewInterestPoints(), labelMap, cMap );

		// run global optimization
		final ConvergenceStrategy cs = new ConvergenceStrategy( 10.0 );
		final PointMatchCreator pmc = new InterestPointMatchCreator( resultG );

		return GlobalOpt.compute( (Model)icpp.getModel().copy(), pmc, cs, fixedViews, groups );
	}

	public static double[] minmax( final SpimData2 spimData, final Collection< ? extends ViewId > viewIdsToProcess )
	{
		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Determining it approximate Min & Max for all views at lowest resolution levels ... " );

		IJ.showProgress( 0 );

		final ImgLoader imgLoader = spimData.getSequenceDescription().getImgLoader();

		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;

		int count = 0;
		for ( final ViewId view : viewIdsToProcess )
		{
			final double[] minmax = FusionTools.minMaxApprox( DownsampleTools.openAtLowestLevel( imgLoader, view ) );
			min = Math.min( min, minmax[ 0 ] );
			max = Math.max( max, minmax[ 1 ] );

			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): View " + Group.pvid( view ) + ", Min=" + minmax[ 0 ] + " max=" + minmax[ 1 ] );

			IJ.showProgress( (double)++count / viewIdsToProcess.size() );
		}

		IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Total Min=" + min + " max=" + max );

		return new double[]{ min, max };
	}
}
