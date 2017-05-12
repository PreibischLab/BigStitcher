package gui;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.table.AbstractTableModel;


import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class LinkExplorerTableModel extends AbstractTableModel implements StitchingResultsSettable
{

	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3972623555571460757L;

	public List< Pair< Set<ViewId>, Set<ViewId> > > getActiveLinks()
	{
		return activeLinks;
	}

	@Override
	public String getColumnName(int column)
	{
		return new String[]{"ViewIDs A", "ViewIDs B", "cross correlation", "shift"}[column];
	}


	private List<Pair<Set<ViewId>, Set<ViewId>>> activeLinks;
	private StitchingResults results;
	
	public LinkExplorerTableModel()
	{
		activeLinks = new ArrayList<>();
	}
	
	public void setActiveLinks(List<Pair<Set<ViewId>, Set<ViewId>>> links)
	{
		activeLinks.clear();
		activeLinks.addAll( links );
	}
	
	@Override
	public int getRowCount()
	{
		return activeLinks == null ? 0 : activeLinks.size();
	}

	@Override
	public int getColumnCount()
	{
		return 4;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex)
	{
		if (columnIndex == 0)
		{
			Set< ViewId > views = results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).pair().getA();
			List< String > descs = views.stream().map( view -> "(View " + view.getViewSetupId() + ", Timepoint " + view.getTimePointId() + ")" ).collect( Collectors.toList() );
			return "[" + String.join( ", ", descs ) + "]";
		}
			
		else if (columnIndex == 1)
		{
			Set< ViewId > views = results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).pair().getB();
			List< String > descs = views.stream().map( view -> "(View " + view.getViewSetupId() + ", Timepoint " + view.getTimePointId() + ")" ).collect( Collectors.toList() );
			return "[" + String.join( ", ", descs ) + "]";
		}
		else if (columnIndex == 2)
			return results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).r();
		else if (columnIndex == 3)
		{
			double[] shift = results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).getTransform().getRowPackedCopy();
			
			StringBuilder res = new StringBuilder();
			// round to 3 decimal places
			DecimalFormat df = new DecimalFormat( "#.###" );
			df.setRoundingMode( RoundingMode.HALF_UP );

			// TODO: for now, just display the translation part of the transform
			res.append(df.format( shift[3]) );
			res.append(", ");
			res.append(df.format(shift[7]) );
			res.append(", ");
			res.append(df.format( shift[11]) );
			return res.toString();
		}
			
		else
			return "";
		
	}
	
	

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.results = res;		
	}
	
	
}
