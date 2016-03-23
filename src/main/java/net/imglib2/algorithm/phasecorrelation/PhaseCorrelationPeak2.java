package net.imglib2.algorithm.phasecorrelation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Point;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealRandomAccessible;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.algorithm.localextrema.SubpixelLocalization;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.InvertibleRealTransform;
import net.imglib2.realtransform.RealViews;
import net.imglib2.realtransform.Translation2D;
import net.imglib2.realtransform.Translation3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.imglib2.Interval;
import net.imglib2.Localizable;


public class PhaseCorrelationPeak2 {
	
	Localizable pcmLocation; // location in the raw PCM
	RealLocalizable subpixelPcmLocation; // subpixel localized PCM location

	Localizable shift; // corresponding shift between images
	RealLocalizable subpixelShift; // corresponding subpixel accurate shift between images

	double phaseCorr; // value in PCM
	double crossCorr; // crosscorrelation bewtween imgs
	long nPixel; // number of overlapping pixels
	
	public Localizable getPcmLocation() {
		return pcmLocation;
	}

	public void setPcmLocation(Localizable pcmLocation) {
		this.pcmLocation = pcmLocation;
	}

	public Localizable getShift() {
		return shift;
	}

	public void setShift(Localizable shift) {
		this.shift = shift;
	}

	public RealLocalizable getSubpixelPcmLocation() {
		return subpixelPcmLocation;
	}

	public void setSubpixelPcmLocation(RealLocalizable subpixelPcmLocation) {
		this.subpixelPcmLocation = subpixelPcmLocation;
	}

	public RealLocalizable getSubpixelShift() {
		return subpixelShift;
	}

	public void setSubpixelShift(RealLocalizable subpixelShift) {
		this.subpixelShift = subpixelShift;
	}

	public double getPhaseCorr() {
		return phaseCorr;
	}

	public void setPhaseCorr(double phaseCorr) {
		this.phaseCorr = phaseCorr;
	}

	public double getCrossCorr() {
		return crossCorr;
	}

	public void setCrossCorr(double crossCorr) {
		this.crossCorr = crossCorr;
	}

	public long getnPixel() {
		return nPixel;
	}

	public void setnPixel(long nPixel) {
		this.nPixel = nPixel;
	}
	
	/**
	 * constructor with just raw PCM location and value
	 * @param pcmPosition
	 * @param phaseCorr
	 */
	public PhaseCorrelationPeak2(Localizable pcmPosition, double phaseCorr)
	{
		this.pcmLocation = new Point( pcmPosition );
		this.shift = null;
		this.subpixelPcmLocation = null;
		this.subpixelShift = null;
		this.phaseCorr = phaseCorr;
		this.crossCorr = 0.0;
		this.nPixel = 0;
	}
	
	/**
	 * copy contructor
	 * @param src
	 */
	public PhaseCorrelationPeak2(PhaseCorrelationPeak2 src){
		this.pcmLocation = new Point(src.pcmLocation);
		this.shift = src.shift == null? null : new Point(src.shift);
		this.subpixelPcmLocation = src.subpixelPcmLocation == null ? null : new RealPoint( src.subpixelPcmLocation );
		this.subpixelShift = src.subpixelShift == null ? null : new RealPoint( src.subpixelShift );
		this.phaseCorr = src.phaseCorr;
		this.crossCorr = src.crossCorr;
		this.nPixel = src.nPixel;
	}
	
	public static class ComparatorByPhaseCorrelation implements Comparator<PhaseCorrelationPeak2> {
		@Override
		public int compare(PhaseCorrelationPeak2 o1, PhaseCorrelationPeak2 o2) {
			return Double.compare(o1.phaseCorr, o2.phaseCorr);
		}		
	}
	
	public static class ComparatorByCrossCorrelation implements Comparator<PhaseCorrelationPeak2>{
		@Override
		public int compare(PhaseCorrelationPeak2 o1, PhaseCorrelationPeak2 o2) {
			int ccCompare = Double.compare(o1.crossCorr, o2.crossCorr);
			if (ccCompare != 0){
				return ccCompare;
			} else {
				return (int)(o1.nPixel - o2.nPixel);
			}
		}
	}
	
	
	/**
	 * checks the cross correlation of two images shifted as indicated by this phaseCorrelationPeak,
	 * update the values of crossCor and nPixels accordingly
	 * if the images do not overlap (or overlap less than a specified minimal overlap),
	 * crossCorr and nPixel will be set to 0
	 * @param img1
	 * @param img2
	 * @param minOverlapPx
	 */
	
