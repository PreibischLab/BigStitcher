package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import fiji.util.gui.GenericDialogPlus;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.util.Pair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield.DefaultFlatfieldCorrectionWrappedImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield.FlatfieldCorrectionWrappedImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield.LazyLoadingFlatFieldCorrectionMap;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield.MultiResolutionFlatfieldCorrectionWrappedImgLoader;

public class FlatFieldCorrectionPopup extends JMenuItem implements ExplorerWindowSetable
{

	private static final long serialVersionUID = 950277697000203629L;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public FlatFieldCorrectionPopup()
	{
		super( "Flatfield Correction (experimental) ..." );
		addActionListener( new MyActionListener() );
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	private class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{
			new Thread( () -> {
				SpimData2 data = null;

				if ( panel.getSpimData() instanceof SpimData2 )
					data = (SpimData2) panel.getSpimData();

				if ( data == null )
					return;

				final boolean alreadyFF = ( data.getSequenceDescription()
						.getImgLoader() instanceof FlatfieldCorrectionWrappedImgLoader< ? > );

				final ArrayList< Illumination > illums = SpimData2.getAllInstancesOfEntitySorted( data,
						data.getSequenceDescription().getViewDescriptions().keySet(), Illumination.class );
				final ArrayList< Channel > channels = SpimData2.getAllInstancesOfEntitySorted( data,
						data.getSequenceDescription().getViewDescriptions().keySet(), Channel.class );

				final GenericDialogPlus gdp = new GenericDialogPlus( "Flatfield Correction" );

				gdp.addCheckbox( "set_active", alreadyFF
						? ( (FlatfieldCorrectionWrappedImgLoader< ? >) data.getSequenceDescription().getImgLoader() )
								.isActive()
						: true );
				gdp.addCheckbox( "cache_corrected_images", alreadyFF
						? ( (FlatfieldCorrectionWrappedImgLoader< ? >) data.getSequenceDescription().getImgLoader() )
								.isCached()
						: true );

				Map< ViewId, Pair< File, File > > fileMap = null;
				if ( alreadyFF )
					fileMap = ( (LazyLoadingFlatFieldCorrectionMap< ImgLoader >) data.getSequenceDescription()
							.getImgLoader() ).getFileMap();

				for ( Channel c : channels )
					for ( Illumination ill : illums )
					{
						String bright = "";
						String dark = "";
						if ( alreadyFF )
						{
							ViewId anyViewId = data.getSequenceDescription().getViewDescriptions().values().stream()
									.filter( vd -> {
										return vd.getViewSetup().getChannel() == c
												&& vd.getViewSetup().getIllumination() == ill;
									} ).findAny().orElseGet( null );

							if ( anyViewId != null )
								if ( fileMap.containsKey( anyViewId ) )
								{
									Pair< File, File > files = fileMap.get( anyViewId );
									if ( files.getA() != null )
										bright = files.getA().getAbsolutePath();
									if ( files.getB() != null )
										dark = files.getB().getAbsolutePath();
								}
						}
						gdp.addMessage( "Channel: " + c.getName() + ", Illumination: " + ill.getName() + ":" );
						gdp.addFileField( "Bright_Image", bright );
						gdp.addFileField( "Dark_Image", dark );
					}

				gdp.showDialog();
				if ( gdp.wasCanceled() )
					return;

				final boolean activate = gdp.getNextBoolean();
				final boolean cache = gdp.getNextBoolean();

				final FlatfieldCorrectionWrappedImgLoader< ? extends ImgLoader > ffIL;
				if ( alreadyFF )
					ffIL = (FlatfieldCorrectionWrappedImgLoader< ImgLoader >) data.getSequenceDescription()
							.getImgLoader();
				else if ( data.getSequenceDescription().getImgLoader() instanceof MultiResolutionImgLoader )
					ffIL = new MultiResolutionFlatfieldCorrectionWrappedImgLoader(
							(MultiResolutionImgLoader) data.getSequenceDescription().getImgLoader(), cache );
				else
					ffIL = new DefaultFlatfieldCorrectionWrappedImgLoader( data.getSequenceDescription().getImgLoader(),
							cache );

				for ( Channel c : channels )
					for ( Illumination ill : illums )
					{
						String lightPath = gdp.getNextString();
						String darkPath = gdp.getNextString();
						File lightFile = !lightPath.equals( "" ) ? new File( lightPath ) : null;
						File darkFile = !darkPath.equals( "" ) ? new File( darkPath ) : null;

						data.getSequenceDescription().getViewDescriptions().entrySet().forEach( el -> {
							if ( el.getValue().getViewSetup().getChannel() == c
									&& el.getValue().getViewSetup().getIllumination() == ill )
							{
								ffIL.setBrightImage( el.getKey(), lightFile );
								ffIL.setDarkImage( el.getKey(), darkFile );
							}
						} );
					}

				ffIL.setActive( activate );
				ffIL.setCached( cache );

				data.getSequenceDescription().setImgLoader( ffIL );
				gdp.dispose();

				if ( panel.bdvPopup().bdvRunning() )
				{
					// we have to close and re-open BDV for the changed
					// ImgLoader to be recognized
					Dimension oldSize = panel.bdvPopup().getBDV().getViewerFrame().getSize();
					final Point oldLoc = panel.bdvPopup().getBDV().getViewerFrame().getLocation();
					final CountDownLatch latch = new CountDownLatch( 1 );

					final Thread closeTask = new Thread( () -> {
						panel.bdvPopup().closeBDV();
						latch.countDown();
					} );
					closeTask.start();

					try
					{
						latch.await();
					}
					catch ( InterruptedException e1 )
					{
						e1.printStackTrace();
					}

					final CountDownLatch latch2 = new CountDownLatch( 1 );
					new Thread( () -> {
						( (BDVPopup) panel.bdvPopup() ).setBDV( BDVPopup.createBDV( panel ) );
						latch2.countDown();
					} ).start();

					try
					{
						latch2.await();
					}
					catch ( InterruptedException e1 )
					{
						e1.printStackTrace();
					}

					panel.bdvPopup().getBDV().getViewerFrame().setSize( oldSize );
					panel.bdvPopup().getBDV().getViewerFrame().setLocation( oldLoc );
				}

				panel.updateContent();

			} ).start();

//			WrapBasicImgLoader.wrapImgLoaderIfNecessary( data );
//			BigDataViewer.initSetups( data,bdv.getSetupAssignments().getConverterSetups(), (List< SourceAndConverter<?> >) (Object) bdv.getViewer().getState().getSources() );
		}
	}
}
