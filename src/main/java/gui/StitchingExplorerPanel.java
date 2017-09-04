package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import algorithm.SpimDataFilteringAndGrouping;
import bdv.BigDataViewer;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.viewer.DisplayMode;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceState;
import gui.overlay.DemoLinkOverlay;
import gui.overlay.LinkOverlay;
import gui.popup.ApplyBDVTransformationPopup;
import gui.popup.BDVPopupStitching;
import gui.popup.CalculatePCPopup;
import gui.popup.CalculatePCPopup.Method;
import gui.popup.CalculatePCPopupExpertBatch;
import gui.popup.OptimizeGloballyPopup;
import gui.popup.OptimizeGloballyPopupExpertBatch;
import gui.popup.PairwiseInterestPointRegistrationPopup;
import gui.popup.RegularGridPopup;
import gui.popup.SelectIlluminationPopup;
import gui.popup.SimpleRemoveLinkPopup;
import gui.popup.TogglePreviewPopup;
import gui.popup.TranslateGroupManuallyPopup;
import input.FractalImgLoader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import spim.fiji.plugin.util.MultiWindowLayoutHelper;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.SpimDataTools;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorer;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.FilteredAndGroupedTableModel;
import spim.fiji.spimdata.explorer.ISpimDataTableModel;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.explorer.ViewSetupExplorerInfoBox;
import spim.fiji.spimdata.explorer.interestpoint.InterestPointTableModel;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.BoundingBoxPopup;
import spim.fiji.spimdata.explorer.popup.DetectInterestPointsPopup;
import spim.fiji.spimdata.explorer.popup.DisplayFusedImagesPopup;
import spim.fiji.spimdata.explorer.popup.DisplayRawImagesPopup;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.explorer.popup.LabelPopUp;
import spim.fiji.spimdata.explorer.popup.RemoveTransformationPopup;
import spim.fiji.spimdata.explorer.popup.ResavePopup;
import spim.fiji.spimdata.explorer.popup.Separator;
import spim.fiji.spimdata.explorer.util.ColorStream;
import spim.fiji.spimdata.imgloaders.filemap2.FileMapImgLoaderLOCI2;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class StitchingExplorerPanel<AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS >>
		extends FilteredAndGroupedExplorerPanel< AS, X > implements ExplorerWindow< AS, X >
{
	public final static double xPosLinkExplorer = 0.6;
	public final static double yPosLinkExplorer = 0.0;

	// indicates whether we are in link preview mode or not
	boolean previewMode = false;

	LinkOverlay linkOverlay;
	RegularGridPopup regularGridPopup; 
	
	DemoLinkOverlay demoLinkOverlay;

	StitchingResults stitchingResults;

	LinkExplorerPanel linkExplorer;
	JFrame linkFrame;
	JComboBox< ? > angleCB;
	
	// save SpimDataFilteringAndGrouping so we can go preview -> global opt
	SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > savedFilteringAndGrouping;
	
	// offset to get different "random" colors
	private long colorOffset = 0;

	protected JCheckBox checkboxGroupChannels;
	protected JCheckBox checkboxGroupIllums;

	public StitchingExplorerPanel(final FilteredAndGroupedExplorer< AS, X > explorer, final AS data, final String xml,
			final X io, boolean requestStartBDV)
	{
		super( explorer, data, xml, io );

		if ( data instanceof SpimData2 )
			this.stitchingResults = ( (SpimData2) data ).getStitchingResults();
		else
			this.stitchingResults = new StitchingResults();

		linkOverlay = new LinkOverlay( stitchingResults, data );
		demoLinkOverlay = new DemoLinkOverlay( stitchingResults, data );
		
		addListener( (SelectedViewDescriptionListener< AS >) demoLinkOverlay );

		popups = initPopups();
		initComponent();

		if ( requestStartBDV && 
				(Hdf5ImageLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) 
				|| FractalImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) 
				|| (( data instanceof SpimData2 ) && ((SpimData2)data).gridMoveRequested )  
				|| FileMapImgLoaderLOCI2.class.isInstance( data.getSequenceDescription().getImgLoader() ) ) )
		{
			if (!bdvPopup().bdvRunning())
				bdvPopup().bdv = BDVPopupStitching.createBDV( this, linkOverlay );
		}

		if ( data instanceof SpimData2 )
			if (((SpimData2)data).gridMoveRequested)
			{
				((SpimData2)data).gridMoveRequested = false;
				for (int i = 0; i<angleCB.getItemCount(); i++)
				{
					IOFunctions.println("Defining grid for Angle " + (i+1) + " of " + angleCB.getItemCount());
					angleCB.setSelectedIndex( i );
					try { Thread.sleep( 100 ); } catch ( InterruptedException e ){}

					if ( table.getRowCount() > 1 )
					{
						table.getSelectionModel().setSelectionInterval( 0, table.getRowCount() - 1 );
						regularGridPopup.doClick();
						// wait for regular grid movement to complete before continuing with the next angle
						try {
							synchronized ( this )
							{
								this.wait();
							}
						} catch ( InterruptedException e ) { e.printStackTrace(); }
					}
				}
			}

		savedFilteringAndGrouping = null;
	}


	@Override
	public boolean tilesGrouped() { return false; }

	@Override
	public boolean channelsGrouped()
	{
		if ( checkboxGroupChannels == null || !checkboxGroupChannels.isSelected() )
			return false;
		else
			return true;
	}

	@Override
	public boolean illumsGrouped()
	{
		if ( checkboxGroupIllums == null || !checkboxGroupIllums.isSelected() )
			return false;
		else
			return true;
	}



	void quitLinkExplorer()
	{
		linkOverlay.setSelectedLink( null );
		if ( linkFrame != null )
		{
			linkFrame.setVisible( false );
			linkFrame.dispose();
			linkFrame = null;
			this.getListeners().remove( linkExplorer );
			linkExplorer = null;
		}
	}

	public void togglePreviewMode()
	{
		previewMode = !previewMode;
		linkOverlay.isActive = previewMode;

		if ( previewMode )
		{
			int oldFirstSelection = table.getSelectionModel().getMinSelectionIndex();

			// remember whole selection
			if (savedFilteringAndGrouping == null)
			{
				savedFilteringAndGrouping = new SpimDataFilteringAndGrouping< AbstractSpimData<?> >( data );
				savedFilteringAndGrouping.addFilters( getSelectedRows().stream().reduce( new ArrayList<>(), (x,y) -> {x.addAll( y ); return x;}) );
				for (Class<? extends Entity> groupingFactor : tableModel.getGroupingFactors())
					savedFilteringAndGrouping.addGroupingFactor( groupingFactor );
				savedFilteringAndGrouping.addComparisonAxis( Tile.class );
				if (!channelsGrouped())
					savedFilteringAndGrouping.addComparisonAxis( Channel.class );
				if (!illumsGrouped())
					savedFilteringAndGrouping.addComparisonAxis( Illumination.class );
				savedFilteringAndGrouping.addApplicationAxis( TimePoint.class );
				savedFilteringAndGrouping.addApplicationAxis( Angle.class );
			}

			initLinkExplorer();
			table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
			table.setRowSelectionInterval( oldFirstSelection, oldFirstSelection );
			if (bdvPopup().bdvRunning())
				updateBDVPreviewMode();
		}
		else
		{
			boolean doGlobalOpt = false;
			if (savedFilteringAndGrouping != null)
				doGlobalOpt = JOptionPane.showConfirmDialog( linkFrame, "Proceed to Global Optimization?", "Optimize Globally?", JOptionPane.YES_NO_OPTION ) == JOptionPane.YES_OPTION;

			quitLinkExplorer();
			linkOverlay.clearActiveLinks();
			int oldFirstSelection = table.getSelectionModel().getMinSelectionIndex();
			table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			table.setRowSelectionInterval( oldFirstSelection, oldFirstSelection );
			if (bdvPopup().bdvRunning())
				updateBDV( bdvPopup().bdv, colorMode, data, firstSelectedVD, selectedRows );

			if (doGlobalOpt)
			{
				Optional< ExplorerWindowSetable > globalOptPopupOpt = popups.stream().filter( p -> OptimizeGloballyPopupExpertBatch.class.isInstance( p ) ).findFirst();
				OptimizeGloballyPopupExpertBatch globalOptPopup = (OptimizeGloballyPopupExpertBatch) globalOptPopupOpt.get();
				globalOptPopup.doClick();
			}
			else
			{
				// discard the temp. SpimDataFilteringAndGrouping
				// if we discard it right now, but want to do global opt (which runs asynchronously)
				// it would not work. therefore, the global optimization will take care of this
				savedFilteringAndGrouping = null;
			}
		}
	}

	public void initComponent()
	{
		// only do that if needed
		// initLinkExplorer();

		tableModel = new FilteredAndGroupedTableModel< AS >( this );
		tableModel = new StitchingTableModelDecorator< >( tableModel );
		( (StitchingTableModelDecorator< AS >) tableModel ).setStitchingResults( stitchingResults );

		tableModel.addGroupingFactor( Channel.class );
		tableModel.addGroupingFactor( Illumination.class );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );

		final DefaultListSelectionModel selectionModel = new DefaultListSelectionModel()
		{
			private List<Integer> invalidIndices;

			private void updateInvalidSelections()
			{
				invalidIndices = new ArrayList<>();
				if (savedFilteringAndGrouping == null)
					return;
				final List< Group< BasicViewDescription< ? > > > savedGroups = savedFilteringAndGrouping.getGroupedViews( true );
				final ISpimDataTableModel< AS > model = (ISpimDataTableModel< AS >) table.getModel();
				final List< List< BasicViewDescription< ? > > > elements = model.getElements();
			A:	for (int i = 0; i<elements.size(); i++)
				{
					List< BasicViewDescription< ? > > row = elements.get( i );
					for (Group< BasicViewDescription< ? > > grp : savedGroups)
						if (grp.getViews().containsAll( row ))
							continue A;
					invalidIndices.add( i );
				}
			}

			private boolean isValidSelection(int index0, int index1)
			{
				if (index0 > index1)
				{
					int index0Tmp = index0;
					index0 = index1;
					index1 = index0Tmp;
				}
				for (Integer invalidIndex : invalidIndices)
					if( index0 <= invalidIndex && index1 >= invalidIndex)
						return false;
				return true;
			}

			@Override
			public void setSelectionInterval(int index0, int index1)
			{
				updateInvalidSelections();
				if (isValidSelection( index0, index1 ))
					super.setSelectionInterval( index0, index1 );
			}

			@Override
			public void addSelectionInterval(int index0, int index1)
			{
				updateInvalidSelections();
				if (isValidSelection( index0, index1 ))
					super.addSelectionInterval( index0, index1 );
			}
		};

		table.setSelectionModel( selectionModel );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );

		final DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer()
		{
			final Color backgroundColor = getBackground();

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column)
			{
				final Component c = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row,
						column );
				if (!isSelected)
					c.setBackground( backgroundColor );

				if (savedFilteringAndGrouping == null) { return c; }

				final List< Group< BasicViewDescription< ? > > > savedGroups = savedFilteringAndGrouping.getGroupedViews( true );
				final ISpimDataTableModel< AS > model = (ISpimDataTableModel< AS >) table.getModel();
				final List< BasicViewDescription< ? > > views = model.getElements().get( row );

				boolean isSavedSelection = false;
				for (Group< BasicViewDescription< ? > > grp : savedGroups)
					if (grp.getViews().containsAll( views ))
					{
						isSavedSelection = true;
						break;
					}

				c.setForeground( Color.black );
				if ( isSavedSelection )
					if (isSelected)
					{
						c.setBackground( Color.orange );
						c.setForeground( Color.white );
					}
					else
						c.setBackground( Color.yellow );
				else
					if( isSelected)
						c.setBackground( Color.pink );
					else
						c.setBackground( Color.gray );

				return c;
			}

		};
		cellRenderer.setHorizontalAlignment( JLabel.CENTER );

		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
		{
			table.getColumnModel().getColumn( column ).setCellRenderer( cellRenderer );
		}

		// add listener to which row is selected
		table.getSelectionModel().addListSelectionListener( getSelectionListener() );

		// check out if the user clicked on the column header and potentially
		// sorting by that
		table.getTableHeader().addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent mouseEvent)
			{
				int index = table.convertColumnIndexToModel( table.columnAtPoint( mouseEvent.getPoint() ) );
				if ( index >= 0 )
				{
					int row = table.getSelectedRow();
					tableModel.sortByColumn( index );
					table.clearSelection();
					table.getSelectionModel().setSelectionInterval( row, row );
				}
			};
		} );

		if ( isMac )
			addAppleA();

		addColorMode();

		table.setPreferredScrollableViewportSize( new Dimension( 750, 300 ) );
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 20 );
		table.getColumnModel().getColumn( 1 ).setPreferredWidth( 15 );

		this.setLayout( new BorderLayout() );

		final JButton save = new JButton( "Save" );
		save.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(final ActionEvent e)
			{
				if ( save.isEnabled() )
					saveXML();
			}
		} );

		final JButton info = new JButton( "Info" );
		info.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(final ActionEvent e)
			{
				if ( info.isEnabled() )
					showInfoBox();
			}
		} );

		final JPanel buttons = new JPanel( new BorderLayout() );
		buttons.add( info, BorderLayout.WEST );
		buttons.add( save, BorderLayout.EAST );

		final JPanel header = new JPanel( new BorderLayout() );
		header.add( new JLabel( "XML: " + xml ), BorderLayout.WEST );

		header.add( buttons, BorderLayout.EAST );
		this.add( header, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );

		final JPanel footer = new JPanel();
		footer.setLayout( new BoxLayout( footer, BoxLayout.LINE_AXIS ) );

		// All instances of Entities in SpimData with "own local coordinate
		// system"
		Vector< ? > vAngle = new Vector< >( getEntityNamesOrIds(
				SpimDataTools.getInstancesOfAttribute( getSpimData().getSequenceDescription(), Angle.class ) ) );
		Vector< ? > vTimepoint = new Vector< >( getEntityNamesOrIds(
				SpimDataTools.getInstancesOfAttribute( getSpimData().getSequenceDescription(), TimePoint.class ) ) );

		// TimePoint ComboBox
		final JComboBox< ? > timePointCB = new JComboBox< >( vTimepoint );
		timePointCB.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				updateFilter( TimePoint.class,
						(TimePoint) getInstanceFromNameOrId( getSpimData().getSequenceDescription(), TimePoint.class,
								(String) timePointCB.getSelectedItem() ) );
			}
		} );
		if ( vTimepoint.size() == 1 )
			timePointCB.setEnabled( false );
		else
			updateFilter( TimePoint.class, (TimePoint) getInstanceFromNameOrId( getSpimData().getSequenceDescription(),
					TimePoint.class, (String) timePointCB.getSelectedItem() ) );

		// Angle ComboBox
		angleCB = new JComboBox< >( vAngle );
		angleCB.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				updateFilter( Angle.class, (Angle) getInstanceFromNameOrId( getSpimData().getSequenceDescription(),
						Angle.class, (String) angleCB.getSelectedItem() ) );
			}
		} );
		if ( vAngle.size() == 1 )
			angleCB.setEnabled( false );
		else
			updateFilter( Angle.class, (Angle) getInstanceFromNameOrId( getSpimData().getSequenceDescription(),
					Angle.class, (String) angleCB.getSelectedItem() ) );

		final JPanel footer_tp = new JPanel();
		footer_tp.setLayout( new BoxLayout( footer_tp, BoxLayout.LINE_AXIS ) );
		footer_tp.add( new JLabel( "Timepoint:" ) );
		footer_tp.add( timePointCB );

		final JPanel footer_angle = new JPanel();
		footer_angle.setLayout( new BoxLayout( footer_angle, BoxLayout.LINE_AXIS ) );
		footer_angle.add( new JLabel( "Angle:" ) );
		footer_angle.add( angleCB );

		// checkbox to toggle channel grouping
		final JPanel footerGroupChannels = new JPanel();
		footerGroupChannels.setLayout( new BoxLayout( footerGroupChannels, BoxLayout.LINE_AXIS ) );
		footerGroupChannels.add( new JLabel( "Group Channels:" ) );
		this.checkboxGroupChannels = new JCheckBox( "", true );
		checkboxGroupChannels.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				HashSet< Class< ? extends Entity > > groupingOld = new HashSet< >( tableModel.getGroupingFactors() );
				if ( checkboxGroupChannels.isSelected() )
					groupingOld.add( Channel.class );
				else
					groupingOld.remove( Channel.class );
				tableModel.clearGroupingFactors();

				for ( Class< ? extends Entity > c : groupingOld )
					tableModel.addGroupingFactor( c );

			}
		} );
		footerGroupChannels.add( checkboxGroupChannels );
		footerGroupChannels.setAlignmentX( RIGHT_ALIGNMENT );

		// checkbox to toggle illumination grouping
		final JPanel footerGroupIllums = new JPanel();
		footerGroupIllums.setLayout( new BoxLayout( footerGroupIllums, BoxLayout.LINE_AXIS ) );
		footerGroupIllums.add( new JLabel( "Group Illuminations:" ) );
		this.checkboxGroupIllums = new JCheckBox( "", true );
		checkboxGroupIllums.addActionListener( new ActionListener()
		{

			@Override
			public void actionPerformed(ActionEvent e)
			{
				HashSet< Class< ? extends Entity > > groupingOld = new HashSet< >( tableModel.getGroupingFactors() );
				if ( checkboxGroupIllums.isSelected() )
					groupingOld.add( Illumination.class );
				else
					groupingOld.remove( Illumination.class );
				tableModel.clearGroupingFactors();

				for ( Class< ? extends Entity > c : groupingOld )
					tableModel.addGroupingFactor( c );

			}
		} );
		footerGroupIllums.add( checkboxGroupIllums );
		footerGroupIllums.setAlignmentX( RIGHT_ALIGNMENT );

		final JPanel footerComboboxes = new JPanel();
		footerComboboxes.setLayout( new BoxLayout( footerComboboxes, BoxLayout.PAGE_AXIS ) );

		final JPanel footerCheckboxes = new JPanel();
		footerCheckboxes.setLayout( new BoxLayout( footerCheckboxes, BoxLayout.PAGE_AXIS ) );

		footerComboboxes.add( footer_tp );
		footerComboboxes.add( footer_angle );

		footerCheckboxes.add( footerGroupChannels );
		footerCheckboxes.add( footerGroupIllums );

		footer.add( footerComboboxes );
		footer.add( footerCheckboxes );
		footer.setBorder( BorderFactory.createEmptyBorder( 0, 10, 0, 10 ) );

		// footer.add( footer_tp, BorderLayout.NORTH );
		// footer.add( footer_angle, BorderLayout.SOUTH );
		// footer.add( illumCB, BorderLayout.WEST );

		this.add( footer, BorderLayout.SOUTH );

		table.getSelectionModel().setSelectionInterval( 0, 0 );

		addPopupMenu( table );
	}

	@Override
	protected ListSelectionListener getSelectionListener()
	{
		return new ListSelectionListener()
		{
			int lastRow = -1;

			@Override
			public void valueChanged(final ListSelectionEvent arg0)
			{
				BDVPopup b = bdvPopup();

				selectedRows.clear();
				firstSelectedVD = null;
				boolean foundFirstView = false;
				for ( final int row : table.getSelectedRows() )
				{
					if (!foundFirstView)
					for (int i = 0; i < tableModel.getElements().get( row ).size(); i++)
						if ( firstSelectedVD == null || !firstSelectedVD.isPresent())
						{
							foundFirstView = true;
							firstSelectedVD = tableModel.getElements().get( row ).get( i );
							break;
						}
							

					// FIXME: some generics fixes necessary to avoid this ugly cast (which is necessary for maven to compile)
					selectedRows.add( (List<BasicViewDescription< ? extends BasicViewSetup >>) (Object) tableModel.getElements().get( row ) );
				}

				List< List< BasicViewDescription< ? extends BasicViewSetup > > > selectedList = new ArrayList< >();
				for ( List< BasicViewDescription< ? extends BasicViewSetup > > selectedI : selectedRows )
					selectedList.add( selectedI );

				for ( int i = 0; i < listeners.size(); ++i )
					listeners.get( i ).selectedViewDescriptions( selectedList );

				/*
				 * 
				 * if ( table.getSelectedRowCount() != 1 ) { lastRow = -1;
				 * 
				 * for ( int i = 0; i < listeners.size(); ++i ) listeners.get( i
				 * ).firstSelectedViewDescriptions( null );
				 * 
				 * selectedRows.clear();
				 * 
				 * firstSelectedVD = null; for ( final int row :
				 * table.getSelectedRows() ) { if ( firstSelectedVD == null ) //
				 * TODO: is this okay? only adding first vd of // potentially
				 * multiple per row firstSelectedVD =
				 * tableModel.getElements().get( row ).get( 0 );
				 * 
				 * selectedRows.add( tableModel.getElements().get( row ) ); }
				 * 
				 * } else { final int row = table.getSelectedRow();
				 * 
				 * if ( ( row != lastRow ) && row >= 0 && row <
				 * tableModel.getRowCount() ) { lastRow = row;
				 * 
				 * // not using an iterator allows that listeners can close //
				 * the frame and remove all listeners while they are // called
				 * final List< BasicViewDescription< ? extends BasicViewSetup >
				 * > vds = tableModel.getElements() .get( row );
				 * 
				 * for ( int i = 0; i < listeners.size(); ++i ) listeners.get( i
				 * ).firstSelectedViewDescriptions( null );
				 * 
				 * selectedRows.clear(); selectedRows.add( vds );
				 * 
				 * firstSelectedVD = vds.get( 0 ); } }
				 */

				if ( b != null && b.bdv != null )
				{
					if ( !previewMode )
						updateBDV( b.bdv, colorMode, data, firstSelectedVD, selectedRows );
					else
						updateBDVPreviewMode();
				}

			}

		};
	}

	private void initLinkExplorer()
	{
		// we already should have an instance
		if ( linkExplorer != null )
			return;

		linkExplorer = new LinkExplorerPanel( stitchingResults, (StitchingExplorerPanel< AbstractSpimData< ? >, ? >) this );
		// init the LinkExplorer
		linkFrame = new JFrame( "Link Explorer" );
		linkFrame.add( linkExplorer, BorderLayout.CENTER );
		linkFrame.setSize( linkExplorer.getPreferredSize() );

		linkFrame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent evt)
			{
				togglePreviewMode();
			}
		} );

		linkFrame.pack();
		linkFrame.setVisible( true );
		linkFrame.requestFocus();

		MultiWindowLayoutHelper.moveToScreenFraction( linkFrame, xPosLinkExplorer, yPosLinkExplorer );
	}

	LinkExplorerPanel getLinkExplorer()
	{
		if ( linkExplorer == null )
			initLinkExplorer();
		return linkExplorer;
	}

	public ArrayList< ExplorerWindowSetable > initPopups()
	{
		final ArrayList< ExplorerWindowSetable > popups = new ArrayList< ExplorerWindowSetable >();

		popups.add( new LabelPopUp( " Displaying" ) );
		popups.add( new BDVPopupStitching( linkOverlay ) );
		popups.add( new DisplayRawImagesPopup() );
		popups.add( new DisplayFusedImagesPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Preprocessing" ) );
		popups.add( new TranslateGroupManuallyPopup() );
		regularGridPopup = new RegularGridPopup();
		popups.add( regularGridPopup );
		popups.add( new SelectIlluminationPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Stitching Wizard" ) );
		CalculatePCPopup calculatePCPopup = new CalculatePCPopup("Stitch dataset ...", true, Method.PHASECORRELATION, true);
		calculatePCPopup.setStitchingResults( stitchingResults );
		popups.add( calculatePCPopup );

		CalculatePCPopupExpertBatch calculatePCPopupExpert = new CalculatePCPopupExpertBatch("Stitch dataset (expert) ...", true);
		calculatePCPopupExpert.setStitchingResults( stitchingResults );
		popups.add( calculatePCPopupExpert );

		OptimizeGloballyPopup optimizePopup = new OptimizeGloballyPopup();
		optimizePopup.setStitchingResults( stitchingResults );
		popups.add( optimizePopup );
		
		OptimizeGloballyPopupExpertBatch optimizePopupExpert = new OptimizeGloballyPopupExpertBatch();
		optimizePopupExpert.setStitchingResults( stitchingResults );
		popups.add( optimizePopupExpert );

		SimpleRemoveLinkPopup removeLinkPopup = new SimpleRemoveLinkPopup();
		removeLinkPopup.setStitchingResults( stitchingResults );
		popups.add( removeLinkPopup );
		
		//DemoLinkOverlayPopup dlPopup = new DemoLinkOverlayPopup(demoLinkOverlay);
		//popups.add( dlPopup );

		popups.add( new TogglePreviewPopup() );
		popups.add( new PairwiseInterestPointRegistrationPopup() );
		popups.add( new DetectInterestPointsPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( "Step-by-step Stitching" ) );
		CalculatePCPopupExpertBatch calculatePCPopupExpertStepByStep = new CalculatePCPopupExpertBatch("Calculate Pairwise Shifts ...", false);
		calculatePCPopupExpertStepByStep.setStitchingResults( stitchingResults );
		popups.add( calculatePCPopupExpertStepByStep );
		popups.add( new Separator() );
		
		popups.add( new LabelPopUp( "Fusion" ) );
		popups.add( new BoundingBoxPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Calibration/Transformations" ) );
		popups.add( new RemoveTransformationPopup() );
		popups.add( new ApplyBDVTransformationPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Modifications" ) );
		popups.add( new ResavePopup() );

		return popups;
	}

	public void updateBDVPreviewMode()
	{
		// BDV is not open, nothing to do
		if (!bdvPopup().bdvRunning())
			return;

		// we always set the fused mode
		setFusedModeSimple( bdvPopup().bdv, data );

		// first, re-color sources (we might have set one or two of them to green/magenta)
		if (!colorMode)
			BDVPopupStitching.colorByChannels( bdvPopup().bdv, data, colorOffset );
		else
			colorSources( bdvPopup().bdv.getSetupAssignments().getConverterSetups(), colorOffset );

		if ( selectedRowsGroups().size() < 1 )
			return;

		// in Preview Mode, only one row should be selected
		List< BasicViewDescription< ? extends BasicViewSetup > > selectedRow = selectedRowsGroups().iterator().next();
		BasicViewDescription< ? extends BasicViewSetup > firstVD = firstSelectedVD;

		if ( selectedRow == null || selectedRow.size() == 0 )
			return;

		if ( firstVD == null )
			firstVD = selectedRow.get( 0 );

		final Pair< Group< ViewId >, Group< ViewId > > selectedPair = linkExplorer.getSelectedPair();
		// we have a pair selected, hide all other views
		if (selectedPair != null)
			for (final ConverterSetup cs : bdvPopup().bdv.getSetupAssignments().getConverterSetups())
				cs.setColor(new ARGBType(ARGBType.rgba( 0, 0, 0, 0) ) );
		
		// always use the first timepoint
		final TimePoint firstTP = firstVD.getTimePoint();
		bdvPopup().bdv.getViewer().setTimepoint( getBDVTimePointIndex( firstTP, data ) );

		// get all of the rows
		List< List< BasicViewDescription< ? > > > elements = tableModel.getElements();

		// get all pairwise results which involve the views of the selected row
		Set< ViewId > selectedVids = new HashSet< >( selectedRow );
		SpimData2.filterMissingViews( data, selectedVids );
		List< PairwiseStitchingResult< ViewId > > resultsForId = stitchingResults
				.getAllPairwiseResultsForViewId( selectedVids );
		
		// if links have been filtered out, do not display them
		if (linkExplorer != null)
			resultsForId = resultsForId.stream().filter( r -> linkExplorer.model.getActiveLinks().contains( r.pair() ) ).collect( Collectors.toList() );

		// get the Translations of first View
		ViewRegistration selectedVr = data.getViewRegistrations().getViewRegistration( selectedVids.iterator().next() );
		AffineGet selectedModel = selectedVr.getModel();

		final boolean[] active = new boolean[data.getSequenceDescription().getViewSetupsOrdered().size()];

		// set all views of the selected row visible
		for ( final BasicViewDescription< ? extends BasicViewSetup > vd : selectedRow )
			if ( vd.getTimePointId() == firstTP.getId() )
				active[getBDVSourceIndex( vd.getViewSetup(), data )] = true;

		resetBDVManualTransformations( bdvPopup().bdv );

		List< Pair< Group< ViewId >, Group< ViewId > > > activeLinks = new ArrayList< >();

		
		for ( PairwiseStitchingResult< ViewId > psr : resultsForId )
		{
			activeLinks.add( psr.pair() );

			// if we have no BDV open, continue adding active Links, but do not
			// update BDV obviously
			// TODO: un-tangle this method, BDV update and active link
			// determination should be separate
			if ( bdvPopup().bdv == null )
				continue;

			for ( List< BasicViewDescription< ? > > group : elements )
			{
				List< BasicViewDescription< ? > > groupInner = new ArrayList<>(group);
				SpimData2.filterMissingViews( data, groupInner );
				// there is a link selected -> other
				if ( psr.pair().getA().getViews().equals( selectedVids ) && psr.pair().getB().getViews().containsAll( groupInner ) )
				{
					if (psr.pair().equals( selectedPair ))
					{
						for (ViewId vid : psr.pair().getA())
							for (final ConverterSetup cs : bdvPopup().bdv.getSetupAssignments().getConverterSetups())
								if (cs.getSetupId() == vid.getViewSetupId())
									cs.setColor(new ARGBType(ARGBType.rgba( 0, 255, 0, 255) ) );
						for (ViewId vid : psr.pair().getB())
							for (final ConverterSetup cs : bdvPopup().bdv.getSetupAssignments().getConverterSetups())
								if (cs.getSetupId() == vid.getViewSetupId())
									cs.setColor(new ARGBType(ARGBType.rgba( 255, 0, 255, 255) ) );
					}
					
					for ( final BasicViewDescription< ? > vd : groupInner )
						if ( vd.getTimePointId() == firstTP.getId() )
						{

							// set all views of the other group visible
							int sourceIdx = getBDVSourceIndex( vd.getViewSetup(), data );
							SourceState< ? > s = bdvPopup().bdv.getViewer().getVisibilityAndGrouping().getSources()
									.get( sourceIdx );
							active[sourceIdx] = true;
 
							// accumulative transform determined by stitching
							AffineTransform3D trans = new AffineTransform3D();
							trans.set( psr.getTransform().getRowPackedCopy() );
							trans.concatenate( selectedModel.inverse() );
							trans.preConcatenate( selectedModel );

							( (TransformedSource< ? >) s.getSpimSource() ).setFixedTransform( trans );
						}

				}
				

				// there is a link other -> selected
				if ( psr.pair().getB().getViews().equals( selectedVids ) && psr.pair().getA().getViews().containsAll( groupInner ) )
				{
					if (psr.pair().equals( selectedPair ))
					{
						for (ViewId vid : psr.pair().getB())
							for (final ConverterSetup cs : bdvPopup().bdv.getSetupAssignments().getConverterSetups())
								if (cs.getSetupId() == vid.getViewSetupId())
									cs.setColor(new ARGBType(ARGBType.rgba( 0, 255, 0, 255) ) );
						for (ViewId vid : psr.pair().getA())
							for (final ConverterSetup cs : bdvPopup().bdv.getSetupAssignments().getConverterSetups())
								if (cs.getSetupId() == vid.getViewSetupId())
									cs.setColor(new ARGBType(ARGBType.rgba( 255, 0, 255, 255) ) );
					}
					
					for ( final BasicViewDescription< ? > vd : groupInner )
						if ( vd.getTimePointId() == firstTP.getId() )
						{
							// set all views of the other group visible
							int sourceIdx = getBDVSourceIndex( vd.getViewSetup(), data );
							SourceState< ? > s = bdvPopup().bdv.getViewer().getVisibilityAndGrouping().getSources()
									.get( sourceIdx );
							active[sourceIdx] = true;

							// accumulative transform determined by stitching
							AffineTransform3D trans = new AffineTransform3D();
							trans.set( psr.getInverseTransform().getRowPackedCopy() );
							
							trans.concatenate( selectedModel.inverse() );
							trans.preConcatenate( selectedModel );

							if (psr.pair().equals( selectedPair ))
							{
								for (final ConverterSetup cs : bdvPopup().bdv.getSetupAssignments().getConverterSetups())
									if (cs.getSetupId() == vd.getViewSetupId())
										cs.setColor(new ARGBType(ARGBType.rgba( 255, 0, 255, 255) ) );
							}

							( (TransformedSource< ? >) s.getSpimSource() ).setFixedTransform( trans );

						}

				}
			}
		}

		linkOverlay.setActiveLinks( activeLinks, new Group<ViewId>( selectedVids ));
		

		setVisibleSources( bdvPopup().bdv.getViewer().getVisibilityAndGrouping(), active );

		if ( bdvPopup().bdv != null )
			bdvPopup().bdv.getViewer().requestRepaint();

	}

	public static void setFusedModeSimple(final BigDataViewer bdv, final AbstractSpimData< ? > data)
	{
		if ( bdv == null )
			return;

		if ( bdv.getViewer().getVisibilityAndGrouping().getDisplayMode() != DisplayMode.FUSED )
		{
			final boolean[] active = new boolean[data.getSequenceDescription().getViewSetupsOrdered().size()];
			active[0] = true;
			setVisibleSources( bdv.getViewer().getVisibilityAndGrouping(), active );
			bdv.getViewer().getVisibilityAndGrouping().setDisplayMode( DisplayMode.FUSED );
		}
	}

	public static void colorSources(final List< ConverterSetup > cs, final long j)
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ColorStream.get( i + j ) ) );
	}

	public static void whiteSources(final List< ConverterSetup > cs)
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ARGBType.rgba( 255, 255, 255, 0 ) ) );
	}

	public static void setVisibleSources(final VisibilityAndGrouping vag, final boolean[] active)
	{
		for ( int i = 0; i < active.length; ++i )
			vag.setSourceActive( i, active[i] );
	}

	public static int getBDVTimePointIndex(final TimePoint t, final AbstractSpimData< ? > data)
	{
		final List< TimePoint > list = data.getSequenceDescription().getTimePoints().getTimePointsOrdered();

		for ( int i = 0; i < list.size(); ++i )
			if ( list.get( i ).getId() == t.getId() )
				return i;

		return 0;
	}

	public static int getBDVSourceIndex(final BasicViewSetup vs, final AbstractSpimData< ? > data)
	{
		final List< ? extends BasicViewSetup > list = data.getSequenceDescription().getViewSetupsOrdered();

		for ( int i = 0; i < list.size(); ++i )
			if ( list.get( i ).getId() == vs.getId() )
				return i;

		return 0;
	}

	public void showInfoBox()
	{
		new ViewSetupExplorerInfoBox< AS >( data, xml );
	}

	@Override
	public void saveXML()
	{
		try
		{
			io.save( data, xml );

			for ( final SelectedViewDescriptionListener< AS > l : listeners )
				l.save();

			if ( SpimData2.class.isInstance( data ) )
			{
				final ViewInterestPoints vip = ( (SpimData2) data ).getViewInterestPoints();

				for ( final ViewInterestPointLists vipl : vip.getViewInterestPoints().values() )
				{
					for ( final String label : vipl.getHashMap().keySet() )
					{
						final InterestPointList ipl = vipl.getInterestPointList( label );
						ipl.saveInterestPoints( false );
						ipl.saveCorrespondingInterestPoints( false );
					}
				}
			}

			IOFunctions.println( "Saved XML '" + xml + "'." );
		}
		catch ( SpimDataException e )
		{
			IOFunctions.println( "Failed to save XML '" + xml + "': " + e );
			e.printStackTrace();
		}
	}

	protected void addColorMode()
	{
		table.addKeyListener( new KeyListener()
		{
			@Override
			public void keyPressed(final KeyEvent arg0)
			{
				if ( arg0.getKeyChar() == 'c' || arg0.getKeyChar() == 'C' )
				{
					colorMode = !colorMode;

					System.out.println( "colormode" );

					final BDVPopup p = bdvPopup();
					if ( p != null && p.bdv != null && p.bdv.getViewerFrame().isVisible() )
					{
						updateBDV( p.bdv, colorMode, data, null, selectedRows );

						if (previewMode)
							updateBDVPreviewMode();

						if (!colorMode)
							BDVPopupStitching.colorByChannels( p.bdv, data, colorOffset );
						else
						{
							// cycle between color schemes
							colorOffset = (colorOffset + 1) % 5;
							colorSources( p.bdv.getSetupAssignments().getConverterSetups(), colorOffset );
						}
					}
				}
			}

			@Override
			public void keyReleased(final KeyEvent arg0)
			{
			}

			@Override
			public void keyTyped(final KeyEvent arg0)
			{
			}
		} );
	}

	protected void addAppleA()
	{
		table.addKeyListener( new KeyListener()
		{
			boolean appleKeyDown = false;

			@Override
			public void keyTyped(KeyEvent arg0)
			{
				if ( appleKeyDown && arg0.getKeyChar() == 'a' )
					table.selectAll();
			}

			@Override
			public void keyReleased(KeyEvent arg0)
			{
				if ( arg0.getKeyCode() == 157 )
					appleKeyDown = false;
			}

			@Override
			public void keyPressed(KeyEvent arg0)
			{
				if ( arg0.getKeyCode() == 157 )
					appleKeyDown = true;
			}
		} );
	}

	public SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > getSavedFilteringAndGrouping()
	{
		return savedFilteringAndGrouping;
	}

	public void setSavedFilteringAndGrouping(SpimDataFilteringAndGrouping< ? extends AbstractSpimData< ? > > savedFilteringAndGrouping)
	{
		this.savedFilteringAndGrouping = savedFilteringAndGrouping;
		table.repaint();
	}

}
