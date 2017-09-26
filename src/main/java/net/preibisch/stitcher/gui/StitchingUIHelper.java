package net.preibisch.stitcher.gui;

import java.awt.Choice;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.util.Util;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;

public class StitchingUIHelper
{
	public static final String[] ds = { "1", "2", "4", "8", "16", "32", "64"};
	public static final String[] methods = {"Phase Correlation", "Iterative Intensity Based (Lucas-Kanade)"};
	public static final long[] dsDefault = {4, 4, 2};

	public static boolean allViews2D(final List< ? extends BasicViewDescription< ? > > views)
	{
		List< BasicViewDescription< ? > > all3DVds = views.stream().filter( vd -> {
			if (!vd.getViewSetup().hasSize())
				return true;
			Dimensions dims = vd.getViewSetup().getSize();
			boolean all3D = true;
			for (int d = 0; d<dims.numDimensions(); d++)
				if (dims.dimension( d ) == 1)
					all3D = false;
			return all3D;
		}).collect( Collectors.toList() );

		boolean is2d = all3DVds.size() == 0;
		return is2d;
	}

	public static long[] askForDownsampling(AbstractSpimData< ? > data, boolean is2d)
	{
		// get first non-missing viewDescription
		final Optional<? extends BasicViewDescription< ? > > firstPresent = 
				data.getSequenceDescription().getViewDescriptions().values().stream().filter( v -> v.isPresent() ).findFirst();

		final VoxelDimensions voxelDims;
		if (firstPresent.isPresent() && firstPresent.get().getViewSetup().hasVoxelSize())
			voxelDims = firstPresent.get().getViewSetup().getVoxelSize();
		else
			voxelDims = new FinalVoxelDimensions( "pixels", new double[] {1, 1, 1} );

		String[] dsChoicesXY = new String[ds.length + (is2d ? 0 : 2)];
		System.arraycopy( ds, 0, dsChoicesXY, 0, ds.length );
		if (!is2d)
		{
			dsChoicesXY[dsChoicesXY.length-2] = "Match Z Resolution (less downsampling)";
			dsChoicesXY[dsChoicesXY.length-1] = "Match Z Resolution (more downsampling)";
		}

//		DownsampleTools.downsampleFactor( downsampleXY, downsampleZ, v );

		GenericDialog gd = new GenericDialog( "Downsampling Options" );

		final long[] dsPreset;
		final String[] dsStrings;
		final boolean isHDF5 = MultiResolutionImgLoader.class.isInstance( data.getSequenceDescription().getImgLoader() );
		if (isHDF5)
		{
			dsStrings = DownsampleTools.availableDownsamplings( data, firstPresent.get() );
			gd.addMessage( "Precomputed Downsamplings (x, y, z):", GUIHelper.largefont, GUIHelper.good );
			for (String dsString : dsStrings)
				gd.addMessage( dsString, GUIHelper.smallStatusFont, GUIHelper.neutral );
			dsPreset = closestPresentDownsampling( dsStrings, dsDefault );
		}
		else
		{
			gd.addMessage( "No Precomputed Downsamplings", GUIHelper.largefont, GUIHelper.warning );
			gd.addMessage( "Consider re-saving as HDF5 for better performance.", GUIHelper.smallStatusFont, GUIHelper.neutral );
			dsPreset = dsDefault.clone();
			dsStrings = new String[]{"1, 1, 1"};
		}

		gd.addMessage( "Specify Downsampling", GUIHelper.largefont, GUIHelper.neutral );
		if (isHDF5)
		{
			gd.addMessage( "Choices that are pre-computed will be labeled in green", GUIHelper.smallStatusFont, GUIHelper.good );
			gd.addMessage( "Choices that require additional downsampling will be labeled in orange", GUIHelper.smallStatusFont, GUIHelper.warning );
		}

		gd.addChoice( "Downsample_in_X", dsChoicesXY, Long.toString( dsPreset[0] ));
		final Choice xChoice = (Choice) gd.getChoices().get( gd.getChoices().size()-1 );
		gd.addChoice( "Downsample_in_Y", dsChoicesXY, Long.toString( dsPreset[1] ) );
		final Choice yChoice = (Choice) gd.getChoices().get( gd.getChoices().size()-1 );
		final Choice zChoice;
		if ( !is2d )
		{
			gd.addChoice( "Downsample_in_Z", ds, Long.toString( dsPreset[2] ) );
			zChoice = (Choice) gd.getChoices().get( gd.getChoices().size()-1 );
		}
		else
			zChoice = null;

		if (isHDF5)
		{
			xChoice.addItemListener( e -> choiceCallback( xChoice, yChoice, zChoice, dsStrings ));
			yChoice.addItemListener( e -> choiceCallback( xChoice, yChoice, zChoice, dsStrings ));
			if (zChoice != null)
				zChoice.addItemListener( e -> choiceCallback( xChoice, yChoice, zChoice, dsStrings ));
			choiceCallback( xChoice, yChoice, zChoice, dsStrings );
		}


		gd.showDialog();
		
		if (gd.wasCanceled())
			return null;

		int dsXIdx = gd.getNextChoiceIndex();
		int dsYIdx = gd.getNextChoiceIndex();
		long dsZ = is2d ? 1 : Long.parseLong( gd.getNextChoice() );

		long dsX;
		long dsY;
		if (!is2d)
		{
			if (dsXIdx >= ds.length)
			{
				dsXIdx = -1 * (dsXIdx - ds.length);
				dsX = DownsampleTools.downsampleFactor( dsXIdx, (int) dsZ, voxelDims );
			}
			else
			{
				dsX = Integer.parseInt( ds[ dsXIdx ] );
			}

			if (dsYIdx >= ds.length)
			{
				dsYIdx = -1 * (dsYIdx - ds.length);
				dsY = DownsampleTools.downsampleFactor( dsYIdx, (int) dsZ, voxelDims );
			}
			else
			{
				dsY = Integer.parseInt( ds[ dsYIdx ] );
			}
		}
		else
		{
			dsX = Long.parseLong( ds[dsXIdx] );
			dsY = Long.parseLong( ds[dsYIdx] );
		}

		return new long[] {dsX, dsY, dsZ};
	}

