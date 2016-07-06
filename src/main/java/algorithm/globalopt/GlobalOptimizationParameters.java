package algorithm.globalopt;

public class GlobalOptimizationParameters
{
	public double correlationT;
	public double relativeThreshold;
	public double absoluteThreshold;
	
	public GlobalOptimizationParameters()
	{
		this(0, 2.5, 3.5);		
	}
	
	public GlobalOptimizationParameters(double correlationThreshold, double relativeThreshold, double absoluteThreshold)
	{
		this.correlationT = correlationThreshold;
		this.relativeThreshold = relativeThreshold;
		this.absoluteThreshold = absoluteThreshold;		
	}
}
