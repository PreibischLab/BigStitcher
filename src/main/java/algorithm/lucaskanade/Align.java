package algorithm.lucaskanade;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import Jama.Matrix;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.iterator.LocalizingIntervalIterator;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.NumericType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class Align<T extends RealType< T >>
{
	final RandomAccessibleInterval< T > template;

	final WarpFunction warpFunction;

	/**
	 * Number of dimensions in the image
	 */
	final int n;

	/**
	 * Number of parameters of the warp function
	 */
	final int numParameters;

	final AffineTransform currentTransform;

	/**
	 * Image of <em>n+1</em> dimensions to store the steepest descent images of
	 * the template image at the identity warp. Dimension <em>n</em> is used to
	 * index the parameters of the warp function. For example, the partial
	 * derivative of the template image intensity by parameter 2 of the warp
	 * function at pixel <em>(x,y)</em> is stored at position <em>(x,y,1)</em>.
	 */
	final Img< FloatType > descent;

	/**
	 * Inverse of the Hessian matrix.
	 */
	double[][] Hinv;

	/**
	 * The error image for the last iteration shows the difference between the
	 * template and the warped (with {@link #currentTransform}) image.
	 */
	final Img< FloatType > error;
	
	boolean lastAlignConverged;
	
	/**
	 * returns true if the last align() call did not run for the maximum allowed number of iterations
	 */
	public boolean didConverge()
	{
		return lastAlignConverged;
	}
	
	public void setCurrentTransform(AffineGet tr)
	{
		this.currentTransform.set( tr );
	}

	public Align(final RandomAccessibleInterval< T > template, final ImgFactory< FloatType > factory, WarpFunction model, AffineGet startTransform)
	{
		this.template = template;
		final T type = Util.getTypeFromInterval( template );

		n = template.numDimensions();
		warpFunction = model;
		numParameters = warpFunction.numParameters();
		
		currentTransform = new AffineTransform( n );

		if (!( startTransform == null ))
			currentTransform.set( startTransform.getRowPackedCopy() );
		
		final long[] dim = new long[n + 1];
		for ( int d = 0; d < n; ++d )
			dim[d] = template.dimension( d );
		dim[n] = n;
		final Img< FloatType > gradients = factory.create( dim, new FloatType() );
		gradients( Views.extendBorder( template ), gradients );

		dim[n] = numParameters;
		descent = factory.create( dim, new FloatType() );
		computeSteepestDescents( gradients, warpFunction, descent );

		Hinv = computeInverseHessian( descent );

		error = factory.create( template, new FloatType() );
	}

	/**
	 * Compute the steepest descent images of the template at the identity warp.
	 * Each steepest descent image comprises the partial derivatives of template
	 * intensities with respect to one parameter of the warp function.
	 *
	 * The result is stored in the <em>n+1</em> dimensional {@link #target}
	 * image. Dimension <em>n</em> is used to index the partial derivative. For
	 * example, the partial derivative by the second parameter of the warp
	 * function is stored in slice <em>n=1</em>.
	 *
	 * @param gradients
	 *            n+1 dimensional image of partial derivatives of the template.
	 *            Dimension n is used to index the partial derivative. For
	 *            example, the partial derivative by Y is stored in slice n=1.
	 * @param warpFunction
	 *            The warp function to be applied to the template. The partial
	 *            derivatives of template intensities with respect to the
	 *            parameters of this warp function are computed.
	 * @param target
	 *            Image of <em>n+1</em> dimensions to store the steepest descent
	 *            Dimension <em>n</em> is used to index the parameters of the
	 *            warp function. For example, the partial derivative of the
	 *            template image intensity by parameter 2 of the warp function
	 *            at pixel <em>(x,y)</em> is stored at position <em>(x,y,1)</em>
	 *            .
	 */
	public static <T extends NumericType< T >> void computeSteepestDescents(
			final RandomAccessibleInterval< T > gradients, final WarpFunction warpFunction,
			final RandomAccessibleInterval< T > target)
	{
		final int n = gradients.numDimensions() - 1;
		final int numParameters = warpFunction.numParameters();
		final T tmp = Util.getTypeFromInterval( gradients ).createVariable();
		for ( int p = 0; p < numParameters; ++p )
		{
			for ( int d = 0; d < n; ++d )
			{
				final Cursor< T > gd = Views.flatIterable( Views.hyperSlice( gradients, n, d ) ).localizingCursor();
				for ( final T t : Views.flatIterable( Views.hyperSlice( target, n, p ) ) )
				{
					tmp.set( gd.next() );
					tmp.mul( warpFunction.partial( gd, d, p ) );
					t.add( tmp );
				}
			}
		}
	}

	/**
	 * Compute the inverse Hessian matrix from the the steepest descent images.
	 */
	public static <T extends RealType< T >> double[][] computeInverseHessian(
			final RandomAccessibleInterval< T > descent)
	{
		final int n = descent.numDimensions() - 1;
		final int numParameters = (int) descent.dimension( n );
		final long[] dim = new long[n + 1];
		descent.dimensions( dim );
		dim[n] = 1;
		final LocalizingIntervalIterator pos = new LocalizingIntervalIterator( dim );
		final RandomAccess< T > r = descent.randomAccess();
		final double[] deriv = new double[numParameters];
		final double[][] H = new double[numParameters][numParameters];
		while ( pos.hasNext() )
		{
			pos.fwd();
			r.setPosition( pos );
			for ( int p = 0; p < numParameters; ++p )
			{
				deriv[p] = r.get().getRealDouble();
				r.fwd( n );
			}
			for ( int i = 0; i < numParameters; ++i )
				for ( int j = 0; j < numParameters; ++j )
					H[i][j] += deriv[i] * deriv[j];
		}
		return new Matrix( H ).inverse().getArray();
	}

	/**
	 * Computed and return the affine transform that aligns image to template.
	 */
	public AffineTransform align(final RandomAccessibleInterval< T > image, final int maxIterations,
			final double minParameterChange)
	{
		lastAlignConverged = false;
		ExecutorService service = Executors.newFixedThreadPool( Runtime.getRuntime().availableProcessors() * 4 );
		currentTransform.set( new AffineTransform( n ) );
		int i = 0;
		while ( i < maxIterations )
		{
			System.out.println( ++i );
			if ( alignStep( image, service ) < minParameterChange )
			{
				lastAlignConverged = true;
				break;
			}
		}
		System.out.println( "computed " + i + " iterations." );
		return currentTransform;
	}

	double alignStep(final RandomAccessibleInterval< T > image, ExecutorService service)
	{
		// compute error image = warped image - template
		computeDifference( Views.extendZero( image ), currentTransform, template, error );

		
		
		// compute transform parameter update
		final double[] gradient = new double[numParameters];
		
		ArrayList< Callable< Void > > calls =  new ArrayList< Callable<Void> >();
		for ( int p = 0; p < numParameters; ++p )
		{
			final int pInner = p;
			Callable< Void > callable = new Callable< Void >()
			{
				@Override
				public Void call() throws Exception
				{
					final Cursor< FloatType > err = Views.flatIterable( error ).cursor();
					for ( final FloatType t : Views.flatIterable( Views.hyperSlice( descent, n, pInner ) ) )
						gradient[pInner] += t.getRealDouble() * err.next().getRealDouble();
					return null;

				}
			};
			
			calls.add( callable );
			
		}
		
		List<Future<Void>> futures = null;
		
		try
		{
			futures = service.invokeAll( calls );
			for (Future<Void> f : futures)
				f.get();
		}
		catch ( InterruptedException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch ( ExecutionException e )
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
		
		final double[] dp = new double[numParameters];
		LinAlgHelpers.mult( Hinv, gradient, dp );

		// udpate transform
		currentTransform.preConcatenate( warpFunction.getAffine( dp ) );

		// return norm of parameter update vector
		return LinAlgHelpers.length( dp );
	}
	
	/**
	 * Compute the partial derivative of source in a particular dimension.
	 *
	 * @param source
	 *            source image, has to provide valid data in the interval of the
	 *            gradient image plus a one pixel border in dimension.
	 * @param target
	 *            output image, the partial derivative of source in the
	 *            specified dimension.
	 * @param dimension
	 *            along which dimension the partial derivatives are computed
	 */
	public static < T extends RealType< T >, S extends RealType< S > > void gradient(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< S > target,
			final int dimension )
	{
		final Cursor< T > front = Views.flatIterable(
				Views.interval( source,
						Intervals.translate( target, 1, dimension ) ) ).cursor();
		final Cursor< T > back = Views.flatIterable(
				Views.interval( source,
						Intervals.translate( target, -1, dimension ) ) ).cursor();
		for( final S t : Views.flatIterable( target ) )
		{
			t.setReal( front.next().getRealDouble() - back.next().getRealDouble());
			t.mul( 0.5 );
		}
	}
	
	/**
	 * Compute the partial derivatives of source every dimension.
	 *
	 * @param source
	 *            n dimensional source image, has to provide valid data in the
	 *            interval of the gradient image plus a one pixel border in
	 *            every dimension.
	 * @param target
	 *            n+1 dimensional output image. Dimension n is used to index the
	 *            partial derivative. For example, the partial derivative by Y
	 *            is stored in slice n=1.
	 */
	public static < T extends RealType< T >, S extends RealType<S> > void gradients(
			final RandomAccessible< T > source,
			final RandomAccessibleInterval< S > target )
	{
		final int n = source.numDimensions();
		for ( int d = 0; d < n; ++d )
			gradient( source, Views.hyperSlice( target, n, d ), d );
	}
	
	
	/**
	 * Compute the pixel-wise difference between an affine-transformed source
	 * image and a target image.
	 *
	 * @param source
	 *            The source image.
	 * @param transform
	 *            A coordinate transformation to apply to the source image.
	 * @param target
	 *            The target image.
	 * @param difference
	 *            Output image. The pixel-wise difference between the
	 *            transformed source image and the target image is stored here.
	 */
	public static < T extends RealType< T >,  S extends RealType< S > > void computeDifference(
			final RandomAccessible< T > source,
			final AffineTransform transform,
			final RandomAccessible< T > target,
			final RandomAccessibleInterval< S > difference )
	{
		final RealRandomAccessible< T > interpolated = Views.interpolate( source, new NLinearInterpolatorFactory< T >() );
		final RandomAccessible< T > warped = RealViews.affine( interpolated, transform );

		final Cursor< T > cw = Views.flatIterable( Views.interval( warped, difference ) ).cursor();
		final Cursor< T > ct = Views.flatIterable( Views.interval( target, difference ) ).cursor();
		for ( final S t : Views.flatIterable( difference ) )
		{
			t.setReal( ( cw.next().getRealDouble() - ct.next().getRealDouble() ));
			//t.sub( ct.next() );
		}
	}
}
