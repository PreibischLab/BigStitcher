package net.preibisch.mvrecon.fiji.datasetmanager;

import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class StackListLOCIGUI<S extends StackListGUI> extends StackListLOCI implements MultiViewDatasetDefinition {
	S gui;
	
	@Override
	public String getTitle()
	{
		return "Manual Loader (Bioformats based)";
	}
	
	@Override
	public String getExtendedDescription()
	{
		return  "This dataset definition supports series of image stacks all present in the same\n" +  
				 "folder. The filename of each file is defined by timepoint, angle, channel and\n" +  
				 "illumination direction or multiple timepoints and channels per file.\n" + 
				 "The image stacks can be stored in any fileformat that LOCI Bioformats is able\n" + 
				 "to import, for example TIFF, LSM, CZI, ...\n" + 
				 "\n" + 
				 "The filenames of the 3d image stacks could be for example:\n" +
				 "\n" + 
				 "spim_TL1_Ill1_Angle0.tif ... spim_TL100_Ill2_Angle315.tif [2 channels each]\n" + 
				 "data_TP01_Angle000.lsm ... data_TP70_Angle180.lsm\n" +
				 "Angle0.ome.tiff ... Angle288.ome.tiff\n" +
				 "\n" +
				 "Note: this definition can be used for OpenSPIM data.";
	}

	@Override
	public SpimData2 createDataset() {
		return gui.createDataset();
	}

	@Override
	public StackListLOCIGUI<S> newInstance() { return new StackListLOCIGUI<S>(); }

	
}
