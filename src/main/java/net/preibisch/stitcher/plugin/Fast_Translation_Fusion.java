package net.preibisch.stitcher.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.joda.time.DateTime;

import bdv.util.ConstantRandomAccessible;
import ij.ImageJ;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.NativeImg;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineGet;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import net.preibisch.mvrecon.Threads;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.process.interestpointdetection.methods.downsampling.DownsampleTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;
import net.preibisch.stitcher.algorithm.TransformTools;
import net.preibisch.stitcher.algorithm.fastfusion.FastFusionTools;

public class Fast_Translation_Fusion implements PlugIn
{

	final static String[] dsChoices = new String[] {"1", "2", "4", "8", "16"};
	static int defaultDsChoice = 0;
	static boolean defaultUseInterpolation = true;
	static boolean defaultUseBlending = true;
	final static String[] dtypeChoices = new String[] {"16-bit Unsigned Integer", "32-bit Floating Point"};
	static int defaultDtypeChoice = 0;

	// TODO: allow adjustment of blending/border as it is done in normal fusion
	final static float[] staticBorder = Util.getArrayFromValue( 0.0f, 3 );
	final static float[] staticBlending = Util.getArrayFromValue( 30.0f, 3 );

	@Override
	public void run( String arg )
	{
		// ask for everything but the channels
		final LoadParseQueryXML result = new LoadParseQueryXML();

		if ( !result.queryXML( "Fast Translation Fusion", true, true, true, true, true ) )
			return;
		fuse( result.getData(), SpimData2.getAllViewIdsSorted( result.getData(), result.getViewSetupsToProcess(), result.getTimePointsToProcess() ) );
	}

