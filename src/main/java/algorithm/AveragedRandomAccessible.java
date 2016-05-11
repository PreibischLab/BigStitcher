package algorithm;

import java.util.ArrayList;

import net.imglib2.Interval;
import net.imglib2.Point;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.Sampler;
import net.imglib2.type.numeric.NumericType;

public  class  AveragedRandomAccessible <T extends NumericType<T >> implements RandomAccessible< T >
{
	private int numD;
	private ArrayList<RandomAccessible< T >> randomAccessibles;
	
	public AveragedRandomAccessible(int numD)
	{
		this.numD = numD;
		this.randomAccessibles = new ArrayList<>();
	}
	
	public void addRAble( RandomAccessible< T > RAble)
	{
		randomAccessibles.add( RAble );
	}
	
	@Override
	public int numDimensions()
	{
		return numD;
	}

	@Override
	public RandomAccess< T > randomAccess()
	{
		return new AverageRandomAccess();
	}

	@Override
	public RandomAccess< T > randomAccess(Interval interval)
	{
		return new AverageRandomAccess();
	}
	
	class AverageRandomAccess extends Point implements RandomAccess< T >
	{
		ArrayList< RandomAccess< T > > RAs;
		T type;
		T one;
		
		public AverageRandomAccess()
		{
			super(randomAccessibles.get( 0 ).numDimensions());
			RAs = new ArrayList<>();
			for (RandomAccessible< T > RAbleI : randomAccessibles)
			{
				type = RAbleI.randomAccess().get().createVariable();
				RAs.add( RAbleI.randomAccess() );
			}
			one = type.createVariable();
			one.setOne();
		}
		
		
		@Override
		public T get()
		{
			// TODO position on move
			T sum = type.createVariable();
			T count = type.createVariable();
			for (RandomAccess< T > ra : RAs)
			{
				ra.setPosition( this );
				sum.add( ra.get() );
				count.add( one );
			}	
			
			sum.div( count );
			return sum;
		}

		@Override
		public Sampler< T > copy()
		{
			return copyRandomAccess();
		}

		@Override
		public RandomAccess< T > copyRandomAccess()
		{
			AverageRandomAccess ra = new AverageRandomAccess();
			ra.setPosition( this );
			return ra;
		}
		
	}

	

}
