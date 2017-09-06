package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.TransformTools;
import algorithm.globalopt.GlobalOptimizationParameters;
import algorithm.globalopt.GlobalTileOptimization;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.models.RigidModel3D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.global.GlobalOpt;
import spim.process.interestpointregistration.global.GlobalOptIterative;
import spim.process.interestpointregistration.global.GlobalOptTwoRound;
import spim.process.interestpointregistration.global.convergence.ConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.IterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.convergence.SimpleIterativeConvergenceStrategy;
import spim.process.interestpointregistration.global.linkremoval.MaxErrorLinkRemoval;
import spim.process.interestpointregistration.global.pointmatchcreating.strong.ImageCorrelationPointMatchCreator;
import spim.process.interestpointregistration.global.pointmatchcreating.weak.MetaDataWeakLinkFactory;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class OptimizeGloballyPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -8153572686300701480L;
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

	public OptimizeGloballyPopup()
	{
		super( "Optimize Globally And Apply Shift" );
		this.addActionListener( new MyActionListener() );
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
					GlobalOptimizationParameters params = GlobalOptimizationParameters.askUserForParameters();
					if ( params == null )
						return;

					final AbstractSpimData< ? > d = panel.getSpimData();

					final ArrayList< Set< ViewId > > viewIds = new ArrayList<>();
					for ( List< ViewId > vidl : ( (GroupedRowWindow) panel ).selectedRowsViewIdGroups() )
					{
						final HashSet< ViewId > vidsTmp = new HashSet< ViewId >( vidl );
						SpimData2.filterMissingViews( d, vidsTmp );
						viewIds.add( vidsTmp );
					}


					Collections.sort( viewIds, new Comparator< Set< ViewId > >()
					{
						@Override
						public int compare(Set< ViewId > o1, Set< ViewId > o2)
						{
							final ArrayList< ViewId > o1List = new ArrayList<>( o1 );
							final ArrayList< ViewId > o2List = new ArrayList<>( o2 );
							Collections.sort( o1List );
							Collections.sort( o2List );
							Iterator< ViewId > it1 = o1List.iterator();
							Iterator< ViewId > it2 = o2List.iterator();
							while ( it1.hasNext() && it2.hasNext() )
							{
								int comp = it1.next().compareTo( it2.next() );
								if ( comp != 0 )
									return comp;
							}
							// list 1 is longer
							if ( it1.hasNext() )
								return -1;
							// list 2 is longer
							if ( it2.hasNext() )
								return 1;
							// lists equal
							else
								return 0;
						}
					} );

					// define fixed tiles
					// the first selected Tile will be fixed
					final ArrayList< Set< ViewId > > fixedViews = new ArrayList<>();

					GenericDialog gdFixing = new GenericDialog( "Pick view (group) to fix" );
					List< String > choices = new ArrayList<>();
					for ( Set< ViewId > s : viewIds )
					{
						List< String > descs = s.stream().map( view -> "(View " + view.getViewSetupId() + ", Timepoint "
								+ view.getTimePointId() + ")" ).collect( Collectors.toList() );
						choices.add( "[" + String.join( ", ", descs ) + "]" );
					}
					gdFixing.addChoice( "view (group) to fix", choices.toArray( new String[choices.size()] ),
							choices.get( 0 ) );

					gdFixing.showDialog();
					if ( gdFixing.wasCanceled() )
						return;

					fixedViews.add( viewIds.get( gdFixing.getNextChoiceIndex() ) );

					List< PairwiseStitchingResult< ViewId > > results = new ArrayList<>(
							stitchingResults.getPairwiseResults().values() );
//					final Map< ViewId, AffineGet > translations = new HashMap<>();
//					Map< ViewId, Dimensions > dims = new HashMap<>();
//
//					boolean allHaveSize = true;
//					for ( Set< ViewId > sid : viewIds )
//					{
//						for ( ViewId id : sid )
//						{
//							AffineGet a3d = d.getViewRegistrations().getViewRegistration( id ).getModel();
//
//							translations.put( id, a3d );
//
//							if ( allHaveSize )
//							{
//								BasicViewSetup vs = d.getSequenceDescription().getViewDescriptions().get( id )
//										.getViewSetup();
//								if ( !vs.hasSize() )
//								{
//									allHaveSize = false;
//									continue;
//								}
//								dims.put( id, vs.getSize() );
//							}
//
//						}
//
//					}
//
//					if ( !allHaveSize )
//						dims = null;

					ArrayList< Group< ViewId > > groupsIn = new ArrayList< Group< ViewId > >();
					viewIds.forEach( vids -> groupsIn.add( new Group<>( vids ) ) );

					// filter to only process links between selected views
					results = results.stream().filter(
							psr -> groupsIn.contains( psr.pair().getA() ) && groupsIn.contains( psr.pair().getB() ) )
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
								new ConvergenceStrategy( Double.MAX_VALUE ), fixedViews.iterator().next(),
								new HashSet<>( groupsIn ) );

						globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
						globalOptResults.forEach( (k, v) -> {

							final ViewRegistration vr = panel.getSpimData().getViewRegistrations()
									.getViewRegistration( k );

							AffineTransform3D viewTransform = new AffineTransform3D();
							viewTransform.set( v );

							// TODO: this works only for raw data shifts
							viewTransform = getAccumulativeTransformForRawDataTransform( vr, viewTransform );

							System.out.println( viewTransform );

							final ViewTransform vt = new ViewTransformAffine( "Stitching Transform", viewTransform );
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
								new MaxErrorLinkRemoval(), fixedViews.iterator().next(), new HashSet<>( groupsIn ) );

						globalOptResults.forEach( (k, v) -> System.out.println( k + ": " + v ) );
						globalOptResults.forEach( (k, v) -> {

							final ViewRegistration vr = panel.getSpimData().getViewRegistrations()
									.getViewRegistration( k );
							AffineTransform3D viewTransform = new AffineTransform3D();
							viewTransform.set( v.getModel().getMatrix( null ) );

							// TODO: this works only for raw data shifts
							viewTransform = getAccumulativeTransformForRawDataTransform( vr, viewTransform );

							final ViewTransform vt = new ViewTransformAffine( "Stitching Transform", viewTransform );
							vr.preconcatenateTransform( vt );
							vr.updateModel();

						} );
					}

					panel.bdvPopup().updateBDV();

				}
			} ).start();

		}

	}

	public static AffineTransform3D getAccumulativeTransformForRawDataTransform(ViewRegistration viewRegistration,
			AffineGet rawTransform)
	{
		final AffineTransform3D vrModel = viewRegistration.getModel();
		final AffineTransform3D result = vrModel.inverse().copy();
		result.preConcatenate( rawTransform ).preConcatenate( vrModel );
		return result;
	}

}
