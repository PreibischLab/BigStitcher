package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.type.numeric.ARGBType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.ViewSetupExplorerInfoBox;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.DisplayViewPopup;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.explorer.popup.LabelPopUp;
import spim.fiji.spimdata.explorer.popup.MaxProjectPopup;
import spim.fiji.spimdata.explorer.popup.Separator;
import spim.fiji.spimdata.explorer.util.ColorStream;
import spim.fiji.spimdata.interestpoints.InterestPointList;
import spim.fiji.spimdata.interestpoints.ViewInterestPointLists;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import bdv.BigDataViewer;
import bdv.img.hdf5.Hdf5ImageLoader;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.DisplayMode;
import bdv.viewer.VisibilityAndGrouping;

public class StitchingExplorerPanel< AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS > > extends JPanel implements ExplorerWindow< AS, X >
{
	public static StitchingExplorerPanel< ?, ? > currentInstance = null;

	final ArrayList< ExplorerWindowSetable > popups;

	static
	{
		IOFunctions.printIJLog = true;
	}

	private static final long serialVersionUID = -3767947754096099774L;
	
	protected JTable table;
	protected StitchingExplorerTableModel< AS > tableModel;
	protected ArrayList< SelectedViewDescriptionListener< AS > > listeners;
	protected AS data;
	protected StitchingExplorer< AS, X > explorer;
	final String xml;
	final X io;
	final boolean isMac;
	protected boolean colorMode = true;

	final protected HashSet< BasicViewDescription< ? extends BasicViewSetup > > selectedRows;
	protected BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD;

