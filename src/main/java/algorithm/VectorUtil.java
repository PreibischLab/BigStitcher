package algorithm;

public class VectorUtil
{
	public static double getVectorLength(double[] vec){
		double res = 0;
		for (int i = 0; i<vec.length; i++)
			res += vec[i] * vec[i];
		return Math.sqrt( res );
	}
	
	public static double[] getVectorDiff(double[] a, double[] b)
	{
		double[] res = new double[a.length];
		for (int i = 0; i<a.length; i++)
			res[i] = b[i] - a[i];
		return res;
	}
	
	public static double[] getVectorSum(double[] a, double[] b)
	{
		double[] res = new double[a.length];
		for (int i = 0; i<a.length; i++)
			res[i] = b[i] + a[i];
		return res;
	}

}
