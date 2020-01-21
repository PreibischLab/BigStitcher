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
package net.preibisch.mvrecon.process.boundingbox;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JOptionPane;

import bdv.BigDataViewer;
import bdv.tools.InitializeViewerState;
import bdv.tools.boundingbox.BoundingBoxDialog;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.ValuePair;
import net.preibisch.mvrecon.fiji.plugin.apply.BigDataViewerTransformationWindow;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.BDVPopup;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgLoader;

public class BoundingBoxBigDataViewer implements BoundingBoxEstimation
{
	final SpimData spimData;
	final Collection< ViewId > views;

	public static int[] defaultMin, defaultMax;

	public BoundingBoxBigDataViewer(
			final SpimData spimData,
			final Collection< ViewId > views )
	{
		this.spimData = spimData;
		this.views = views;

		SpimData2.filterMissingViews( spimData, views );
	}

	@Override
	public BoundingBox estimate( final String title )
	{
		// defines the range for the BDV bounding box
		final BoundingBox maxBB = new BoundingBoxMaximal( views, spimData ).estimate( "Maximum bounding box used for initalization" );
		IOFunctions.println( maxBB );

		final Pair< BigDataViewer, Boolean > bdvPair = getBDV( spimData, views );
		
		if ( bdvPair == null || bdvPair.getA() == null )
			return null;

		final BigDataViewer bdv = bdvPair.getA();

		// =============== the bounding box dialog ==================
		final AtomicBoolean lock = new AtomicBoolean( false );

		final int[] min, max;

		if ( defaultMin != null && defaultMax != null && defaultMin.length == maxBB.getMin().length && defaultMax.length == maxBB.getMax().length )
		{
			min = defaultMin.clone();
			max = defaultMax.clone();
		}
		else
		{
			min = maxBB.getMin().clone();
			max = maxBB.getMax().clone();
		}

		for ( int d = 0; d < min.length; ++d )
		{
			if ( min[ d ] > max[ d ] )
				min[ d ] = max[ d ];
	
			if ( min[ d ] < maxBB.getMin()[ d ] )
				min[ d ] = maxBB.getMin()[ d ];
	
			if ( max[ d ] > maxBB.getMax()[ d ] )
				max[ d ] = maxBB.getMax()[ d ];
		}

		final int boxSetupId = 9999; // some non-existing setup id
		final Interval initialInterval = Intervals.createMinMax( min[ 0 ], min[ 1 ], min[ 2 ], max[ 0 ], max[ 1 ], max[ 2 ] ); // the initially selected bounding box
		final Interval rangeInterval = Intervals.createMinMax(
				maxBB.getMin()[ 0 ], maxBB.getMin()[ 1 ], maxBB.getMin()[ 2 ],
				maxBB.getMax()[ 0 ], maxBB.getMax()[ 1 ], maxBB.getMax()[ 2 ] ); // the range (bounding box of possible bounding boxes)

		final BoundingBoxDialog boundingBoxDialog =
				new BoundingBoxDialog( bdv.getViewerFrame(), "bounding box", bdv.getViewer(), bdv.getSetupAssignments(), boxSetupId, initialInterval, rangeInterval )
		{
			@Override
			public void createContent()
			{
				// button prints the bounding box interval
				final JButton button = new JButton( "ok" );
				button.addActionListener( new AbstractAction()
				{
					private static final long serialVersionUID = 1L;

					@Override
					public void actionPerformed( final ActionEvent e )
					{
						setVisible( false );
						System.out.println( "bounding box:" + BoundingBoxTools.printInterval( boxRealRandomAccessible.getInterval() ) );

						for ( int d = 0; d < min.length; ++ d )
						{
							min[ d ] = (int)boxRealRandomAccessible.getInterval().realMin( d );
							max[ d ] = (int)boxRealRandomAccessible.getInterval().realMax( d );
						}

						lock.set( true );

						try
						{
							synchronized ( lock ) { lock.notifyAll(); }
						}
						catch (Exception e1) {}

					}
				} );

				getContentPane().add( boxSelectionPanel, BorderLayout.NORTH );
				getContentPane().add( button, BorderLayout.SOUTH );
				pack();
			}

			private static final long serialVersionUID = 1L;
		};

		boundingBoxDialog.setVisible( true );

		do
		{
			try
			{
				synchronized ( lock ) { lock.wait(); }
			}
			catch (Exception e) {}
		}
		while ( lock.get() == false );

		final BoundingBox bdvBB = new BoundingBox( title, min, max );
		IOFunctions.println( bdvBB );

		defaultMin = min.clone();
		defaultMax = max.clone();

		// was locally opened?
		if ( bdvPair.getB() )
			BigDataViewerTransformationWindow.disposeViewerWindow( bdv );

		return bdvBB;
	}

	public static Pair< BigDataViewer, Boolean > getBDV( final AbstractSpimData< ? > spimData, final Collection< ViewId > viewIdsToProcess )
	{
		final BDVPopup popup = ViewSetupExplorerPanel.currentInstance == null ? null : ViewSetupExplorerPanel.currentInstance.bdvPopup();
		BigDataViewer bdv;
		boolean bdvIsLocal = false;

		if ( popup == null || popup.panel == null )
		{
			// locally run instance
			if ( AbstractImgLoader.class.isInstance( spimData.getSequenceDescription().getImgLoader() ) )
			{
				if ( JOptionPane.showConfirmDialog( null,
						"Opening <SpimData> dataset that is not suited for interactive browsing.\n" +
						"Consider resaving as HDF5 for better performance.\n" +
						"Proceed anyways?",
						"Warning",
						JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
					return null;
			}

			bdv = BigDataViewer.open( spimData, "BigDataViewer", IOFunctions.getProgressWriter(), ViewerOptions.options() );
			bdvIsLocal = true;

//			if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
				InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );

			final List< BasicViewDescription< ? > > vds = new ArrayList< BasicViewDescription< ? > >();

			for ( final ViewId viewId : viewIdsToProcess )
				vds.add( spimData.getSequenceDescription().getViewDescriptions().get( viewId ) );

			final List<List< BasicViewDescription< ? > >> vdsWrapped = new ArrayList<>();
			vdsWrapped.add( vds );
			ViewSetupExplorerPanel.updateBDV( bdv, true, spimData, null, vdsWrapped );
		}
		else if ( popup.bdv == null )
		{
			// if BDV was closed by the user
			if ( popup.bdv != null && !popup.bdv.getViewerFrame().isVisible() )
				popup.bdv = null;

			try
			{
				bdv = popup.bdv = BDVPopup.createBDV( popup.panel );
			}
			catch (Exception e)
			{
				IOFunctions.println( "Could not run BigDataViewer: " + e );
				e.printStackTrace();
				bdv = popup.bdv = null;
			}
		}
		else
		{
			bdv = popup.bdv;
		}

		return new ValuePair< BigDataViewer, Boolean >( bdv, bdvIsLocal );
	}

}
