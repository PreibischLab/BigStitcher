package input;

import ij.ImagePlus;

import java.io.File;
import java.util.ArrayList;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.generic.sequence.ImgLoaderHints;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Cursor;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;

public class GenerateSpimData
{
	public static SpimData grid3x2()
	{
		final ArrayList< ViewSetup > setups = new ArrayList< ViewSetup >();

		final Channel c0 = new Channel( 0, "RFP" );
		final Channel c1 = new Channel( 1, "YFP" );
		final Channel c2 = new Channel( 2, "GFP" );

		final Angle a0 = new Angle( 0 );
		final Illumination i0 = new Illumination( 0 );

		final Tile t0 = new Tile( 0, "Tile0", new double[]{ 0.0, 0.0, 0.0 } );
		final Tile t1 = new Tile( 0, "Tile1", new double[]{ 450.0, 0.0, 0.0 } );
		final Tile t2 = new Tile( 0, "Tile2", new double[]{ 0.0, 450.0, 0.0 } );
		final Tile t3 = new Tile( 0, "Tile3", new double[]{ 450.0, 450.0, 0.0 } );

		final Dimensions d0 = new FinalDimensions( 512l, 512l, 86l );
		final VoxelDimensions vd0 = new FinalVoxelDimensions( "px", 0.4566360, 0.4566360, 2.0000000 );

		setups.add( new ViewSetup( 0, "setup 0", d0, vd0, t0, c0, a0, i0 ) );
		setups.add( new ViewSetup( 1, "setup 1", d0, vd0, t1, c0, a0, i0 ) );
		setups.add( new ViewSetup( 2, "setup 2", d0, vd0, t2, c0, a0, i0 ) );
		setups.add( new ViewSetup( 3, "setup 3", d0, vd0, t3, c0, a0, i0 ) );

		setups.add( new ViewSetup( 4, "setup 4", d0, vd0, t0, c1, a0, i0 ) );
		setups.add( new ViewSetup( 5, "setup 5", d0, vd0, t1, c1, a0, i0 ) );
		setups.add( new ViewSetup( 6, "setup 6", d0, vd0, t2, c1, a0, i0 ) );
		setups.add( new ViewSetup( 7, "setup 7", d0, vd0, t3, c1, a0, i0 ) );

		setups.add( new ViewSetup( 8, "setup 8", d0, vd0, t0, c2, a0, i0 ) );
		setups.add( new ViewSetup( 9, "setup 9", d0, vd0, t1, c2, a0, i0 ) );
		setups.add( new ViewSetup( 10, "setup 10", d0, vd0, t2, c2, a0, i0 ) );
		setups.add( new ViewSetup( 11, "setup 11", d0, vd0, t3, c2, a0, i0 ) );

		final ArrayList< TimePoint > t = new ArrayList< TimePoint >();
		t.add( new TimePoint( 0 ) );
		final TimePoints timepoints = new TimePoints( t );

		final ArrayList< ViewId > missing = new ArrayList< ViewId >();
		final MissingViews missingViews = new MissingViews( missing );

		final ImgLoader imgLoader = new ImgLoader()
		{
			@Override
			public SetupImgLoader< ? > getSetupImgLoader( int setupId )
			{
				return new MySetupImgLoader( setupId );
			}
		};

		final SequenceDescription sd = new SequenceDescription( timepoints, setups, imgLoader, missingViews );

		return new SpimData( new File( "" ), sd, null );
	}

	public static class MySetupImgLoader implements SetupImgLoader< FloatType >
	{
		final int setupId;

		public MySetupImgLoader( final int setupId )
		{
			this.setupId = setupId;
		}

		@Override
		public RandomAccessibleInterval< FloatType > getImage(
				int timepointId, ImgLoaderHint... hints )
		{
			return getFloatImage( timepointId, false, hints );
		}

		@Override
		public FloatType getImageType() { return new FloatType(); }

		@Override
		public RandomAccessibleInterval< FloatType > getFloatImage(
				int timepointId, boolean normalize, ImgLoaderHint... hints )
		{
			ImagePlus imp;
			
			if ( setupId % 4 == 0 )
				imp = new ImagePlus( "/Users/spreibi/Documents/Microscopy/Stitching/Truman/standard/73.tif" );
			else if ( setupId % 4 == 1 )
				imp = new ImagePlus( "/Users/spreibi/Documents/Microscopy/Stitching/Truman/standard/74.tif" );
			else if ( setupId % 4 == 2 )
				imp = new ImagePlus( "/Users/spreibi/Documents/Microscopy/Stitching/Truman/standard/75.tif" );
			else 
				imp = new ImagePlus( "/Users/spreibi/Documents/Microscopy/Stitching/Truman/standard/76.tif" );

			Img< FloatType > img = copyChannel( imp, setupId / 4 );

			imp.close();

			return img;
		}

		@Override
		public Dimensions getImageSize( int timepointId ) { return null; }

		@Override
		public VoxelDimensions getVoxelSize( int timepointId ) { return null; }
	}

	public static Img< FloatType > copyChannel( final ImagePlus imp, final int channel )
	{
		final int w, h, d;

		Img< FloatType > img = ArrayImgs.floats( w = imp.getWidth(), h = imp.getHeight(), d = imp.getNSlices() );
		
		final Cursor< FloatType > c = img.cursor();

		for ( int z = 0; z < d; ++z )
		{
			final int[] pixels = (int[])imp.getStack().getProcessor( z + 1 ).getPixels();
			
			for ( int i = 0; i < w*h; ++i )
			{
				if ( channel == 0 )
					c.next().set( ( pixels[ i ] & 0xff0000) >> 16 );
				else if ( channel == 1 )
					c.next().set( ( pixels[ i ] & 0xff00 ) >> 8 );
				else
					c.next().set( pixels[ i ] & 0xff );
			}
		}
		
		return img;
	}

	public static void main( String[] args )
	{
		SpimData spimData = grid3x2();
		ImgLoader i = spimData.getSequenceDescription().getImgLoader();
		
		for ( final ViewSetup vs: spimData.getSequenceDescription().getViewSetups().values() )
		{
			ImageJFunctions.show( i.getSetupImgLoader( vs.getId() ).getFloatImage( spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().get( 0 ).getId(), false, ImgLoaderHints.LOAD_COMPLETELY ) );
		}
	}
}
