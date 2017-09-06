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
import javax.swing.JMenu;
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
