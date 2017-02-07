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
import bdv.BigDataViewerActions;
import bdv.ViewerImgLoader;
import bdv.spimdata.WrapBasicImgLoader;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.transformation.TransformedSource;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvHandlePanel;
import bdv.util.BehaviourTransformEventHandlerPlanar.BehaviourTransformEventHandlerPlanarFactory;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.SourceState;
import gui.AveragingProjectorARGB;
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
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.FilteredAndGroupedExplorerPanel;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
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
						catch (Exception ex)
						{
							IOFunctions.println( "Could not run BigDataViewer: " + ex );
							ex.printStackTrace();
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
	
	public static void minMaxGroupByChannels(BigDataViewer bdv, AbstractSpimData< ? > data)
	{
		Set< Class< ? extends Entity> > groupingFactors = new HashSet<>();
		groupingFactors.add( Channel.class );
		groupingFactors.add( Illumination.class );
		minMaxGroupByFactors( bdv, data, groupingFactors );
	}
	
	public static void minMaxGroupByFactors(BigDataViewer bdv, AbstractSpimData< ? > data, Set<Class<? extends Entity>> groupingFactors)
	{
		List<BasicViewDescription< ? > > vds = new ArrayList<>();
		Map<BasicViewDescription< ? >, ConverterSetup> vdToCs = new HashMap<>();
		
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			Integer timepointId = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( bdv.getViewer().getState().getCurrentTimepoint()).getId();
			BasicViewDescription< ? > vd = data.getSequenceDescription().getViewDescriptions().get( new ViewId( timepointId, cs.getSetupId() ) );
			vds.add( vd );
			vdToCs.put( vd, cs );
		}
		
		List< List< BasicViewDescription< ? > > > vdGroups = SpimDataTools.collapseByAttributes( vds, groupingFactors );
		
		// nothing to group
		if (vdGroups.size() <= 1)
			return;
		List<ArrayList<ConverterSetup>> groups =  new ArrayList<>();
		
		for (List< BasicViewDescription< ? > > lVd : vdGroups)
		{
			ArrayList< ConverterSetup > lCs = new ArrayList<>();
			for (BasicViewDescription< ? > vd : lVd)
				lCs.add( vdToCs.get( vd ) );
			groups.add( lCs );
		}
	
					
		for (int i = 1; i < groups.size(); i++)
		{
		
			ArrayList<ConverterSetup> cs = groups.get( i );
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
			Integer timepointId = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( bdv.getViewer().getState().getCurrentTimepoint()).getId();
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
	
	public static void colorByChannels(BigDataViewer bdv, AbstractSpimData< ? > data)
	{
		Set< Class< ? extends Entity> > groupingFactors = new HashSet<>();
		groupingFactors.add( Channel.class );
		groupingFactors.add( Illumination.class );
		colorByFactors( bdv, data, groupingFactors );
	}
	
	public static void colorByFactors(BigDataViewer bdv, AbstractSpimData< ? > data, Set<Class<? extends Entity>> groupingFactors)
	{
		List<BasicViewDescription< ? > > vds = new ArrayList<>();
		Map<BasicViewDescription< ? >, ConverterSetup> vdToCs = new HashMap<>();
		
		for (ConverterSetup cs : bdv.getSetupAssignments().getConverterSetups())
		{
			Integer timepointId = data.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( bdv.getViewer().getState().getCurrentTimepoint()).getId();
			BasicViewDescription< ? > vd = data.getSequenceDescription().getViewDescriptions().get( new ViewId( timepointId, cs.getSetupId() ) );
			vds.add( vd );
			vdToCs.put( vd, cs );
		}
		
		List< List< BasicViewDescription< ? > > > vdGroups = SpimDataTools.collapseByAttributes( vds, groupingFactors );
		
		// nothing to group
		if (vdGroups.size() <= 1)
			return;
		List<ArrayList<ConverterSetup>> groups =  new ArrayList<>();
		
		for (List< BasicViewDescription< ? > > lVd : vdGroups)
		{
			ArrayList< ConverterSetup > lCs = new ArrayList<>();
			for (BasicViewDescription< ? > vd : lVd)
				lCs.add( vdToCs.get( vd ) );
			groups.add( lCs );
		}
				
		Iterator< ARGBType > colorIt = ColorStream.iterator();
				
		for (ArrayList< ConverterSetup > csg : groups)
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
		//WrapBasicImgLoader.wrapImgLoaderIfNecessary( panel.getSpimData() );
		
		//ArrayList< ConverterSetup > convSetups = new ArrayList<>();
		//ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		
		// TODO: this loads all views? why?
		//BigDataViewer.initSetups( panel.getSpimData(), convSetups, sources );		
		
		BigDataViewer bdv = BigDataViewer.open( panel.getSpimData(), 
												"BigDataViewer", 
												null, 
												ViewerOptions.options().accumulateProjectorFactory( MaximumProjectorARGB.factory ) );
		

		/*
		BigDataViewer bdv = new BigDataViewer( 	convSetups,
												sources,
												panel.getSpimData(),
												panel.getSpimData().getSequenceDescription().getTimePoints().size(), 
												( ( ViewerImgLoader ) panel.getSpimData().getSequenceDescription().getImgLoader() ).getCache(),
												"BigDataViewer",
												null, 
												ViewerOptions.options().accumulateProjectorFactory( MaximumProjectorARGB.factory ));
		*/
		
		
				// For 2D behaviour								.transformEventHandlerFactory(new BehaviourTransformEventHandlerPlanarFactory() ));
		//ViewerOptions.options().transformEventHandlerFactory(new BehaviourTransformEventHandlerPlanarFactory() );
		
		InitializeViewerState.initTransform( bdv.getViewer() );		
			// if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should
			// work, but currently tryLoadSettings is protected. fix that.
		InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );

		FilteredAndGroupedExplorerPanel.setFusedModeSimple( bdv, panel.getSpimData() );
//		if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
		//	InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );
		
		
		minMaxGroupByChannels( bdv, panel.getSpimData() );
		colorByChannels( bdv, panel.getSpimData() );
		
		
		// FIXME: source grouping is quite hacky atm
		Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		groupingFactors.add( Channel.class );
		groupingFactors.add( Illumination.class );		
		groupSourcesByFactors( bdv, panel.getSpimData(), groupingFactors );
			
		FilteredAndGroupedExplorerPanel.updateBDV( bdv, panel.colorMode(), panel.getSpimData(), panel.firstSelectedVD(), ((GroupedRowWindow)panel).selectedRowsGroups());

		bdv.getViewer().addTransformListener( lo );
		bdv.getViewer().getDisplay().addOverlayRenderer( lo );
		
		bdv.getViewerFrame().setVisible( true );		
		bdv.getViewer().requestRepaint();

		return bdv;
		
	}
	
	@Override
	public void setBDV(BigDataViewer existingBdv)
	{
		// close existing bdv if necessary
		if (bdvRunning())
			new Thread(() -> {closeBDV();}).start();
		
		this.bdv = existingBdv;
		FilteredAndGroupedExplorerPanel.setFusedModeSimple( bdv, panel.getSpimData() );
//		if ( !bdv.tryLoadSettings( panel.xml() ) ) TODO: this should work, but currently tryLoadSettings is protected. fix that.
		//	InitializeViewerState.initBrightness( 0.001, 0.999, bdv.getViewer(), bdv.getSetupAssignments() );
		
		
		minMaxGroupByChannels( bdv, panel.getSpimData() );
		colorByChannels( bdv, panel.getSpimData() );
		
		
		// FIXME: source grouping is quite hacky atm
		Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		groupingFactors.add( Channel.class );
		groupingFactors.add( Illumination.class );		
		groupSourcesByFactors( bdv, panel.getSpimData(), groupingFactors );
			
		FilteredAndGroupedExplorerPanel.updateBDV( bdv, panel.colorMode(), panel.getSpimData(), panel.firstSelectedVD(), ((GroupedRowWindow)panel).selectedRowsGroups());

		bdv.getViewer().removeTransformListener( lo );
		bdv.getViewer().addTransformListener( lo );
		bdv.getViewer().getDisplay().addOverlayRenderer( lo );
		
		bdv.getViewerFrame().setVisible( true );		
		bdv.getViewer().requestRepaint();
	}

}
