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
package net.preibisch.mvrecon.fiji.spimdata.explorer.registration;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.TreeTableColumn.CellDataFeatures;
import javafx.scene.control.TreeTableColumn.CellEditEvent;
import javafx.util.Callback;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Pair;



public class RegistrationExplorerPanel extends JPanel
{
	private static final long serialVersionUID = -3767947754096099774L;
	
	final RegistrationExplorer< ?, ? > explorer;
	
	protected JTable table;
	protected RegistrationTableModel tableModel;
	protected JLabel label;
	protected TreeTableView< RegistrationExplorerRow > treeTable;
	protected JFXPanel jfx;
	protected ViewRegistrations viewRegistrations;
	protected List<BasicViewDescription<?>> lastSelectedVDs;
	
	protected ArrayList< ViewTransform > cache;
	
	public RegistrationExplorerPanel( final ViewRegistrations viewRegistrations, final RegistrationExplorer< ?, ? > explorer )
	{
		this.cache = new ArrayList< ViewTransform >();
		this.explorer = explorer;

		this.viewRegistrations = viewRegistrations;
		initComponent( viewRegistrations );
	}

	public RegistrationTableModel getTableModel() { return tableModel; }
	public JTable getTable() { return table; }
	
	public void updateViewDescriptions( final List<BasicViewDescription<?>> vds )
	{
		/*
		if ( vds != null && label != null )
			this.label.setText( "View Description --- Timepoint: " + vds.getTimePointId() + ", View Setup Id: " + vds.getViewSetupId() );

		if ( vds == null )
			this.label.setText( "No or multiple View Descriptions selected");

		tableModel.updateViewDescription( vds );
		
		if ( table.getSelectedRowCount() == 0 )
			table.getSelectionModel().setSelectionInterval( 0, 0 );
		*/
		
		this.lastSelectedVDs = vds;
		
		updateTree();
		
	
		
	}

	protected void updateTree()
	{
		final TreeItem< RegistrationExplorerRow > root = new TreeItem<>(new RegistrationExplorerRow( 
																		RegistrationExplorerRowType.ROOT, null, 0 ));
		
		root.setExpanded( true );
		
		
		for (final BasicViewDescription<?> vd : lastSelectedVDs )
		{
			final TreeItem< RegistrationExplorerRow > groupTI = new TreeItem<>(new RegistrationExplorerRow( 
					RegistrationExplorerRowType.GROUP, vd, 0 ));
			groupTI.setExpanded( true );
			
			ViewRegistration vr = viewRegistrations.getViewRegistration( vd );
			for (int i = 0; i < vr.getTransformList().size(); i++)
			{
				final TreeItem< RegistrationExplorerRow > transformTI = new TreeItem<>(new RegistrationExplorerRow( 
						RegistrationExplorerRowType.REGISTRATION, vd, i ));
				groupTI.getChildren().add( transformTI );
			}
			
			root.getChildren().add( groupTI );
		}
		
		Platform.runLater( new Runnable()
		{
			@Override
			public void run()
			{
				treeTable.setRoot( root );
				
			}
		} );
	}
	
	private enum RegistrationExplorerRowType
	{
		ROOT, GROUP, REGISTRATION
	}
	
	private class RegistrationExplorerRow
	{
		RegistrationExplorerRowType rowType;
		final BasicViewDescription< ? > vd;
		int transformIndex;
		
		public RegistrationExplorerRow(RegistrationExplorerRowType rowType, final BasicViewDescription< ? > vd,
				int transformIndex)
		{
			this.rowType = rowType;
			this.vd = vd;
			this.transformIndex = transformIndex;
		}
		
	}

	private class NameCallback implements Callback< TreeTableColumn.CellDataFeatures<RegistrationExplorerRow, String>, ObservableValue<String> >
	{		
		@Override
		public ObservableValue< String > call(CellDataFeatures< RegistrationExplorerRow, String > param)
		{
			RegistrationExplorerRow row = param.getValue().getValue();
			
			// group row -> return description
			if (row.rowType == RegistrationExplorerRowType.GROUP)
				return new ReadOnlyStringWrapper("ViewSetup: " + row.vd.getViewSetupId() + ", TP: " + row.vd.getTimePointId());
			
			// single transform -> return name
			else if (row.rowType == RegistrationExplorerRowType.REGISTRATION)
				return new ReadOnlyStringWrapper(viewRegistrations.getViewRegistration( row.vd).getTransformList().get( row.transformIndex ).getName());
			
			// else return empty String
			else
				return new ReadOnlyStringWrapper();			
		}
	};
	
