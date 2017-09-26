package net.preibisch.stitcher.algorithm;

import java.util.ArrayList;

import mpicbg.models.Affine3D;
import mpicbg.models.AffineModel3D;
import mpicbg.models.PointMatch;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.FinalInterval;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Translation2D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

public class TransformTools {
	
	
	public static Pair<AffineGet, AffineGet> decomposeIntoAffineAndTranslation(AffineGet tr)
	{
		AffineTransform3D t = new AffineTransform3D();
		t.set( tr.getRowPackedCopy() );
		t.set( 0, 0, 3 );
		t.set( 0, 1, 3 );
		t.set( 0, 2, 3 );
		AffineTransform3D tt = new AffineTransform3D();
		tt.set( tr.get( 0, 3 ), 0, 3 );
		tt.set( tr.get( 1, 3 ), 1, 3 );
		tt.set( tr.get( 2, 3 ), 2, 3 );
		
		return new ValuePair< AffineGet, AffineGet >( t, tt );
	}
	public static AffineTransform3D mapBackTransform(AffineGet to, AffineGet from)
	{
		final double[][] p = new double[][]{
			{ 0, 0, 0 },
			{ 1, 0, 0 },
			{ 0, 1, 0 },
			{ 1, 1, 0 },
			{ 0, 0, 1 },
			{ 1, 0, 1 },
			{ 0, 1, 1 },
			{ 1, 1, 1 }};

		final double[][] pa = new double[8][3];
		final double[][] pb = new double[8][3];
		
		for ( int i = 0; i < p.length; ++i )
			to.apply( p[ i ], pa[ i ] );
		
		for ( int i = 0; i < p.length; ++i )
			from.apply( p[ i ], pb[ i ] );
		
		// compute the model that maps pb >> pa
		AffineModel3D mapBackModel = new AffineModel3D();
		
		try
		{
			final ArrayList< PointMatch > pm = new ArrayList< PointMatch >();

			for ( int i = 0; i < p.length; ++i )
				pm.add( new PointMatch( new mpicbg.models.Point( pb[i] ), new mpicbg.models.Point( pa[i] ) ) );

			mapBackModel.fit( pm );
		}
		catch ( Exception e )
		{
			IOFunctions.println( "Could not compute model for mapping back: " + e );
			e.printStackTrace();
			return null;
		}

		final AffineTransform3D mapBack = new AffineTransform3D();
		final double[][] m = new double[3][4];
		( (Affine3D< ? >) mapBackModel ).toMatrix( m );

		mapBack.set( m[0][0], m[0][1], m[0][2], +m[0][3], m[1][0], m[1][1], m[1][2], m[1][3], m[2][0], m[2][1], m[2][2],
				m[2][3] );

		return mapBack;
		
	}
	
	public static String printRealInterval( final RealInterval interval )
	{
		String out = "(Interval empty)";

		if ( interval == null || interval.numDimensions() == 0 )
			return out;

		out = "[" + interval.realMin( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMin( i );

		out += "] -> [" + interval.realMax( 0 );

		for ( int i = 1; i < interval.numDimensions(); i++ )
			out += ", " + interval.realMax( i );

		out += "]";

		return out;
	}

	public static AffineTransform3D createTranslation( final double tx, final double ty, final double tz )
	{
		final AffineTransform3D translation = new AffineTransform3D();

		translation.set( tx, 0, 3 );
		translation.set( ty, 1, 3 );
		translation.set( tz, 2, 3 );

		return translation;
	}

	/**
	 * 
	 * @param vr the ViewRegistration to decompose
	 * @param is2d true or false
	 * @param dsCorrectionT downsampling correction 
	 * @return (1) the ViewRegistration without Translation part and the translation, with the inverse of (1) and dsCorrection applied
	 */
	public static Pair<AffineGet, TranslationGet> getInitialTransforms( final ViewRegistration vr, final boolean is2d, final AffineTransform3D dsCorrectionT )
	{
		AffineTransform3D model = vr.getModel().copy();
		
		// get model without translation (last column set to 0)
		AffineTransform3D modelWithoutTranslation = model.copy();
		modelWithoutTranslation.set( 0, 0, 3 );
		modelWithoutTranslation.set( 0, 1, 3 );
		modelWithoutTranslation.set( 0, 2, 3 );
		
		// the translation with inverse of other part of model applied
		final double[] target = model.getTranslation();
		modelWithoutTranslation.applyInverse( target, target );
		
		// we go from big to downsampled, thats why the inverse
		dsCorrectionT.applyInverse( target, target );
		
		
		if ( is2d )
			return new ValuePair<>(modelWithoutTranslation, new Translation2D( target[ 0 ], target[ 1 ] ));
		else
			return new ValuePair<>(modelWithoutTranslation, new Translation3D( target[ 0 ], target[ 1 ], target[ 2 ] ));
	}
	
	/**
	 * check for all a_i, b_i: {@literal abs(a_i - b_i) <= eps} 
	 * @param a double array a
	 * @param b double array b
	 * @param eps tolerance
	 * @return true or false
	 */
	public static boolean allAlmostEqual(double[] a, double[] b, double eps)
	{
		if (a.length != b.length)
			return false;
		
		for (int i = 0; i < a.length; i++)
			if (Math.abs( a[i] - b[i] ) > eps)
				return false;
		
		return true;
	}
	
	public static boolean nonTranslationsEqual(final ViewRegistration vr1, final ViewRegistration vr2)
	{
		final int n = vr1.getModel().numDimensions();
		final AffineTransform3D dsTransform = new AffineTransform3D();
		
		final Pair< AffineGet, TranslationGet > initialTransforms1 = getInitialTransforms( vr1, n == 2, dsTransform );
		final Pair< AffineGet, TranslationGet > initialTransforms2 = getInitialTransforms( vr2, n == 2, dsTransform );
		
		return allAlmostEqual( initialTransforms1.getA().getRowPackedCopy(), initialTransforms2.getA().getRowPackedCopy(), 0.01 );
	}

	public static FinalRealInterval applyTranslation(RealInterval img, TranslationGet translation, boolean[] ignoreDims){

		// get number of dimensions we actually use
		int n = 0;
		for (int d = 0; d < ignoreDims.length; ++d)
			if (!ignoreDims[d])
				n++;
		
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		int i2 = 0;
		for (int i = 0; i< img.numDimensions();++i)
		{
			if (!ignoreDims[i])
			{
				min[i2] = img.realMin(i) + translation.getTranslation(i);
				max[i2] = img.realMax(i) + translation.getTranslation(i);
				i2++;
			}
		}
		return new FinalRealInterval(min, max);
	}
	
	/**
	 * get overlap in local image coordinates (assuming min = (0,0,..))
	 * @param img image interval (global coordinates)
	 * @param overlap overlap interval (global coordinates)
	 * @return overlap interval  in local coordinates
	 */
	public static FinalRealInterval getLocalOverlap(RealInterval img, RealInterval overlap){
		final int n = img.numDimensions();
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = Math.max(0, overlap.realMin(i) - img.realMin(i)) ;
			max[i] = Math.max(0, overlap.realMax(i) - img.realMin(i));
		}
		return new FinalRealInterval(min, max);
	}
	
