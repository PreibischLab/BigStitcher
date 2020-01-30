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
package net.preibisch.mvrecon.fiji.plugin;

import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

import java.awt.TextField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.util.Util;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.ViewSetupUtils;

public class Specify_Calibration implements PlugIn
{
	@Override
	public void run( final String arg0 )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "specifying calibration", true, true, true, true, true ) )
			return;

		final SpimData2 data = result.getData();
		final List< ViewId > viewIds = SpimData2.getAllViewIdsSorted( data, result.getViewSetupsToProcess(), result.getTimePointsToProcess() );

		final ArrayList< Cal > calibrations = findCalibrations( data, viewIds );

		final Cal maxCal = mostPresentCal( calibrations );

		if ( !queryNewCal( calibrations, maxCal ) )
			return;

		applyCal( maxCal, data, viewIds );

		// save the xml
		SpimData2.saveXML( data, result.getXMLFileName(), result.getClusterExtension() );
	}

	public static boolean queryNewCal( final ArrayList< Cal > calibrations, final Cal maxCal )
	{
		final GenericDialog gd = new GenericDialog( "Define new calibration" );
		
		gd.addNumericField( "Calibration_x", maxCal.getCal()[ 0 ], 40, 20, "" );
		// ImageJ cuts of part of the number otherwise
		((TextField)gd.getNumericFields().lastElement()).setText( "" + maxCal.getCal()[ 0 ] );
		gd.addNumericField( "Calibration_y", maxCal.getCal()[ 1 ], 40, 20, "" );
		// ImageJ cuts of part of the number otherwise
		((TextField)gd.getNumericFields().lastElement()).setText( "" + maxCal.getCal()[ 1 ] );
		gd.addNumericField( "Calibration_z", maxCal.getCal()[ 2 ], 40, 20, "" );
		// ImageJ cuts of part of the number otherwise
		((TextField)gd.getNumericFields().lastElement()).setText( "" + maxCal.getCal()[ 2 ] );
		gd.addStringField( "Unit", maxCal.unit() );

		if ( calibrations.size() > 1 )
			gd.addMessage( "WARNING: Calibrations are not the same for all\n" +
						   "view setups! All calibrations will be overwritten\n" +
						   "for all view setups if defined here.",
						   GUIHelper.mediumstatusfont, GUIHelper.warning );

		gd.addMessage( "Note: These values will be applied to selected view\n" +
					   "setups, existing registration are not affected and\n" +
					   "will need to be recomputed if necessary.",
					   GUIHelper.mediumstatusfont );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		maxCal.getCal()[ 0 ] = gd.getNextNumber();
		maxCal.getCal()[ 1 ] = gd.getNextNumber();
		maxCal.getCal()[ 2 ] = gd.getNextNumber();
		maxCal.setUnit( gd.getNextString() );

		return true;
	}

	// TODO: this should not be necessary, could be AbstractSpimData
	public static void applyCal( final Cal maxCal, final SpimData spimData, final List< ViewId > viewIds )
	{
		// this is the same for all timepoints, we are just interested in the ViewSetup
		final TimePoint t = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 );

		for ( final ViewId viewId : viewIds )
		{
			if ( viewId.getTimePointId() != t.getId() )
				continue;

			final ViewDescription desc = spimData.getSequenceDescription().getViewDescriptions().get( viewId );
			final ViewSetup viewSetup = desc.getViewSetup();

			viewSetup.setVoxelSize( new FinalVoxelDimensions( maxCal.unit(),
					maxCal.getCal()[ 0 ],
					maxCal.getCal()[ 1 ],
					maxCal.getCal()[ 2 ] ) );
		}
	}

	public static ArrayList< Cal > findCalibrations( final AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > > spimData, final List< ViewId > viewIds )
	{
		// this is the same for all timepoints, we are just interested in the ViewSetup
		final TimePoint t = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 );

		final ArrayList< Cal > calibrations = new ArrayList< Cal >(); 
		
		for ( final ViewId viewId : viewIds )
		{
			if ( viewId.getTimePointId() != t.getId() )
				continue;

			final BasicViewDescription< ? > vd = spimData.getSequenceDescription().getViewDescriptions().get( viewId );
			final BasicViewSetup vs = vd.getViewSetup();
			final String name;

			if ( ViewSetup.class.isInstance( vs ) )
			{
				name =
					"angle: " + ((ViewSetup)vs).getAngle().getName() +
					" channel: " + ((ViewSetup)vs).getChannel().getName() +
					" illum: " + ((ViewSetup)vs).getIllumination().getName() +
					", present at timepoint: " + t.getName() +
					": " + vd.isPresent();
			}
			else
			{
				name =
					"viewsetup: " + vs.getId() + ", present at timepoint: " +
					t.getName() + ": " + vd.isPresent();
			}

			// only consider voxelsizes as defined in the XML
			VoxelDimensions voxelSize = ViewSetupUtils.getVoxelSize( vs );

			if ( voxelSize == null )
				voxelSize = new FinalVoxelDimensions( "", new double[]{ 1, 1, 1 } );

			final double x = voxelSize.dimension( 0 );
			final double y = voxelSize.dimension( 1 );
			final double z = voxelSize.dimension( 2 );
			String unit = voxelSize.unit();

			if ( unit == null )
				unit = "";

			IOFunctions.println( "cal: [" + x + ", " + y + ", " + z + "] " + unit + "  -- " + name );

			final Cal calTmp = new Cal( new double[]{ x, y, z }, unit );
			boolean foundMatch = false;

			for ( int j = 0; j < calibrations.size() && !foundMatch; ++j )
			{
				final Cal cal = calibrations.get( j );
				if ( cal.equals( calTmp ) )
				{
					cal.increaseCount();
					foundMatch = true;
				}
			}

			if ( !foundMatch )
				calibrations.add( calTmp );
		}

		return calibrations;
	}

	public static Cal mostPresentCal( final Collection< Cal > calibrations )
	{
		int max = 0;
		Cal maxCal = null;
		
		for ( final Cal cal : calibrations )
		{
			if ( cal.getCount() > max )
			{
				max = cal.getCount();
				maxCal = cal;
			}
		}
		
		IOFunctions.println( "Number of calibrations: " + calibrations.size() );
		IOFunctions.println( "Calibration most often present: " + Util.printCoordinates( maxCal.getCal() ) + " (" + maxCal.getCount() + " times)" );

		return maxCal;
	}

	public static class Cal
	{
		final double[] cal;
		int count;
		String unit;

		public Cal( final double[] cal, final String unit )
		{
			this.cal = cal;
			this.count = 1;
			this.unit = unit;
		}
		
		public void increaseCount() { ++count; }
		public int getCount() { return count; }
		public double[] getCal() { return cal; }
		public String unit() { return unit; }
		public void setUnit( final String unit ) { this.unit = unit; }

		@Override
		public boolean equals( final Object o )
		{
			if ( o instanceof Cal )
			{
				final Cal c2 = (Cal)o;
				
				if ( c2.cal.length != this.cal.length )
					return false;
				else
				{
					for ( int d = 0; d < cal.length; ++d )
						if ( c2.cal[ d ] != cal[ d ] )
							return false;
					
					return true;
				}
			}
			else
				return false;
		}
	}

	public static void main( String[] args )
	{
		IOFunctions.printIJLog = true;
		new ImageJ();
		new Specify_Calibration().run( null );
	}
}