	private class MatrixCallback implements Callback< TreeTableColumn.CellDataFeatures<RegistrationExplorerRow, String>, ObservableValue<String> >
	{

		private int matRow;
		private int matColumn;
		
		public MatrixCallback(int row, int column)
		{
			this.matRow = row;
			this.matColumn = column;
		}
		
		@Override
		public ObservableValue< String > call(CellDataFeatures< RegistrationExplorerRow, String > param)
		{
			RegistrationExplorerRow row = param.getValue().getValue();
			
			// single transform -> return matrix element
			if (row.rowType == RegistrationExplorerRowType.REGISTRATION)
				return new ReadOnlyStringWrapper(Double.toString( viewRegistrations.getViewRegistration( row.vd).getTransformList().get( row.transformIndex ).asAffine3D().get( matRow, matColumn )));
			// else return empty String
			else
				return new ReadOnlyStringWrapper();
		}
		
	}
	
	
	
	public void initComponent( final ViewRegistrations viewRegistrations )
	{

		// NB: JavaFX Platform will startup when the first JFXPanel is created and
		// shutdown implicitly on MVR <-> Stitcher switching (parent closes).
		// It will not restart and therefore, the registration explorer creation will hang.

		// Disable the implicit exit
		Platform.setImplicitExit( false );

		jfx = new JFXPanel();
		initFX();
		this.add(jfx);

		/*
		tableModel = new RegistrationTableModel( viewRegistrations, this );

		
		
		table = new JTable();
		table.setModel( tableModel );
		table.setSurrendersFocusOnKeystroke( true );
		table.setSelectionMode( ListSelectionModel.SINGLE_INTERVAL_SELECTION );
		
		final DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		
		// center all columns
		for ( int column = 0; column < tableModel.getColumnCount(); ++column )
			table.getColumnModel().getColumn( column ).setCellRenderer( centerRenderer );

		table.setPreferredScrollableViewportSize( new Dimension( 1020, 300 ) );
		table.getColumnModel().getColumn( 0 ).setPreferredWidth( 300 );
		for ( int i = 1; i < table.getColumnCount(); ++i )
			table.getColumnModel().getColumn( i ).setPreferredWidth( 100 );
		final Font f = table.getFont();
		
		table.setFont( new Font( f.getName(), f.getStyle(), 11 ) );
		
		this.setLayout( new BorderLayout() );
		this.label = new JLabel( "View Description --- " );
		this.add( label, BorderLayout.NORTH );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );
		*/
		
		
		addPopupMenu( jfx );
	}

	class EditingCell extends TreeTableCell<RegistrationExplorerRow, String> {
		 
        private TextField textField; 
        public EditingCell() {
        }
 
        @Override
        public void startEdit() {
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                textField.selectAll();
            }
        }
 
