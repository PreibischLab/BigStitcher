package gui.popup;

import javax.swing.JComponent;
import javax.swing.JMenu;

import gui.StitchingResultsSettable;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

public class OptimizeGloballyPopup extends JMenu implements ExplorerWindowSetable, StitchingResultsSettable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = -8153572686300701480L;

	public final OptimizeGloballyPopupExpertBatch simpleOptimize;
	public final OptimizeGloballyPopupExpertBatch expertOptimize;

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		simpleOptimize.setStitchingResults( res );
		expertOptimize.setStitchingResults( res );
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		simpleOptimize.setExplorerWindow( panel );
		expertOptimize.setExplorerWindow( panel );
		return this;
	}

	public OptimizeGloballyPopup()
	{
		super( "Optimize Globally And Apply Shift" );
		this.simpleOptimize = new OptimizeGloballyPopupExpertBatch( false );
		this.expertOptimize = new OptimizeGloballyPopupExpertBatch( true );

		this.add( simpleOptimize );
		this.add( expertOptimize );
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
