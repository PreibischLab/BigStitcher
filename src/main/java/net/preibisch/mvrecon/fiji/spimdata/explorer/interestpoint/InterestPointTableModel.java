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
package net.preibisch.mvrecon.fiji.spimdata.explorer.interestpoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import bdv.BigDataViewer;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RealLocalizable;
import net.imglib2.realtransform.AffineTransform3D;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorerHelper;
import net.preibisch.mvrecon.fiji.spimdata.explorer.interestpoint.InterestPointOverlay.InterestPointSource;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BasicBDVPopup;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.CorrespondingInterestPoints;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPointList;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class InterestPointTableModel extends AbstractTableModel implements InterestPointSource
{
	private static final long serialVersionUID = -1263388435427674269L;
	
	ViewInterestPoints viewInterestPoints;
	final ArrayList< String > columnNames;

	List< BasicViewDescription< ? > > currentVDs;
	final InterestPointExplorerPanel panel;

	private int selectedRow = -1;
	private int selectedCol = -1;

	final ArrayList< InterestPointSource > interestPointSources;
	volatile InterestPointOverlay interestPointOverlay = null;

	HashMap< ViewId, Collection< ? extends RealLocalizable > > points = new HashMap<>();

	public InterestPointTableModel( final ViewInterestPoints viewInterestPoints, final InterestPointExplorerPanel panel )
	{
		this.columnNames = new ArrayList< String >();

		this.columnNames.add( "Interest Point Label" );
		this.columnNames.add( "#Detections" );
		this.columnNames.add( "#Corresponding" );
		this.columnNames.add( "#Correspondences" );
		this.columnNames.add( "Present in Views" );
		this.columnNames.add( "Parameters" );

		this.viewInterestPoints = viewInterestPoints;
		this.currentVDs = new ArrayList<>();
		this.panel = panel;

		this.interestPointSources = new ArrayList< InterestPointSource >();
		this.interestPointSources.add( this );

	}

	protected void update( final ViewInterestPoints viewInterestPoints ) { this.viewInterestPoints = viewInterestPoints; }
	protected ViewInterestPoints getViewInterestPoints() { return viewInterestPoints; }
	protected List< BasicViewDescription< ? > > getCurrentViewDescriptions() { return currentVDs; } 
	
	protected void updateViewDescription( final List< BasicViewDescription< ? > > vds )
	{
		this.currentVDs = vds;

		// update everything
		fireTableDataChanged();

		setSelected( selectedRow, selectedCol );
	}

	@Override
	public int getColumnCount() { return columnNames.size(); }
	
	@Override
	public int getRowCount()
	{
		if ( currentVDs.size() == 0 )
			return 1;
		else
			return Math.max( 1, InterestPointTools.getAllInterestPointMap( viewInterestPoints, currentVDs ).keySet().size() );
	}

	@Override
	public boolean isCellEditable( final int row, final int column )
	{
		if ( column == 5 )
			return true;
		else
			return false;
	}

	@Override
	public void setValueAt( final Object value, final int row, final int column ) {}

	public static String label( final HashMap< String, Integer > labelMap, final int row )
	{
		final ArrayList< String > labels = new ArrayList< String >();
		labels.addAll( labelMap.keySet() );
		Collections.sort( labels );

		if ( row >= labels.size() )
			return null;
		else
			return labels.get( row );
	}

	@Override
	public Object getValueAt( final int row, final int column )
	{
		if ( currentVDs == null || currentVDs.size() == 0 )
			return column == 0 ? "No View Description selected" : "";

		final HashMap< String, Integer > labels = InterestPointTools.getAllInterestPointMap( viewInterestPoints, currentVDs );

		if ( labels.keySet().size() == 0 )
		{
			return column == 0 ? "No interest points segmented" : "";
		}
		else
		{
			final String label = label( labels, row );

			if ( column == 0 )
				return label;
			else if ( column == 1 )
				return numDetections( viewInterestPoints, currentVDs, label );
			else if ( column == 2 )
				return numCorresponding( viewInterestPoints, currentVDs, label );
			else if ( column == 3 )
				return numCorrespondences( viewInterestPoints, currentVDs, label );
			else if ( column == 4 )
				return findNumPresent( labels, currentVDs, label );
			else if ( column == 5 )
				return getParameters( viewInterestPoints, currentVDs, label );
			else
				return -1;
		}
	}

	protected String getParameters( final ViewInterestPoints vip, final List< ? extends ViewId > views, final String label )
	{
		if ( !vip.getViewInterestPointLists( views.get( 0 ) ).getHashMap().containsKey( label ) )
			return "Not present in all views.";

		final String parameters = vip.getViewInterestPointLists( views.get( 0 ) ).getInterestPointList( label ).getParameters();

		for ( final ViewId v : views )
		{
			if ( !vip.getViewInterestPointLists( v ).getHashMap().containsKey( label ) )
			{
				return "Not present in all views.";
			}

			if ( !vip.getViewInterestPointLists( v ).getInterestPointList( label ).getParameters().equals( parameters ) )
			{
				return "Different types of parameters used for detection, cannot display.";
			}
		}

		return parameters;
	}

	protected int numCorresponding( final ViewInterestPoints vip, final List< ? extends ViewId > views, final String label )
	{
		int sum = 0;

		for ( final ViewId v : views )
		{
			final HashSet< Integer > cips = new HashSet< Integer >();

			if ( vip.getViewInterestPointLists( v ).getHashMap().containsKey( label ) )
				for ( final CorrespondingInterestPoints c : vip.getViewInterestPointLists( v ).getInterestPointList( label ).getCorrespondingInterestPointsCopy() )
					cips.add( c.getDetectionId() );
	
			sum += cips.size();
		}

		return sum;
	}

	protected String findNumPresent( final HashMap< String, Integer > labels, final List< ? extends ViewId > views, final String label )
	{
		final int num = labels.get( label );
		final int total = views.size();

		return num + "/" + total;
	}

	protected int numCorrespondences( final ViewInterestPoints vip, final List< ? extends ViewId > views, final String label )
	{
		int sum = 0;

		for ( final ViewId v : views )
			if ( vip.getViewInterestPointLists( v ).getHashMap().containsKey( label ) )
				sum += vip.getViewInterestPointLists( v ).getInterestPointList( label ).getCorrespondingInterestPointsCopy().size();

		return sum;
	}

	protected int numDetections( final ViewInterestPoints vip, final List< ? extends ViewId > views, final String label )
	{
		int sum = 0;

		for ( final ViewId v : views )
			if ( vip.getViewInterestPointLists( v ).getHashMap().containsKey( label ) )
				sum += vip.getViewInterestPointLists( v ).getInterestPointList( label ).getInterestPointsCopy().size();

		return sum;
	}

	@Override
	public String getColumnName( final int column )
	{
		return columnNames.get( column );
	}

	public boolean getState( final int row, final int column )
	{
		if ( row == selectedRow && column == selectedCol )
			return true;
		else
			return false;
	}

	public void setSelected( final int row, final int col )
	{
		final BasicBDVPopup bdvPopup = panel.viewSetupExplorer.getPanel().bdvPopup();

		if ( currentVDs != null && currentVDs.size() != 0 && bdvPopup.bdvRunning() && row >= 0 && row < getRowCount() && col >= 1 && col <= 2  )
		{
			this.selectedRow = row;
			this.selectedCol = col;

			final String label = label( InterestPointTools.getAllInterestPointMap( viewInterestPoints, currentVDs ), row );

			if ( label == null )
			{
				this.selectedRow = this.selectedCol = -1;
				this.points = new HashMap<>();
			}
			else if ( col == 1 )
			{
				this.points = new HashMap<>();

				for ( final ViewId v : currentVDs )
					this.points.put( v, viewInterestPoints.getViewInterestPointLists( v ).getInterestPointList( label ).getInterestPointsCopy() );
			}
			else //if ( col == 2 )
			{
				for ( final ViewId v : currentVDs )
				{
					System.out.println( Group.pvid( v ) );
					final HashMap< Integer, InterestPoint > map = new HashMap< Integer, InterestPoint >();

					final InterestPointList ipList = viewInterestPoints.getViewInterestPointLists( v ).getInterestPointList( label );

					for ( final InterestPoint ip : ipList.getInterestPointsCopy() )
						map.put( ip.getId(), ip );

					final Collection< InterestPoint > tmp = new HashSet<>();
	
					for ( final CorrespondingInterestPoints ip : ipList.getCorrespondingInterestPointsCopy() )
					{
						if ( !map.containsKey( ip.getDetectionId() ) )
						{
							IOFunctions.println( "Inconsistency in the interest points of view: " + Group.pvid( v ) );
							IOFunctions.println( "Cannot find interestpoint for id = " + ip.getDetectionId() );
						}
						else
						{
							tmp.add( map.get( ip.getDetectionId() ) );
						}
					}

					points.put( v, tmp );
				}
			}

			if ( interestPointOverlay == null )
			{
				final BigDataViewer bdv = bdvPopup.getBDV();
				interestPointOverlay = new InterestPointOverlay( bdv.getViewer(), interestPointSources );
				bdv.getViewer().addRenderTransformListener( interestPointOverlay );
				bdv.getViewer().getDisplay().addOverlayRenderer( interestPointOverlay );
				bdvPopup.updateBDV();
			}
		}
		else
		{
			this.selectedRow = this.selectedCol = -1;
			this.points = new HashMap<>();
		}

		if ( bdvPopup.bdvRunning() )
			bdvPopup.updateBDV();
	}

	public int getSelectedRow() { return selectedRow; }
	public int getSelectedCol() { return selectedCol; }

	public List< BasicViewDescription< ? > > filteredViewIdsCurrentTimepoint( final int timepointIndex )
	{
		final ArrayList< BasicViewDescription< ? > > currentlyVisible = new ArrayList<>();

		for ( final BasicViewDescription< ? > viewId : currentVDs )
			if ( timepointIndex == ViewSetupExplorerHelper.getBDVTimePointIndex( viewId.getTimePoint(), panel.viewSetupExplorer.getSpimData() ) )
				currentlyVisible.add( viewId );

		return currentlyVisible;
	}

	@Override
	public HashMap< ? extends ViewId, ? extends Collection< ? extends RealLocalizable > > getLocalCoordinates( final int timepointIndex )
	{
		final HashMap< ViewId, Collection< ? extends RealLocalizable > > coords = new HashMap<>();
		final List< BasicViewDescription< ? > > currentlyVisible = filteredViewIdsCurrentTimepoint( timepointIndex );

		if ( currentlyVisible == null || currentlyVisible.size() == 0 )
			return coords;

		for ( final ViewId viewId : currentlyVisible )
			if ( points.containsKey( viewId ) )
				coords.put( viewId, points.get( viewId ) );

		return coords;
	}

	@Override
	public void getLocalToGlobalTransform( final ViewId viewId, final int timepointIndex, final AffineTransform3D transform )
	{
		if ( currentVDs != null )
		{
			final ViewRegistration vr = panel.viewSetupExplorer.getSpimData().getViewRegistrations().getViewRegistration( viewId );
			vr.updateModel();
			transform.set( vr.getModel() );
		}
	}
}
