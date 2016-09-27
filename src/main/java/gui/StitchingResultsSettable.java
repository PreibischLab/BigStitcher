package gui;

import spim.fiji.spimdata.stitchingresults.StitchingResults;

/**
 * Interface to be implemented by classes that need access to the global stitching results
 * @author david
 *
 */
public interface StitchingResultsSettable
{
	public void setStitchingResults(StitchingResults res);
}
