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
package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.plugin.CreateFromCorresponding_Detections;
import net.preibisch.mvrecon.fiji.plugin.RelativeThinOut_Detections;
import net.preibisch.mvrecon.fiji.plugin.Show_Relative_Histogram;
import net.preibisch.mvrecon.fiji.plugin.ThinOut_Detections;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPointLists;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.ViewInterestPoints;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.mvrecon.process.interestpointremoval.DistanceHistogram;
import net.preibisch.mvrecon.process.interestpointremoval.InteractiveProjections;

public class RemoveDetectionsPopup extends JMenu implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 1L;

	ExplorerWindow< ?, ? > panel = null;

	public static int defaultLabel = 0;
	public static String defaultNewLabel = "Manually removed";

	public RemoveDetectionsPopup()
	{
		super( "Manage Interest Points" );

		final JMenuItem createFromCorr = new JMenuItem( "New interest points from correspondences ..." );
		final JMenu showDistanceHist = new JMenu( "Show Distance Histogram" );
		final JMenuItem showRelativeDistanceHist = new JMenuItem( "Show Relative Distance Histogram ... " );
		final JMenuItem byDistance = new JMenuItem( "Remove by Distance ..." );
		final JMenuItem byRelativeDistance = new JMenuItem( "Remove by relative Distance ..." );
		final JMenuItem interactivelyXY = new JMenuItem( "Remove Interactively (XY Projection) ..." );
		final JMenuItem interactivelyXZ = new JMenuItem( "Remove Interactively (XZ Projection) ..." );
		final JMenuItem interactivelyYZ = new JMenuItem( "Remove Interactively (YZ Projection) ..." );

		createFromCorr.addActionListener( new CreateFromCorrespondencesListener() );
		this.add( createFromCorr );

		this.add( new Separator() );

		showDistanceHist.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected( MenuEvent e )
			{
				showDistanceHist.removeAll();

				final SpimData2 spimData = (SpimData2)panel.getSpimData();

				final ArrayList< ViewId > views = new ArrayList<>();
				views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

				// filter not present ViewIds
				SpimData2.filterMissingViews( panel.getSpimData(), views );

				final String[] labels = InterestPointTools.getAllInterestPointLabels( spimData, views );

				if ( labels.length == 0 )
				{
					JMenuItem item = new JMenuItem( "No interest points found" );
					item.setForeground( Color.GRAY );
					showDistanceHist.add( item );
				}
				else
				{
					for ( int i = 0; i < labels.length; ++i )
					{
						JMenuItem item = new JMenuItem( labels[ i ] );
						item.addActionListener( new HistogramListener( spimData, views, InterestPointTools.getSelectedLabel( labels, i ), i ) );
						showDistanceHist.add( item );
					}
				}
			}

			@Override
			public void menuDeselected( MenuEvent e ) {}

			@Override
			public void menuCanceled( MenuEvent e ) {}
		} );
		this.add( showDistanceHist );

		showRelativeDistanceHist.addActionListener( new RelativeDistanceHistogramListener() );
		this.add( showRelativeDistanceHist );

		this.add( new Separator() );

		byDistance.addActionListener( new ThinOutListener() );
		this.add( byDistance );
		byRelativeDistance.addActionListener( new RelativeThinOutListener() );
		this.add( byRelativeDistance );

		interactivelyXY.addActionListener( new InteractiveListener( 1 ) );
		interactivelyXZ.addActionListener( new InteractiveListener( 2 ) );
		interactivelyYZ.addActionListener( new InteractiveListener( 3 ) );
		this.add( interactivelyXY );
		this.add( interactivelyXZ );
		this.add( interactivelyYZ );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;

		return this;
	}

	public class HistogramListener implements ActionListener
	{
		final SpimData2 spimData;
		final ArrayList< ViewId > views;
		final String label;
		final int i;

		public HistogramListener( final SpimData2 spimData, final ArrayList< ViewId > views, final String label, final int i )
		{
			this.spimData = spimData;
			this.views = views;
			this.label = label;
			this.i = i;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					// suggest to thin out this one when calling the plugin
					ThinOut_Detections.defaultLabel = i;

					DistanceHistogram.plotHistogram( spimData, views, label, DistanceHistogram.getHistogramTitle( views ) );
				}
			} ).start();
		}
	}

	public class ThinOutListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			final SpimData2 spimData = (SpimData2)panel.getSpimData();

			final ArrayList< ViewId > views = new ArrayList<>();
			views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

			// filter not present ViewIds
			SpimData2.filterMissingViews( spimData, views );

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( ThinOut_Detections.thinOut( spimData, views ) )
						panel.updateContent(); // update interestpoint panel if available
				}
			} ).start();

		}
	}

	public class RelativeThinOutListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			final SpimData2 spimData = (SpimData2)panel.getSpimData();

			final ArrayList< ViewId > views = new ArrayList<>();
			views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

			// filter not present ViewIds
			SpimData2.filterMissingViews( spimData, views );

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( RelativeThinOut_Detections.thinOut( spimData, views ) )
						panel.updateContent(); // update interestpoint panel if available
				}
			} ).start();

		}
	}

	public class RelativeDistanceHistogramListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			final SpimData2 spimData = (SpimData2)panel.getSpimData();

			final ArrayList< ViewId > views = new ArrayList<>();
			views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

			// filter not present ViewIds
			SpimData2.filterMissingViews( spimData, views );

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( Show_Relative_Histogram.plotHistogram( spimData, views ) )
						panel.updateContent(); // update interestpoint panel if available
				}
			} ).start();

		}
	}

	public class CreateFromCorrespondencesListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			final SpimData2 spimData = (SpimData2)panel.getSpimData();

			final ArrayList< ViewId > views = new ArrayList<>();
			views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

			// filter not present ViewIds
			SpimData2.filterMissingViews( spimData, views );

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					if ( CreateFromCorresponding_Detections.create( spimData, views ) )
						panel.updateContent(); // update interestpoint panel if available
				}
			} ).start();

		}
	}

	public class InteractiveListener implements ActionListener
	{
		final int index;

		public InteractiveListener( final int index )
		{
			this.index = index;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			final SpimData2 spimData = (SpimData2)panel.getSpimData();

			final ArrayList< ViewId > views = new ArrayList<>();
			views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

			// filter not present ViewIds
			SpimData2.filterMissingViews( panel.getSpimData(), views );

			if ( views.size() != 1 )
			{
				JOptionPane.showMessageDialog( null, "Interactive Removal of Detections only supports a single view at a time." );
				return;
			}

			final Pair< String, String > labels = queryLabelAndNewLabel( (SpimData2)panel.getSpimData(), views.get( 0 ) );

			if ( labels == null )
				return;

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final InteractiveProjections ip = InteractiveProjections.removeInteractively( spimData, views.get( 0 ), index - 1, labels.getA(), labels.getB() );

					do
					{
						try { Thread.sleep( 100 ); } catch ( InterruptedException e ) {}
					}
					while ( ip.isRunning() );

					panel.updateContent(); // update interestpoint panel if available
				}
			} ).start();
		}
	}

	private static Pair< String, String > queryLabelAndNewLabel( final SpimData2 spimData, final ViewId vd )
	{
		final ViewInterestPoints interestPoints = spimData.getViewInterestPoints();
		final ViewInterestPointLists lists = interestPoints.getViewInterestPointLists( vd );

		if ( lists.getHashMap().keySet().size() == 0 )
		{
			IOFunctions.println( "No interest points available for view: " + Group.pvid( vd ) );
			return null;
		}

		final String[] labels = new String[ lists.getHashMap().keySet().size() ];

		int i = 0;
		for ( final String label : lists.getHashMap().keySet() )
			labels[ i++ ] = label;

		if ( defaultLabel >= labels.length )
			defaultLabel = 0;

		Arrays.sort( labels );

		final GenericDialog gd = new GenericDialog( "Select Interest Points To Remove" );

		gd.addChoice( "Interest_Point_Label", labels, labels[ defaultLabel ]);
		gd.addStringField( "New_Label", defaultNewLabel, 20 );

		gd.showDialog();
		if ( gd.wasCanceled() )
			return null;

		final String label = labels[ defaultLabel = gd.getNextChoiceIndex() ];
		final String newLabel = gd.getNextString();

		return new ValuePair< String, String >( label, newLabel );
	}
}
