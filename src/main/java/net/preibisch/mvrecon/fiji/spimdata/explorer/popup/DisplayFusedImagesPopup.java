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
import java.util.Date;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Interval;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.util.MouseOverPopUpStateChanger;
import net.preibisch.mvrecon.fiji.plugin.util.MouseOverPopUpStateChanger.StateChanger;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.process.boundingbox.BoundingBoxTools;
import net.preibisch.mvrecon.process.fusion.FusionDisplayHelper;
import net.preibisch.mvrecon.process.fusion.FusionTools;
import net.preibisch.mvrecon.process.fusion.FusionTools.ImgDataType;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;

public class DisplayFusedImagesPopup extends JMenu implements ExplorerWindowSetable
{
	public static int[] quickDownsampling = new int[]{ 1, 2, 3, 4, 8, 16 };
	public static int defaultCache = 0;
	public static int[] cellDim = new int[]{ 100, 100, 1 };
	public static int maxCacheSize = 100000;

	public static int defaultInterpolation = 1;
	public static boolean defaultUseBlending = true;

	private static final long serialVersionUID = -4895470813542722644L;

	ExplorerWindow< ?, ? > panel = null;

	public DisplayFusedImagesPopup()
	{
		super( "Quick Display Transformed/Fused Image(s)" );

		final JMenu boundingBoxes = this;

		// populate with the current available boundingboxes
		this.addMenuListener( new MenuListener()
		{
			@Override
			public void menuSelected( MenuEvent e )
			{
				if ( panel != null )
				{
					boundingBoxes.removeAll();

					final SpimData2 spimData = (SpimData2)panel.getSpimData();

					final ArrayList< ViewId > views = new ArrayList<>();
					views.addAll( ApplyTransformationPopup.getSelectedViews( panel ) );

					// filter not present ViewIds
					SpimData2.filterMissingViews( panel.getSpimData(), views );

					for ( final BoundingBox bb : BoundingBoxTools.getAllBoundingBoxes( spimData, views, true ) )
					{
						final JMenu downsampleOptions = new JMenu( bb.getTitle() + " [" + bb.dimension( 0 ) + "x" + bb.dimension( 1 ) + "x" + bb.dimension( 2 ) + "px]" );

						for ( final int ds : quickDownsampling )
						{
							final JMenuItem fused;
							final double downsample;

							if ( ds == 1 )
							{
								fused = new JMenuItem( "Not downsampled" );
								downsample = Double.NaN;
							}
							else
							{
								fused = new JMenuItem( "Downsampled " + ds + "x" );
								downsample = ds;
							}

							fused.addActionListener( new DisplayVirtualFused( spimData, views, bb, downsample, ImgDataType.values()[ defaultCache ] ) );
							downsampleOptions.add( fused );
						}
						boundingBoxes.add( downsampleOptions );
					}

					boundingBoxes.add( new Separator() );

					final JMenuItem[] items = new JMenuItem[ FusionTools.imgDataTypeChoice.length ];
					final StateChanger typeStateChanger = new StateChanger() { public void setSelectedState( int state ) { defaultCache = state; } };

					for ( int i = 0; i < items.length; ++i )
					{
						final JMenuItem item = new JMenuItem( FusionTools.imgDataTypeChoice[ i ] );

						if ( i == defaultCache )
							item.setForeground( Color.RED );
						else
							item.setForeground( Color.GRAY );

						items[ i ] = item;
					}

					for ( int i = 0; i < items.length; ++i )
					{
						final JMenuItem item = items[ i ];
						final MouseOverPopUpStateChanger mopusc = new MouseOverPopUpStateChanger( items, i, typeStateChanger );
						item.addActionListener( mopusc );
						item.addMouseListener( mopusc );
						boundingBoxes.add( item );
					}

				}
			}

			@Override
			public void menuDeselected( MenuEvent e ) {}

			@Override
			public void menuCanceled( MenuEvent e ) {}
		} );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ?, ? > panel )
	{
		this.panel = panel;

		return this;
	}

	public class DisplayVirtualFused implements ActionListener
	{
		final SpimData spimData;
		final ArrayList< ViewId > views;
		final Interval bb;
		final double downsampling;
		final ImgDataType imgType;

		public DisplayVirtualFused( final SpimData spimData, final ArrayList< ViewId > views, final Interval bb, final double downsampling, final ImgDataType imgType )
		{
			this.spimData = spimData;
			this.views = views;
			this.bb = bb;
			this.downsampling = downsampling;
			this.imgType = imgType;
		}

		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Fusing " + views.size() + ", downsampling=" + DownsampleTools.printDownsampling( downsampling ) + ", caching strategy=" + imgType );
					final ImagePlus imp = FusionDisplayHelper.display( FusionTools.fuseVirtual( spimData, views, defaultUseBlending, false, defaultInterpolation, bb, downsampling, null ).getA(), imgType );

					if ( imp.getStack().getSize() > 1 )
					{
						imp.setSlice( Math.max( 1, imp.getStackSize() / 2 ) );
						imp.updateAndRepaintWindow();
					}

					imp.show();

					try
					{
						// update the z-slider without redrawing everything
						final ImageWindow win = imp.getWindow();
						if ( win != null && StackWindow.class.isInstance( win ) )
							((StackWindow)win).updateSliceSelector();
					}
					catch ( Exception e ){}

				}
			} ).start();
		}
	}
}