	public static void choiceCallback(Choice x, Choice y, Choice z, String[] available)
	{
		boolean goodChoice = false;
		try
		{
			long selectedItemX = Long.parseLong( x.getSelectedItem() );
			long selectedItemY = Long.parseLong( y.getSelectedItem() );
			long selectedItemZ = 1;
			if (z != null)
				selectedItemZ = Long.parseLong( z.getSelectedItem() );

			for (String availableI : available)
			{
				long[] parseDownsampleChoice = DownsampleTools.parseDownsampleChoice( availableI );
				if (selectedItemX == parseDownsampleChoice[0] && selectedItemY == parseDownsampleChoice[1] && selectedItemZ == parseDownsampleChoice[2])
				{
					goodChoice = true;
					break;
				}
			}
		}
		catch (Exception e)
		{
		}

		x.setForeground( goodChoice ? GUIHelper.good : GUIHelper.warning);
		y.setForeground( goodChoice ? GUIHelper.good : GUIHelper.warning);
		if (z != null)
			z.setForeground( goodChoice ? GUIHelper.good : GUIHelper.warning);
	}

	public static boolean allSmaller(long[] a, long[] b)
	{
		for (int i = 0; i<a.length; i++)
			if (a[i] > b[i])
				return false;
		return true;
	}

	public static double distanceLog(long[] a, long[] b)
	{
		double logD = 0;
		for (int i = 0; i<a.length; i++)
			logD += Math.pow( Math.log( a[i] ) / Math.log( 2 ) - Math.log( b[i] ) / Math.log( 2 ), 2);
		return Math.sqrt( logD );
		
	}

	public static long[] closestPresentDownsampling(String[] dsStrings, long[] maximalDesiredDownsampling)
	{
		int closestIdx = 0;
		double closestDist = Double.MAX_VALUE;
		for (int i=0; i<dsStrings.length; i++)
		{
			long[] dsChoiceI = DownsampleTools.parseDownsampleChoice( dsStrings[i] );
			if (!allSmaller( dsChoiceI, maximalDesiredDownsampling ))
				continue;
			double d = distanceLog( dsChoiceI, maximalDesiredDownsampling );
			if (d < closestDist)
			{
				closestDist = d;
				closestIdx = i;
			}
		}
		return DownsampleTools.parseDownsampleChoice( dsStrings[closestIdx] );
	}

	public static void main( String[] args ) throws SpimDataException
	{
		String xml = "/Users/spreibi/Documents/Grants and CV/BIMSB/Projects/CLARITY/Big Data Sticher/Dros_converted/dataset.xml";

		XmlIoSpimData2 io = new XmlIoSpimData2( null );
		final SpimData2 data = io.load( xml );
		System.out.println(  "chosen ds: " + Util.printCoordinates( askForDownsampling( data, false ) ) );
		System.exit( 0 );
	}
}
