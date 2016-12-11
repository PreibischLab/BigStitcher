package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.TransformTools;
import algorithm.globalopt.GlobalOpt;
import algorithm.globalopt.GlobalOptimizationParameters;
import algorithm.globalopt.GlobalTileOptimization;
import algorithm.globalopt.GroupedViews;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.realtransform.TranslationGet;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class OptimizeGloballyPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -8153572686300701480L;
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
	
	public OptimizeGloballyPopup()
	{
		super( "Optimize Globally And Apply Shift" );
		this.addActionListener( new MyActionListener() );
	}
	
	public class MyActionListener implements ActionListener{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			
			GlobalOptimizationParameters params = GlobalOptimizationParameters.askUserForParameters();
			if (params == null)
				return;
			
			final AbstractSpimData< ? > d =  panel.getSpimData();
			
			final ArrayList< GroupedViews > viewIds = new ArrayList<>();			
			for (List<ViewId> vidl : ((GroupedRowWindow)panel).selectedRowsViewIdGroups())
				viewIds.add( new GroupedViews( vidl ) );
			
			Collections.sort( viewIds );
			
			// define fixed tiles
			// the first selected Tile will be fixed
			final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
			fixedViews.add( viewIds.get( 0 ) );

			
			final ArrayList< PairwiseStitchingResult<ViewId> > results = new ArrayList<>(stitchingResults.getPairwiseResults().values());
			final Map<ViewId, AffineGet> translations = new HashMap<>();
			
			for (ViewId id : viewIds){
				AffineGet a3d = d.getViewRegistrations().getViewRegistration( id ).getModel();
//				Translation3D tr = new Translation3D( a3d.get( 0, 3 ),
//						a3d.get( 1, 3 ), a3d.get( 2, 3 ) );
				translations.put( id, a3d );
			}
			
			
			Map< ViewId, AffineGet > models = GlobalTileOptimization.twoRoundGlobalOptimization( 
														3,
														viewIds,
														fixedViews,
														translations,
														results, 
														params );
			
			
			
			// view transformation of the first fixed view - every result will be relative to this
			AffineGet vtFixed = new AffineTransform3D();
			( (AffineTransform3D) vtFixed ).set( panel.getSpimData().getViewRegistrations().getViewRegistration( fixedViews.get( 0 ) ).getModel().getRowPackedCopy());
			
		//			(AffineGet) panel.getSpimData().getViewRegistrations().getViewRegistration( fixedViews.get( 0 ) ).getModel().copy();
			
			for (ViewId vid : models.keySet())
			{
				// the transformation determined by stitching
				AffineGet ag = models.get( vid );//.getModel().getTranslation();
				AffineTransform3D at = new AffineTransform3D();
				at.set(ag.getRowPackedCopy() );
				
				// the original view transform of this tile
				AffineGet transformOriginal = new AffineTransform3D();
				( (AffineTransform3D) transformOriginal ).set( panel.getSpimData().getViewRegistrations().getViewRegistration( vid ).getModel().getRowPackedCopy());
				
				// transformation from fixed to original
				AffineGet mapBackToFixed = TransformTools.mapBackTransform( vtFixed, transformOriginal );
				// difference to transformation determined by optimization -> result
				AffineTransform3D mapBackToOriginal = new AffineTransform3D();
				mapBackToOriginal.set( TransformTools.mapBackTransform( mapBackToFixed, at ).getRowPackedCopy());
				
				
				System.out.println( "original:" + transformOriginal );
				System.out.println( "tranform determined by optim:" + at );
				System.out.println( "mapback to original:" + mapBackToOriginal );
				System.out.println( "mapback to fixed:" + mapBackToFixed );
				
				//at.preConcatenate( mapBackToFixed );
				
				
				//ViewTransform vt = new ViewTransformAffine( "Translation", at);
				
				// set the shift in stitchingResults
				stitchingResults.getGlobalShifts().put( vid, ag );
				
				// find the GroupedViews that contains vid, update Registrations for all viewIDs in group
				for (ViewId groupVid : viewIds){
					if (((GroupedViews) groupVid).getViewIds().contains( vid ))
					{
						for (ViewId vid2 : ((GroupedViews) groupVid).getViewIds()){
							
							
							ViewRegistration vr = d.getViewRegistrations().getViewRegistration( vid2 );
							
//							AffineTransform3D atI = at.copy();
//							atI.set( atI.get( 0, 3 ) - vr.getModel().get( 0, 3 ), 0, 3  );
//							atI.set( atI.get( 1, 3 ) - vr.getModel().get( 1, 3 ), 1, 3  );
//							atI.set( atI.get( 2, 3 ) - vr.getModel().get( 2, 3 ), 2, 3  );
							
							ViewTransform vt = new ViewTransformAffine( "stitching transformation", mapBackToOriginal );
							//if (d.getViewRegistrations().getViewRegistration( vid2 ).getTransformList().size() < 2)
								d.getViewRegistrations().getViewRegistration( vid2 ).preconcatenateTransform( vt );
							//else
							//	d.getViewRegistrations().getViewRegistration( vid2 ).getTransformList().set( 1 , vt);
							d.getViewRegistrations().getViewRegistration( vid2 ).updateModel();
						}
					}
				}				
				
			}
			
			panel.bdvPopup().updateBDV();

		}
		
	}

}
