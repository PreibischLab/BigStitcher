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
package net.preibisch.mvrecon.fiji.plugin.interestpointdetection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.legacy.segmentation.InteractiveIntegral;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.interestpoints.InterestPoint;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dom.DoM;
import net.preibisch.mvrecon.process.interestpointdetection.methods.dom.DoMParameters;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;

import ij.ImagePlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;


public class DifferenceOfMeanGUI extends DifferenceOfGUI
{
	public static int defaultRadius1 = 2;
	public static int defaultRadius2 = 3;
	public static double defaultThreshold = 0.005;
	public static boolean defaultFindMin = false;
	public static boolean defaultFindMax = true;
	
	int radius1;
	int radius2;
	double threshold;
	boolean findMin;
	boolean findMax;
	
	public DifferenceOfMeanGUI( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{
		super( spimData, viewIdsToProcess );
	}

	@Override
	public String getDescription() { return "Difference-of-Mean (Integral image based)"; }

	@Override
	public DifferenceOfMeanGUI newInstance( final SpimData2 spimData, final List< ViewId > viewIdsToProcess )
	{ 
		return new DifferenceOfMeanGUI( spimData, viewIdsToProcess );
	}

	@Override
	public HashMap< ViewId, List< InterestPoint > > findInterestPoints( final TimePoint t )
	{
		final DoMParameters dom = new DoMParameters();

		dom.imgloader = spimData.getSequenceDescription().getImgLoader();
		dom.toProcess = new ArrayList< ViewDescription >();

		dom.localization = this.localization;
		dom.downsampleZ = this.downsampleZ;
		dom.imageSigmaX = this.imageSigmaX;
		dom.imageSigmaY = this.imageSigmaY;
		dom.imageSigmaZ = this.imageSigmaZ;

		dom.minIntensity = this.minIntensity;
		dom.maxIntensity = this.maxIntensity;

		dom.radius1 = this.radius1;
		dom.radius2 = this.radius2;
		dom.threshold = (float)this.threshold;
		dom.findMin = this.findMin;
		dom.findMax = this.findMax;

		dom.limitDetections = this.limitDetections;
		dom.maxDetections = this.maxDetections;
		dom.maxDetectionsTypeIndex = this.maxDetectionsTypeIndex;

		final HashMap< ViewId, List< InterestPoint > > interestPoints = new HashMap< ViewId, List< InterestPoint > >();

		for ( final ViewDescription vd : SpimData2.getAllViewIdsForTimePointSorted( spimData, viewIdsToProcess, t ) )
		{
			// make sure not everything crashes if one file is missing
			try
			{
				if ( !vd.isPresent() )
					continue;

				dom.toProcess.clear();
				dom.toProcess.add( vd );

				// downsampleXY == 0 : a bit less then z-resolution
				// downsampleXY == -1 : a bit more then z-resolution
				if ( downsampleXYIndex < 1 )
					dom.downsampleXY = DownsampleTools.downsampleFactor( downsampleXYIndex, downsampleZ, vd.getViewSetup().getVoxelSize() );
				else
					dom.downsampleXY = downsampleXYIndex;

				DoM.addInterestPoints( interestPoints, dom );
			}
			catch ( Exception  e )
			{
				IOFunctions.println( "An error occured (DOM): " + e ); 
				IOFunctions.println( "Failed to segment angleId: " + 
						vd.getViewSetup().getAngle().getId() + " channelId: " +
						vd.getViewSetup().getChannel().getId() + " illumId: " +
						vd.getViewSetup().getIllumination().getId() + ". Continuing with next one." );
				e.printStackTrace();
			}
		}

		return interestPoints;
	}

	@Override
	protected boolean setDefaultValues( final int brightness )
	{
		this.radius1 = defaultRadius1;
		this.radius2 = defaultRadius2;
		this.findMin = false;
		this.findMax = true;
		
		if ( brightness == 0 )
			this.threshold = 0.0025f;
		else if ( brightness == 1 )
			this.threshold = 0.02f;
		else if ( brightness == 2 )
			this.threshold = 0.075f;
		else if ( brightness == 3 )
			this.threshold = 0.25f;
		else
			return false;
		
		return true;
	}

	@Override
	protected boolean setAdvancedValues()
	{
		final GenericDialog gd = new GenericDialog( "Advanced values" );

		gd.addNumericField( "Radius_1", defaultRadius1, 0 );
		gd.addNumericField( "Radius_2", defaultRadius2, 0 );
		gd.addNumericField( "Threshold", defaultThreshold, 4 );
		gd.addCheckbox( "Find_minima", defaultFindMin );
		gd.addCheckbox( "Find_maxima", defaultFindMax );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		this.radius1 = defaultRadius1 = (int)Math.round( gd.getNextNumber() );
		this.radius2 = defaultRadius2 = (int)Math.round( gd.getNextNumber() );
		this.threshold = defaultThreshold = gd.getNextNumber();
		this.findMin = defaultFindMin = gd.getNextBoolean();
		this.findMax = defaultFindMax = gd.getNextBoolean();
		
		return true;
	}

	@Override
	protected boolean setInteractiveValues()
	{
		final ImagePlus imp;

		if ( !groupIllums && !groupTiles )
			imp = getImagePlusForInteractive( "Interactive Difference-of-Gaussian" );
		else
			imp = getGroupedImagePlusForInteractive( "Interactive Difference-of-Gaussian" );

		if ( imp == null )
			return false;

		imp.setDimensions( 1, imp.getStackSize(), 1 );
		imp.show();
		imp.setSlice( imp.getStackSize() / 2 );
		
		final InteractiveIntegral ii = new InteractiveIntegral();

		ii.setInitialRadius( Math.round( defaultRadius1 ) );
		ii.setThreshold( (float)defaultThreshold );
		ii.setLookForMinima( defaultFindMin );
		ii.setLookForMaxima( defaultFindMax );
		ii.setMinIntensityImage( minIntensity ); // if is Double.NaN will be ignored
		ii.setMaxIntensityImage( maxIntensity ); // if is Double.NaN will be ignored

		ii.run( null );
		
		while ( !ii.isFinished() )
		{
			try
			{
				Thread.sleep( 100 );
			}
			catch (InterruptedException e) {}
		}

		imp.close();

		if ( ii.wasCanceld() )
			return false;

		this.radius1 = defaultRadius1 = ii.getRadius1();
		this.radius2 = defaultRadius2 = ii.getRadius2();
		this.threshold = defaultThreshold = ii.getThreshold();
		this.findMin = defaultFindMin = ii.getLookForMinima();
		this.findMax = defaultFindMax = ii.getLookForMaxima();

		return true;
	}

	@Override
	public String getParameters()
	{
		return "DOM r1=" + radius1 + " t=" + threshold + " min=" + findMin + " max=" + findMax + 
				" imageSigmaX=" + imageSigmaX + " imageSigmaY=" + imageSigmaY + " imageSigmaZ=" + imageSigmaZ + " downsampleXYIndex=" + downsampleXYIndex +
				" downsampleZ=" + downsampleZ + " minIntensity=" + minIntensity + " maxIntensity=" + maxIntensity;
	}

	@Override
	protected void addAddtionalParameters( final GenericDialog gd ) {}

	@Override
	protected boolean queryAdditionalParameters( final GenericDialog gd ) { return true; }
}
