package gui;

import java.awt.Image;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.Timer;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import bdv.BigDataViewer;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import gui.RegularTranformHelpers.RegularTranslationParameters;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;

public class PreviewRegularGridPanel <AS extends AbstractSpimData<?> > extends JPanel implements SelectedViewDescriptionListener< AS >
{
	
	/*
	 * get int array from 3 comma-separated numbers, return null if fragments cannot be parsed as int or there are != 3 numbers
	 */
	private static int[] getSteps(String s)
	{
		List<String> splitted = Arrays.asList( s.split( "," ) );
		
		for (int i = 0; i < splitted.size(); ++i)
			splitted.set( i, splitted.get( i ).replaceAll( "\\s+", "" ));
		
		List<Integer> steps;
		try
		{
			steps = splitted.stream().map( ( st ) -> Integer.parseInt( st ) ).collect( Collectors.toList() );
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		
		if (steps.size() != 3)
			return null;
		
		int[] res = new int[3];
		res[0] = steps.get( 0 );
		res[1] = steps.get( 1 );
		res[2] = steps.get( 2 );
		
		return res;
	}
	
	/*
	 * get dimension order array from string containing x,y and z separated by commas
	 * returns null for malformed strings
	 */
	private static int[] getDimensionOrder(String s)
	{
		List<String> splitted = Arrays.asList( s.split( "," ) );
		
		for (int i = 0; i < splitted.size(); ++i)
			splitted.set( i, splitted.get( i ).replaceAll( "\\s+", "" ).toUpperCase() );

		
		Set<String> splittedSet = new HashSet<>(splitted);
		//System.out.println( splittedSet );
		if (!(splittedSet.size() == 3 ) || !splittedSet.contains( "X" ) || !splittedSet.contains( "Y" ) || !splittedSet.contains( "Z" ))
			return null;
		
		int res[] = new int[3];
		res[0] = (int)(char)splitted.get( 0 ).charAt( 0 ) - 88;
		res[1] = (int)(char)splitted.get( 1 ).charAt( 0 ) - 88;
		res[2] = (int)(char)splitted.get( 2 ).charAt( 0 ) - 88;
		
		return res;
	}
	
	private static String[] dimensionNames = new String[] {"X", "Y", "Z"};

	
	private ExplorerWindow< AS, ?> parent;
	
	
	// UI elements
	private List<JCheckBox> alternatingCheckboxes;
	private List<JCheckBox> increasingCheckboxes;
	private List<JSlider> overlapSliders;
	private JTextField orderTextField;
	private JTextField stepsTextField;
	private JLabel orderWarningLabel;
	private JLabel stepsWarningLabel;
	
	// state
	private boolean[] alternating;
	private boolean[] increasing;
	private int[] dimensionOrder;
	private int[] steps;
	private double[] overlaps;
	private List<List<BasicViewDescription< ? >>> selectedVDs;
	
	// save old transformation to undo if we cancel
	private AffineTransform3D oldViewerTransform;
	
	/*
	 * save old BDV View Transformation if we haven't done that before
	 * move to origin, keep zoom level
	 */
	private void saveOldTransformAndMoveToOriginIfNecessary()
	{
		if (oldViewerTransform == null)
		{
			if (parent.bdvPopup() == null)
				return;
			
			BigDataViewer bdv = parent.bdvPopup().getBDV();
			
			if (bdv == null)
				return;
			
			oldViewerTransform = new AffineTransform3D();
			bdv.getViewer().getState().getViewerTransform( oldViewerTransform );
			
			AffineTransform3D t = oldViewerTransform.copy();
			t.set( 0.0, 0, 3 );
			t.set( 0.0, 1, 3 );
			t.set( 0.0, 2, 3 );
			bdv.getViewer().setCurrentViewerTransform( t );
			
			bdv.getViewer().requestRepaint();
		}
	}
	
	
	public PreviewRegularGridPanel(ExplorerWindow< AS, ? > parent)
	{
		int iconSizeX = 64;
		int iconSizeY = 64;
		
		Image imColumn1 = null;
		Image imColumn2;
		Image imColumn3;
		Image imColumn4;
		try
		{
			imColumn1 = ImageIO.read( getClass().getResource( "/images/column1.png" ) ).getScaledInstance( iconSizeX, iconSizeY, Image.SCALE_SMOOTH );
			imColumn2 = ImageIO.read( getClass().getResource( "/images/column2.png" ) ).getScaledInstance( iconSizeX, iconSizeY, Image.SCALE_DEFAULT );
			imColumn3 = ImageIO.read( getClass().getResource( "/images/column2.png" ) ).getScaledInstance( iconSizeX, iconSizeY, Image.SCALE_DEFAULT );
			imColumn4 = ImageIO.read( getClass().getResource( "/images/column2.png" ) ).getScaledInstance( iconSizeX, iconSizeY, Image.SCALE_DEFAULT );
		}
		catch (IOException e)
		{
			IOFunctions.printErr( "WARNING: Could not load preset icons" );
		}
		
		this.parent = parent;
		
		saveOldTransformAndMoveToOriginIfNecessary();
				
		this.setLayout( new BoxLayout( this, BoxLayout.PAGE_AXIS ) );
		
		
		// preset Icon panel
		JPanel presetPanel = new JPanel();
		presetPanel.setLayout( new BoxLayout( presetPanel, BoxLayout.LINE_AXIS ) );
		presetPanel.add( new JLabel( new ImageIcon( imColumn1 ) ) );
		this.add( presetPanel );
		
		
		// checkboxes to set wether the grid alternates along a given axis
		alternatingCheckboxes = new ArrayList<>();
		alternatingCheckboxes.add( new JCheckBox( "X", true ));
		alternatingCheckboxes.add( new JCheckBox( "Y", true ));
		alternatingCheckboxes.add( new JCheckBox( "Z", true ));
		
		JPanel alternatingCheckboxesPanel = new JPanel();
		alternatingCheckboxesPanel.setLayout( new BoxLayout( alternatingCheckboxesPanel, BoxLayout.LINE_AXIS ) );
		
		for (JCheckBox c : alternatingCheckboxes)
		{
			alternatingCheckboxesPanel.add( c );
			c.addActionListener( (e) -> update() );
		}
		
		this.add( new JLabel( "Alternating in dimensions" ) );
		this.add( alternatingCheckboxesPanel );
		
		// checkboxes to set wether the coordinates increase along a given axis
		increasingCheckboxes = new ArrayList<>();
		increasingCheckboxes.add( new JCheckBox( "X", true ));
		increasingCheckboxes.add( new JCheckBox( "Y", true ));
		increasingCheckboxes.add( new JCheckBox( "Z", true ));
		
		JPanel increasingCheckboxesPanel = new JPanel();
		increasingCheckboxesPanel.setLayout( new BoxLayout( increasingCheckboxesPanel, BoxLayout.LINE_AXIS ) );
		
		for (JCheckBox c : increasingCheckboxes)
		{
			increasingCheckboxesPanel.add( c );
			c.addActionListener( (e) -> update() );
		}
		
		this.add( new JLabel( "Increasing in dimensions" ) );
		this.add( increasingCheckboxesPanel );
		
		// sliders for overlap percents
		overlapSliders = new ArrayList<>();
		overlapSliders.add( new JSlider( JSlider.HORIZONTAL, 0, 100, 10 ) );
		overlapSliders.add( new JSlider( JSlider.HORIZONTAL, 0, 100, 10 ) );
		overlapSliders.add( new JSlider( JSlider.HORIZONTAL, 0, 100, 10 ) );
		
		JPanel overlapSlidersPanel = new JPanel();
		overlapSlidersPanel.setLayout( new BoxLayout( overlapSlidersPanel, BoxLayout.LINE_AXIS ) );
		int i = 0;
		for (JSlider c : overlapSliders)
		{
			overlapSlidersPanel.add( new JLabel( dimensionNames[i++] ) );
			overlapSlidersPanel.add( c );
			final JLabel valueDisplay = new JLabel( Integer.toString( c.getValue() ) );
			overlapSlidersPanel.add( valueDisplay );
			c.addChangeListener( (e) -> {update();
											valueDisplay.setText( Integer.toString( c.getValue() ) );} );
		}
		
		this.add( new JLabel( "Overlap in dimensions" ) );
		this.add( overlapSlidersPanel );
		
		
		// dimension order
		orderTextField = new JTextField( "x, y, z", 30 );
		orderWarningLabel = new JLabel("");
		orderTextField.getDocument().addDocumentListener( new DocumentListener()
		{			
			@Override
			public void removeUpdate(DocumentEvent e){  }
			
			@Override
			public void insertUpdate(DocumentEvent e){  }
			
			@Override
			public void changedUpdate(DocumentEvent e){ update(); }
		} ); 
		this.add( new JLabel( "Dimension order" ) );
		this.add( orderTextField );
		this.add( orderWarningLabel );
		
		// steps in each dimension
		stepsTextField = new JTextField( "4, 4, 1", 30 );
		stepsWarningLabel = new JLabel( "" );
		stepsTextField.getDocument().addDocumentListener( new DocumentListener()
		{			
			@Override
			public void removeUpdate(DocumentEvent e){  }
			
			@Override
			public void insertUpdate(DocumentEvent e){  }
			
			@Override
			public void changedUpdate(DocumentEvent e){ update(); }
		} ); 
		this.add( new JLabel( "Tiles in each dimension" ) );
		this.add( stepsTextField );
		this.add( stepsWarningLabel );
		
		
		
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout( new BoxLayout( buttonPanel, BoxLayout.LINE_AXIS ) );
		
		JButton cancelButton = new JButton( "cancel" );
		cancelButton.addActionListener( ( e ) -> {((JFrame)this.getTopLevelAncestor()).dispose(); quit();}) ;
		
		JButton applyButton = new JButton( "Apply Transformations" );
		applyButton.addActionListener( ( e ) -> {applyButtonClicked(); }) ; 
		buttonPanel.add( cancelButton );
		buttonPanel.add( applyButton );
		
		this.add( buttonPanel );
		
		// add this as a listener for selection changes in parent view explorer
		// this should trigger update immediately
		FilteredAndGroupedExplorerPanel< AS,? > FilteredAndGroupedExplorerPanel = (FilteredAndGroupedExplorerPanel< AS,? >)parent;
		FilteredAndGroupedExplorerPanel.addListener( this );
		
					
	}
	
	
	
	
	public void update()
	{
		
		// update state from gui
		alternating = new boolean[3];
		alternating[0] = alternatingCheckboxes.get( 0 ).isSelected();
		alternating[1] = alternatingCheckboxes.get( 1 ).isSelected();
		alternating[2] = alternatingCheckboxes.get( 2 ).isSelected();
		
		increasing = new boolean[3];
		increasing[0] = increasingCheckboxes.get( 0 ).isSelected();
		increasing[1] = increasingCheckboxes.get( 1 ).isSelected();
		increasing[2] = increasingCheckboxes.get( 2 ).isSelected();
		
		overlaps = new double[3];
		overlaps[0] = (double) overlapSliders.get( 0 ).getValue() / 100.0;
		overlaps[1] = (double) overlapSliders.get( 1 ).getValue() / 100.0;
		overlaps[2] = (double) overlapSliders.get( 2 ).getValue() / 100.0;
		
		dimensionOrder = getDimensionOrder( orderTextField.getText() );
		if (dimensionOrder == null)
			orderWarningLabel.setText( "<html><p style=\"color:red \"> WARNING: dimension order must be x,y and z separated by commas </p></html>" );
		else
			orderWarningLabel.setText("");

		steps = getSteps( stepsTextField.getText() );
		if (steps == null)
			stepsWarningLabel.setText( "<html><p style=\"color:red \"> WARNING: steps must be three numbers separated by commas </p></html>" );
		else
			stepsWarningLabel.setText("");
		

		updateBDV();
		
	}
	
	
	private void updateBDV()
	{

		BigDataViewer bdv = parent.bdvPopup().getBDV();
		if ( bdv != null )

		{
			
			//FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( bdv );

			RegularTranslationParameters params = new RegularTranslationParameters();
			params.nDimensions = 3;
			params.alternating = alternating;
			params.dimensionOrder = dimensionOrder;
			params.increasing = increasing;
			params.overlaps = overlaps;
			params.nSteps = steps;

			Dimensions size = parent.getSpimData().getSequenceDescription().getViewDescriptions()
					.get( selectedVDs.get( 0 ).get( 0 ) ).getViewSetup().getSize();
			List< AffineTransform3D > generateRegularGrid = RegularTranformHelpers.generateRegularGrid( params, size );
			int i = 0;
			for ( List< BasicViewDescription< ? > > lvd : selectedVDs )
			{
				for ( BasicViewDescription< ? > vd : lvd )
				{
					
					int sourceIdx = StitchingExplorerPanel.getBDVSourceIndex( vd.getViewSetup(), parent.getSpimData() );
					SourceState< ? > s = parent.bdvPopup().getBDV().getViewer().getState().getSources().get( sourceIdx );
					

					ViewRegistration vr = parent.getSpimData().getViewRegistrations().getViewRegistration( vd );
					AffineTransform3D inv = vr.getModel().inverse();
					AffineTransform3D calib = new AffineTransform3D();
					calib.set( vr.getTransformList().get( vr.getTransformList().size() - 1 ).asAffine3D().getRowPackedCopy() );
							
					//invAndCalib.preConcatenate( vr.getTransformList().get( 0 ).asAffine3D() );

					AffineTransform3D gridTransform = ( i < generateRegularGrid.size() )
							? generateRegularGrid.get( i ).copy().concatenate( inv ) : inv.copy();

					gridTransform.preConcatenate( calib );
					//System.out.println( gridTransform );
						
					
					( (TransformedSource< ? >) s.getSpimSource() ).setFixedTransform( gridTransform );
					//( (TransformedSource< ? >) s.getSpimSource() ).setIncrementalTransform( gridTransform );
					
					
					//System.out.println( i );

				}
				i++;
			}

			
			bdv.getViewer().requestRepaint();

		}

	}
	
	private void applyButtonClicked()
	{
		String message1 = "<html><strong>WARNING:</strong> this will overwrite all tranformations but the calibration with the new translations</html>";
		String message2 = "<html>apply to all TimePoints?</html>";
		int confirm1 = JOptionPane.showConfirmDialog( this, message1, "Apply to dataset", JOptionPane.OK_CANCEL_OPTION );
		
		if (confirm1 == JOptionPane.CANCEL_OPTION)
			return;
		
		int confirm2 = JOptionPane.showConfirmDialog( this, message2, "Apply to dataset", JOptionPane.YES_NO_CANCEL_OPTION );
		
		if (confirm2 == JOptionPane.CANCEL_OPTION)
			return;
		
		boolean allTPs = confirm2 == JOptionPane.YES_OPTION;
		
		RegularTranslationParameters params = new RegularTranslationParameters();
		params.nDimensions = 3;
		params.alternating = alternating;
		params.dimensionOrder = dimensionOrder;
		params.increasing = increasing;
		params.overlaps = overlaps;
		params.nSteps = steps;
		
		applyToSpimData( parent.getSpimData() , selectedVDs, params, allTPs );
		
		// reset viewer transform to recall to current transform & update with new sources
		parent.bdvPopup().getBDV().getViewer().getState().getViewerTransform( oldViewerTransform );
		parent.bdvPopup().updateBDV();
		
		// close the window
		((JFrame)this.getTopLevelAncestor()).dispose();
		quit();
		
	}
	
	public static <AS extends AbstractSpimData<?> > void applyToSpimData(
			AS data, 
			List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions,
			RegularTranslationParameters params,
			boolean applyToAllTimePoints)
	{
		
		if (!applyToAllTimePoints)
			applyToSpimDataSingleTP( data, viewDescriptions, params, viewDescriptions.get( 0 ).get( 0 ).getTimePoint() );
		else
		{
			for (TimePoint tp : data.getSequenceDescription().getTimePoints().getTimePointsOrdered())
				applyToSpimDataSingleTP( data, viewDescriptions, params, tp );
		}
				
	}
	
	private static <AS extends AbstractSpimData<?> > void applyToSpimDataSingleTP(
			AS data, 
			List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions,
			RegularTranslationParameters params,
			TimePoint tp)
	{
		Dimensions size = data.getSequenceDescription().getViewDescriptions()
				.get( viewDescriptions.get( 0 ).get( 0 ) ).getViewSetup().getSize();
		List< AffineTransform3D > generateRegularGrid = RegularTranformHelpers.generateRegularGrid( params, size );
		
		int i = 0;
		for (List<BasicViewDescription< ? >> lvd : viewDescriptions)
		{
			for (BasicViewDescription< ? > vd : lvd)
			{
				// only do for present Views
				if (data.getSequenceDescription().getViewDescriptions().get( new ViewId( tp.getId(), vd.getViewSetupId() ) ).isPresent())
				{
					ViewRegistration vr = data.getViewRegistrations().getViewRegistration( tp.getId(), vd.getViewSetupId() );
					ViewTransform calibration = vr.getTransformList().get( 0 );
					vr.getTransformList().clear();
					vr.getTransformList().add( calibration );
					vr.updateModel();
					
					// get translation and multiply shift with calibration
					AffineTransform3D translation = generateRegularGrid.get( i ).copy();
					translation.set( translation.get( 0, 3 ) * calibration.asAffine3D().get( 0, 0 ), 0, 3 );
					translation.set( translation.get( 1, 3 ) * calibration.asAffine3D().get( 1, 1 ), 1, 3 );
					translation.set( translation.get( 2, 3 ) * calibration.asAffine3D().get( 2, 2 ), 2, 3 );
					vr.preconcatenateTransform( new ViewTransformAffine( "translation", translation ));
					vr.updateModel();
					
					System.out.println(translation);
				}
				
			}
			i++;
		}
		
		
		
	}

	public void quit()
	{
		FilteredAndGroupedExplorerPanel< AS,? > FilteredAndGroupedExplorerPanel = (FilteredAndGroupedExplorerPanel< AS,? >)parent;
		FilteredAndGroupedExplorerPanel.getListeners().remove( this );
		parent.bdvPopup().getBDV().getViewer().setCurrentViewerTransform( oldViewerTransform );
		
		spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel.resetBDVManualTransformations( parent.bdvPopup().getBDV() );
		parent.bdvPopup().getBDV().getViewer().requestRepaint();
		
		
	}

	@Override
	public void selectedViewDescriptions(
			List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions)
	{
		//System.out.println( " selection upd " );
		selectedVDs = viewDescriptions;
		
		// sort the selected groups by the view id of the first member
		Collections.sort( selectedVDs, (x, y) -> x.get( 0 ).compareTo( y.get( 0 ) ) );
		
		// hacky solution, wait a bit with update since parent will reset BDV after selection change
		new Timer().schedule(new TimerTask()
		{

			public void run()
			{
				update();
				
			}
		}, 100 );

	}

	@Override
	public void updateContent(AS data)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void save()
	{
		// TODO Auto-generated method stub
		
	}

	

}