	public StitchingExplorerPanel( final StitchingExplorer< AS, X > explorer, final AS data, final String xml, final X io )
	{
		popups = initPopups();

		this.explorer = explorer;
		this.listeners = new ArrayList< SelectedViewDescriptionListener< AS > >();
		this.data = data;
		this.xml = xml == null ? "" : xml.replace( "\\", "/" ).replace( "//", "/" ).replace( "/./", "/" );
		this.io = io;
		this.isMac = System.getProperty( "os.name" ).toLowerCase().contains( "mac" );
		this.selectedRows = new HashSet< BasicViewDescription< ? extends BasicViewSetup > >();
		this.firstSelectedVD = null;

		initComponent();

		if ( Hdf5ImageLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
		{
			final BDVPopup bdvpopup = bdvPopup();
			
			if ( bdvpopup != null )
			{
				bdvpopup.bdv = new BigDataViewer( getSpimData(), xml(), null );

//				if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
					InitializeViewerState.initBrightness( 0.001, 0.999, bdvpopup.bdv.getViewer(), bdvpopup.bdv.getSetupAssignments() );

				setFusedModeSimple( bdvpopup.bdv, data );
			}
		}

		// for access to the current BDV
		currentInstance = this;
	}

	@Override
	public BDVPopup bdvPopup()
	{
		for ( final ExplorerWindowSetable s : popups )
			if ( BDVPopup.class.isInstance( s ) )
				return ((BDVPopup)s);

		return null;
	}

	@Override
	public boolean colorMode() { return colorMode; }
	@Override
	public BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD() { return firstSelectedVD; }
	public StitchingExplorerTableModel< AS > getTableModel() { return tableModel; }
	
	@Override
	public AS getSpimData() { return data; }
	@Override
	public String xml() { return xml; }
	public X io() { return io; }
	public StitchingExplorer< AS, X > explorer() { return explorer; }

	@SuppressWarnings("unchecked")
	public void setSpimData( final Object data ) { this.data = (AS)data; }

	@Override
	public void updateContent()
	{
		this.getTableModel().fireTableDataChanged();
		for ( final SelectedViewDescriptionListener< AS > l : listeners )
			l.updateContent( this.data );
	}

	@Override
	public List< BasicViewDescription< ? extends BasicViewSetup > > selectedRows()
	{
		final ArrayList< BasicViewDescription< ? extends BasicViewSetup > > list = new ArrayList< BasicViewDescription< ? extends BasicViewSetup > >();
		list.addAll( selectedRows );
		Collections.sort( list );
		return list;
	}

	@Override
	public List< ViewId > selectedRowsViewId()
	{
		final ArrayList< ViewId > list = new ArrayList< ViewId >();
		list.addAll( selectedRows );
		Collections.sort( list );
		return list;
	}

	public void addListener( final SelectedViewDescriptionListener< AS > listener )
	{
		this.listeners.add( listener );
		
		// update it with the currently selected row
		if ( table.getSelectedRow() != -1 )
			listener.seletedViewDescription( tableModel.getElements().get( table.getSelectedRow() ) );
	}

	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners() { return listeners; }

	public void initComponent()
	{
		tableModel = new StitchingExplorerTableModel< AS >( this );

		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );

		// add listener to which row is selected
		table.getSelectionModel().addListSelectionListener( getSelectionListener() );

		// check out if the user clicked on the column header and potentially sorting by that
		table.getTableHeader().addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent mouseEvent)
			{
				int index = table.convertColumnIndexToModel(table.columnAtPoint(mouseEvent.getPoint()));
				if (index >= 0)
				{
					int row = table.getSelectedRow();
					tableModel.sortByColumn( index );
					table.clearSelection();
					table.getSelectionModel().setSelectionInterval( row, row );
				}
			};
		});

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
			public void actionPerformed( final ActionEvent e )
			{
				if ( save.isEnabled() )
					saveXML();
			}
		});

		final JButton info = new JButton( "Info" );
		info.addActionListener( new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				if ( info.isEnabled() )
					showInfoBox();
			}
		});

		final JPanel buttons = new JPanel( new BorderLayout() );
		buttons.add( info, BorderLayout.WEST );
		buttons.add( save, BorderLayout.EAST );

		final JPanel header = new JPanel( new BorderLayout() );
		header.add( new JLabel( "XML: " + xml ), BorderLayout.WEST );
		header.add( buttons, BorderLayout.EAST );
		this.add( header, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );

		table.getSelectionModel().setSelectionInterval( 0, 0 );

		addPopupMenu( table );
	}

	protected ListSelectionListener getSelectionListener()
	{
		return new ListSelectionListener()
		{
			int lastRow = -1;

			@Override
			public void valueChanged( final ListSelectionEvent arg0 )
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
							firstSelectedVD = tableModel.getElements().get( row );

						selectedRows.add( tableModel.getElements().get( row ) );
					}

				}
				else
				{
					final int row = table.getSelectedRow();

					if ( ( row != lastRow ) && row >= 0 && row < tableModel.getRowCount() )
					{
						lastRow = row;

						// not using an iterator allows that listeners can close the frame and remove all listeners while they are called
						final BasicViewDescription< ? extends BasicViewSetup > vd = tableModel.getElements().get( row );
						for ( int i = 0; i < listeners.size(); ++i )
							listeners.get( i ).seletedViewDescription( vd );

						selectedRows.clear();
						selectedRows.add( vd );

						firstSelectedVD = vd;
					}
				}

				if ( b != null && b.bdv != null )
					updateBDV( b.bdv, colorMode, data, firstSelectedVD, selectedRows );
			}
		};
	}

	public static void updateBDV(
			final BigDataViewer bdv,
			final boolean colorMode,
			final AbstractSpimData< ? > data,
			BasicViewDescription< ? extends BasicViewSetup > firstVD,
			final Collection< ? extends BasicViewDescription< ? extends BasicViewSetup > > selectedRows )
	{
		// we always set the fused mode
		setFusedModeSimple( bdv, data );

		if ( selectedRows == null || selectedRows.size() == 0 )
			return;

		if ( firstVD == null )
			firstVD = selectedRows.iterator().next();

		// always use the first timepoint
		final TimePoint firstTP = firstVD.getTimePoint();
		bdv.getViewer().setTimepoint( getBDVTimePointIndex( firstTP, data ) );

		final boolean[] active = new boolean[ data.getSequenceDescription().getViewSetupsOrdered().size() ];

		for ( final BasicViewDescription< ? > vd : selectedRows )
			if ( vd.getTimePointId() == firstTP.getId() )
				active[ getBDVSourceIndex( vd.getViewSetup(), data ) ] = true;

		if ( selectedRows.size() > 1 && colorMode )
			colorSources( bdv.getSetupAssignments().getConverterSetups(), 0 );
		else
			whiteSources( bdv.getSetupAssignments().getConverterSetups() );

		setVisibleSources( bdv.getViewer().getVisibilityAndGrouping(), active );
	}

	public static void setFusedModeSimple( final BigDataViewer bdv, final AbstractSpimData< ? > data )
	{
		if ( bdv == null )
			return;

		if ( bdv.getViewer().getVisibilityAndGrouping().getDisplayMode() != DisplayMode.FUSED )
		{
			final boolean[] active = new boolean[ data.getSequenceDescription().getViewSetupsOrdered().size() ];
			active[ 0 ] = true;
			setVisibleSources( bdv.getViewer().getVisibilityAndGrouping(), active );
			bdv.getViewer().getVisibilityAndGrouping().setDisplayMode( DisplayMode.FUSED );
		}
	}

	public static void colorSources( final List< ConverterSetup > cs, final long j )
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ColorStream.get( i + j ) ) );
	}

	public static void whiteSources( final List< ConverterSetup > cs )
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ARGBType.rgba( 255, 255, 255, 0 ) ) );
	}

	public static void setVisibleSources( final VisibilityAndGrouping vag, final boolean[] active )
	{
		for ( int i = 0; i < active.length; ++i )
			vag.setSourceActive( i, active[ i ] );
	}

	public static int getBDVTimePointIndex( final TimePoint t, final AbstractSpimData< ? > data )
	{
		final List< TimePoint > list = data.getSequenceDescription().getTimePoints().getTimePointsOrdered();

		for ( int i = 0; i < list.size(); ++i )
			if ( list.get( i ).getId() == t.getId() )
				return i;

		return 0;
	}

	public static int getBDVSourceIndex( final BasicViewSetup vs, final AbstractSpimData< ? > data )
	{
		final List< ? extends BasicViewSetup > list = data.getSequenceDescription().getViewSetupsOrdered();
		
		for ( int i = 0; i < list.size(); ++i )
			if ( list.get( i ).getId() == vs.getId() )
				return i;

		return 0;
	}

	public HashSet< BasicViewDescription< ? extends BasicViewSetup > > getSelectedRows() { return selectedRows; }

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
				final ViewInterestPoints vip = ( (SpimData2)data ).getViewInterestPoints();
				
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

	protected void addPopupMenu( final JTable table )
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
			public void keyPressed( final KeyEvent arg0 )
			{
				if ( arg0.getKeyChar() == 'c' || arg0.getKeyChar() == 'C' )
				{
					colorMode = !colorMode;
					
					System.out.println( "colormode" );

					final BDVPopup p = bdvPopup();
					if ( p != null && p.bdv != null && p.bdv.getViewerFrame().isVisible() )
						updateBDV( p.bdv, colorMode, data, null, selectedRows );
				}
			}

			@Override
			public void keyReleased( final KeyEvent arg0 ) {}

			@Override
			public void keyTyped( final KeyEvent arg0 ) {}
		} );
	}

	protected void addAppleA()
	{
		table.addKeyListener( new KeyListener()
		{
			boolean appleKeyDown = false;

			@Override
			public void keyTyped( KeyEvent arg0 )
			{
				if ( appleKeyDown && arg0.getKeyChar() == 'a' )
					table.selectAll();
			}

			@Override
			public void keyReleased( KeyEvent arg0 )
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
		});
	}

	public ArrayList< ExplorerWindowSetable > initPopups()
	{
		final ArrayList< ExplorerWindowSetable > popups = new ArrayList< ExplorerWindowSetable >();

		popups.add( new LabelPopUp( " Displaying" ) );
		popups.add( new BDVPopup() );
		popups.add( new DisplayViewPopup() );
		popups.add( new MaxProjectPopup() );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Processing" ) );
		popups.add( new Separator() );

		popups.add( new LabelPopUp( " Calibration/Transformations" ) );
		popups.add( new Separator() );

		return popups;
	}
}
