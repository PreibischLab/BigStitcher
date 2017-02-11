package gui;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Dimensions;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.Util;

public class RegularTranformHelpers
{
	private static int prod(int ... v)
	{
		int res = 1;
		for (int i = 0; i < v.length; ++i)
			res *= v[i];
		return res;
	}
	
	public static class RegularTranslationParameters
	{
		public int nDimensions;
		public int[] nSteps;
		public double[] overlaps;
		public int[] dimensionOrder;
		public boolean[] alternating;
		public boolean[] increasing;
	}
	
	public static List< AffineTransform3D > generateRegularGrid(RegularTranslationParameters params, Dimensions dims)
	{
		
		final ArrayList< AffineTransform3D > transforms = new ArrayList<>();
		int nTiles = prod(params.nSteps);
		for (int i = 0; i < nTiles; ++i)
			transforms.add(new AffineTransform3D());
		
		int modulo = 1;
		for (int i = 0; i < params.nDimensions; ++i)
		{
			int d = params.dimensionOrder[i];
			double moveSize = (1.0 - params.overlaps[d]) * dims.dimension( d );			
			
			
			moveD(transforms, d, moveSize, modulo, params.nSteps[d], params.alternating[d], params.increasing[d]);
			modulo *= params.nSteps[d];
			
		}
		
		//transforms.forEach( ( t ) -> System.out.println( Util.printCoordinates( t.getTranslation() ))) ;
		return transforms;
	}
	
	private static void moveD(	List< AffineTransform3D > transforms, 
								int d,
								double moveSize,
								int modulo,
								int steps, 
								boolean alternate, 
								boolean increasing)
	{
		for (int i = 0; i < transforms.size(); ++i)
		{
			int stepNo = i / (modulo * steps) ;
			int inStep = alternate && stepNo % 2 != 0 ? steps - 1 - i / modulo % steps: i / modulo % steps;
			transforms.get( i ).set( (increasing ? 1.0 : -1.0) * inStep * moveSize, d, 3 );			
		}
		
	}
	
	public static void main(String[] args)
	{
		RegularTranslationParameters params = new RegularTranslationParameters();
		params.nDimensions = 3;
		params.alternating = new boolean[] {true, true, true};
		params.dimensionOrder = new int[] {1, 0, 2};
		params.increasing = new boolean[] {true, true, true};
		params.overlaps = new double[] {0.2, 0.2, 0.2};
		params.nSteps = new int[] {3, 3, 2};
		
		Dimensions dims = new FinalInterval( new long[] {100, 100, 100} );
		
		generateRegularGrid( params, dims );
		
	}
	

}
