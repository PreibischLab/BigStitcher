package net.preibisch.mvrecon.fiji.datasetmanager;

import java.io.File;
import java.util.Date;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.process.ImageProcessor;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.RealUnsignedShortConverter;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.img.planar.PlanarImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.fiji.plugin.resave.GenericResaveHDF5.Parameters;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyStackImgLoaderIJ;
import net.preibisch.mvrecon.process.export.ExportSpimData2HDF5;

public class LegacyStackImgLoaderIJGUI extends LegacyStackImgLoaderIJ {
	
	
	public LegacyStackImgLoaderIJGUI(
			final File path, final String fileNamePattern, final ImgFactory< ? extends NativeType< ? > > imgFactory,
			final int layoutTP, final int layoutChannels, final int layoutIllum, final int layoutAngles, final int layoutTiles,
			final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
	{
		super( path, fileNamePattern, imgFactory, layoutTP, layoutChannels, layoutIllum, layoutAngles, layoutTiles, sequenceDescription );
	}

	protected static Parameters queryParameters()
	{
		final GenericDialog gd = new GenericDialog( "Opening 32bit TIFF as 16bit" );

		gd.addMessage( "You are trying to open 32-bit images as 16-bit (resaving as HDF5 maybe). Please define how to convert to 16bit.", GUIHelper.mediumstatusfont );
		gd.addMessage( "Note: This dialog will only show up once for the first image.", GUIHelper.mediumstatusfont );
		gd.addChoice( "Convert_32bit", Generic_Resave_HDF5.convertChoices, Generic_Resave_HDF5.convertChoices[ Generic_Resave_HDF5.defaultConvertChoice ] );

		gd.showDialog();

		if ( gd.wasCanceled() )
			return null;

		Generic_Resave_HDF5.defaultConvertChoice = gd.getNextChoiceIndex();

		if ( Generic_Resave_HDF5.defaultConvertChoice == 2 )
		{
			if ( Double.isNaN( Generic_Resave_HDF5.defaultMin ) )
				Generic_Resave_HDF5.defaultMin = 0;

			if ( Double.isNaN( Generic_Resave_HDF5.defaultMax ) )
				Generic_Resave_HDF5.defaultMax = 5;

			final GenericDialog gdMinMax = new GenericDialog( "Define min/max" );

			gdMinMax.addNumericField( "Min_Intensity_for_16bit_conversion", Generic_Resave_HDF5.defaultMin, 1 );
			gdMinMax.addNumericField( "Max_Intensity_for_16bit_conversion", Generic_Resave_HDF5.defaultMax, 1 );
			gdMinMax.addMessage( "Note: the typical range for multiview deconvolution is [0 ... 10] & for fusion the same as the input intensities., ",GUIHelper.mediumstatusfont );

			gdMinMax.showDialog();

			if ( gdMinMax.wasCanceled() )
				return null;

			Generic_Resave_HDF5.defaultMin = gdMinMax.getNextNumber();
			Generic_Resave_HDF5.defaultMax = gdMinMax.getNextNumber();
		}
		else
		{
			Generic_Resave_HDF5.defaultMin = Generic_Resave_HDF5.defaultMax = Double.NaN;
		}

		return new Parameters( false, null, null, null, null, false, false, 0, 0, false, 0, Generic_Resave_HDF5.defaultConvertChoice, Generic_Resave_HDF5.defaultMin, Generic_Resave_HDF5.defaultMax );
	}
	
	
	/**
	 * Get {@link UnsignedShortType} un-normalized image.
	 *
	 * @param view
	 *            timepoint and setup for which to retrieve the image.
	 * @return {@link UnsignedShortType} image.
	 */
	@Override
	public RandomAccessibleInterval< UnsignedShortType > getImage( final ViewId view )
	{
		final File file = getFile( view );

		if ( file == null )
			throw new RuntimeException( "Could not find file '" + file + "'." );

		IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Loading '" + file + "' ..." );

		final ImagePlus imp = open( file );

		if ( imp == null )
			throw new RuntimeException( "Could not load '" + file + "'." );

		final boolean is32bit;
		final RealUnsignedShortConverter< FloatType > converter;

		if ( imp.getType() == ImagePlus.GRAY32 )
		{
			is32bit = true;
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Image '" + file + "' is 32bit, opening as 16bit with scaling" );

			if ( params == null )
				params = queryParameters();

			if ( params == null )
				return null;

			final double[] minmax = ExportSpimData2HDF5.updateAndGetMinMax( ImageJFunctions.wrapFloat( imp ), params );
			converter = new RealUnsignedShortConverter< FloatType >( minmax[ 0 ], minmax[ 1 ] );
		}
		else
		{
			is32bit = false;
			converter = null;
		}

		final long[] dim = new long[]{ imp.getWidth(), imp.getHeight(), imp.getStack().getSize() };
		final Img< UnsignedShortType > img = instantiateImg( dim, new UnsignedShortType() );

		if ( img == null )
			throw new RuntimeException( "Could not instantiate " + getImgFactory().getClass().getSimpleName() + " for '" + file + "', most likely out of memory." );
		else
			IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Opened '" + file + "' [" + dim[ 0 ] + "x" + dim[ 1 ] + "x" + dim[ 2 ] + " image=" + img.getClass().getSimpleName() + "<UnsignedShortType>]" );

		final ImageStack stack = imp.getStack();
		final int sizeZ = imp.getStack().getSize();

		if ( img instanceof ArrayImg || img instanceof PlanarImg )
		{
			final Cursor< UnsignedShortType > cursor = img.cursor();
			final int sizeXY = imp.getWidth() * imp.getHeight();

			for ( int z = 0; z < sizeZ; ++z )
			{
				final ImageProcessor ip = stack.getProcessor( z + 1 );

				if( is32bit )
				{
					final FloatType input = new FloatType();
					final UnsignedShortType output = new UnsignedShortType();

					for ( int i = 0; i < sizeXY; ++i )
					{
						input.set( ip.getf( i ) );
						converter.convert( input, output );
						cursor.next().set( output.get() );
					}
				}
				else
				{
					for ( int i = 0; i < sizeXY; ++i )
						cursor.next().set( ip.get( i ) );
				}
			}
		}
		else
		{
			final int width = imp.getWidth();

			for ( int z = 0; z < sizeZ; ++z )
			{
				final Cursor< UnsignedShortType > cursor = Views.iterable( Views.hyperSlice( img, 2, z ) ).localizingCursor();
				final ImageProcessor ip = stack.getProcessor( z + 1 );

				if ( is32bit )
				{
					final FloatType input = new FloatType();
					final UnsignedShortType output = new UnsignedShortType();

					while ( cursor.hasNext() )
					{
						cursor.fwd();
						input.set( ip.getf( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) );
						converter.convert( input, output );
						cursor.get().set( output );
					}
				}
				else
				{
					while ( cursor.hasNext() )
					{
						cursor.fwd();
						cursor.get().set( ip.get( cursor.getIntPosition( 0 ) + cursor.getIntPosition( 1 ) * width ) );
					}
				}
			}
		}

		// update the MetaDataCache of the AbstractImgLoader
		// this does not update the XML ViewSetup but has to be called explicitly before saving
		updateMetaDataCache( view, imp.getWidth(), imp.getHeight(), imp.getStack().getSize(),
				imp.getCalibration().pixelWidth, imp.getCalibration().pixelHeight, imp.getCalibration().pixelDepth );

		imp.close();

		return img;
	}
}
