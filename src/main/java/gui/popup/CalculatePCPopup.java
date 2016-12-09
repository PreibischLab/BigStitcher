package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JMenuItem;

import com.jgoodies.common.base.Strings;

import algorithm.GroupedViewAggregator;
import algorithm.GroupedViewAggregator.ActionType;
import algorithm.PairwiseStitchingParameters;
import algorithm.SpimDataTools;
import algorithm.TransformTools;
import algorithm.globalopt.GroupedViews;
import algorithm.globalopt.PairwiseStrategyTools;
import algorithm.globalopt.TransformationTools;
import gui.StitchingResultsSettable;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Dimensions;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.GroupedRowWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import spim.fiji.spimdata.stitchingresults.PairwiseStitchingResult;
import spim.fiji.spimdata.stitchingresults.StitchingResults;

public class CalculatePCPopup extends JMenuItem implements ExplorerWindowSetable, StitchingResultsSettable
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 8664967345630864576L;

	private StitchingResults stitchingResults;
	private ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public static final String[] ds = { "1", "2", "4", "8" };

	public CalculatePCPopup()
	{
		super( "Calculate Pairwise Shift" );
		this.addActionListener( new MyActionListener() );
	}

	@Override
	public void setStitchingResults(StitchingResults res)
	{
		this.stitchingResults = res;
	}

	@Override
	public JComponent setExplorerWindow(
			ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel)
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{

		@Override
		public void actionPerformed(ActionEvent e)
		{

			new Thread( new Runnable()
			{
				@Override
				public void run()
				{
					final AbstractSpimData< ? > d = panel.getSpimData();
					final AbstractSequenceDescription< ?, ?, ? > sd = d.getSequenceDescription();
					final ViewRegistrations vr = d.getViewRegistrations();

					// take together all views where the all attributes are the
					// same except channel (i.e. group the channels)
					// they are now represented by the channel of the first ID
					// (e.g. channelId=0)
					final ArrayList< GroupedViews > viewIds = new ArrayList< >();

					for ( List< ViewId > vidl : ( (GroupedRowWindow) panel ).selectedRowsViewIdGroups() )
						viewIds.add( new GroupedViews( vidl ) );

					Collections.sort( viewIds );

					ArrayList< String > channelNames = new ArrayList< >();
					channelNames.add( "average all" );

					List< Entity > channels = SpimDataTools.getInstancesOfAttribute( sd, Channel.class );
					for ( Entity en : channels )
						channelNames.add( NamedEntity.class.isInstance( en ) ? ( (NamedEntity) en ).getName()
								: Integer.toString( en.getId() ) );

					ArrayList< String > illuminationNames = new ArrayList< >();
					illuminationNames.add( "pick brightest" );

					List< Entity > illums = SpimDataTools.getInstancesOfAttribute( sd, Illumination.class );
					for ( Entity en : illums )
						illuminationNames.add( NamedEntity.class.isInstance( en ) ? ( (NamedEntity) en ).getName()
								: Integer.toString( en.getId() ) );

					GroupedViews gv = viewIds.get( 0 );
					boolean is2d = sd.getViewDescriptions().get( gv ).getViewSetup().getSize().numDimensions() == 2;
					// boolean is2d = false;

					
										
					GenericDialog gd = new GenericDialog( "Stitching options" );
					gd.addChoice( "channel to use", channelNames.toArray( new String[0] ), "average all" );
					gd.addChoice( "illumination to use", illuminationNames.toArray( new String[0] ), "pick brightest" );
					
					
					// Ask for downsampling factors
					
					// we have a HDF5 dataset - display mipmap resolutins present as choices
					if ( MultiResolutionImgLoader.class.isInstance( sd.getImgLoader() ))
					{
						MultiResolutionImgLoader mrImgLoader = (MultiResolutionImgLoader) sd.getImgLoader();
						// we consider the resolutions present for the first selected ViewId
						double[][] mipmapResolutions = mrImgLoader.getSetupImgLoader( panel.firstSelectedVD().getViewSetupId()).getMipmapResolutions();
						String[] dsStrings = new String[mipmapResolutions.length];
						
						for (int i = 0; i<mipmapResolutions.length; i++)
						{
							String fx = ((Long)Math.round( mipmapResolutions[i][0] )).toString(); 
							String fy = ((Long)Math.round( mipmapResolutions[i][1] )).toString(); 
							String fz = ((Long)Math.round( mipmapResolutions[i][2] )).toString();
							String dsString = String.join( ", ", fx, fy, fz );
							dsStrings[i] = dsString;
						}
						
						gd.addChoice( "downsampling (x, y, z)", dsStrings, dsStrings[0] );
						
					}
					
					/*
					
					gd.addChoice( "downsample x", ds, ds[0] );
					gd.addChoice( "downsample y", ds, ds[0] );
					if ( !is2d )
					{
						gd.addChoice( "downsample z", ds, ds[0] );
					}
					
					*/
					
					gd.showDialog();

					if ( gd.wasCanceled() )
						return;

					String channel = gd.getNextChoice();
					String illum = gd.getNextChoice();
					

					//long[] downSamplingFactors = !is2d ? new long[3] : new long[2];
					long[] downSamplingFactors = new long[] {1, 1, 1};
					
					if (( MultiResolutionImgLoader.class.isInstance( sd.getImgLoader() )))
					{
						String dsChoice = gd.getNextChoice();
						String[] choiceSplit = dsChoice.split( ", " );
						downSamplingFactors[0] = Long.parseLong( choiceSplit[0] );
						downSamplingFactors[1] = Long.parseLong( choiceSplit[1] );
						downSamplingFactors[2] = Long.parseLong( choiceSplit[2] );
					}
					
					
					
					/*
		
					downSamplingFactors[0] = Integer.parseInt( gd.getNextChoice() );
					downSamplingFactors[1] = Integer.parseInt( gd.getNextChoice() );
					if ( !is2d )
					{
						downSamplingFactors[2] = Integer.parseInt( gd.getNextChoice() );
					}
					*/

					PairwiseStitchingParameters params = PairwiseStitchingParameters.askUserForParameters();
					if ( params == null )
						return;

					// final ArrayList< ViewId > viewIdsSelectedChannel = new
					// ArrayList<>();

					int channelIdxInGroup = channelNames.indexOf( channel ) - 1;
					boolean doChannelAverage = channelIdxInGroup < 0;

					int illumIdxInGroup = illuminationNames.indexOf( illum ) - 1;
					boolean doIllumBrightest = illumIdxInGroup < 0;

					/*
					 * // get only one channel from grouped views if (
					 * !doGrouped ) { for (GroupedViews g : viewIds) {
					 * viewIdsSelectedChannel.add( g.getViewIds().get(
					 * channelIdxInGroup ) ); } } // keep GroupedViews else {
					 * viewIdsSelectedChannel.addAll( viewIds ); }
					 */

					// find all pairwise matchings that we need to compute
					final HashMap< ViewId, Dimensions > vd = new HashMap< >();
					final HashMap< ViewId, TranslationGet > vl = new HashMap< >();

					for ( final ViewId viewId : viewIds )
					{
						vd.put( viewId, sd.getViewDescriptions().get( viewId ).getViewSetup().getSize() );
						vl.put( viewId, TransformTools.getInitialTransforms( vr.getViewRegistration( viewId ), is2d,
								new AffineTransform3D() ).getB() );
					}

					final List< Pair< ViewId, ViewId > > pairs = PairwiseStrategyTools.overlappingTiles( vd, vl,
							viewIds );

					// compute them

					GroupedViewAggregator groupedViewAggregator = new GroupedViewAggregator();

					// decide how to handle illuminations
					if ( doIllumBrightest )
						groupedViewAggregator.addAction( ActionType.PICK_BRIGHTEST, Illumination.class, null );
					else
						groupedViewAggregator.addAction( ActionType.PICK_SPECIFIC, Illumination.class,
								(Illumination) illums.get( illumIdxInGroup ) );

					// decide how to handle channels
					if ( doChannelAverage )
						groupedViewAggregator.addAction( ActionType.AVERAGE, Channel.class, null );
					else
						groupedViewAggregator.addAction( ActionType.PICK_SPECIFIC, Channel.class,
								(Channel) channels.get( channelIdxInGroup ) );

					final ArrayList< PairwiseStitchingResult< ViewId > > results = TransformationTools.computePairs(
							pairs, params, d.getViewRegistrations(), d.getSequenceDescription(), groupedViewAggregator,
							downSamplingFactors );

					// update StitchingResults with Results
					for ( final PairwiseStitchingResult< ViewId > psr : results )
					{
						// find the ViewId of the GroupedViews that the results
						// belong to
						ViewId gvA = null;
						ViewId gvB = null;
						for ( GroupedViews g : viewIds )
						{
							if ( g.getViewIds().contains( psr.pair().getA() ) )
							{
								gvA = g;
							}
							if ( g.getViewIds().contains( psr.pair().getB() ) )
							{
								gvB = g;
							}
						}

						stitchingResults.setPairwiseResultForPair( new ValuePair< >( gvA, gvB ), psr );
					}
				}
			} ).start();

		}
	}
}
