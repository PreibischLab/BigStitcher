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
package net.preibisch.mvrecon.fiji.plugin;

import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import net.preibisch.legacy.io.IOFunctions;

public class Toggle_Cluster_Options implements PlugIn
{
	/**
	 * Set this to true so that the option to process as individual cluster jobs shows up in the dialogs
	 */
	public static boolean displayClusterProcessing = false;

	@Override
	public void run( String arg0 )
	{
		final GenericDialog gd = new GenericDialog( "Toggle Cluster Processing Options" );
		gd.addCheckbox( "Display_Cluster Processing Options", displayClusterProcessing );
		gd.showDialog();

		if ( gd.wasCanceled() )
			return;

		displayClusterProcessing = gd.getNextBoolean();

		IOFunctions.println( "Cluster processing option: " + ( displayClusterProcessing ? "ON" : "OFF" ) );
	}
}
