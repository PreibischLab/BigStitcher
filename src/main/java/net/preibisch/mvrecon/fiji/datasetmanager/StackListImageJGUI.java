/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2017 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.fiji.datasetmanager;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class StackListImageJGUI<S extends StackListGUI> extends StackListImageJ implements MultiViewDatasetDefinition {

	S gui;
	
	@Override
	public SpimData2 createDataset() {
		return gui.createDataset();
	}

	@Override
	public String getTitle() 
	{
		return "Manual Loader (TIFF only, ImageJ Opener)";
	}

	@Override
	public String getExtendedDescription()
	{
		return "This dataset definition supports a series of three-dimensional (3d) image stacks\n" +  
				 "all present in the same folder. The filename of each file must define timepoint,\n" +  
				 "angle, channel and illumination direction (or a subset of those).\n" + 
				 "The 3d image stacks must be stored as TIFF that can be opened by ImageJ natively.\n" + 
				 "\n" + 
				 "\n" + 
				 "The filenames of the 3d image stacks could be for example:\n" +
				 "\n" + 
				 "spim_TL1_Channel1_Illum1_Angle0.tif ... spim_TL100_Channel2_Illum2_Angle315.tif\n" + 
				 "data_TP01_Angle000.lsm ... data_TP70_Angle180.lsm\n" +
				 "Angle0.ome.tiff ... Angle288.ome.tiff\n" +
				 "\n" +
				 "Note: this definition can be used for OpenSPIM data if saved as plain TIFF.";
	}

	@Override
	public StackListImageJGUI<S> newInstance() {
		return new StackListImageJGUI<S>();
	}

}
