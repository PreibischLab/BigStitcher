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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.ProgressWriterIJ;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_TIFF.Parameters;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.AbstractImgFactoryImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.MicroManagerImgLoader;

public class ResavePopup extends JMenu implements ExplorerWindowSetable
{
	public static final int askWhenMoreThan = 5;
	private static final long serialVersionUID = 5234649267634013390L;

	FilteredAndGroupedExplorerPanel< ?, ? > panel;

	protected static String[] types = new String[]{ "As TIFF ...", "As compressed TIFF ...", "As HDF5 ...", "As compressed HDF5 ..." };

	public ResavePopup()
	{
		super( "Resave Dataset" );

		final JMenuItem tiff = new JMenuItem( types[ 0 ] );
		final JMenuItem zippedTiff = new JMenuItem( types[ 1 ] );
		final JMenuItem hdf5 = new JMenuItem( types[ 2 ] );
		final JMenuItem deflatehdf5 = new JMenuItem( types[ 3 ] );

		tiff.addActionListener( new MyActionListener( 0 ) );
		zippedTiff.addActionListener( new MyActionListener( 1 ) );
		hdf5.addActionListener( new MyActionListener( 2 ) );
		deflatehdf5.addActionListener( new MyActionListener( 3 ) );

		this.add( tiff );
		this.add( zippedTiff );
		this.add( hdf5 );
		this.add( deflatehdf5 );
	}

	@Override
	public JMenuItem setExplorerWindow(ExplorerWindow<? extends AbstractSpimData<? extends AbstractSequenceDescription<?, ?, ?>>, ?> panel )
	{
		this.panel = (FilteredAndGroupedExplorerPanel< ?, ? >)panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		final int index; // 0 == TIFF, 1 == HDF5

		public MyActionListener( final int index )
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

			if ( !SpimData2.class.isInstance( panel.getSpimData() ) )
			{
				IOFunctions.println( "Only supported for SpimData2 objects: " + this.getClass().getSimpleName() );
				return;
			}

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final SpimData2 data = (SpimData2)panel.getSpimData();

					final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );
					String question;
					
					final boolean notAllSelected = viewIds.size() < data.getSequenceDescription().getViewDescriptions().size();

					// user has not selected all views (or not all views are selectable at once)
					// ask them whether they want to expand selection to all views in SpimData
					if ( notAllSelected )
					{
						question =
							"You have only selected " + viewIds.size() + " of " +
							data.getSequenceDescription().getViewDescriptions().size() + " views for export.\n" +
							"(the rest will not be visible in the new dataset - except they are missing)\n";
						
						final int  choice = JOptionPane.showConfirmDialog( null,
								question + "Note: this will first save the current state of the open XML.\n"
										+ "Do you wish to expand the selection to the whole dataset before continuing?",
								"Warning",
								JOptionPane.YES_NO_CANCEL_OPTION );
						
						if (choice == JOptionPane.CANCEL_OPTION)
							return;
						else if (choice == JOptionPane.YES_OPTION)
						{
							viewIds.clear();
							viewIds.addAll( data.getSequenceDescription().getViewDescriptions().keySet() );
						}
					}
					
					// all views in SpimData have been selected, ask user for confirmation before starting resave.
					else
					{
						question = "Resaving all (except missing) views of the current dataset.\n";

					if ( JOptionPane.showConfirmDialog( null,
							question + "Note: this will first save the current state of the open XML. Proceed?",
							"Warning",
							JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
						return;
					}

					// filter not present ViewIds
					final List< ViewId > removed = SpimData2.filterMissingViews( panel.getSpimData(), viewIds );
					if ( removed.size() > 0 ) IOFunctions.println( "(" + new Date( System.currentTimeMillis()) + "): Removed " + removed.size() + " missing views from the list before saving." );

					final ProgressWriter progressWriter = new ProgressWriterIJ();
					progressWriter.out().println( "Resaving " + viewIds.size() + " views " + types[ index ] );

					if ( index < 2 ) // zip, compressed zip
					{
						panel.saveXML();

						final Parameters params = new Parameters();

						params.compress = index != 0;

						if ( AbstractImgFactoryImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
							params.imgFactory = ( (AbstractImgFactoryImgLoader)data.getSequenceDescription().getImgLoader() ).getImgFactory();
						else if ( MicroManagerImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() ) )
							params.imgFactory = new ArrayImgFactory< FloatType >();
						else
							params.imgFactory = new CellImgFactory< FloatType >();

						params.xmlFile = panel.xml();

						// write the TIFF's
						Resave_TIFF.writeTIFF( data, viewIds, new File( params.xmlFile ).getParent(), params.compress, progressWriter );
	
						// write the XML
						final Pair< SpimData2, List< String > > result = Resave_TIFF.createXMLObject( data, viewIds, params );
						progressWriter.setProgress( 1.01 );

						// copy the interest points is not necessary as we overwrite the XML if they exist
						// Resave_TIFF.copyInterestPoints( data.getBasePath(), new File( params.xmlFile ).getParentFile(), result.getB() );

						// replace the spimdata object
						panel.setSpimData( result.getA() );
						panel.updateContent();
						panel.saveXML();
					}
					else if ( index == 2 || index == 3 ) // HDF5, compressed HDF5
					{
						final List< ViewSetup > setups = SpimData2.getAllViewSetupsSorted( data, viewIds );
						
						// load all dimensions if they are not known (required for estimating the mipmap layout)
						Resave_HDF5.loadDimensions( data, setups );

						panel.saveXML();

						final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( setups );
						final int firstviewSetupId = data.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
						final ExportMipmapInfo autoMipmapSettings = perSetupExportMipmapInfo.get( firstviewSetupId );

						final boolean compress = (index != 2);

						final String hdf5Filename = panel.xml().substring( 0, panel.xml().length() - 4 ) + ".h5";
						final File hdf5File = new File( hdf5Filename );
						IOFunctions.println( "HDF5 file: " + hdf5File.getAbsolutePath() );

						final Generic_Resave_HDF5.Parameters params =
								new Generic_Resave_HDF5.Parameters(
										false,
										autoMipmapSettings.getExportResolutions(),
										autoMipmapSettings.getSubdivisions(),
										new File( panel.xml() ),
										hdf5File,
										compress,
										false,
										1,
										0,
										false,
										0,
										0, Double.NaN, Double.NaN );

						// write hdf5
						Generic_Resave_HDF5.writeHDF5( Resave_HDF5.reduceSpimData2( data, viewIds ), params, progressWriter );

						final Pair< SpimData2, List< String > > result = Resave_HDF5.createXMLObject( data, viewIds, params, progressWriter, true );

						// copy the interest points is not necessary as we overwrite the XML if they exist
						// Resave_TIFF.copyInterestPoints( xml.getData().getBasePath(), params.getSeqFile().getParentFile(), result.getB() );

						// replace the spimdata object
						panel.setSpimData( result.getA() );
						panel.updateContent();

						progressWriter.setProgress( 1.0 );
						panel.saveXML();
						progressWriter.out().println( "done" );
					}
				}
			} ).start();
		}
	}
}
