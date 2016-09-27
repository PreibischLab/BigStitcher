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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import algorithm.SpimDataTools;
import algorithm.globalopt.GroupedViews;

import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;

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
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.ViewSetupExplorerInfoBox;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.BoundingBoxPopup;
import spim.fiji.spimdata.explorer.popup.DisplayViewPopup;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.explorer.popup.LabelPopUp;
import gui.overlay.LinkOverlay;
import gui.popup.ApplyBDVTransformationPopup;
import gui.popup.BDVPopupStitching;
import gui.popup.CalculatePCPopup;
import gui.popup.OptimizeGloballyPopup;
import gui.popup.ResavePopup;
import gui.popup.SimpleRemoveLinkPopup;
import gui.popup.TestPopup;
import gui.popup.TogglePreviewPopup;
import input.FractalImgLoader;
import input.GenerateSpimData;
import spim.fiji.spimdata.explorer.popup.Separator;
import spim.fiji.spimdata.explorer.util.ColorStream;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvHandleFrame;
import bdv.util.BdvHandlePanel;
import bdv.viewer.DisplayMode;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceState;

public class FilteredAndGroupedExplorerPanel<AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS >>
		extends JPanel implements ExplorerWindow< AS, X >, GroupedRowWindow
{
	public static FilteredAndGroupedExplorerPanel< ?, ? > currentInstance = null;

	final ArrayList< ExplorerWindowSetable > popups;

	static
	{
		IOFunctions.printIJLog = true;
	}

	// indicates whether we are in link preview mode or not
	boolean previewMode = false;
	
	private static final long serialVersionUID = -3767947754096099774L;

	protected JTable table;
	protected ISpimDataTableModel< AS > tableModel;
	protected ArrayList< SelectedViewDescriptionListener< AS > > listeners;
	protected AS data;
	protected FilteredAndGroupedExplorer< AS, X > explorer;
	final String xml;
	final X io;
	final boolean isMac;
	protected boolean colorMode = true;
	LinkOverlay linkOverlay;
	
	StitchingResults stitchingResults;
	
	LinkExplorerPanel linkExplorer;
	JFrame linkFrame;

	final protected HashSet< List<BasicViewDescription< ? extends BasicViewSetup >> > selectedRows;
	protected BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD;

	public FilteredAndGroupedExplorerPanel(final FilteredAndGroupedExplorer< AS, X > explorer, final AS data,
			final String xml, final X io)
	{
		
		if (data instanceof SpimData2)
			this.stitchingResults = ( (SpimData2) data ).getStitchingResults();
		else
			this.stitchingResults = new StitchingResults();
		
		this.explorer = explorer;
		this.listeners = new ArrayList< SelectedViewDescriptionListener< AS > >();
		this.data = data;
		this.xml = xml == null ? "" : xml.replace( "\\", "/" ).replace( "//", "/" ).replace( "/./", "/" );
		this.io = io;
		this.isMac = System.getProperty( "os.name" ).toLowerCase().contains( "mac" );
		this.selectedRows = new HashSet<>();
		this.firstSelectedVD = null;

		linkOverlay = new LinkOverlay( stitchingResults, data );
		popups = initPopups();
		initComponent();

		if ( Hdf5ImageLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) ||
			FractalImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
		{
			bdvPopup().bdv = BDVPopupStitching.createBDV( this, linkOverlay );
		}

		// for access to the current BDV
		currentInstance = this;
	}

	@Override
	public BDVPopup bdvPopup()
	{
		for ( final ExplorerWindowSetable s : popups )
			if ( BDVPopup.class.isInstance( s ) )
				return ( (BDVPopup) s );

		return null;
	}

	@Override
	public boolean colorMode()
	{
		return colorMode;
	}

	@Override
	public BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD()
	{
		return firstSelectedVD;
	}

	public ISpimDataTableModel< AS > getTableModel()
	{
		return tableModel;
	}

	@Override
	public AS getSpimData()
	{
		return data;
	}

	@Override
	public String xml()
	{
		return xml;
	}

	public X io()
	{
		return io;
	}

	public FilteredAndGroupedExplorer< AS, X > explorer()
	{
		return explorer;
	}

	@SuppressWarnings("unchecked")
	public void setSpimData(final Object data)
	{
		this.data = (AS) data;
	}

	@Override
	public void updateContent()
	{
		// this.getTableModel().fireTableDataChanged();
		for ( final SelectedViewDescriptionListener< AS > l : listeners )
			l.updateContent( this.data );
	}

	@Override
	public List< BasicViewDescription< ? extends BasicViewSetup > > selectedRows()
	{
		// TODO: this will break the grouping of selected Views -> change interface???
		final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > list = new ArrayList< BasicViewDescription< ? extends BasicViewSetup > >();
		for (List<BasicViewDescription< ? >> vds : selectedRows)
			list.addAll( vds );
		Collections.sort( list );
		return list;
	}

	@Override
	public List< ViewId > selectedRowsViewId()
	{
		// TODO: adding Grouped Views here, not all selected ViewIds individually
		final ArrayList< ViewId > list = new ArrayList< ViewId >();
		for (List<BasicViewDescription< ? >> vds : selectedRows)
		{
			ArrayList< ViewId > vids = new ArrayList<>();
			vids.addAll( vds );
			list.add( new GroupedViews( vids ));
		}
		Collections.sort( list );
		return list;
	}
	
	public void togglePreviewMode()
	{
		previewMode = !previewMode;
		linkOverlay.isActive = previewMode;
		
		if(previewMode)
		{
			initLinkExplorer();
			table.setSelectionMode( ListSelectionModel.SINGLE_SELECTION );
			updateBDVPreviewMode();
		}
		else
		{
			quitLinkExplorer();
			linkOverlay.clearActiveLinks();
			table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			updateBDV( bdvPopup().bdv, colorMode, data, firstSelectedVD, selectedRows);
		}
	}

	public void addListener(final SelectedViewDescriptionListener< AS > listener)
	{
		this.listeners.add( listener );

		// TODO: does this break anything?
		// update it with the currently selected row
		// if ( table.getSelectedRow() != -1 )
		// listener.seletedViewDescription( tableModel.getElements().get(
		// table.getSelectedRow() ) );
	}

	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners()
	{
		return listeners;
	}

	public void initComponent()
	{
		// only do that if needed
		//initLinkExplorer();
		
		tableModel = new FilteredAndGroupedTableModel< AS >( this );
		tableModel = new StitchingTableModelDecorator< >( tableModel );
		((StitchingTableModelDecorator< AS >)tableModel).setStitchingResults( stitchingResults );

		tableModel.addGroupingFactor( Channel.class );
		tableModel.addGroupingFactor( Illumination.class );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );

		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column ){
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );
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

		final JPanel footer = new JPanel( new BorderLayout() );

		// All instances of Entities in SpimData with "own local coordinate
		// system"
		Vector< ? > vAngle = new Vector< >(
				SpimDataTools.getInstancesOfAttribute( getSpimData().getSequenceDescription(), Angle.class ) );
		Vector< ? > vTimepoint = new Vector< >(
				SpimDataTools.getInstancesOfAttribute( getSpimData().getSequenceDescription(), TimePoint.class ) );

		// TimePoint ComboBox
		final JComboBox< ? > timePointCB = new JComboBox< >( vTimepoint );
		timePointCB.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				ArrayList< TimePoint > selectedTPs = new ArrayList< >();
				selectedTPs.add( (TimePoint) timePointCB.getSelectedItem() );
				tableModel.addFilter( TimePoint.class, selectedTPs );
			}
		} );
		if (vTimepoint.size() == 1)
			timePointCB.setEnabled( false );
		

		// Angle ComboBox
		final JComboBox< ? > angleCB = new JComboBox< >( vAngle );
		angleCB.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				ArrayList< Angle > selectedAngles = new ArrayList< >();
				selectedAngles.add( (Angle) angleCB.getSelectedItem() );
				tableModel.addFilter( Angle.class, selectedAngles );
			}
		} );
		if (vAngle.size() == 1)
			angleCB.setEnabled( false );

		final JPanel footer_tp = new JPanel( new BorderLayout() );
		footer_tp.add( new JLabel( "Timepoint:" ), BorderLayout.WEST );
		footer_tp.add( timePointCB, BorderLayout.EAST );
		
		final JPanel footer_angle = new JPanel( new BorderLayout() );
		footer_angle.add( new JLabel( "Angle:" ), BorderLayout.WEST );
		footer_angle.add( angleCB, BorderLayout.EAST );
		

		footer.add( footer_tp, BorderLayout.NORTH );
		footer.add( footer_angle, BorderLayout.SOUTH );
		//footer.add( illumCB, BorderLayout.WEST );

		this.add( footer, BorderLayout.SOUTH );

		table.getSelectionModel().setSelectionInterval( 0, 0 );

		addPopupMenu( table );
	}

	private void initLinkExplorer()
	{
		// we already should have an instance
		if (linkExplorer != null)
			return;
			
		linkExplorer = new LinkExplorerPanel( stitchingResults, this );
		// init the LinkExplorer
		linkFrame = new JFrame( "Link Explorer" );
		linkFrame.add( linkExplorer, BorderLayout.CENTER );
		linkFrame.setSize( linkExplorer.getPreferredSize() );
		
		linkFrame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( WindowEvent evt )
			{
				quitLinkExplorer();
			}
		} );
		
		linkFrame.pack();
		linkFrame.setVisible( true );
		linkFrame.requestFocus();
	}

	protected ListSelectionListener getSelectionListener()
	{
		return new ListSelectionListener()
		{
			int lastRow = -1;

			@Override
			public void valueChanged(final ListSelectionEvent arg0)
			{
				BDVPopup b = bdvPopup();

				if ( table.getSelectedRowCount() != 1 )
				{
					lastRow = -1;

					for ( int i = 0; i < listeners.size(); ++i )
						listeners.get( i ).seletedViewDescription( null );

					selectedRows.clear();

					firstSelectedVD = null;
					for ( final int row : table.getSelectedRows() )
					{
						if ( firstSelectedVD == null )
							// TODO: is this okay? only adding first vd of
							// potentially multiple per row
							firstSelectedVD = tableModel.getElements().get( row ).get( 0 );

						selectedRows.add( tableModel.getElements().get( row ) );
					}

				}
				else
				{
					final int row = table.getSelectedRow();

					if ( ( row != lastRow ) && row >= 0 && row < tableModel.getRowCount() )
					{
						lastRow = row;

						// not using an iterator allows that listeners can close
						// the frame and remove all listeners while they are
						// called
						final List< BasicViewDescription< ? extends BasicViewSetup > > vds = tableModel.getElements()
								.get( row );

						for ( int i = 0; i < listeners.size(); ++i )
							listeners.get( i ).seletedViewDescription( null );

						selectedRows.clear();
						selectedRows.add( vds );

						firstSelectedVD = vds.get( 0 );
					}
				}

				if ( b != null && b.bdv != null )
				{	
					if (!previewMode)
						updateBDV( b.bdv, colorMode, data, firstSelectedVD, selectedRows);
					else
						updateBDVPreviewMode();
				}
					
				
			}
		};
	}

	public void updateBDVPreviewMode()
	{
		
		// we always set the fused mode
		setFusedModeSimple( bdvPopup().bdv, data );
		
		if (selectedRowsGroups().size() < 1)
			return;
		
		// in Preview Mode, only one row should be selected
		List<BasicViewDescription< ? extends BasicViewSetup >> selectedRow = selectedRowsGroups().iterator().next();
		BasicViewDescription< ? extends BasicViewSetup > firstVD = firstSelectedVD;
		
		//System.out.println( selectedRow );
		
		if ( selectedRow == null || selectedRow.size() == 0 )
			return;

		if ( firstVD == null )
			firstVD = selectedRow.get( 0 );
		
		// always use the first timepoint
		final TimePoint firstTP = firstVD.getTimePoint();
		bdvPopup().bdv.getViewer().setTimepoint( getBDVTimePointIndex( firstTP, data ) );

		// get all of the rows
		List< List< BasicViewDescription< ? > > > elements = tableModel.getElements();
		
		// get all pairwise results which involve the views of the selected row
		ViewId selectedVid = selectedRow.get( 0 );
		ArrayList< PairwiseStitchingResult< ViewId > > resultsForId = stitchingResults.getAllPairwiseResultsForViewId( selectedVid );
		
		// get the Translations of first View
		ViewRegistration selectedVr = data.getViewRegistrations().getViewRegistration(selectedVid);
		AffineGet selectedTranslation = selectedVr.getTransformList().get( 1 ).asAffine3D();
		
		
		final boolean[] active = new boolean[data.getSequenceDescription().getViewSetupsOrdered().size()];

		// set all views of the selected row visible
		for ( final BasicViewDescription< ? extends BasicViewSetup >  vd : selectedRow )
				if ( vd.getTimePointId() == firstTP.getId() )
					active[getBDVSourceIndex( vd.getViewSetup(), data )] = true;
	
		
		resetBDVManualTransformations(bdvPopup().bdv);
		
		ArrayList< Pair<ViewId, ViewId> > activeLinks = new ArrayList<>();
		
		for (PairwiseStitchingResult< ViewId > psr: resultsForId)
		{
			activeLinks.add( psr.pair() );
			
			// if we have no BDV open, continue adding active Links, but do not update BDV obviously
			// TODO: un-tangle this method, BDV update and active link determination should be separate
			if ( bdvPopup().bdv == null )
				continue;
			
			for (List<BasicViewDescription< ? >> group : elements)
			{
				// there is a link selected -> other
				if (psr.pair().getA().equals( selectedVid ) &&  group.contains( psr.pair().getB()))
				{
					// set all views of the other group visible
					for ( final BasicViewDescription< ? >  vd : group )
							if ( vd.getTimePointId() == firstTP.getId() )
							{
								int sourceIdx = getBDVSourceIndex( vd.getViewSetup(), data );
								SourceState<?> s = bdvPopup().bdv.getViewer().getVisibilityAndGrouping().getSources().get( sourceIdx );
								active[sourceIdx] = true;
															
								// get the Translations of second View
								ViewRegistration vr = data.getViewRegistrations().getViewRegistration( new ViewId(firstTP.getId(), sourceIdx ));
								AffineGet otherTranslation = vr.getTransformList().get( 1 ).asAffine3D();
																
								// use BDV manual transform to preview 								
								double viewShiftX = selectedTranslation.get( 0, 3 ) - otherTranslation.get( 0, 3 );
								double viewShiftY = selectedTranslation.get( 1, 3 ) - otherTranslation.get( 1, 3 );
								double viewShiftZ = selectedTranslation.get( 2, 3 ) - otherTranslation.get( 2, 3 );
								
								AffineTransform3D shift = new AffineTransform3D();
								shift.set( viewShiftX + psr.relativeVector()[0], 0, 3 );
								shift.set( viewShiftY + psr.relativeVector()[1], 1, 3 );
								shift.set( viewShiftZ + psr.relativeVector()[2], 2, 3 );							
								
								((TransformedSource< ? >)s.getSpimSource()).setFixedTransform( shift );
							}
					
				}
				
				// there is a link other -> selected
				if (psr.pair().getB().equals( selectedVid ) && group.contains( psr.pair().getA()))
				{
					// set all views of the other group visible
					for ( final BasicViewDescription< ? >  vd : group )
							if ( vd.getTimePointId() == firstTP.getId() )
							{
								int sourceIdx = getBDVSourceIndex( vd.getViewSetup(), data );
								SourceState<?> s = bdvPopup().bdv.getViewer().getVisibilityAndGrouping().getSources().get( sourceIdx );
								active[sourceIdx] = true;
								
								// get the Translations of second View
								ViewRegistration vr = data.getViewRegistrations().getViewRegistration( new ViewId(firstTP.getId(), sourceIdx ));
								AffineGet otherTranslation = vr.getTransformList().get( 1 ).asAffine3D();
																
								// use BDV manual transform to preview 								
								double viewShiftX = selectedTranslation.get( 0, 3 ) - otherTranslation.get( 0, 3 );
								double viewShiftY = selectedTranslation.get( 1, 3 ) - otherTranslation.get( 1, 3 );
								double viewShiftZ = selectedTranslation.get( 2, 3 ) - otherTranslation.get( 2, 3 );
								
								AffineTransform3D shift = new AffineTransform3D();
								shift.set( viewShiftX - psr.relativeVector()[0], 0, 3 );
								shift.set( viewShiftY - psr.relativeVector()[1], 1, 3 );
								shift.set( viewShiftZ - psr.relativeVector()[2], 2, 3 );							
								
								((TransformedSource< ? >)s.getSpimSource()).setFixedTransform( shift );
								
							}
								

				}
			}
		}
		
		linkOverlay.setActiveLinks( activeLinks, selectedVid );
		getLinkExplorer().setActiveLinks( activeLinks );
		
		setVisibleSources( bdvPopup().bdv.getViewer().getVisibilityAndGrouping(), active );

		if ( bdvPopup().bdv != null )
			bdvPopup().bdv.getViewer().requestRepaint();
		
	}
	
	
	
	private LinkExplorerPanel getLinkExplorer()
	{
		if (linkExplorer == null)
			initLinkExplorer();
		return linkExplorer;
	}
	
	private void quitLinkExplorer()
	{
		linkOverlay.setSelectedLink( null );
		if (linkFrame != null)
		{
			linkFrame.setVisible( false );
			linkFrame.dispose();
			linkFrame = null;
			linkExplorer = null;
		}
	}

	public static void resetBDVManualTransformations(BigDataViewer bdv)
	{
		if ( bdv == null )
			return;
		
		// reset manual transform for all views
		for (int sourceIdx = 0; sourceIdx <bdv.getViewer().getVisibilityAndGrouping().getSources().size(); sourceIdx++)
		{
			SourceState<?> s = bdv.getViewer().getVisibilityAndGrouping().getSources().get( sourceIdx );
			((TransformedSource< ? >)s.getSpimSource()).setFixedTransform( new AffineTransform3D() );
		}
	}
	
	public static void updateBDV(final BigDataViewer bdv, final boolean colorMode, final AbstractSpimData< ? > data,
			BasicViewDescription< ? extends BasicViewSetup > firstVD,
			final Collection< List< BasicViewDescription< ? extends BasicViewSetup >> > selectedRows)
	{
		
		// bdv is not open
		if ( bdv == null )
			return;
		
		// we always set the fused mode
		setFusedModeSimple( bdv, data );
		
		resetBDVManualTransformations( bdv );

		if ( selectedRows == null || selectedRows.size() == 0 )
			return;

		if ( firstVD == null )
			firstVD = selectedRows.iterator().next().get( 0 );

		// always use the first timepoint
		final TimePoint firstTP = firstVD.getTimePoint();
		bdv.getViewer().setTimepoint( getBDVTimePointIndex( firstTP, data ) );

		final boolean[] active = new boolean[data.getSequenceDescription().getViewSetupsOrdered().size()];

		for ( final List< ? extends BasicViewDescription< ? extends BasicViewSetup > > vds : selectedRows )
			for ( BasicViewDescription< ? > vd : vds){
				if ( vd.getTimePointId() == firstTP.getId() )
					active[getBDVSourceIndex( vd.getViewSetup(), data )] = true;
			}

//		if ( selectedRows.size() > 1 && colorMode )
//			colorSources( bdv.getSetupAssignments().getConverterSetups(), data, channelColors);
//		else
//			whiteSources( bdv.getSetupAssignments().getConverterSetups() );

		setVisibleSources( bdv.getViewer().getVisibilityAndGrouping(), active );
		bdv.getViewer().requestRepaint();
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

	public static void colorSources(final List< ConverterSetup > cs, AbstractSpimData< ? > data, Map<Channel, ARGBType> channelColors)
	{
		for ( int i = 0; i < cs.size(); ++i )
		{			
			Channel ch = data.getSequenceDescription().getViewSetups().get(cs.get( i ).getSetupId()).getAttribute( Channel.class );			
			cs.get( i ).setColor( channelColors.get( ch ) );
		}
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

	public HashSet< List<BasicViewDescription< ? extends BasicViewSetup > >> getSelectedRows()
	{
		return selectedRows;
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

						if ( ipl.getInterestPoints() == null )
							ipl.loadInterestPoints();

						ipl.saveInterestPoints();

						if ( ipl.getCorrespondingInterestPoints() == null )
							ipl.loadCorrespondingInterestPoints();

						ipl.saveCorrespondingInterestPoints();
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

	protected void addPopupMenu(final JTable table)
	{
		final JPopupMenu popupMenu = new JPopupMenu();

		for ( final ExplorerWindowSetable item : popups )
			popupMenu.add( item.setExplorerWindow( this ) );

		table.setComponentPopupMenu( popupMenu );
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
						updateBDV( p.bdv, colorMode, data, null, selectedRows);
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

	public ArrayList< ExplorerWindowSetable > initPopups()
	{
		final ArrayList< ExplorerWindowSetable > popups = new ArrayList< ExplorerWindowSetable >();

		popups.add( new LabelPopUp( " Displaying" ) );
		popups.add( new BDVPopupStitching(linkOverlay) );
		popups.add( new DisplayViewPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Processing" ) );
				
		CalculatePCPopup calculatePCPopup = new CalculatePCPopup();
		calculatePCPopup.setStitchingResults( stitchingResults );
		popups.add( calculatePCPopup );
		
		OptimizeGloballyPopup optimizePopup = new OptimizeGloballyPopup();
		optimizePopup.setStitchingResults( stitchingResults );
		popups.add( optimizePopup );
		
		SimpleRemoveLinkPopup removeLinkPopup = new SimpleRemoveLinkPopup();
		removeLinkPopup.setStitchingResults( stitchingResults );
		popups.add( removeLinkPopup );
		
		popups.add( new ApplyBDVTransformationPopup() );
		popups.add( new TogglePreviewPopup() );
		
		
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Calibration/Transformations" ) );
		popups.add( new TestPopup() );
		popups.add( new BoundingBoxPopup() );
		popups.add( new Separator() );
		
		popups.add( new LabelPopUp( " Modifications" ) );
		popups.add( new ResavePopup() );

		return popups;
	}

	@Override
	public Collection< List< BasicViewDescription< ? extends BasicViewSetup > > > selectedRowsGroups()
	{
		return selectedRows;
	}

	@Override
	public List< List< ViewId > > selectedRowsViewIdGroups()
	{
		final ArrayList< List<ViewId >> list = new ArrayList<>();
		for (List<BasicViewDescription< ? >> vds : selectedRows)
		{
			ArrayList< ViewId > vids = new ArrayList<>();
			vids.addAll( vds );
			list.add( vids);
		}
		//Collections.sort( list );
		return list;
	}
}
