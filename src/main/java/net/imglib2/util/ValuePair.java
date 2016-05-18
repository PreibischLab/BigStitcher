package net.imglib2.util;

import net.imglib2.util.Pair;

/**
 * TODO
 * 
 */
public class ValuePair< A, B > implements Pair< A, B >
{


	final public A a;

	final public B b;

	public ValuePair( final A a, final B b )
	{
		this.a = a;
		this.b = b;
	}

	@Override
	public A getA()
	{
		return a;
	}

	@Override
	public B getB()
	{
		return b;
	}
	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ( ( a == null ) ? 0 : a.hashCode() );
		result = prime * result + ( ( b == null ) ? 0 : b.hashCode() );
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if ( this == obj )
			return true;
		if ( obj == null )
			return false;
		if ( getClass() != obj.getClass() )
			return false;
		ValuePair other = (ValuePair) obj;
		if ( a == null )
		{
			if ( other.a != null )
				return false;
		}
		else if ( !a.equals( other.a ) )
			return false;
		if ( b == null )
		{
			if ( other.b != null )
				return false;
		}
		else if ( !b.equals( other.b ) )
			return false;
		return true;
	}
}