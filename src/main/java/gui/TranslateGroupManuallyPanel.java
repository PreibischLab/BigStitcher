package gui;

import java.awt.event.WindowEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;

import algorithm.TransformTools;
import bdv.SpimSource;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.Source;
import bdv.viewer.state.SourceState;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.BasicBDVPopup;
import spim.process.boundingbox.BoundingBoxMaximal;

public class TranslateGroupManuallyPanel extends JPanel implements SelectedViewDescriptionListener< AbstractSpimData< ? > >
{

	private static final long serialVersionUID = 1L;
	private final static AffineTransform3D identity = new AffineTransform3D();
	private final static int conversionFactor = 10;
	private final static double eps = 0.1;

	private final Set< ViewId > selected;
	private SpimData2 data;	
	private AffineTransform3D theTransform;
	private Interval bb;
	private BasicBDVPopup bdvPopup;
	
	private JFrame parent;
	private JSlider xslider;
	private JSlider yslider;
	private JSlider zslider;

	public TranslateGroupManuallyPanel(SpimData2 data, Collection< ? extends ViewId > selectedInitial, BasicBDVPopup bdvPopup, JFrame parent)
	{
		this.parent = parent;
		this.data = data;
		this.bdvPopup = bdvPopup;
		theTransform = new AffineTransform3D();

		selected = new HashSet<>();
		selected.addAll( selectedInitial );

		bb = new BoundingBoxMaximal( selected, data ).estimate( "" );
		
		initGUI();
	}
	
	private void initGUI()
	{
		this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
		
		if (bb != null)
		{
			xslider = new JSlider( - (int) bb.dimension( 0 ) * conversionFactor,  (int) bb.dimension( 0 ) * conversionFactor, 0 );
			yslider = new JSlider( - (int) bb.dimension( 1 ) * conversionFactor,  (int) bb.dimension( 1 ) * conversionFactor, 1 );
			zslider = new JSlider( - (int) bb.dimension( 2 ) * conversionFactor,  (int) bb.dimension( 2 ) * conversionFactor, 2 );
		}
		else
		{
			xslider = new JSlider(-1, 1, 0);
			yslider = new JSlider(-1, 1, 0);
			zslider = new JSlider(-1, 1, 0);
			xslider.setEnabled( false );
			yslider.setEnabled( false );
			zslider.setEnabled( false );
		}
		
		xslider.addChangeListener( event -> slidersUpdated());
		yslider.addChangeListener( event -> slidersUpdated());
		zslider.addChangeListener( event -> slidersUpdated());
		
		this.add( xslider );
		this.add( yslider );
		this.add( zslider );
		
		
		final JPanel footer = new JPanel();
		footer.setLayout( new BoxLayout( footer, BoxLayout.LINE_AXIS ) );

		final JButton applyButton = new JButton( "Apply" );
		final JButton closeButton = new JButton( "Close" );
		
		applyButton.addActionListener( event -> applyTranslation() );
		closeButton.addActionListener( event -> quit() );
		
		footer.add( closeButton );
		footer.add( applyButton );
		
		this.add( footer );
	}
	
	private void resetSliders()
	{
		// temporarily remove change listeners
		final ChangeListener[] changeListenersX = xslider.getChangeListeners();
		final ChangeListener[] changeListenersY = yslider.getChangeListeners();
		final ChangeListener[] changeListenersZ = zslider.getChangeListeners();

		for ( ChangeListener cl : changeListenersX )
			xslider.removeChangeListener( cl );
		for ( ChangeListener cl : changeListenersY )
			yslider.removeChangeListener( cl );
		for ( ChangeListener cl : changeListenersZ )
			zslider.removeChangeListener( cl );

		if (bb != null)
		{
			xslider.setMinimum( - (int) bb.dimension( 0 ) * conversionFactor );
			xslider.setMaximum(  (int) bb.dimension( 0 ) * conversionFactor );
			xslider.setValue( 0 );
			yslider.setMinimum( - (int) bb.dimension( 1 ) * conversionFactor );
			yslider.setMaximum( (int) bb.dimension( 1 ) * conversionFactor );
			yslider.setValue( 0 );
			zslider.setMinimum( - (int) bb.dimension( 2 ) * conversionFactor );
			zslider.setMaximum(  (int) bb.dimension( 2 ) * conversionFactor );
			zslider.setValue( 0 );
			xslider.setEnabled( true );
			yslider.setEnabled( true );
			zslider.setEnabled( true );
		}
		else
		{
			xslider.setMinimum( -1 );
			xslider.setMaximum( 1 );
			xslider.setValue( 0 );
			yslider.setMinimum( -1 );
			yslider.setMaximum( 1 );
			yslider.setValue( 0 );
			zslider.setMinimum( -1 );
			zslider.setMaximum( 1 );
			zslider.setValue( 0 );
			xslider.setEnabled( false );
			yslider.setEnabled( false );
			zslider.setEnabled( false );
		}

		// add listeners again
		for ( ChangeListener cl : changeListenersX )
			xslider.addChangeListener( cl );
		for ( ChangeListener cl : changeListenersY )
			yslider.addChangeListener( cl );
		for ( ChangeListener cl : changeListenersZ )
			zslider.addChangeListener( cl );
	}
	
