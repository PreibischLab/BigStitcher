package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;

import gui.popup.LinkExplorerRemoveLinkPopup;
import gui.popup.SimpleRemoveLinkPopup;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import spim.fiji.spimdata.explorer.SelectedViewDescriptionListener;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class LinkExplorerPanel extends JPanel implements SelectedViewDescriptionListener< AbstractSpimData<?> >
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
	private StitchingExplorerPanel< ?, ? > parent;
	LinkExplorerTableModel model;
	protected JTable table;
	
	public void setActiveLinks(List<Pair<Group<ViewId>, Group<ViewId>>> links)
	{
//		System.out.println( "selected links:" );
//		links.forEach( ( l ) -> System.out.println( l ) );
		model.setActiveLinks( links );
		model.fireTableDataChanged();
	}
	
	
	
	public LinkExplorerPanel (StitchingResults results, StitchingExplorerPanel< ?, ? > parent)
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
