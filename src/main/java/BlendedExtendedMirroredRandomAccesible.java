

import java.io.File;

import ij.ImageJ;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.Sampler;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import spim.process.fusion.weights.Blending;


public class BlendedExtendedMirroredRandomAccesible <T extends RealType<T>>implements RandomAccessible<T> {

	private RandomAccessibleInterval<T> img;
	Blending blending;
	private int numDimensions;
	private FinalInterval extDims;
	
	public BlendedExtendedMirroredRandomAccesible(RandomAccessibleInterval<T> img, int[] border) {
		this.img = img;
		this.numDimensions = img.numDimensions();
		
		float[] blendingBorder = new float[numDimensions];
		float[] border2 = new float[numDimensions];
		
		this.extDims = new FinalInterval(img);		
		for (int i = 0; i < numDimensions; i++)
		{
			extDims = Intervals.expand(extDims, border[i], i);
			blendingBorder[i] = border[i];
			border2[i] = 0.0f;
		}
		
		this.blending = new Blending(extDims, border2, blendingBorder);
	}
	
	
	@Override
	public int numDimensions() {
		return numDimensions;
	}
	
	public FinalInterval getExtInterval() {
		return extDims;
	}

	public class BlendedRandomAccess extends Point implements RandomAccess<T>
	{
		public BlendedRandomAccess() {
			super(img.numDimensions());
		}

		@Override
		public T get() {
			RandomAccess<T> imgRA = Views.extendMirrorSingle(img).randomAccess();
			imgRA.setPosition(position);
			RealRandomAccess<FloatType> blendRA = blending.realRandomAccess();
			blendRA.setPosition(position);
			T val = imgRA.get().createVariable();
			val.setReal(imgRA.get().getRealFloat() * blendRA.get().getRealFloat());
			return val;
		}

		@Override
		public Sampler<T> copy() {
			return copy();
		}

		@Override
		public RandomAccess<T> copyRandomAccess() {
			BlendedRandomAccess a = new BlendedRandomAccess();
			a.move(this);
			return a;
		}
		
	}
	
	@Override
	public RandomAccess<T> randomAccess() {
		return new BlendedRandomAccess();
	}

	@Override
	public RandomAccess<T> randomAccess(Interval interval) {
		return randomAccess();
	}

	

	public static void main(String[] args) {
		
		new ImageJ();
		Img<FloatType> img1 = ImgLib2Util.openAs32Bit(new File("src/main/resources/img1.tif"));
		long[] dims = new long[img1.numDimensions()];

		BlendedExtendedMirroredRandomAccesible<FloatType> ext = new BlendedExtendedMirroredRandomAccesible<FloatType>(img1, new int[]{100, 100, 5});
		
		ext.getExtInterval().dimensions(dims);
		
		Img<FloatType> img2 = ArrayImgs.floats(dims);
		
		Cursor<FloatType> c = img2.cursor();
		RandomAccess<FloatType> ra = ext.randomAccess();
		while (c.hasNext())
		{
			c.fwd();
			ra.setPosition(c);
			c.get().set(ra.get());
		}
		
		//ImageJFunctions.show(img2);
		
		RandomAccessibleInterval<FloatType> img3 = Views.interval(ext, ext.getExtInterval());
		ImageJFunctions.show(img3);
		

	}
}
