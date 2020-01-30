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
package net.preibisch.mvrecon.fiji.plugin.util;

import ij.ImagePlus;
import ij.io.Opener;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.type.numeric.real.FloatType;

public class OpenImg
{
	public static Img< FloatType > open( final String name, final ImgFactory< FloatType > factory )
	{
		final Opener io = new Opener();
		ImagePlus imp = io.openImage( name );
		
		if ( imp.getStack().getSize() > 1 )
		{
			final int depth = imp.getStack().getSize();

			final Img< FloatType > img = factory.create( new long[]{ imp.getWidth(), imp.getHeight(), depth }, new FloatType() );

			final int w = imp.getWidth();
			final int h = imp.getHeight();

			final RandomAccess< FloatType > r = img.randomAccess();
			final int[] l = new int[ 3 ];
			
			for ( int i = 0; i < depth; ++i )
			{
				final ImageProcessor ip = imp.getStack().getProcessor( i + 1 );

				l[ 2 ] = i;

				for ( int y = 0; y < h; ++y )
				{
					l[ 0 ] = 0;
					l[ 1 ] = y;
					r.setPosition( l );
					
					for ( int x = 0; x < w; ++x )
					{
						r.get().set( ip.getPixelValue( x, y ) );
						if ( x < w - 1 )
							r.fwd( 0 );
					}
				}
			}			

			return img;
		}
		else
		{
			final Img< FloatType > img = factory.create( new long[]{ imp.getWidth(), imp.getHeight() }, new FloatType() );
			
			final ImageProcessor ip = imp.getProcessor();
			
			final Cursor< FloatType > cursorOut = img.localizingCursor();
			
			while ( cursorOut.hasNext() )
			{
				cursorOut.fwd();
				cursorOut.get().set( ip.getPixelValue( cursorOut.getIntPosition( 0 ), cursorOut.getIntPosition( 1 ) ) );
			}
			
			return img;
		}
	}
}
