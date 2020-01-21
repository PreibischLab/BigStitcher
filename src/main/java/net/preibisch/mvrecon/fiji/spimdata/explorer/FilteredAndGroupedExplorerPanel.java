/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.spimdata.explorer;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.GroupedViews;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.SpimDataTools;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorerInfoBox;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BDVPopup;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import bdv.BigDataViewer;
import bdv.tools.HelpDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.transformation.TransformedSource;

import bdv.viewer.DisplayMode;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceState;

public abstract class FilteredAndGroupedExplorerPanel<AS extends AbstractSpimData< ? >, X extends XmlIoAbstractSpimData< ?, AS >>
		extends JPanel implements ExplorerWindow< AS, X >, GroupedRowWindow
{
	public static FilteredAndGroupedExplorerPanel< ?, ? > currentInstance = null;

	protected ArrayList< ExplorerWindowSetable > popups;

	static
	{
		IOFunctions.printIJLog = true;
	}

	
	
	private static final long serialVersionUID = -3767947754096099774L;

	public JTable table;
	protected ISpimDataTableModel< AS > tableModel;
	protected ArrayList< SelectedViewDescriptionListener< AS > > listeners;
	protected AS data;
	protected FilteredAndGroupedExplorer< AS, X > explorer;
	protected final String xml;
	protected final X io;
	protected final boolean isMac;
	protected boolean colorMode = false;
	

	final protected HashSet< List<BasicViewDescription< ? extends BasicViewSetup >> > selectedRows;
	protected BasicViewDescription< ? extends BasicViewSetup > firstSelectedVD;

	public FilteredAndGroupedExplorerPanel(final FilteredAndGroupedExplorer< AS, X > explorer, final AS data,
			final String xml, final X io)
	{
		
		
		
		this.explorer = explorer;
		this.listeners = new ArrayList< SelectedViewDescriptionListener< AS > >();
		this.data = data;

		// normalize the xml path
		this.xml = xml == null ? "" : xml.replace("\\\\", "////").replace( "\\", "/" ).replace( "//", "/" ).replace( "/./", "/" );
		// NB: a lot of path normalization problems (e.g. windows network locations not accessible) are also fixed by not normalizing
		// therefore, if we run into problems in the future, we could also use the line below:
		//this.xml = xml == null ? "" : xml;
		this.io = io;
		this.isMac = System.getProperty( "os.name" ).toLowerCase().contains( "mac" );
		this.selectedRows = new HashSet<>();
		this.firstSelectedVD = null;

		
		popups = initPopups();
		
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
		this.getTableModel().updateElements();
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

	public void addListener(final SelectedViewDescriptionListener< AS > listener)
	{
		this.listeners.add( listener );

		List<List<BasicViewDescription< ? extends BasicViewSetup >>> selectedList = new ArrayList<>();
		for (List<BasicViewDescription< ? extends BasicViewSetup >> selectedI : selectedRows)
			selectedList.add( selectedI );
		
		listener.selectedViewDescriptions( selectedList );
	}

	public ArrayList< SelectedViewDescriptionListener< AS > > getListeners()
	{
		return listeners;
	}

	public abstract void initComponent();
	
	public void updateFilter(Class<? extends Entity> entityClass, Entity selectedInstance)
	{
		ArrayList<Entity> selectedInstances = new ArrayList<>();
		selectedInstances.add( selectedInstance );
		tableModel.addFilter( entityClass, selectedInstances );		
	}
	
	protected static List<String> getEntityNamesOrIds(List<? extends Entity> entities)
	{
		ArrayList<String> names = new ArrayList<>();
		
		for (Entity e : entities)
			names.add( NamedEntity.class.isInstance( e ) ? ((NamedEntity)e).getName() : Integer.toString( e.getId()));
		
		return names;
	}
	
	public static Entity getInstanceFromNameOrId(AbstractSequenceDescription<?,?,?> sd, Class<? extends Entity> entityClass, String nameOrId)
	{
		for (Entity e : SpimDataTools.getInstancesOfAttribute( sd, entityClass ))
			if (NamedEntity.class.isInstance( e ) && ((NamedEntity)e).getName().equals( nameOrId ) || Integer.toString( e.getId()).equals( nameOrId ))
				return e;
		return null;
	}

	protected void addHelp()
	{
		table.addKeyListener( new KeyListener()
			{
				public void keyTyped( KeyEvent e ) {}
	
				@Override
				public void keyReleased( KeyEvent e ) {}
	
				@Override
				public void keyPressed( KeyEvent e )
				{
					if ( e.getKeyCode() == 112 )
						new HelpDialog( explorer().getFrame(), this.getClass().getResource( getHelpHtml() ) ).setVisible( true );
				}
			} );
	}

	protected abstract String getHelpHtml();

	protected ListSelectionListener getSelectionListener()
	{
		return new ListSelectionListener()
		{
			//int lastRow = -1;

			@Override
			public void valueChanged(final ListSelectionEvent arg0)
			{
				BDVPopup b = bdvPopup();

				selectedRows.clear();
				firstSelectedVD = null;
				for ( final int row : table.getSelectedRows() )
				{
					if ( firstSelectedVD == null )
						firstSelectedVD = tableModel.getElements().get( row ).get( 0 );

					selectedRows.add( tableModel.getElements().get( row ) );
				}
				
				List<List<BasicViewDescription< ? extends BasicViewSetup >>> selectedList = new ArrayList<>();
				for (List<BasicViewDescription< ? extends BasicViewSetup >> selectedI : selectedRows)
					selectedList.add( selectedI );
								
				for ( int i = 0; i < listeners.size(); ++i )
					listeners.get( i ).selectedViewDescriptions( selectedList );
				
				/*
				if ( table.getSelectedRowCount() != 1 )
				{
					lastRow = -1;

					for ( int i = 0; i < listeners.size(); ++i )
						listeners.get( i ).firstSelectedViewDescriptions( null );

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
							listeners.get( i ).firstSelectedViewDescriptions( vds );

						selectedRows.clear();
						selectedRows.add( vds );

						firstSelectedVD = vds.get( 0 );
					}
				}
				*/

				if ( b != null && b.bdv != null )
				{	
					updateBDV( b.bdv, colorMode, data, firstSelectedVD, selectedRows);
					
				}
					
				
			}

			
		};
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
			((TransformedSource< ? >)s.getSpimSource()).setIncrementalTransform( new AffineTransform3D() );
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
			cs.get( i ).setColor( new ARGBType( ARGBType.rgba( 255, 255, 255, 255 ) ) );
	}

	public static void sameColorSources(final List< ConverterSetup > cs, final int r, final int g, final int b, final int a)
	{
		for ( int i = 0; i < cs.size(); ++i )
			cs.get( i ).setColor( new ARGBType( ARGBType.rgba( r, g, b, a ) ) );
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

	protected void addReCenterShortcut()
	{
		table.addKeyListener( new KeyListener()
		{
			@Override
			public void keyPressed(final KeyEvent arg0)
			{
				if ( arg0.getKeyChar() == 'r' || arg0.getKeyChar() == 'R' )
				{
					final BDVPopup p = bdvPopup();
					if ( p != null && p.bdv != null && p.bdv.getViewerFrame().isVisible() )
					{
						TransformationTools.reCenterViews( p.bdv,
								selectedRows.stream().collect( 
										HashSet< BasicViewDescription< ? > >::new,
										(a, b) -> a.addAll( b ), (a, b) -> a.addAll( b ) ),
										data.getViewRegistrations() );
					}
				}
			}

			@Override
			public void keyReleased(final KeyEvent arg0){}
			@Override
			public void keyTyped(final KeyEvent arg0){}
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

	public abstract ArrayList< ExplorerWindowSetable > initPopups();

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