	public static boolean fuse(
			final SpimData2 spimData,
			final List< ViewId > viewsToProcess )
	{
		// thread pool for all operations
		final ExecutorService pool = Executors.newFixedThreadPool( Threads.numThreads() );

		// FIXME: we have to sort views, otherwise weird view-transformation mixups happen?
		// TODO: this should not be necessary, look into this
		// NB: this is probably fixed? sorting no longer necessary?
		final List< ViewId > viewsSorted = new ArrayList<>();
		viewsSorted.addAll( viewsToProcess );
		//Collections.sort( viewsSorted );

		// get view descriptions for all present views
		final List< ViewDescription > presentViewDescriptions = viewsSorted.stream()
								.filter( v -> spimData.getSequenceDescription().getViewDescription( v ).isPresent() )
								.map( v -> spimData.getSequenceDescription().getViewDescription( v ) )
								.collect( Collectors.toList() );

		// get registrations and corresponding affine transforms for all present
		final List< ViewRegistration > registrations = presentViewDescriptions.stream()
				.map( v -> spimData.getViewRegistrations().getViewRegistration( v ) )
				.collect( Collectors.toList() );
		final List< AffineTransform3D > transforms = registrations.stream()
			.map( vr -> {
				vr.updateModel();
				return vr.getModel();
			})
			.collect( Collectors.toList() );

		// check if the non-translation part of transforms is equal (only in this case, we can cleanly do the fast fusion)
		final boolean allNonTranslationsEqual = registrations.stream()
				.reduce( 
						new ValuePair<>(true, registrations.get( 0 )),
						(a,vr) -> new ValuePair<>(a.getA() && TransformTools.nonTranslationsEqual(a.getB(), vr), vr),
						(a1, a2) -> new ValuePair<>(a1.getA() && a2.getA() && TransformTools.nonTranslationsEqual(a1.getB(), a2.getB()), a1.getB() ) )
				.getA();

		// TODO: use subclassed FusionGUI here? -> that way, we could use exporters
		// TODO: at least extract parameter query to separate method
		final GenericDialog gd = new GenericDialog( "Fast Fusion Options" );

		// warn when not allNonTranslationsEqual
		if (!allNonTranslationsEqual)
			gd.addMessage( "WARNING: View Registrations differ in more than just Translation, the fast fusion will not be exact"
					+ ", consider using the normal fusion", GUIHelper.mediumstatusfont, GUIHelper.warning );

		// query downsampling, datatype, blending, interpolation
		gd.addChoice( "Downsampling", dsChoices, dsChoices[defaultDsChoice] );
		gd.addCheckbox( "Use_Linear_Interpolation", defaultUseInterpolation );
		gd.addCheckbox( "Use_Blending", defaultUseBlending );
		gd.addChoice( "Output_Data_Type", dtypeChoices, dtypeChoices[defaultDtypeChoice] );

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		defaultDsChoice = gd.getNextChoiceIndex();
		final int downsampling = Integer.parseInt( dsChoices[defaultDsChoice] );
		final boolean interpolation = defaultUseInterpolation = gd.getNextBoolean();
		// NB: no blending but interpolation will cause some artifacts on the border pixels, make blending default for now
		//final boolean blending = defaultUseBlending; // = gd.getNextBoolean();
		final boolean blending = gd.getNextBoolean();
		defaultDtypeChoice = gd.getNextChoiceIndex();


		// get dimensions of downsampled images
		final List< Dimensions > dims = presentViewDescriptions.stream()
				.map( v -> {
					if (downsampling == 1)
						return v.getViewSetup().getSize();
					else
					{
						// NB: this might overestimate size for openAndDownsample
						// (should be ok, but fused image might contain a bit of blank space due to this)
						// TODO: implement this cleanly, also check how downsampling in done in HDF5
						long[] dimsDS = new long[3];
						v.getViewSetup().getSize().dimensions( dimsDS );
						dimsDS[0] /= downsampling;
						dimsDS[1] /= downsampling;
						dimsDS[2] /= downsampling;
						return new FinalInterval( dimsDS );
					}
				} )
				.collect( Collectors.toList() );

		// get the downsampling transforms for all images (we only support power of two downsampling in this mode)
		final List< AffineGet > dsTransforms = presentViewDescriptions.stream()
			.map( vd -> FastFusionTools.getDownsamplingTransfomPowerOf2( spimData.getSequenceDescription().getImgLoader(), vd, downsampling ) )
			.collect( Collectors.toList() );

		// get shift vectors
		final List< double[] > shifts = new ArrayList<>();
		final Iterator< AffineGet > dsIt = dsTransforms.iterator();
		transforms.forEach( tr -> {
			final AffineGet dsTr = dsIt.next();
			final double[] translation = new double[3];
			translation[0] = tr.get( 0, 3 );
			translation[1] = tr.get( 1, 3 );
			translation[2] = tr.get( 2, 3 );
			// get to downsampled pixel shift by applying the inverse of non-translational part of registration
			// and then the inverse downsampling transform
			TransformTools.decomposeIntoAffineAndTranslation( tr ).getA().inverse().apply( translation, translation );
			dsTr.inverse().apply( translation, translation );
			shifts.add( translation );
		});

		final List< float[] > subpixelOffs = new ArrayList<>();
		final List< int[] > pixelShifts = new ArrayList<>();
		final long[] min = Util.getArrayFromValue( Long.MAX_VALUE, 3 );
		final long[] max = Util.getArrayFromValue( Long.MIN_VALUE, 3 );
		for (int i=0; i<shifts.size(); i++)
		{
			final double[] shift = shifts.get( i );
			final Dimensions dim = dims.get( i );
			final int[] pixelShift = new int[3];
			final float[] subpixelOff = new float[3];
			for (int d=0; d<3; d++)
			{
				pixelShift[d] = (int) (interpolation ? Math.floor( shift[d] ) : Math.round( shift[d] ));
				subpixelOff[d] = (float) ( shift[d] - pixelShift[d] );
				max[d] = Math.max( max[d], dim.dimension( d ) + pixelShift[d] - (interpolation ? 0 : 1) );
				min[d] = Math.min( min[d], pixelShift[d] );
			}
			subpixelOffs.add( subpixelOff );
			pixelShifts.add( pixelShift );
		}

		// convert pixel shifts for zero-min output
		for (int i=0; i<pixelShifts.size(); i++)
		{
			final int[] pixelShift = pixelShifts.get( i );
			for (int d=0; d<3; d++)
			{
				pixelShift[d] -= min[d];
			}
		}

		// the interval in which we render and number of pixels
		final FinalInterval renderInterval = new FinalInterval( min, max );
		final long numPixels = Intervals.numElements( renderInterval );

		// TODO: query grouping factors from user
		final Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
		groupingFactors.add( Channel.class );

		final List< Group< ViewDescription > > groups = Group.splitBy( presentViewDescriptions, groupingFactors );

		final int nGroups = groups.size();
		final AtomicInteger groupCount = new AtomicInteger();

		IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Fast Fusion of " + nGroups + " groups." );

		for (final Group< ViewDescription > group: groups)
		{

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): "
					+ "Fusing group " + groupCount.incrementAndGet() + " of " + nGroups + "." );

