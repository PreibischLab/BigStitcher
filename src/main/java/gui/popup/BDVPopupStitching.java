package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.swing.JOptionPane;

import algorithm.SpimDataTools;
import bdv.AbstractSpimSource;
import bdv.BigDataViewer;
import bdv.ViewerImgLoader;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BehaviourTransformEventHandlerPlanar.BehaviourTransformEventHandlerPlanarFactory;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.SourceState;
import gui.AveragingProjectorARGB;
import gui.FilteredAndGroupedExplorerPanel;
import gui.GroupedRowWindow;
import gui.MaximumProjectorARGB;
import gui.overlay.LinkOverlay;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.ui.TransformListener;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.ViewSetupExplorerPanel;
import spim.fiji.spimdata.explorer.popup.BDVPopup;
import spim.fiji.spimdata.explorer.popup.BDVPopup.MyActionListener;
import spim.fiji.spimdata.explorer.util.ColorStream;
import spim.fiji.spimdata.imgloaders.AbstractImgLoader;

public class BDVPopupStitching extends BDVPopup
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -8852442192041303045L;

	private LinkOverlay lo;
	
	public BDVPopupStitching(LinkOverlay lo)
	{

		super();
		this.lo = lo;
		this.removeActionListener( this.getActionListeners()[0] );
		this.addActionListener( new MyActionListener() );
				
	}
	
	
	
	public class MyActionListener implements ActionListener
	{
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
					// if BDV was closed by the user
					if ( bdv != null && !bdv.getViewerFrame().isVisible() )
						bdv = null;

					if ( bdv == null )
					{

						try
						{
							bdv = createBDV( panel, lo );
						}
						catch (Exception e)
						{
							IOFunctions.println( "Could not run BigDataViewer: " + e );
							e.printStackTrace();
							bdv = null;
						}
					}
					else
					{
						closeBDV();
					}
				}
			}).start();
		}
	}
	
	public static void minMaxGroupByChannel(BigDataViewer bdv, AbstractSpimData< ? > data)
	{
		// group ConverterSetups according to Channel
		HashMap< Channel, ArrayList< ConverterSetup > > groups = new HashMap<>();
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			
			 Channel key = data.getSequenceDescription().getViewSetups().get( cs.getSetupId() ).getAttribute( Channel.class );
			 if (!groups.containsKey( key ))
				 groups.put( key, new ArrayList< ConverterSetup >() );
			 groups.get( key ).add( cs );
		}
		
		ArrayList<Channel> keyList = new ArrayList<>(groups.keySet());
		
		// nothing to group
		if (keyList.size() <= 1)
			return;
		
		for (int i = 1; i < keyList.size(); ++i)
		{
			ArrayList< ConverterSetup > cs = groups.get( keyList.get( i ) );
			
			// remove first setup from its group (group 0), creating a new one
			bdv.getSetupAssignments().removeSetupFromGroup( cs.get( 0 ), bdv.getSetupAssignments().getMinMaxGroups().get( 0 ));
			
			// move all other setups in group to the new MinMaxGroup (group i)
			for (int j = 1; j < cs.size(); ++j)
			{
				bdv.getSetupAssignments().moveSetupToGroup( cs.get( j ), bdv.getSetupAssignments().getMinMaxGroups().get( i ) );
			}
		}
		
		
		
	}

	public static void groupSourcesByFactors(BigDataViewer bdv, AbstractSpimData< ? > data,
			Set< Class< ? extends Entity > > groupingFactors)
	{
		//  clear source groups		
		for (int i = 0; i < bdv.getViewer().getState().numSourceGroups(); ++i)
		{
			SourceGroup sg = bdv.getViewer().getState().getSourceGroups().get( i );
			SortedSet< Integer > sourceIds = sg.getSourceIds();
			for (Integer si: sourceIds)
				bdv.getViewer().getVisibilityAndGrouping().removeSourceFromGroup( si, i );

		}

		List<BasicViewDescription< ? > > vds = new ArrayList<>();
		Map<BasicViewDescription< ? >, Integer> vdToSource = new HashMap<>();
		

		for(int i = 0; i < bdv.getViewer().getState().getSources().size(); ++i)
		{
			Integer timepointId = bdv.getViewer().getState().getCurrentTimepoint();
			BasicViewDescription< ? > vd = data.getSequenceDescription().getViewDescriptions().get( new ViewId( timepointId, i ) );
			vds.add( vd );
			vdToSource.put( vd, i );
		}

		
		List< List< BasicViewDescription< ? > > > groupByAttributes = SpimDataTools.groupByAttributes( vds, groupingFactors );
		
		for (int gi = 0; gi < groupByAttributes.size(); gi ++)
		{
			List< BasicViewDescription< ? > > vdsI = groupByAttributes.get( gi );
			for (BasicViewDescription< ? > vd : vdsI)
			{
				bdv.getViewer().getVisibilityAndGrouping().addSourceToGroup( vdToSource.get( vd ), gi );
			}
		}
	}
	
	public static void colorByChannel(BigDataViewer bdv, AbstractSpimData< ? > data)
	{
		// group ConverterSetups according to Channel
		HashMap< Channel, ArrayList< ConverterSetup > > groups = new HashMap<>();
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			Channel key = data.getSequenceDescription().getViewSetups().get( cs.getSetupId() ).getAttribute( Channel.class );
			if (!groups.containsKey( key ))
				groups.put( key, new ArrayList< ConverterSetup >() );
			groups.get( key ).add( cs );
		}
		
		Iterator< ARGBType > colorIt = ColorStream.iterator();
		
		
		
		for (ArrayList< ConverterSetup > csg : groups.values())
		{
			ARGBType color = colorIt.next();
			for (ConverterSetup cs : csg)
				cs.setColor( color );
		}
	}
	

	public static BigDataViewer createBDV( final ExplorerWindow< ?, ? > panel , LinkOverlay lo)
	{
		if ( AbstractImgLoader.class.isInstance( panel.getSpimData().getSequenceDescription().getImgLoader() ) )
		{
			if ( JOptionPane.showConfirmDialog( null,
					"Opening <SpimData> dataset that is not suited for interactive browsing.\n" +
					"Consider resaving as HDF5 for better performance.\n" +
					"Proceed anyways?",
					"Warning",
					JOptionPane.YES_NO_OPTION ) == JOptionPane.NO_OPTION )
				return null;
		}

		
		
		// FIXME: do this somewhere else?
		WrapBasicImgLoader.wrapImgLoaderIfNecessary( panel.getSpimData() );
		
		ArrayList< ConverterSetup > convSetups = new ArrayList<>();
		ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		
		BigDataViewer.initSetups( panel.getSpimData(), convSetups, sources );		
		
		BigDataViewer bdv = new BigDataViewer( 	convSetups,
												sources,
												panel.getSpimData(),
												panel.getSpimData().getSequenceDescription().getTimePoints().size(), 
												( ( ViewerImgLoader ) panel.getSpimData().getSequenceDescription().getImgLoader() ).getCache(),
												"BigDataViewer",
												null, 
												ViewerOptions.options().accumulateProjectorFactory( MaximumProjectorARGB.factory ));
		
		
				// For 2D behaviour								.transformEventHandlerFactory(new BehaviourTransformEventHandlerPlanarFactory() ));
		//ViewerOptions.options().transformEventHandlerFactory(new BehaviourTransformEventHandlerPlanarFactory() );
		
		bdv.getViewerFrame().setVisible( true );
		
		InitializeViewerState.initTransform( bdv.getViewer() );
		
			// if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should
			// work, but currently tryLoadSettings is protected. fix that.
		InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );

		FilteredAndGroupedExplorerPanel.setFusedModeSimple( bdv, panel.getSpimData() );
//		if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
		//	InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );
		
			
		minMaxGroupByChannel( bdv, panel.getSpimData() );
		colorByChannel( bdv, panel.getSpimData() );
		
		
		// FIXME: source grouping is quite hacky atm
		Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		groupingFactors.add( Channel.class );
		groupingFactors.add( Illumination.class );		
		groupSourcesByFactors( bdv, panel.getSpimData(), groupingFactors );
			
		FilteredAndGroupedExplorerPanel.updateBDV( bdv, panel.colorMode(), panel.getSpimData(), panel.firstSelectedVD(), ((GroupedRowWindow)panel).selectedRowsGroups());

		bdv.getViewer().addTransformListener( lo );
		bdv.getViewer().getDisplay().addOverlayRenderer( lo );

		return bdv;
		
	}

}
