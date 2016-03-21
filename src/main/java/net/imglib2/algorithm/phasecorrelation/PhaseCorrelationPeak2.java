package net.imglib2.algorithm.phasecorrelation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.algorithm.localextrema.SubpixelLocalization;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.imglib2.Interval;
import net.imglib2.Localizable;


public class PhaseCorrelationPeak2 {
	
	Localizable pcmLocation; // location in the raw PCM
	Localizable shift; // corresponding shift between images
	RealLocalizable subpixelShift; // subpixel localized shift
	
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
		this.pcmLocation = new Point(pcmPosition);
		this.shift = new Point(pcmPosition);
		this.subpixelShift = new RealPoint(pcmPosition);
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
		this.shift = new Point(src.shift);
		this.subpixelShift = new RealPoint(src.subpixelShift);
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
		
		boolean[] allowedToMoveInDim = new boolean[pcm.numDimensions()];
		for (int i = 0; i< allowedToMoveInDim.length; i++){
			allowedToMoveInDim[i]=false;
		}
		// TODO: It doesnt look like this does anything? Subpixel peaks are just regular peaks as RealPoint		
		List<RefinedPeak<Point>> res = SubpixelLocalization.refinePeaks(peaks, Views.extendMirrorSingle(pcm), pcm, true,
					0, false, 0.0f, allowedToMoveInDim);
		
		double[] subpixelShift = new double[pcm.numDimensions()];
		long[] pcmPos = new long[pcm.numDimensions()];
		long[] shift = new long[pcm.numDimensions()];
		
		res.get(0).localize(subpixelShift);
		this.pcmLocation.localize(pcmPos);
		this.shift.localize(shift);
		
		for (int i = 0; i<pcm.numDimensions(); i++){
			subpixelShift[i] += (shift[i] - pcmPos[i]);
		}
		
		this.subpixelShift = new RealPoint(subpixelShift);
	}
		
	
	public static void main(String[] args) {
		
		PhaseCorrelationPeak2 peaks = new PhaseCorrelationPeak2(new Point(new int[] {10, 10, 10}), 1.0);
		Dimensions pcmDims = new FinalDimensions(new int[] {50, 50, 50});
		Dimensions imgDims = new FinalDimensions(new int[] {30, 30, 30});
		PhaseCorrelation2Util.expandPeakToPossibleShifts(peaks, pcmDims, imgDims, imgDims);
		
	}
	

}