	/**
	 * create an integer interval from real interval, being conservatie on the size
	 * (min is ceiled, max is floored)
	 * @param overlap real input
	 * @return interger interval, with mins ceiled and maxs floored
	 */
	public static FinalInterval getLocalRasterOverlap(RealInterval overlap)
	{
		final int n = overlap.numDimensions();
		final long [] min = new long [n];
		final long [] max = new long [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = Math.round((overlap.realMin(i))) + 1;
			max[i] = Math.round((overlap.realMax(i))) - 1;
		}
		
		return new FinalInterval(min, max);
	}
	
	
	public static FinalRealInterval getOverlap(final RealInterval img1, final RealInterval img2){
		final int n = img1.numDimensions();
		final double [] min = new double [n];
		final double [] max = new double [n];
		
		for (int i = 0; i< n; i++)
		{
			min[i] = Math.max(img1.realMin(i), img2.realMin(i));
			max[i] = Math.min(img1.realMax(i), img2.realMax(i));
			
			// intervals do not overlap
			if ( max[i] < min [i])
				return null;
		}
		
		return new FinalRealInterval(min, max);
	}
	
	public static void main(String[] args)
	{
		AffineTransform3D scale = new AffineTransform3D();
		
		scale.scale( 2.0 );
		//scale.rotate( 0, 45 );
		
		
		AffineTransform3D translate = new AffineTransform3D();
		translate.translate( new double[] {100.0, 100.0, 100.0} );
		AffineTransform3D translate2 = new AffineTransform3D();
		translate2.translate( new double[] {200.0, 200.0, 200.0} );
		
		AffineTransform3D conc = scale.copy().preConcatenate( translate );
		scale.scale( 3.0 );
		AffineTransform3D conc2 = scale.copy().preConcatenate( translate2 );
		//System.out.println( scale );
		//System.out.println( translate );
		System.out.println( conc );
		System.out.println( conc2 );
		
		RealPoint p1 = new RealPoint( 3 );
		conc.apply( p1, p1 );
		RealPoint p2 = new RealPoint( 3 );
		conc2.apply( p2, p2 );
		
		System.out.println( p1 );
		System.out.println( p2 );
		
		AffineTransform3D everythingbuttraslation = conc.copy();
		everythingbuttraslation.set( 0, 0, 3 );
		everythingbuttraslation.set( 0, 1, 3 );
		everythingbuttraslation.set( 0, 2, 3 );
		
		AffineTransform3D everythingbuttraslation2 = conc2.copy();
		everythingbuttraslation2.set( 0, 0, 3 );
		everythingbuttraslation2.set( 0, 1, 3 );
		everythingbuttraslation2.set( 0, 2, 3 );
		
		double[] trans1 = conc.getTranslation();
		double[] trans2 = conc2.getTranslation();
		
		everythingbuttraslation.inverse().apply( trans1, trans1 );
		everythingbuttraslation2.inverse().apply( trans2, trans2 );
		
		System.out.println( new RealPoint( trans1 ) );
		System.out.println( new RealPoint( trans2 ) );
		
		
		System.out.println( mapBackTransform( everythingbuttraslation, everythingbuttraslation2 ) );
		
		
		
		
	}
	
	

}
