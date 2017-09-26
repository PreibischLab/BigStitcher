package net.preibisch.stitcher.gui;

import net.preibisch.mvrecon.fiji.spimdata.stitchingresults.StitchingResults;

/**
 * Interface to be implemented by classes that need access to the global stitching results
 * @author david
 *
 */
public interface StitchingResultsSettable
{
	public void setStitchingResults(StitchingResults res);
}
