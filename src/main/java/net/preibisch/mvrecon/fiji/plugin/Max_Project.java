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

import static mpicbg.spim.data.generic.sequence.ImgLoaderHints.LOAD_COMPLETELY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.PlugIn;
import ij.process.FloatProcessor;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.psf.PSFCombination;

public class Max_Project implements PlugIn
{
	@Override
	public void run( final String arg )
	{
		// ask for everything
		final LoadParseQueryXML result = new LoadParseQueryXML();
		
		if ( !result.queryXML( "image fusion", true, true, true, true, true ) )
			return;

		maxProject(
			result.getData(),
			SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ),
			new UnsignedShortType() );
	}

	public static < T extends RealType< T > & NativeType< T > > boolean maxProject(
			final SpimData data,
			final List< ? extends ViewId > viewIds,
			final T type )
	{
		final ArrayList< ViewDescription > list = new ArrayList< ViewDescription >();

		for ( final ViewId viewId : viewIds )
		{
			final ViewDescription vd = data.getSequenceDescription().getViewDescription( viewId );

			if ( vd != null && vd.isPresent() )
				list.add( vd );
		}

		return maxProject( list, data.getSequenceDescription().getImgLoader(), type );
	}

	public static < T extends RealType< T > & NativeType< T > > boolean maxProject(
			final List< ? extends ViewDescription > vds,
			final ImgLoader imgLoader,
			final T type )
	{
		Collections.sort( vds );

		final ArrayList< TimePoint > tps = SpimData2.getAllTimePointsSorted( vds );
		final ArrayList< ViewSetup > setups = SpimData2.getAllViewSetups( vds );

		for ( final ViewSetup setup : setups )
		{
			ImageStack stack = null;

			for ( final TimePoint t : tps )
				for ( final ViewDescription vd : vds )
					if ( vd.getTimePointId() == t.getId() && vd.getViewSetupId() == setup.getId() )
					{
						IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Loading image for timepoint " + t.getId() + " viewsetup " + vd.getViewSetupId() );

						final RandomAccessibleInterval< T > img = getImage( type, imgLoader, vd, false );

						final FloatProcessor fp =
								toProcessor( PSFCombination.computeMaxProjection( img, new ArrayImgFactory< T >(), 2, true ) );

						if ( stack == null )
							stack = new ImageStack( fp.getWidth(), fp.getHeight() );

						stack.addSlice( "Timepoint=" + t.getId(), fp);
					}

			final ImagePlus imp = new ImagePlus( "ViewSetupId=" + setup.getId(), stack );
			imp.setDimensions( 1, 1, stack.getSize() );
			imp.show();
		}

		return true;
	}

	public static FloatProcessor toProcessor( final Img< ? extends RealType< ? > > img )
	{
		final FloatProcessor fp = new FloatProcessor( (int)img.dimension( 0 ), (int)img.dimension( 1 ) );
		final float[] array = (float[])fp.getPixels();

		final Cursor< ? extends RealType< ? > > c = img.cursor();
		
		for ( int i = 0; i < array.length; ++ i)
			array[ i ] = c.next().getRealFloat();

		return fp;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static < T extends RealType< T > > RandomAccessibleInterval< T > getImage( final T type, ImgLoader imgLoader, final ViewId view, final boolean normalize )
	{
		if ( (RealType)type instanceof FloatType )
			return (RandomAccessibleInterval)imgLoader.getSetupImgLoader( view.getViewSetupId() ).getFloatImage( view.getTimePointId(), normalize, LOAD_COMPLETELY );
		else if ( (RealType)type instanceof UnsignedShortType )
			return (RandomAccessibleInterval)imgLoader.getSetupImgLoader( view.getViewSetupId() ).getImage( view.getTimePointId(), LOAD_COMPLETELY );
		else
			return null;
	}

	public static void main( String[] args )
	{
		// TODO Auto-generated method stub

	}

}
