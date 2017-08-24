package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.globalopt.TransformationTools;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import spim.fiji.plugin.Interest_Point_Registration;
import spim.fiji.plugin.interestpointregistration.parameters.BasicRegistrationParameters;
import spim.fiji.plugin.interestpointregistration.parameters.GroupParameters.InterestpointGroupingType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBox;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.process.boundingbox.BoundingBoxMaximalGroupOverlap;
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class PairwiseInterestPointRegistrationPopup extends JMenuItem implements ExplorerWindowSetable
{

	private static final long serialVersionUID = -396274656320474433L;
	ExplorerWindow< ?, ? > panel;

	public PairwiseInterestPointRegistrationPopup()
	{
		super( "Pairwise Registration using Interest Points ..." );
		this.addActionListener( new MyActionListener() );	}

	@Override
	public JComponent setExplorerWindow(
			final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
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

			new Thread( new Runnable()
			{

				@Override
				public void run()
				{
					// get selected groups, filter missing views, get all present and selected vids
					final List< List< ViewId > > selectedGroupsAsLists = ((GroupedRowWindow)panel).selectedRowsViewIdGroups();
					final SpimData2 data = (SpimData2) panel.getSpimData();
					final List< Group< ViewId > > selectedGroups = selectedGroupsAsLists.stream().map( l -> new Group<>( l ) ).collect( Collectors.toList() );
					final ArrayList< Group< ViewId > > presentGroups = SpimData2.filterGroupsForMissingViews( data, selectedGroups );
					final List< ViewId > viewIds = new ArrayList<>(presentGroups.stream().map(g -> g.getViews()).reduce( new HashSet<>(), (s1, s2) -> { s1.addAll( s2 ); return s1;} ) );

					// which timepoints are part of the data (we dont necessarily need them, but basicRegistrationParameters GUI wants them)
					final List< TimePoint > timepointToProcess = SpimData2.getAllTimePointsSorted( data, viewIds );
					final int nAllTimepoints = data.getSequenceDescription().getTimePoints().size();

					// query basic registration parameters
					final BasicRegistrationParameters brp = new Interest_Point_Registration().basicRegistrationParameters( timepointToProcess, nAllTimepoints, data, viewIds );
					if ( brp == null )
						return;

					// query algorithm parameters
					GenericDialog gd = new GenericDialog( "Registration Parameters" );
					brp.pwr.addQuery( gd );

					gd.showDialog();

					if ( gd.wasCanceled() )
						return;
					if ( !brp.pwr.parseDialog( gd ) )
						return;

					// get all possible group pairs
					final List< Pair<  Group< ViewId >,  Group< ViewId > > > pairs = new ArrayList<>();
					for (int i = 0; i < presentGroups.size(); i++)
						for (int j = i+1; j<presentGroups.size(); j++)
							pairs.add(new ValuePair<>( presentGroups.get( i ), presentGroups.get( j ) ));

					// remove non-overlapping comparisons
					final List< Pair< Group< ViewId >, Group< ViewId > > > removedPairs = TransformationTools.filterNonOverlappingPairs( pairs, data.getViewRegistrations(), data.getSequenceDescription() );
					removedPairs.forEach( p -> System.out.println( "Skipping non-overlapping pair: " + p.getA() + " -> " + p.getB() ) );


					for (final Pair< Group< ViewId >, Group< ViewId > > pair : pairs)
					{
						// all views in group pair
						final HashSet< ViewId > vids = new HashSet<ViewId>();
						vids.addAll( pair.getA().getViews() );
						vids.addAll( pair.getB().getViews() );

						// simple PairwiseSetup with just two groups (fully connected to each other)
						final PairwiseSetup< ViewId > setup = new PairwiseSetup< ViewId >(new ArrayList<>(vids), new HashSet<>(Arrays.asList( pair.getA(), pair.getB() )))
						{

							@Override
							protected List< Pair< ViewId, ViewId > > definePairsAbstract()
							{
								// all possible links between groups 
								final List< Pair< ViewId, ViewId > > res = new ArrayList<>();
								for (final ViewId vidA : pair.getA() )
									for (final ViewId vidB : pair.getB() )
										res.add( new ValuePair< ViewId, ViewId >( vidA, vidB ) );
								return res;
							}
	
							@Override
							public List< ViewId > getDefaultFixedViews()
							{
								// first group will remain fixed -> we get the transform to align second group to this target
								return new ArrayList<>(pair.getA().getViews());
							}
						};

						// prepare setup
						setup.definePairs();
						setup.detectSubsets();

						// get copies of view registrations (as they will be modified) and interest points
						final Map<ViewId, ViewRegistration> registrationMap = new HashMap<>();
						final Map<ViewId, ViewInterestPointLists> ipMap = new HashMap<>();
						for (ViewId vid : vids)
						{
							final ViewRegistration vrOld = data.getViewRegistrations().getViewRegistration( vid );
							final ViewInterestPointLists iplOld = data.getViewInterestPoints().getViewInterestPointLists( vid );
							registrationMap.put( vid, new ViewRegistration( vid.getTimePointId(), vid.getViewSetupId(), new ArrayList<>(vrOld.getTransformList() ) ) );
							ipMap.put( vid, iplOld );
						}

						// run the registration for this pair
						if ( ! new Interest_Point_Registration().processRegistration(
								setup,
								brp.pwr,
								InterestpointGroupingType.ADD_ALL,
								pair.getA().getViews(),
								null,
								null,
								registrationMap,
								ipMap,
								brp.labelMap,
								false ) )
							continue;

						// get newest Transformation of groupB (the accumulative transform determined by registration)
						final ViewTransform vtB = registrationMap.get( pair.getB().iterator().next() ).getTransformList().get( 0 );

						final AffineTransform3D result = new AffineTransform3D();
						result.set( vtB.asAffine3D().getRowPackedCopy() );
						IOFunctions.println("resulting transformation: " + Util.printCoordinates(result.getRowPackedCopy()));

						// NB: in the global optimization, the final transform of a view will be VR^-1 * T * VR (T is the optimization result)
						// the rationale behind this is that we can use "raw (pixel) coordinate" transforms T (the typical case when stitching)
						//
						// since we get results T' in world coordinates here, we calculate VR * T' * VR^-1 as the result here
						// after the optimization, we will get VR^-1 * VR * T' * VR^-1 * VR = T' (i.e. the result will remain in world coordinates)
						final AffineTransform3D oldVT = data.getViewRegistrations().getViewRegistration( pair.getB().iterator().next() ).getModel();
						result.concatenate( oldVT );
						result.preConcatenate( oldVT.copy().inverse() );

						// get Overlap Bounding Box, which we need for stitching results
						final List<List<ViewId>> groupListsForOverlap = new ArrayList<>();
						groupListsForOverlap.add( new ArrayList<>(pair.getA().getViews()) );
						groupListsForOverlap.add( new ArrayList<>(pair.getB().getViews()) );
						BoundingBoxMaximalGroupOverlap< ViewId > bbDet = new BoundingBoxMaximalGroupOverlap<ViewId>( groupListsForOverlap, data.getSequenceDescription(), data.getViewRegistrations() );
						BoundingBox bbOverlap = bbDet.estimate( "Max Overlap" );

						// TODO: meaningful quality criterion (e.g. inlier ratio ), not just 1.0
						data.getStitchingResults().getPairwiseResults().put( pair, new PairwiseStitchingResult<>( pair, bbOverlap, result, 1.0 ) );
					}
				}
				
			}).start();

		}
	}

}
