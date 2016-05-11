package gui;

import algorithm.StitchingResults;

/**
 * Interface to be implemented by classes that need access to the global stitching results
 * @author david
 *
 */
public interface StitchingResultsSettable
{
	public void setStitchingResults(StitchingResults res);
}
