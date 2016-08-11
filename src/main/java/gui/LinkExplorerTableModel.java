package gui;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import algorithm.StitchingResults;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;

public class LinkExplorerTableModel extends AbstractTableModel implements StitchingResultsSettable
{

	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 3972623555571460757L;

	public List< Pair< ViewId, ViewId > > getActiveLinks()
	{
		return activeLinks;
	}

	@Override
	public String getColumnName(int column)
	{
		return new String[]{"ViewID A", "ViewID B", "cross correlation", "shift"}[column];
	}


	private List<Pair<ViewId, ViewId>> activeLinks;
	private StitchingResults results;
	
	public LinkExplorerTableModel()
	{
		activeLinks = new ArrayList<>();
	}
	
	public void setActiveLinks(List<Pair<ViewId, ViewId>> links)
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
		switch ( columnIndex ) {
		case 0:
			return results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).pair().getA().getViewSetupId();
		case 1:
			return results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).pair().getB().getViewSetupId();
		case 2:
			return results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).r();
		case 3:
			double[] shift = results.getPairwiseResults().get( activeLinks.get( rowIndex ) ).relativeVector();
			
			StringBuilder res = new StringBuilder();
			// round to 3 decimal places
			DecimalFormat df = new DecimalFormat( "#.###" );
			df.setRoundingMode( RoundingMode.HALF_UP );

			res.append(df.format( shift[0]) );
			res.append(", ");
			res.append(df.format(shift[1]) );
			res.append(", ");
			res.append(df.format( shift[2]) );
			return res.toString();
			
		default:
			return "";
		}
	}
	
	

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.results = res;		
	}
	
	
}