			NativeImg< UnsignedShortType, ? > outImg = (numPixels > (Math.pow( 2, 31 ) - 1) ? new CellImgFactory<>( new UnsignedShortType() ) : new ArrayImgFactory<>( new UnsignedShortType() )).create( renderInterval );
			NativeImg< FloatType, ? > weightImg = (numPixels > (Math.pow( 2, 31 ) - 1) ? new CellImgFactory<>( new FloatType() ) : new ArrayImgFactory<>( new FloatType() )).create( renderInterval );
			NativeImg< FloatType, ? > alphaImg = null;
			if (interpolation)
				alphaImg = (numPixels > (Math.pow( 2, 31 ) - 1) ? new CellImgFactory<>( new FloatType() ) : new ArrayImgFactory<>( new FloatType() )).create( renderInterval );

			for (int i=0; i<presentViewDescriptions.size(); i++)
			{
				// TODO: progress indicator?
				if (group.contains( presentViewDescriptions.get( i ) ))
				{
					System.out.println( group.toString() + ": " + i );

					// load downsampled
					RandomAccessibleInterval< FloatType > downsampledImg = DownsampleTools.openAndDownsample( 
							spimData.getSequenceDescription().getImgLoader(),
							presentViewDescriptions.get( i ),
							new AffineTransform3D(),
							downsampling,
							downsampling,
							true );

					RandomAccessibleInterval< FloatType > alpha = null;
					if (interpolation)
					{
						// 
						Pair< RandomAccessibleInterval< FloatType >, RandomAccessibleInterval< FloatType > > linearInterpolation = FastFusionTools.getLinearInterpolation( downsampledImg, new FloatType(), subpixelOffs.get( i ), pool );
						downsampledImg = linearInterpolation.getA();
						alpha = linearInterpolation.getB(); 

						// add alpha to result
						FastFusionTools.alphaBlendTranslated( Views.iterable( alpha ), alphaImg, pixelShifts.get( i ), pool );
					}

					RandomAccessibleInterval< FloatType > weights = (interpolation ?
																	alpha :
																	(Views.iterable( downsampledImg ).size() > (Math.pow( 2, 31 ) - 1) ? new CellImgFactory<>( new FloatType() ) : new ArrayImgFactory<>( new FloatType() )).create( downsampledImg )
																	);
					if (blending)
					{
						FastFusionTools.applyWeights( downsampledImg, weights, subpixelOffs.get( i ), staticBorder, staticBlending, interpolation, pool );
						FastFusionTools.addTranslated( Views.iterable( weights ), weightImg, pixelShifts.get( i ), pool );
					}
					else
					{
						// TODO: correctly weight alpha images
						final ConstantRandomAccessible< FloatType > constantWeightOne = new ConstantRandomAccessible<>( new FloatType( 1 ), 3 );
						FastFusionTools.addTranslated( Views.iterable(interpolation ?  Views.interval( constantWeightOne, downsampledImg ) : Views.interval( constantWeightOne, downsampledImg ) ), weightImg, pixelShifts.get( i ), pool );
					}

					FastFusionTools.addTranslated( Views.iterable( downsampledImg ), outImg, pixelShifts.get( i ), pool );

				}
			}

			FastFusionTools.normalizeWeights( outImg, weightImg, pool );

			if (interpolation)
				FastFusionTools.multiplyEqualSizeImages( outImg, alphaImg, pool );

			// TODO: convert to short if 16-bit was selected
			ImageJFunctions.show( outImg );

			IOFunctions.println(
					"(" + new Date(System.currentTimeMillis()) + "): "
					+ "Fusing group " + groupCount.get() + " of " + nGroups + " DONE." );
		}

		pool.shutdown();

		IOFunctions.println(
				"(" + new Date(System.currentTimeMillis()) + "): "
				+ "Fusion of " + nGroups + " groups DONE." );

		return true;
	}

	public static void main(String[] args)
	{
		new ImageJ();
		new Fast_Translation_Fusion().run( "" );
	}

}
