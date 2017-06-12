package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.text.Format;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import algorithm.FilteredStitchingResults;
import gui.popup.LinkExplorerRemoveLinkPopup;
import gui.popup.SimpleRemoveLinkPopup;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.util.Pair;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class LinkExplorerPanel extends JPanel implements SelectedViewDescriptionListener< AbstractSpimData<?> >
{

	class SimpleDocumentListener implements DocumentListener
	{
		final Consumer< DocumentEvent > callback;

		public SimpleDocumentListener(Consumer< DocumentEvent > callback) { this.callback = callback; }

		@Override
		public void insertUpdate(DocumentEvent e) { callback.accept( e ); }

		@Override
		public void removeUpdate(DocumentEvent e) {	callback.accept( e ); }

		@Override
		public void changedUpdate(DocumentEvent e) { callback.accept( e ); }
	}
	

	public LinkExplorerTableModel getModel()
	{
		return model;
	}



	public JTable getTable()
	{
		return table;
	}

	private StitchingResults results;
	private StitchingExplorerPanel< ?, ? > parent;
	LinkExplorerTableModel model;
	protected JTable table;
	private List<Pair<Group<ViewId>, Group<ViewId>>> activeLinks;
	
	public void setActiveLinks(List<Pair<Group<ViewId>, Group<ViewId>>> links)
	{
		activeLinks.clear();
		activeLinks.addAll( links );
//		System.out.println( "selected links:" );
//		links.forEach( ( l ) -> System.out.println( l ) );
		model.setActiveLinks( links );
		model.fireTableDataChanged();
	}
	
	
	
	public LinkExplorerPanel (StitchingResults results, StitchingExplorerPanel< ?, ? > parent)
	{
		this.results = results;
		this.parent = parent;
		activeLinks = new ArrayList<>();
		
		model = new LinkExplorerTableModel();
		model.setStitchingResults( results );
		
		table = new JTable();
		table.setModel( model );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );

		// center all columns
		for ( int column = 0; column < model.getColumnCount(); ++column ){
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );
		}
		
		table.setPreferredScrollableViewportSize( new Dimension( 400, 300 ) );
		table.getSelectionModel().addListSelectionListener( getSelectionListener() );
		
		final JPanel footer = new JPanel();
		footer.setLayout( new BoxLayout( footer, BoxLayout.PAGE_AXIS ) );
		
		final JPanel corrPanel = new JPanel();
		corrPanel.setLayout( new BoxLayout( corrPanel, BoxLayout.LINE_AXIS ) );		
		final JCheckBox corrCB = new JCheckBox( "filter by correlation coefficient" );
		final JTextField minCorrTextField = new JTextField();
		final JTextField maxCorrTextField = new JTextField();
		
		corrPanel.add( corrCB );
		corrPanel.add( new JLabel( "min R: " ) );
		corrPanel.add( minCorrTextField );
		corrPanel.add( new JLabel( "max R: " ) );
		corrPanel.add( maxCorrTextField );
		footer.add( corrPanel );
		
		final JPanel shiftAbsPanel = new JPanel();
		shiftAbsPanel.setLayout( new BoxLayout( shiftAbsPanel, BoxLayout.LINE_AXIS ) );
		final JCheckBox absoluteShiftCB = new JCheckBox( "filter by shift in dimensions" );
		final JTextField shiftXTextField = new JTextField();
		final JTextField shiftYTextField = new JTextField();
		final JTextField shiftZTextField = new JTextField();
		
		shiftAbsPanel.add( absoluteShiftCB );
		shiftAbsPanel.add( new JLabel( "X: " ) );
		shiftAbsPanel.add( shiftXTextField );
		shiftAbsPanel.add( new JLabel( "Y: " ) );
		shiftAbsPanel.add( shiftYTextField );
		shiftAbsPanel.add( new JLabel( "Z: " ) );
		shiftAbsPanel.add( shiftZTextField );
		footer.add( shiftAbsPanel );
		
		final JPanel shiftMagPanel = new JPanel();
		shiftMagPanel.setLayout( new BoxLayout( shiftMagPanel, BoxLayout.LINE_AXIS ) );
		final JCheckBox shiftMagnitudeCB = new JCheckBox( "filter by shift magnitude" );
		final JTextField shiftMagTextField = new JTextField();
		
		shiftMagPanel.add( shiftMagnitudeCB );
		shiftMagPanel.add( shiftMagTextField );
		footer.add( shiftMagPanel );
		
		final SimpleDocumentListener corrCallback = new SimpleDocumentListener( e -> 
		{
			double minCorr = -1.0;
			double maxCorr = 1.0;

			try { minCorr = Double.parseDouble( minCorrTextField.getText() ); }
			catch (Exception e1) {}

			try { maxCorr = Double.parseDouble( maxCorrTextField.getText() ); }
			catch (Exception e1) {}

			if (corrCB.isSelected())
				model.getFilteredResults().addFilter( new FilteredStitchingResults.CorrelationFilter( minCorr, maxCorr ) );

			model.fireTableDataChanged();
			parent.updateBDVPreviewMode();
			
		});
		
		minCorrTextField.getDocument().addDocumentListener( corrCallback );
		maxCorrTextField.getDocument().addDocumentListener( corrCallback );
		
		corrCB.addActionListener( e -> {
			if ( corrCB.isSelected())
				corrCallback.changedUpdate( null );
			else
				model.getFilteredResults().clearFilter( FilteredStitchingResults.CorrelationFilter.class );

			model.fireTableDataChanged();
			parent.updateBDVPreviewMode();
		});
		
		final SimpleDocumentListener absShiftCallback = new SimpleDocumentListener( ev -> {
			
			double[] maxShift = new double[3];
			Arrays.fill( maxShift, Double.MAX_VALUE );
			
			try { maxShift[0] = Double.parseDouble( shiftXTextField.getText() ); }
			catch (Exception e1) {}
			try { maxShift[1] = Double.parseDouble( shiftYTextField.getText() ); }
			catch (Exception e1) {}
			try { maxShift[2] = Double.parseDouble( shiftZTextField.getText() ); }
			catch (Exception e1) {}
			
			if (absoluteShiftCB.isSelected())
				model.getFilteredResults().addFilter( new FilteredStitchingResults.AbsoluteShiftFilter( maxShift ) );

			model.fireTableDataChanged();
			parent.updateBDVPreviewMode();
			
		});
		
		shiftXTextField.getDocument().addDocumentListener( absShiftCallback );
		shiftYTextField.getDocument().addDocumentListener( absShiftCallback );
		shiftZTextField.getDocument().addDocumentListener( absShiftCallback );
		
		absoluteShiftCB.addActionListener( ev -> {
			if (absoluteShiftCB.isSelected())
				absShiftCallback.changedUpdate( null );
			else
				model.getFilteredResults().clearFilter( FilteredStitchingResults.AbsoluteShiftFilter.class );
			model.fireTableDataChanged();
			parent.updateBDVPreviewMode();

		});
		
		final SimpleDocumentListener shiftMagCallback = new SimpleDocumentListener( ev -> {
			double maxShift = Double.MAX_VALUE;

			try { maxShift = Double.parseDouble( shiftMagTextField.getText() ); }
			catch (Exception e1) {}

			if (shiftMagnitudeCB.isSelected())
				model.getFilteredResults().addFilter( new FilteredStitchingResults.ShiftMagnitudeFilter( maxShift ) );
			
			model.fireTableDataChanged();
			parent.updateBDVPreviewMode();
		});
		
		shiftMagTextField.getDocument().addDocumentListener( shiftMagCallback );
		
		shiftMagnitudeCB.addActionListener( ev -> {
			if (shiftMagnitudeCB.isSelected())
				shiftMagCallback.changedUpdate( null );
			else
				model.getFilteredResults().clearFilter( FilteredStitchingResults.ShiftMagnitudeFilter.class );
			
			model.fireTableDataChanged();
			parent.updateBDVPreviewMode();
		});
		
		
		final JPanel buttons = new JPanel();
		buttons.setLayout( new BoxLayout( buttons, BoxLayout.LINE_AXIS ) );
		
		final JButton applyButton = new JButton( "Apply" );
		final JButton applyAllButton = new JButton( "Apply to All Links" );
		final JButton closeButton = new JButton( "Close" );
		
		closeButton.addActionListener( ev -> parent.togglePreviewMode() );
		applyButton.addActionListener( ev -> {
			final int sizeFiltered = model.getActiveLinks().size();
			final int sizeUnfiltered = activeLinks.size();
			IOFunctions.println( "Removing " + ( sizeUnfiltered - sizeFiltered ) + " of " + sizeUnfiltered + " links." );
			model.getFilteredResults().applyToWrappedSubset( activeLinks );
		});
		
		applyAllButton.addActionListener( ev -> {
			final int sizeUnfiltered = model.getStitchingResults().getPairwiseResults().size();
			final int sizeFiltered = model.getFilteredResults().getPairwiseResults().size();
			IOFunctions.println( "Removing " + ( sizeUnfiltered - sizeFiltered ) + " of " + sizeUnfiltered + " links." );
			model.getFilteredResults().applyToWrappedAll();
		});
		
		buttons.add( applyButton );
		buttons.add( applyAllButton );
		buttons.add( closeButton );
		
		footer.add( buttons );
		
		
		this.setLayout( new BorderLayout() );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );
		this.add( footer, BorderLayout.SOUTH );
		
		final JPopupMenu popupMenu = new JPopupMenu();
		LinkExplorerRemoveLinkPopup rlp = new LinkExplorerRemoveLinkPopup(this);
		rlp.setStitchingResults( results );
		popupMenu.add( rlp.setExplorerWindow( parent ) );
		
		table.setComponentPopupMenu( popupMenu );
		
		parent.addListener( this );
		
	}

	private ListSelectionListener getSelectionListener()
	{
		return new ListSelectionListener()
		{
			
			@Override
			public void valueChanged(ListSelectionEvent e)
			{
				
				int rowIdx = table.getSelectedRow();
				
				if (rowIdx < 0)
				{
					parent.linkOverlay.setSelectedLink( null );
					return;
				}
				Pair< Group<ViewId>, Group<ViewId> > p = model.getActiveLinks().get( rowIdx );
				parent.linkOverlay.setSelectedLink( p );
				
				// repaint BDV if it is open
				if (parent.bdvPopup().bdv != null)
					parent.bdvPopup().bdv.getViewer().requestRepaint();	
				
				
				//System.out.println( p.getA() + "," + p.getB() );
			}
		};
	}



	@Override
	public void selectedViewDescriptions(
			List< List< BasicViewDescription< ? extends BasicViewSetup > > > viewDescriptions)
	{
		if (viewDescriptions.size() < 1) // nothing selected
		{
			setActiveLinks( new ArrayList<>() );
			return;
		}
		// get pairwise results for first (and only) selected view group
		ArrayList< PairwiseStitchingResult< ViewId > > pairwiseResults = results.getAllPairwiseResultsForViewId( new HashSet<>( viewDescriptions.iterator().next()));
		setActiveLinks( pairwiseResults.stream().map( (p) -> p.pair() ).collect( Collectors.toList() ) );
	}



	@Override
	public void updateContent(AbstractSpimData< ? > data) {} // not implemented yet


	@Override
	public void save()	{} // not implemented yet



	@Override
	public void quit() {} // not implemented yet

}
