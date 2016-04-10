package algorithm.globalopt;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.mpicbg.PointMatchGeneric;
import spim.process.interestpointregistration.Detection;

public class PairwiseResult
{
	private ArrayList< PointMatchGeneric< Detection > > candidates, inliers;
	private double error = Double.NaN;
	private long time = 0;
	private String result = "", desc = "";
	private ViewId viewIdA, viewIdB;

	public void setResult( final long time, final String result )
	{
		this.time = time;
		this.result = result;
	}
	public void setDescriptions( final String desc ) { this.desc = desc; }
	public void setViewIdA( final ViewId viewIdA ) { this.viewIdA = viewIdA; }
	public void setViewIdB( final ViewId viewIdB ) { this.viewIdB = viewIdB; }
	public ViewId getViewIdA() { return viewIdA; }
	public ViewId getViewIdB() { return viewIdB; }
	public ArrayList< PointMatchGeneric< Detection > > getCandidates() { return candidates; }
	public ArrayList< PointMatchGeneric< Detection > > getInliers() { return inliers; }
	public String getResultMessage() { return result; }
	public void setResultMessage( final String result ) { this.result = result; }
	public double getError() { return error; }
	public void setCandidates( final ArrayList< PointMatchGeneric< Detection > > candidates ) { this.candidates = candidates; }
	public void setInliers( final ArrayList< PointMatchGeneric< Detection > > inliers, final double error )
	{
		this.inliers = inliers;
		this.error = error;
	}

	public String getFullDesc() { return "(" + new Date( time ) + "): " + desc + ": " + result; }
}
