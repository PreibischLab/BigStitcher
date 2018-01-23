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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel3D;
import mpicbg.models.InterpolatedAffineModel3D;
import mpicbg.models.Model;
import mpicbg.models.RigidModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Detection;
import net.preibisch.mvrecon.fiji.plugin.Interest_Point_Registration;
import net.preibisch.mvrecon.fiji.plugin.interestpointregistration.parameters.AdvancedRegistrationParameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.GroupedRowWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.Separator;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoG;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dog.DoGParameters;
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

				if ( icpType == ICPType.Expert )
				{
					filteringAndGrouping.askUserForFiltering( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;
				}
				else
				{
					// use whatever is selected in panel as filters
					filteringAndGrouping.addFilters( panelFG.selectedRowsGroups().stream().reduce( new ArrayList<>(), (x,y ) -> {x.addAll( y ); return x;}) );
				}
	
				if ( icpType == ICPType.Expert )
				{
					filteringAndGrouping.addComparisonAxis( Tile.class );
					filteringAndGrouping.askUserForGrouping( panelFG );
					if (filteringAndGrouping.getDialogWasCancelled())
						return;
				}
				else
				{
					// get the grouping from panel and compare Tiles
					panelFG.getTableModel().getGroupingFactors().forEach( g -> filteringAndGrouping.addGroupingFactor( g ));
					filteringAndGrouping.addComparisonAxis( Tile.class );
	
					// compare by Channel if channels were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Channel.class ))
						filteringAndGrouping.addComparisonAxis( Channel.class );
	
					// compare by Illumination if illums were ungrouped in UI
					if (!panelFG.getTableModel().getGroupingFactors().contains( Illumination.class ))
						filteringAndGrouping.addComparisonAxis( Illumination.class );
				}

				if ( StitchingUIHelper.allViews2D( filteringAndGrouping.getFilteredViews() ) )
				{
					IOFunctions.println( "ICP refinement is currenty not supported for 2D: " + this.getClass().getSimpleName() );
					return;
				}

				final boolean groupTiles, groupIllums, groupChannels;

				if ( icpType == ICPType.Expert )
				{
					groupTiles = filteringAndGrouping.getGroupingFactors().contains( Tile.class ); // always false?
					groupIllums = filteringAndGrouping.getGroupingFactors().contains( Illumination.class );
					groupChannels = filteringAndGrouping.getGroupingFactors().contains( Channel.class );

					// by default the registration suggests what is selected in the dialog
					Interest_Point_Detection.defaultGroupTiles = false;
					Interest_Point_Detection.defaultGroupIllums = false;
					Interest_Point_Detection.defaultLabel = "forICP";

					new Interest_Point_Detection().detectInterestPoints( data, filteringAndGrouping.getFilteredViews() );
				}
				else
				{
					//
					// filter not present ViewIds
					//
					final ArrayList< ViewId > viewIds = new ArrayList<>();
					viewIds.addAll( filteringAndGrouping.getFilteredViews() );

					final List< ViewId > removed = SpimData2.filterMissingViews( data, viewIds );
					if ( removed.size() > 0 ) IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Removed " +  removed.size() + " views because they are not present." );

					//
					// DoG
					//
					final String label = "forICP";
					boolean presentForAll = true;

					for ( final ViewId viewId : viewIds )
					{
						if ( data.getViewInterestPoints().getViewInterestPointLists( viewId ).getInterestPointList( label ) == null )
						{
							presentForAll = false;
							break;
						}
					}

					if ( !presentForAll )
					{
						final DoGParameters dog = new DoGParameters();
	
						dog.imgloader = data.getSequenceDescription().getImgLoader();
						dog.toProcess = new ArrayList< ViewDescription >();

						for ( final ViewId viewId : viewIds )
							dog.toProcess.add( data.getSequenceDescription().getViewDescription( viewId ) );
	
						dog.downsampleXY = 4;
						dog.downsampleZ = 2;
						dog.sigma = 1.4;
						dog.threshold = 0.005;
	
						dog.limitDetections = true;
						dog.maxDetections = 10000;
						dog.maxDetectionsTypeIndex = 0; // brightest
	
						dog.showProgress( 0, 1 );
	
						final HashMap< ViewId, List< InterestPoint > > points = DoG.findInterestPoints( dog );
	
						InterestPointTools.addInterestPoints( data, label, points, "DoG, sigma=1.4, downsampleXY=4, downsampleZ=2" );
					}
					else
					{
						IOFunctions.println( "Interestpoint '" + label + "' already defined for all views, using those." );
					}

					//
					// ICP
					//
					final IterativeClosestPointParameters icpp =
							new IterativeClosestPointParameters(
									new InterpolatedAffineModel3D< AffineModel3D, RigidModel3D >(
											new AffineModel3D(),
											new RigidModel3D(), 0.1f ) );

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

					if ( icpType == ICPType.TileRefine )
					{
						groupTiles = false;
						groupChannels = true;
						groupIllums = true;
					}
					else
					{
						groupTiles = true;
						groupChannels = false;
						groupIllums = true;
					}

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

						// pre-concatenate models to spimdata2 viewregistrations (from SpimData(2))
						for ( final ViewId viewId : subset.getViews() )
						{
							final mpicbg.models.Tile< ? extends AbstractModel< ? > > tile = models.get( viewId );
							final ViewRegistration vr = data.getViewRegistrations().getViewRegistrations().get( viewId );

							TransformationTools.storeTransformation( vr, viewId, tile, null, "Automatic ICP Refinement" );
						}
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

}
