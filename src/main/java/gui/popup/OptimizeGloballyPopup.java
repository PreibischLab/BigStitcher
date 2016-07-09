package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import algorithm.StitchingResults;
import algorithm.globalopt.GlobalOpt;
import algorithm.globalopt.GlobalOptimizationParameters;
import algorithm.globalopt.GlobalTileOptimization;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStitchingResult;
import gui.GroupedRowWindow;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.models.Tile;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation3D;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

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
			
			// define fixed tiles
			final ArrayList< ViewId > fixedViews = new ArrayList< ViewId >();
			fixedViews.add( viewIds.get( 0 ) );

			// define groups (no checks in between Tiles of a group, they are transformed together)
			//final ArrayList< ArrayList< ViewId > > groupedViews = new ArrayList< ArrayList< ViewId > >();
			
			// TODO: use only selected views?
			final ArrayList< PairwiseStitchingResult<ViewId> > results = new ArrayList<>(stitchingResults.getPairwiseResults().values());
			
			//final HashMap< ViewId, Tile< TranslationModel3D > > models =
			//		GlobalOpt.compute( new TranslationModel3D(), results, fixedViews, groupedViews, params );
			
			final Map<ViewId, AbstractTranslation> translations = new HashMap<>();
			
			for (ViewId id : viewIds){
				AffineGet a3d = d.getViewRegistrations().getViewRegistration( id ).getTransformList().get( 1 ).asAffine3D();
				Translation3D tr = new Translation3D( a3d.get( 0, 4 ),
						a3d.get( 1, 4 ), a3d.get( 2, 4 ) );
				translations.put( id, tr );
			}
			
			
			Map< ViewId, double[] > models = GlobalTileOptimization.twoRoundGlobalOptimization( 
														3,
														viewIds,
														fixedViews,
														translations,
														results, 
														params );
			
			
			for (ViewId vid : models.keySet())
			{
				double[] tr = models.get( vid );//.getModel().getTranslation();
				AffineTransform3D at = new AffineTransform3D();
				at.set( new double []  {1.0, 0.0, 0.0, tr[0],
										0.0, 1.0, 0.0, tr[1],
										0.0, 0.0, 1.0, tr[2]} );
				ViewTransform vt = new ViewTransformAffine( "Translation", at);
				
				// set the shift in stitchingResults
				stitchingResults.getGlobalShifts().put( vid, tr );
				
				// find the GroupedViews that contains vid, update Registrations for all viewIDs in group
				for (ViewId groupVid : viewIds){
					if (((GroupedViews) groupVid).getViewIds().contains( vid ))
					{
						for (ViewId vid2 : ((GroupedViews) groupVid).getViewIds()){
							d.getViewRegistrations().getViewRegistration( vid2 ).getTransformList().set( 1, vt );
							d.getViewRegistrations().getViewRegistration( vid2 ).updateModel();
						}
					}
				}				
				
			}
			
			panel.bdvPopup().updateBDV();

		}
		
	}

}