	public <T extends RealType<T>, S extends RealType<S>> void calculateCrossCorr(RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2, 
			Dimensions minOverlapPx)
	{
		Pair<Interval, Interval> intervals = PhaseCorrelation2Util.getOverlapIntervals(img1, img2, shift);
		
		// no overlap found
		if (intervals == null) {
			crossCorr = 0.0;
			nPixel = 0;
			return;
		}
		
		nPixel = 1;
		for (int i = 0; i< intervals.getA().numDimensions(); i++){
			if (minOverlapPx != null && intervals.getA().dimension(i) < minOverlapPx.dimension(i)){
				crossCorr = 0.0;
				nPixel = 0;
				return;
			}
			
			nPixel *= intervals.getA().dimension(i);
		}

		// for subpixel move the underlying Img2 by the subpixel offset
		if ( subpixelShift != null )
		{
			RealRandomAccessible< S > rra = Views.interpolate( Views.extendMirrorSingle( img2 ), new NLinearInterpolatorFactory< S >() );
			
			InvertibleRealTransform transform = null;
			
			// e.g. subpixel = (-0.4, 0.1, -0.145)
			final double tx = subpixelShift.getDoublePosition( 0 ) - shift.getDoublePosition( 0 );
			final double ty = subpixelShift.getDoublePosition( 1 ) - shift.getDoublePosition( 1 );

			if ( rra.numDimensions() == 2 )
				transform = new Translation2D( -tx, -ty ); // -relative subpixel shift only
			else if ( rra.numDimensions() == 3 )
				transform = new Translation3D( -tx, -ty, shift.getDoublePosition( 2 ) - subpixelShift.getDoublePosition( 2 ) ); // -relative subpixel shift only
	
			img2 = Views.interval( Views.raster( RealViews.transform( rra, transform ) ), img2 );
		}

		crossCorr = PhaseCorrelation2Util.getCorrelation(Views.zeroMin(Views.interval(img1, intervals.getA())), Views.zeroMin(Views.interval(img2, intervals.getB())));
		
	}
	
	/**
	 * calculate cross correlation of two images with no minimal overlap size
	 * @param img1
	 * @param img2
	 */
	public <T extends RealType<T>, S extends RealType<S>> void calculateCrossCorr(RandomAccessibleInterval<T> img1, RandomAccessibleInterval<S> img2){
		calculateCrossCorr(img1, img2, null);
	}
	
	/**
	 * refine the shift using subpixel localization in the original PCM
	 * @param pcm
	 */
	public <T extends RealType<T>> void  calculateSubpixelLocalization(RandomAccessibleInterval<T> pcm){
		
		List<Point> peaks = new ArrayList<Point>();
		peaks.add(new Point(pcmLocation));
		
		final int n = pcm.numDimensions();

		boolean[] allowedToMoveInDim = new boolean[ n ];
		for (int i = 0; i< allowedToMoveInDim.length; i++){
			allowedToMoveInDim[i]=false;
		}

		// TODO: It doesnt look like this does anything? Subpixel peaks are just regular peaks as RealPoint - with maxNumMoves == 1 it should now :)
		// subpixel localization can move on periodic condition outofbounds
		List<RefinedPeak<Point>> res = SubpixelLocalization.refinePeaks(peaks, Views.extendPeriodic( pcm ), null, true,
					1, false, 0.0f, allowedToMoveInDim);

		final RefinedPeak< Point > rp = res.get( 0 );
		this.subpixelPcmLocation = new RealPoint( rp );

		double maxDist = 0.0;

		for ( int d = 0; d < n; ++d )
			maxDist = Math.max( maxDist, Math.abs( rp.getOriginalPeak().getDoublePosition( d ) - this.subpixelPcmLocation.getDoublePosition( d ) ) );

		// not a stable peak
		if ( maxDist > 0.5 )
		{
			this.subpixelPcmLocation = null;
		}
		else
		{
			this.phaseCorr = rp.getValue();
		}
	}

	public static void main(String[] args) {
		
		PhaseCorrelationPeak2 peaks = new PhaseCorrelationPeak2(new Point(new int[] {10, 10, 10}), 1.0);
		Dimensions pcmDims = new FinalDimensions(new int[] {50, 50, 50});
		Dimensions imgDims = new FinalDimensions(new int[] {30, 30, 30});
		PhaseCorrelation2Util.expandPeakToPossibleShifts(peaks, pcmDims, imgDims, imgDims);
		
	}
	

}
