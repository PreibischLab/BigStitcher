package net.preibisch.mvrecon.fiji.plugin.fusion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.fusion.transformed.nonrigid.NonRigidParameters;
import net.preibisch.mvrecon.process.interestpointdetection.InterestPointTools;

public class NonRigidParametersGUI extends NonRigidParameters
{
	public static boolean enableNonRigid = true;

	public static boolean[] defaultAdditional = new boolean[] { true };
	public static boolean defaultAdvanced = false;
	public static boolean defaultAdditionalIPs = false;
	public static boolean defaultAdditionalInterestPoints = false;
	public static int defaultLabel = -1;
	public static long defaultControlPointDistance = 10;
	public static double defaultAlpha = 1.0;
	public static boolean defaultShowDistanceMap = false;
	public static boolean defaultNonRigidAcrossTime = false;

	final SpimData2 spimData;
	final List< ? extends ViewId > viewIds;
	String[] labels = null;
	boolean advanced = defaultAdvanced;
	boolean[] selectedLabels = null;
	boolean isActive = true;

	public NonRigidParametersGUI( final SpimData2 spimData, final List< ? extends ViewId > viewIds )
	{
		this.spimData = spimData;
		this.viewIds = viewIds;
	}

	public boolean userSelectedAdvancedParameters() { return advanced; }
	public boolean isActive() { return isActive; }

	@Override
	public ArrayList< String > getLabels()
	{
		labelList.clear();

		for ( int i = 0; i < selectedLabels.length; ++i )
			if ( selectedLabels[ i ] )
				labelList.add( this.labels[ i ] );

		return labelList;
	}

	public boolean query()
	{
		final GenericDialog gd = new GenericDialog( "Non-Rigid Parameters" );

		addQuery( gd );

		gd.showDialog();
		if ( gd.wasCanceled() )
		{
			isActive = false;
			return false;
		}

		return parseQuery( gd, true );
	}

	/**
	 * @param gd
	 * @param displayAdvancedAutomatically
	 *
	 * @return false if the user pressed cancel in the advanced dialog
	 */
	public boolean parseQuery( final GenericDialog gd, final boolean displayAdvancedAutomatically )
	{
		final int labelIndex = defaultLabel = gd.getNextChoiceIndex();
		this.advanced = defaultAdvanced = gd.getNextBoolean();

		if ( labelIndex == labels.length - 1 )
		{
			isActive = false;
			return true;
		}

		this.selectedLabels = new boolean[ this.labels.length - 1 ];
		this.selectedLabels[ labelIndex ] = true;

		// preselect the label for (optional) additional query
		if ( this.labels.length != defaultAdditional.length )
			defaultAdditional = new boolean[ this.labels.length - 1 ];
		defaultAdditional[ labelIndex ] = true;

		if ( displayAdvancedAutomatically && this.advanced && this.isActive )
			return advancedParameters();
		else
			return true;
	}

	public void addQuery( final GenericDialog gd )
	{
		final HashMap< String, Integer > corrMap = InterestPointTools.getAllCorrespondingInterestPointMap( spimData.getViewInterestPoints(), viewIds );

		if ( corrMap.keySet().size() == 0 )
			IOFunctions.println( "No corresponding interest points available.\n"
					+ "To enable non-rigid please run Interest Point Detection followed by Intererst Point Registration (e.g. ICP) first" ); 

		this.labels = new String[ corrMap.keySet().size() + 1 ];
		this.labels[ labels.length - 1 ] = "-= Disable Non-Rigid =-";

		int i = 0;

		for ( final String label : corrMap.keySet() )
		{
			labels[ i ] = label;

			final int numViewsWithCorr = corrMap.get( label );

			if ( numViewsWithCorr != viewIds.size() )
				labels[ i ] += InterestPointTools.warningLabel + numViewsWithCorr + ")";

			++i;
		}

		// choose the first label that is complete if possible
		if ( defaultLabel < 0 || defaultLabel >= labels.length )
			defaultLabel = labels.length - 1;

		gd.addChoice( "Interest_Points_for_Non_Rigid", labels, labels[ defaultLabel ] );
		gd.addCheckbox( "Non_Rigid_Advanced_Parameters", defaultAdvanced );
	}

	public boolean advancedParameters()
	{
		final GenericDialog gd1 = new GenericDialog( "Advanced Non-Rigid Parameters" );

		gd1.addNumericField( "Alpha (Moving Least Squares)", defaultAlpha, 2 );
		gd1.addSlider( "Control_point_distance", 1, 100, defaultControlPointDistance );
		gd1.addCheckbox( "Nonrigid_transform_across_time_domain", defaultNonRigidAcrossTime );
		gd1.addCheckbox( "Only_display_distance_map (instead of image data)", defaultShowDistanceMap );
		gd1.addCheckbox( "Use_additional_interest_points", defaultAdditionalIPs );

		gd1.showDialog();
		if ( gd1.wasCanceled() )
		{
			isActive = false;
			return false;
		}

		this.alpha = defaultAlpha = gd1.getNextNumber();
		this.controlPointDistance = defaultControlPointDistance = Math.round( gd1.getNextNumber() );
		this.nonRigidAcrossTime = defaultNonRigidAcrossTime = gd1.getNextBoolean();
		this.showDistanceMap = defaultShowDistanceMap = gd1.getNextBoolean();

		if ( defaultAdditionalInterestPoints = gd1.getNextBoolean() )
		{
			final GenericDialog gd2 = new GenericDialog( "Additional Non-Rigid IP Parameters" );

			for ( int i = 0; i < labels.length - 1; ++i )
				gd2.addCheckbox( labels[ i ], defaultAdditional[ i ] );

			gd2.showDialog();
			if ( gd2.wasCanceled() )
			{
				isActive = false;
				return false;
			}

			int countActive = 0;

			for ( int i = 0; i < labels.length - 1; ++i )
			{
				if ( gd2.getNextBoolean() )
				{
					this.selectedLabels[ i ] = defaultAdditional[ i ] = true;
					++countActive;
				}
				else
				{
					this.selectedLabels[ i ] = defaultAdditional[ i ] = false;
				}
			}

			if ( countActive == 0 )
			{
				isActive = false;
				IOFunctions.println( "No interest points selected, non-rigid is disabled." );
			}
		}

		return true;
	}
}
