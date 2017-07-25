package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import algorithm.TransformTools;
import algorithm.globalopt.GlobalOptimizationParameters;
import algorithm.globalopt.GlobalTileOptimization;
import gui.StitchingExplorerPanel;
import gui.StitchingResultsSettable;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.global.GlobalOptIterative;
import spim.process.interestpointregistration.global.GlobalOptTwoRound;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import spim.process.interestpointregistration.global.pointmatchcreating.ImageCorrelationPointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.MetaDataWeakLinkFactory;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.Subset;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class OptimizeGloballyPopupExpertBatch extends JMenuItem
		implements ExplorerWindowSetable, StitchingResultsSettable
{

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

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

	public OptimizeGloballyPopupExpertBatch()
	{
		super( "Optimize Globally And Apply Shift (Expert/Batch Mode)" );
		this.addActionListener( new MyActionListener() );
	}

	private class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			new Thread( new Runnable()
			{

				@Override
				public void run()
				{
					GlobalOptimizationParameters params = GlobalOptimizationParameters.askUserForParameters();
					if ( params == null )
						return;

					SpimDataFilteringAndGrouping< AbstractSpimData< ? > > filteringAndGrouping;
					if ( ( (StitchingExplorerPanel< ?, ? >) panel ).getSavedFilteringAndGrouping() == null )
					{
						FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? > panelFG = (FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? >) panel;
						filteringAndGrouping = new SpimDataFilteringAndGrouping< AbstractSpimData< ? > >(
								panel.getSpimData() );

						filteringAndGrouping.askUserForFiltering( panelFG );
						if ( filteringAndGrouping.getDialogWasCancelled() )
							return;

						filteringAndGrouping.askUserForGrouping( panelFG );
						if ( filteringAndGrouping.getDialogWasCancelled() )
							return;
					}
					else
					{
						// FIXME: there is some generics work to be done,
						// obviously
						filteringAndGrouping = (SpimDataFilteringAndGrouping< AbstractSpimData< ? > >) (Object) ( (StitchingExplorerPanel< ?, ? >) panel )
								.getSavedFilteringAndGrouping();
					}

					// why can type not be BasicViewDescription?
					PairwiseSetup< ViewId > setup = new PairwiseSetup< ViewId >(
							filteringAndGrouping.getFilteredViews().stream().map( x -> (ViewId) x )
									.collect( Collectors.toList() ),
							filteringAndGrouping.getGroupedViews( false )
									.stream().map( x -> new Group< ViewId >( x.getViews().stream()
											.map( y -> (ViewId) y ).collect( Collectors.toList() ) ) )
									.collect( Collectors.toSet() ) )
					{

						@Override
						protected List< Pair< ViewId, ViewId > > definePairsAbstract()
						{
							List< Pair< ViewId, ViewId > > res = new ArrayList<>();
							for ( int i = 0; i < views.size(); i++ )
								for ( int j = i + 1; j < views.size(); j++ )
								{
									boolean differInApplicationAxis = false;
									for ( Class< ? extends Entity > cl : filteringAndGrouping.getAxesOfApplication() )
									{
										// ugly, but just undoes the casting to
										// ViewId in constructor
										BasicViewDescription< ? extends BasicViewSetup > vdA = (BasicViewDescription< ? extends BasicViewSetup >) views
												.get( i );
										BasicViewDescription< ? extends BasicViewSetup > vdB = (BasicViewDescription< ? extends BasicViewSetup >) views
												.get( j );

										if ( cl == TimePoint.class )
											differInApplicationAxis |= !vdA.getTimePoint().equals( vdB.getTimePoint() );
										else
											differInApplicationAxis |= !vdA.getViewSetup().getAttribute( cl )
													.equals( vdB.getViewSetup().getAttribute( cl ) );
									}
									if ( !differInApplicationAxis )
										res.add( new ValuePair< ViewId, ViewId >( views.get( i ), views.get( j ) ) );
								}

							return res;
						}

						@Override
						public List< ViewId > getDefaultFixedViews()
						{
							return new ArrayList<>();
						}
					};

					setup.definePairs();
					// setup.removeNonOverlappingPairs();
					// setup.reorderPairs();
					setup.detectSubsets();
					// setup.sortSubsets();
					ArrayList< Subset< ViewId > > subsets = setup.getSubsets();

					for ( Subset< ViewId > subset : subsets )
					{
						System.out.println( subset );

						Map< ViewId, AffineGet > initialTransformations = new HashMap<>();
						for ( ViewId vid : subset.getViews() )
							initialTransformations.put( vid, filteringAndGrouping.getSpimData().getViewRegistrations()
									.getViewRegistration( vid ).getModel() );

						Map< ViewId, Dimensions > dims = new HashMap<>();
						boolean allHaveSize = true;
						for ( Set< ViewId > sid : subset.getGroups().stream().map( g -> g.getViews() )
								.collect( Collectors.toSet() ) )
						{
							for ( ViewId id : sid )
							{
								if ( allHaveSize )
								{
									BasicViewSetup vs = filteringAndGrouping.getSpimData().getSequenceDescription()
											.getViewDescriptions().get( id ).getViewSetup();
									if ( !vs.hasSize() )
									{
										allHaveSize = false;
										continue;
									}
									dims.put( id, vs.getSize() );
								}

							}

						}

						if ( !allHaveSize )
							dims = null;

						List< Set< ViewId > > fixed = new ArrayList<>();
						fixed.add( subset.getGroups().iterator().next().getViews() );

						Collection< PairwiseStitchingResult< ViewId > > results = stitchingResults.getPairwiseResults()
								.values();
						// filter to only process links between selected views
						results = results.stream()
								.filter( psr -> subset.getGroups().contains( psr.pair().getA() )
										&& subset.getGroups().contains( psr.pair().getB() ) )
								.collect( Collectors.toList() );

						if ( params.doTwoRound )
						{
							HashMap< ViewId, AffineTransform3D > globalOptResults = GlobalOptTwoRound.compute(
									new TranslationModel3D(),
									new ImageCorrelationPointMatchCreator( results, params.correlationT ),
									new SimpleIterativeConvergenceStrategy( params.absoluteThreshold,
											params.relativeThreshold, params.absoluteThreshold ),
									new MaxErrorLinkRemoval(),
									new MetaDataWeakLinkFactory( panel.getSpimData().getViewRegistrations() ),
									new ConvergenceStrategy( Double.MAX_VALUE ), fixed.iterator().next(),
									subset.getGroups() );

							globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
							globalOptResults.forEach( (k, v) -> {

								final ViewRegistration vr = panel.getSpimData().getViewRegistrations()
										.getViewRegistration( k );

								AffineTransform3D viewTransform = new AffineTransform3D();
								viewTransform.set( v );
								viewTransform = OptimizeGloballyPopup.getAccumulativeTransformForRawDataTransform( vr,
										viewTransform );

								final ViewTransform vt = new ViewTransformAffine( "Stitching Transform",
										viewTransform );
								vr.preconcatenateTransform( vt );
								vr.updateModel();

							} );
						}
						else
						{
							HashMap< ViewId, Tile< TranslationModel3D > > globalOptResults = GlobalOptIterative.compute(
									new TranslationModel3D(),
									new ImageCorrelationPointMatchCreator( results, params.correlationT ),
									new SimpleIterativeConvergenceStrategy( params.absoluteThreshold,
											params.relativeThreshold, params.absoluteThreshold ),
									new MaxErrorLinkRemoval(), fixed.iterator().next(), subset.getGroups() );

							globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
							globalOptResults.forEach( (k, v) -> {

								final ViewRegistration vr = panel.getSpimData().getViewRegistrations()
										.getViewRegistration( k );
								AffineTransform3D viewTransform = new AffineTransform3D();
								viewTransform.set( v.getModel().getMatrix( null ) );

								viewTransform = OptimizeGloballyPopup.getAccumulativeTransformForRawDataTransform( vr,
										viewTransform );

								final ViewTransform vt = new ViewTransformAffine( "Stitching Transform",
										viewTransform );
								vr.preconcatenateTransform( vt );
								vr.updateModel();

							} );
						}

					}

					panel.bdvPopup().updateBDV();

				}
			} ).start();

		}

	}

}
