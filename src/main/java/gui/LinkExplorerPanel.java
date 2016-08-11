package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import algorithm.StitchingResults;
import gui.popup.LinkExplorerRemoveLinkPopup;
import gui.popup.SimpleRemoveLinkPopup;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;

public class LinkExplorerPanel extends JPanel
{
	

	public LinkExplorerTableModel getModel()
	{
		return model;
	}



	public JTable getTable()
	{
		return table;
	}

	private StitchingResults results;
	private FilteredAndGroupedExplorerPanel< ?, ? > parent;
	LinkExplorerTableModel model;
	protected JTable table;
	
	public void setActiveLinks(List<Pair<ViewId, ViewId>> links)
	{
		model.setActiveLinks( links );
		model.fireTableDataChanged();
	}
	
	
	
	public LinkExplorerPanel (StitchingResults results, FilteredAndGroupedExplorerPanel< ?, ? > parent)
	{
		this.results = results;
		this.parent = parent;
		
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
		
		this.setLayout( new BorderLayout() );
		this.add( new JScrollPane( table ), BorderLayout.CENTER );
		
		final JPopupMenu popupMenu = new JPopupMenu();
		LinkExplorerRemoveLinkPopup rlp = new LinkExplorerRemoveLinkPopup(this);
		rlp.setStitchingResults( results );
		popupMenu.add( rlp.setExplorerWindow( parent ) );
		
		table.setComponentPopupMenu( popupMenu );
		
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
				Pair< ViewId, ViewId > p = model.getActiveLinks().get( rowIdx );
				parent.linkOverlay.setSelectedLink( p );
				
				// repaint BDV if it is open
				if (parent.bdvPopup().bdv != null)
					parent.bdvPopup().bdv.getViewer().requestRepaint();	
				
				
				//System.out.println( p.getA() + "," + p.getB() );
			}
		};
	}

}