        @Override
        public void cancelEdit() {
            super.cancelEdit();
 
            setText((String) getItem());
            setGraphic(null);
        }
 
        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
 
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                    }
                    setText(null);
                    setGraphic(textField);
                } else {
                    setText(getString());
                    setGraphic(null);
                }
            }
        }
 
        private void createTextField() {
            textField = new TextField(getString());
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap()* 2);
            textField.focusedProperty().addListener(
                (ObservableValue<? extends Boolean> arg0, 
                Boolean arg1, Boolean arg2) -> {
                    if (!arg2) {
                        commitEdit(textField.getText());
                    }
            });
        }
 
        private String getString() {
            return getItem() == null ? "" : getItem().toString();
        }
    }
	
	class MatrixEditEventHandler implements EventHandler< TreeTableColumn.CellEditEvent<RegistrationExplorerRow,String> >
	{
		private int matRow;
		private int matColumn;
				
		public MatrixEditEventHandler(int row, int column)
		{
			this.matRow = row;
			this.matColumn = column;
		}
		
		@Override
		public void handle(CellEditEvent< RegistrationExplorerRow, String > event)
		{
			RegistrationExplorerRow row = event.getRowValue().getValue();
			
			if (row.rowType != RegistrationExplorerRowType.REGISTRATION)
				return;
			
			ViewTransform vtOld = viewRegistrations.getViewRegistration( row.vd ).getTransformList().get( row.transformIndex );
			AffineTransform3D newT = new AffineTransform3D();
			newT.concatenate( vtOld.asAffine3D() );
			newT.set( Double.parseDouble( event.getNewValue() ), matRow, matColumn );
			ViewTransform vtNew = new ViewTransformAffine( vtOld.getName(), newT );
			
			viewRegistrations.getViewRegistration( row.vd ).getTransformList().remove( row.transformIndex );
			viewRegistrations.getViewRegistration( row.vd ).getTransformList().add( row.transformIndex, vtNew );
			viewRegistrations.getViewRegistration( row.vd ).updateModel();
			
			explorer.viewSetupExplorer.getPanel().bdvPopup().updateBDV();
			updateTree();
		}
		
	} 
	
	public void initFX()
	{
		treeTable = new TreeTableView<>();
		treeTable.setEditable( true );
		List<TreeTableColumn< RegistrationExplorerRow , String >> columns = new ArrayList<>();
		
		Callback< TreeTableColumn< RegistrationExplorerRow, String >, TreeTableCell< RegistrationExplorerRow, String > > cellFactory
            = (TreeTableColumn<RegistrationExplorerRow, String> p) -> new EditingCell();
		
		TreeTableColumn< RegistrationExplorerRow , String > nameColumn = new TreeTableColumn<>("Name");
		nameColumn.setCellValueFactory( new NameCallback());
		nameColumn.setPrefWidth( 150 );
		columns.add( nameColumn );
		
		
		
		for (int i = 0; i < 3; i++)
			for (int j = 0; j < 4; j++)
			{
				TreeTableColumn< RegistrationExplorerRow , String > matColumn = new TreeTableColumn<>("m"+i+j);
				matColumn.setCellValueFactory( new MatrixCallback( i, j ) );
				matColumn.setCellFactory( cellFactory );
				
				matColumn.setOnEditCommit( new MatrixEditEventHandler( i, j ));				
				
				columns.add( matColumn );
			}
		
		treeTable.getColumns().addAll( columns );
		treeTable.setShowRoot( false );
		Group root = new Group();
		Scene scene = new Scene(root);
		root.getChildren().add( treeTable );
		jfx.setScene( scene );
		
	}

	protected void copySelection()
	{
		cache.clear();
		
		ObservableList< TreeItem< RegistrationExplorerRow > > selectedItems = treeTable.getSelectionModel().getSelectedItems();
		
		if ( selectedItems.size() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing selected");
			return;
		}
		else
		{
			// TODO: what to do if we selected multiple vds
			
			/*
			final BasicViewDescription< ? > vd = tableModel.getCurrentViewDescription();
			
			if ( vd == null )
			{
				JOptionPane.showMessageDialog( table, "No active viewdescription." );
				return;
			}
			
			final ViewRegistration vr = tableModel.getViewRegistrations().getViewRegistration( vd );
			
			*/
			for ( TreeItem< RegistrationExplorerRow > ti : selectedItems )
			{
				if (ti.getValue().rowType != RegistrationExplorerRowType.REGISTRATION)
					continue;
				
				cache.add( duplicate( viewRegistrations.getViewRegistration( ti.getValue().vd ).getTransformList().get( ti.getValue().transformIndex )) );
				System.out.println( "Copied row " + viewRegistrations.getViewRegistration( ti.getValue().vd ).getTransformList().get( ti.getValue().transformIndex ).getName() );
			}
		}
	}

	/**
	 * 
	 * @param type 0 == before, 1 == replace, 2 == after
	 */
	protected void pasteSelection( final int type )
	{
		
		ObservableList< TreeItem< RegistrationExplorerRow > > selectedItems = treeTable.getSelectionModel().getSelectedItems();
		
		final Map<ViewId, List<Integer>> toInsert = new HashMap<>();
		
		if ( cache.size() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing copied so far." );
			return;
		}
		
		if ( selectedItems.size() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing selected." );
			return;
		}

		/*
		final BasicViewDescription< ? > vd = selectedItems.iterator().next().getValue().vd;
		
		if ( vd == null )
		{
			JOptionPane.showMessageDialog( table, "No active viewdescription." );
			return;
		}

		final ViewRegistration vr = viewRegistrations.getViewRegistration( vd );

		 */
		
		
		for (TreeItem< RegistrationExplorerRow > ti : selectedItems)
		{
			if (ti.getValue().rowType != RegistrationExplorerRowType.REGISTRATION)
				continue;
			
			if (!toInsert.containsKey( ti.getValue().vd ))
				toInsert.put( ti.getValue().vd, new ArrayList<>() );
			toInsert.get( ti.getValue().vd ).add( ti.getValue().transformIndex );
		}
		
		for (ViewId vd : toInsert.keySet())
		{
			List< Integer > idxes = toInsert.get( vd );
			Collections.sort( idxes );
			
			// remove if we want that
			if (type == 1)
				for (int i = 0 ; i < idxes.size(); i++)
					viewRegistrations.getViewRegistration( vd ).getTransformList().remove( idxes.get( i ) - i );
			
			for (int i = 0 ; i < idxes.size(); i++)
				for (int j = 0; j < cache.size(); j++)
				{
					int idxToAddAt = (type == 2) ? idxes.get( i ) + j + i * cache.size() + 1 : idxes.get( i ) + j + i * cache.size();
					viewRegistrations.getViewRegistration( vd ).getTransformList().add( idxToAddAt, duplicate( cache.get( j ) ) );
				}
			
			/*
			if  (viewRegistrations.getViewRegistration( vd ).getTransformList().isEmpty() )
				viewRegistrations.getViewRegistration( vd ).getTransformList().add( new ViewTransformAffine( null, new AffineTransform3D() ) );
			
			*/
			viewRegistrations.getViewRegistration( vd ).updateModel();
		}
		
		
		/*
		// check out where to start inserting
		final int[] selectedRows = table.getSelectedRows();
		Arrays.sort( selectedRows );
		int insertAt;
		
		if ( type == 0 )
		{
			insertAt = selectedRows[ 0 ];
		}
		else if ( type == 1 )
		{
			insertAt = selectedRows[ 0 ];

			// remove the selected entries
			for ( int i = selectedRows[ selectedRows.length - 1 ]; i >= selectedRows[ 0 ]; --i )
				vr.getTransformList().remove( i );
		}
		else
		{
			insertAt = selectedRows[ selectedRows.length - 1 ] + 1;
		}

		// add the new entries
		final ArrayList< ViewTransform > newList = new ArrayList< ViewTransform >();
		
		// add the old entries
		for ( int i = 0; i < insertAt; ++i )
			newList.add( vr.getTransformList().get( i ) );
		
		// add the copied ones
		for ( int i = 0; i < cache.size(); ++i )
			newList.add( duplicate( cache.get( i ) ) );
		
		// add the rest
		for ( int i = insertAt; i < vr.getTransformList().size(); ++i )
			newList.add( vr.getTransformList().get( i ) );
		
		vr.getTransformList().clear();
		vr.getTransformList().addAll( newList );
		vr.updateModel();

		// update everything
		tableModel.fireTableDataChanged();
		*/
		
		explorer.viewSetupExplorer.getPanel().bdvPopup().updateBDV();
		updateTree();
		
	}
	
	protected static ViewTransform duplicate( final ViewTransform vt )
	{
		final AffineTransform3D t = new AffineTransform3D();
		t.set( vt.asAffine3D().getRowPackedCopy() );
		
		return new ViewTransformAffine( vt.getName(), t );
	}

	protected static ViewTransform newName( final ViewTransform vt, final String name )
	{
		final AffineTransform3D t = new AffineTransform3D();
		t.set( vt.asAffine3D().getRowPackedCopy() );
		
		return new ViewTransformAffine( name, t );
	}

	protected static ViewTransform newMatrixEntry( final ViewTransform vt, final double value, final int index )
	{
		final AffineTransform3D t = new AffineTransform3D();
		final double[] m = vt.asAffine3D().getRowPackedCopy();
		m[ index ] = value;
		t.set( m );
		
		return new ViewTransformAffine( vt.getName(), t );
	}

	protected void delete()
	{
		ObservableList< TreeItem< RegistrationExplorerRow > > selectedItems = treeTable.getSelectionModel().getSelectedItems();
		
		final Map<ViewId, List<Integer>> toDelete = new HashMap<>();
		
		if ( selectedItems.size() == 0 )
		{
			JOptionPane.showMessageDialog( table, "Nothing selected." );
			return;
		}

		/*
		final BasicViewDescription< ? > vd = tableModel.getCurrentViewDescription();

		if ( vd == null )
		{
			JOptionPane.showMessageDialog( table, "No active viewdescription." );
			return;
		}

		final int[] selectedRows = table.getSelectedRows();
		Arrays.sort( selectedRows );
		*/
		
		for (TreeItem< RegistrationExplorerRow > ti : selectedItems)
		{
			if (ti.getValue().rowType != RegistrationExplorerRowType.REGISTRATION)
				continue;
			
			if (!toDelete.containsKey( ti.getValue().vd ))
				toDelete.put( ti.getValue().vd, new ArrayList<>() );
			toDelete.get( ti.getValue().vd ).add( ti.getValue().transformIndex );
		}
		
		for (ViewId vd : toDelete.keySet())
		{
			List< Integer > idxes = toDelete.get( vd );
			Collections.sort( idxes );
			for (int i = 0 ; i < idxes.size(); i++)
				viewRegistrations.getViewRegistration( vd ).getTransformList().remove( idxes.get( i ) - i );
			
			if  (viewRegistrations.getViewRegistration( vd ).getTransformList().isEmpty() )
				viewRegistrations.getViewRegistration( vd ).getTransformList().add( new ViewTransformAffine( null, new AffineTransform3D() ) );
			
			viewRegistrations.getViewRegistration( vd ).updateModel();
		}

		/*
		final ViewRegistration vr = tableModel.getViewRegistrations().getViewRegistration( vd );

		for ( int i = selectedRows[ selectedRows.length - 1 ]; i >= selectedRows[ 0 ]; --i )
			vr.getTransformList().remove( i );

		if  ( vr.getTransformList().isEmpty() )
			vr.getTransformList().add( new ViewTransformAffine( null, new AffineTransform3D() ) );

		vr.updateModel();

		// update everything
		tableModel.fireTableDataChanged();
		*/
		
		explorer.viewSetupExplorer.getPanel().bdvPopup().updateBDV();
		updateTree();	
		
	}
	
	protected void addPopupMenu( final JFXPanel table )
	{
		final JPopupMenu popupMenu = new JPopupMenu();
		
		JMenuItem copyItem = new JMenuItem( "Copy" );
		JMenuItem deleteItem = new JMenuItem( "Delete" );
		
		JMenuItem pasteBeforeItem = new JMenuItem( "Paste before selection" );
		JMenuItem pasteAndRepaceItem = new JMenuItem( "Paste and replace selection" );
		JMenuItem pasteAfterItem = new JMenuItem( "Paste after selection" );
		
		copyItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				copySelection();
			}
		});

		pasteBeforeItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				pasteSelection( 0 );
			}
		});

		pasteAndRepaceItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				pasteSelection( 1 );
			}
		});

		pasteAfterItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				pasteSelection( 2 );
			}
		});

		deleteItem.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				delete();
				System.out.println( "Right-click performed on table and choose DELETE" );
			}
		});
		
		popupMenu.add( copyItem );
		popupMenu.add( pasteBeforeItem );
		popupMenu.add( pasteAndRepaceItem );
		popupMenu.add( pasteAfterItem );
		popupMenu.add( deleteItem );
		
		table.setComponentPopupMenu( popupMenu );
	}
}
