package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
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
import gui.StitchingResultsSettable;
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
import spim.process.interestpointregistration.pairwise.constellation.PairwiseSetup;
import spim.process.interestpointregistration.pairwise.constellation.Subset;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class OptimizeGloballyPopupExpertBatch extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{

	private StitchingResults stitchingResults;
	private ExplorerWindow<? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;
	
	@Override
	public void setStitchingResults(StitchingResults res) { this.stitchingResults = res; }

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
			GlobalOptimizationParameters params = GlobalOptimizationParameters.askUserForParameters();
			if (params == null)
				return;
			
			FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? > panelFG = (FilteredAndGroupedExplorerPanel< AbstractSpimData< ? >, ? >) panel;
			SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > filteringAndGrouping = 	new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( panel.getSpimData() );
			
			filteringAndGrouping.askUserForFiltering( panelFG );
			if (filteringAndGrouping.getDialogWasCancelled())
				return;
			
			filteringAndGrouping.askUserForGrouping( panelFG );
			if (filteringAndGrouping.getDialogWasCancelled())
				return;
			
			
			// why can type not be BasicViewDescription?
			PairwiseSetup< ViewId > setup = new PairwiseSetup< ViewId >(
					filteringAndGrouping.getFilteredViews().stream().map( x -> (ViewId) x ).collect( Collectors.toList() ),
					filteringAndGrouping.getGroupedViews( false ).stream().map( x -> 
						new Group<ViewId>( x.stream().map( y -> (ViewId) y ).collect( Collectors.toList() ) )).collect( Collectors.toSet()  ))
			{

				@Override
				protected List< Pair< ViewId, ViewId > > definePairsAbstract()
				{
					List< Pair< ViewId, ViewId > > res = new ArrayList<>();
					for (int i = 0; i < views.size(); i++)
						for (int j = i+1; j < views.size(); j++)
						{
							boolean differInApplicationAxis = false;
							for (Class<? extends Entity> cl : filteringAndGrouping.getAxesOfApplication())
							{
								// ugly, but just undoes the casting to ViewId in constructor
								BasicViewDescription<? extends BasicViewSetup > vdA = (BasicViewDescription<? extends BasicViewSetup >) views.get( i );
								BasicViewDescription<? extends BasicViewSetup > vdB = (BasicViewDescription<? extends BasicViewSetup >) views.get( j );
								
								if (cl == TimePoint.class)
									differInApplicationAxis |= !vdA.getTimePoint().equals( vdB.getTimePoint() );
								else
									differInApplicationAxis |= !vdA.getViewSetup().getAttribute( cl ).equals( vdB.getViewSetup().getAttribute( cl ) );
							}
							if (!differInApplicationAxis)
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
			//setup.removeNonOverlappingPairs();
			//setup.reorderPairs();
			setup.detectSubsets();
			//setup.sortSubsets();
			ArrayList< Subset< ViewId > > subsets = setup.getSubsets();
			
			for (Subset<ViewId> subset : subsets)
			{
				System.out.println( subset );
				
				Map<ViewId, AffineGet> translations = new HashMap<>();
				for (ViewId vid : subset.getViews())
					translations.put( vid, filteringAndGrouping.getSpimData().getViewRegistrations().getViewRegistration( vid ).getModel());
				
				Map<ViewId, Dimensions> dims = new HashMap<>();
				boolean allHaveSize = true;
				for (Set<ViewId> sid : subset.getGroups().stream().map( g -> g.getViews() ).collect( Collectors.toSet() )){
					for (ViewId id : sid)
					{						
						if (allHaveSize)
						{
							BasicViewSetup vs = filteringAndGrouping.getSpimData().getSequenceDescription().getViewDescriptions().get( id ).getViewSetup();
							if (!vs.hasSize())
							{
								allHaveSize = false;
								continue;
							}
							dims.put( id, vs.getSize() );
						}
					
					}
					
				}
				
				if (!allHaveSize)
					dims = null;
				
				
				List<Set<ViewId>> fixed = new ArrayList<>();
				fixed.add( subset.getGroups().iterator().next().getViews());
				
				Map< Set< ViewId >, AffineGet > models = GlobalTileOptimization.twoRoundGlobalOptimization( 
						new TranslationModel3D(),
						subset.getGroups().stream().map( g -> g.getViews() ).collect( Collectors.toList() ),
						fixed,
						translations,
						dims,
						stitchingResults.getPairwiseResults().values(), 
						params );
				
				//models.entrySet().forEach( el -> System.out.println( el.getKey() + ": " + el.getValue() )  );
				
				// view transformation of the first fixed view - every result will be relative to this
				AffineGet vtFixed = new AffineTransform3D();
				( (AffineTransform3D) vtFixed ).set( panel.getSpimData().getViewRegistrations().getViewRegistration( fixed.get( 0 ).iterator().next() ).getModel().getRowPackedCopy());
				
			//			(AffineGet) panel.getSpimData().getViewRegistrations().getViewRegistration( fixedViews.get( 0 ) ).getModel().copy();
				
				for (Set<ViewId> vid : models.keySet())
				{
					// the transformation determined by stitching
					AffineGet ag = models.get( vid );//.getModel().getTranslation();
					AffineTransform3D at = new AffineTransform3D();
					at.set(ag.getRowPackedCopy() );
					
					for (ViewId vidi : vid)
					{
						
						// the original view transform of this tile
						AffineGet transformOriginal = new AffineTransform3D();
						( (AffineTransform3D) transformOriginal ).set( panel.getSpimData().getViewRegistrations().getViewRegistration( vidi ).getModel().getRowPackedCopy());
						
						
						
						
						// transformation from fixed to original
						AffineGet mapBackToFixed = TransformTools.mapBackTransform( transformOriginal, vtFixed );
						// difference to transformation determined by optimization -> result
						AffineTransform3D mapBackToOriginal = new AffineTransform3D();
						
						mapBackToOriginal.set( mapBackToFixed.getRowPackedCopy() );
						mapBackToOriginal.preConcatenate(  at.inverse() );
						//mapBackToOriginal.set( TransformTools.mapBackTransform( at.inverse(), mapBackToFixed ).getRowPackedCopy());
						
						
						System.out.println( "viewID: " + vid.iterator().next().getViewSetupId() );
						System.out.println( "original:" + transformOriginal );
						System.out.println( "tranform determined by optim:" + at );
						System.out.println( "mapback to original inverse :" + mapBackToOriginal.inverse() );
						System.out.println( "mapback to fixed:" + mapBackToFixed );
						
						//at.preConcatenate( mapBackToFixed );
						
						
						//ViewTransform vt = new ViewTransformAffine( "Translation", at);
						
						// set the shift in stitchingResults
						stitchingResults.getGlobalShifts().put( vid.iterator().next(), ag );
						
						
								
									
									
									ViewRegistration vr = filteringAndGrouping.getSpimData().getViewRegistrations().getViewRegistration( vidi );
									
		//							AffineTransform3D atI = at.copy();
		//							atI.set( atI.get( 0, 3 ) - vr.getModel().get( 0, 3 ), 0, 3  );
		//							atI.set( atI.get( 1, 3 ) - vr.getModel().get( 1, 3 ), 1, 3  );
		//							atI.set( atI.get( 2, 3 ) - vr.getModel().get( 2, 3 ), 2, 3  );
									
									ViewTransform vt = new ViewTransformAffine( "stitching transformation", mapBackToOriginal.inverse() );
									//if (d.getViewRegistrations().getViewRegistration( vid2 ).getTransformList().size() < 2)
									filteringAndGrouping.getSpimData().getViewRegistrations().getViewRegistration( vidi ).preconcatenateTransform( vt );
									//else
									//	d.getViewRegistrations().getViewRegistration( vid2 ).getTransformList().set( 1 , vt);
									filteringAndGrouping.getSpimData().getViewRegistrations().getViewRegistration( vidi ).updateModel();
								
						
					
					}
				}
				
				
			}
			
			panel.bdvPopup().updateBDV();
			
			
		}
		
	}
	

	
}
