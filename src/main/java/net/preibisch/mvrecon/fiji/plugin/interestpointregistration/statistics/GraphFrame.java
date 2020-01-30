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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration.statistics;

import ij.gui.GUI;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;

import mpicbg.spim.data.sequence.TimePoints;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

public class GraphFrame extends JFrame implements ActionListener
{
	private static final long serialVersionUID = 1L;

	JFreeChart chart = null;

	ChartPanel chartPanel = null;
	MouseListenerTimelapse mouseListener;
	JPanel mainPanel;

	public GraphFrame(
			final TimePoints timepoints,
			final JFreeChart chart,
			final int referenceTimePoint,
			final boolean enableReferenceTimePoint,
			final List< SelectTimepointEntry > extraMenuItems,
			final ArrayList< RegistrationStatistics > data )
	{
		super();

		mainPanel = new JPanel();
		mainPanel.setLayout( new BorderLayout() );

		updateWithNewChart( timepoints, chart, true, extraMenuItems, data, referenceTimePoint, enableReferenceTimePoint );

		JPanel buttonsPanel = new JPanel();
		mainPanel.add( buttonsPanel, BorderLayout.SOUTH );

		setContentPane( mainPanel );
		validate();
		GUI.center( this );
	}
	
	public int getReferenceTimePoint() { return mouseListener.getReferenceTimePoint(); }

	synchronized public void updateWithNewChart(
			final TimePoints timepoints,
			final JFreeChart c,
			final boolean setSize,
			final List< SelectTimepointEntry > extraMenuItems,
			final ArrayList< RegistrationStatistics > data,
			final int referenceTimePoint,
			final boolean enableReferenceTimePoint )
	{
		if ( chartPanel != null )
			remove( chartPanel );

		chartPanel = null;
		this.chart = c;
		chartPanel = new ChartPanel( c );
		mouseListener = new MouseListenerTimelapse( timepoints, chartPanel, referenceTimePoint, enableReferenceTimePoint );

		chartPanel.addChartMouseListener( mouseListener );

		chartPanel.setMouseWheelEnabled( true );
		chartPanel.setHorizontalAxisTrace( true );
		mainPanel.add( chartPanel, BorderLayout.CENTER );

		// add extra items
		final JPopupMenu menu = chartPanel.getPopupMenu();
		
		if ( extraMenuItems != null )
			for ( final SelectTimepointEntry m : extraMenuItems )
			{
				m.setChartPanel( chartPanel );
				menu.add( new JMenuItem( m ) );
			}

		validate();
	}
	
	@Override
	public void actionPerformed( ActionEvent e )
	{
	}
}