	private void applyTranslation()
	{
		// ask user once more
		int userConfirm = JOptionPane.showConfirmDialog( this, "Apply manual transformation to data", "Confirm", JOptionPane.OK_CANCEL_OPTION );
		if (userConfirm == JOptionPane.CANCEL_OPTION)
			return;
		
		if (data == null)
		{
			resetSliders();
			return;
		}
		
		// update SpimData
		for (ViewId vid : selected)
		{
			final ViewRegistration vr = data.getViewRegistrations().getViewRegistration( vid );
			vr.preconcatenateTransform( new ViewTransformAffine( "Manual Translation", theTransform.copy() ) );
			vr.updateModel();
		}
		
		// reset sliders/transform (so we can apply again)
		theTransform = new AffineTransform3D();
		resetSliders();

		// reset and repaint Bdv if necessary
		if (bdvPopup.bdvRunning())
		{
			FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( bdvPopup.getBDV() );
			bdvPopup.updateBDV();
		}
	}
	
	
	private void slidersUpdated()
	{
		theTransform.set( xslider.getValue() / (double) conversionFactor, 0, 3 );
		theTransform.set( yslider.getValue() / (double) conversionFactor, 1, 3 );
		theTransform.set( zslider.getValue() / (double) conversionFactor, 2, 3 );
		
		if (!bdvPopup.bdvRunning())
			return;

		FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( bdvPopup.getBDV() );
		
		final List< SourceState< ? > > sourceStates = bdvPopup.getBDV().getViewer().getVisibilityAndGrouping().getSources();
		final int currentTimepoint = bdvPopup.getBDV().getViewer().getState().getCurrentTimepoint();
		
		for (final SourceState< ? > state : sourceStates)
		{
			Integer vsId = getViewSetupIdFromBDVSource( state.getSpimSource() );
			if (vsId == null)
				continue;
			
			
			if (selected.contains( new ViewId( currentTimepoint, vsId ) ))
			{
				((TransformedSource< ? >) state.getSpimSource()).setFixedTransform( theTransform );
			}
			
		}
		
		
		bdvPopup.getBDV().getViewer().requestRepaint();
		
	}
	
	/**
	 * try to unwrap source to get the view setup id of wrapped and transformed spimdata view
	 * @param source - BigDataViewer source
	 * @return - view setup id if we can unwrap, null else
	 */
	public static Integer getViewSetupIdFromBDVSource(Source<?> source)
	{
		if (TransformedSource.class.isInstance( source ))
		{
			Source< ? > wrappedSource = ((TransformedSource< ? >) source).getWrappedSource();
			if (SpimSource.class.isInstance( wrappedSource ))
			{
				return ( (SpimSource<?> ) wrappedSource).getSetupId();
			}
			else
				return null;
		}
		else
			return null;
	}
	
	@Override
	public void selectedViewDescriptions(List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions)
	{
		final HashSet<ViewId> selectedNew = new HashSet<>();
		viewDescriptions.forEach( vdl -> selectedNew.addAll( vdl ) );
		
		// selection is the same as before, do nothing
		if (selectedNew.containsAll( selected ) && selected.containsAll( selectedNew ))
			return;
		
		// query user to apply if model is not identity
		if ( !TransformTools.allAlmostEqual( theTransform.getRowPackedCopy(), identity.getRowPackedCopy(), eps ) )
			applyTranslation();
		
		// reset and repaint Bdv if necessary
		if ( bdvPopup.bdvRunning() )
		{
			FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( bdvPopup.getBDV() );
			bdvPopup.getBDV().getViewer().requestRepaint();
		}
		
		// update selection
		selected.clear();
		selected.addAll( selectedNew );
		
		bb = new BoundingBoxMaximal( selected, data ).estimate( "" );
		
		theTransform = new AffineTransform3D();
		resetSliders();
		
	}
	
	@Override
	public void updateContent(AbstractSpimData< ? > data)
	{
		if (SpimData2.class.isInstance( data ))
			this.data = (SpimData2) data;
		else
			this.data = null;

		this.selected.clear();
	}
	
	@Override
	public void save()
	{
		// query user to apply if model is not identity
		if (!TransformTools.allAlmostEqual( theTransform.getRowPackedCopy(), identity.getRowPackedCopy(), eps ))
			applyTranslation();
	}
	
	@Override
	public void quit()
	{
		// query user to apply if model is not identity
		if ( !TransformTools.allAlmostEqual( theTransform.getRowPackedCopy(), identity.getRowPackedCopy(), eps ) )
			applyTranslation();

		// reset and repaint Bdv if necessary
		if ( bdvPopup.bdvRunning() )
		{
			FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( bdvPopup.getBDV() );
			bdvPopup.getBDV().getViewer().requestRepaint();
		}
		
		// close parent, also removing this from panels listeners
		parent.dispatchEvent(new WindowEvent(parent, WindowEvent.WINDOW_CLOSING));
	}
}
