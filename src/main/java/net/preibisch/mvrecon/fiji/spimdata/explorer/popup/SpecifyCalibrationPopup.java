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
package net.preibisch.mvrecon.fiji.spimdata.explorer.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.sequence.ViewId;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.Specify_Calibration;
import net.preibisch.mvrecon.fiji.plugin.Specify_Calibration.Cal;
import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;

public class SpecifyCalibrationPopup extends JMenuItem implements ExplorerWindowSetable
{
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public SpecifyCalibrationPopup()
	{
		super( "Specify Calibration ..." );

		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			final List< ViewId > viewIds = ApplyTransformationPopup.getSelectedViews( panel );

			final ArrayList< Cal > calibrations = Specify_Calibration.findCalibrations( panel.getSpimData(), viewIds );

			final Cal maxCal = Specify_Calibration.mostPresentCal( calibrations );

			if ( !Specify_Calibration.queryNewCal( calibrations, maxCal ) )
				return;

			if ( SpimData.class.isInstance( panel.getSpimData() ) )
			{
				Specify_Calibration.applyCal( maxCal, (SpimData)panel.getSpimData(), viewIds );
	
				if ( showWarning )
				{
					JOptionPane.showMessageDialog(
							null,
							"The calibration was set, but this is not reflected in the transformations yet (Click 'Info' Button). If you want to\n"
							+ "do so, please call 'Apply Transformation' and use the image calibration as basis for transformations." );
	
					showWarning = false;
				}
			}
			else
			{
				JOptionPane.showMessageDialog(
						null,
						"Applying the calibration is not supported for '" + panel.getSpimData().getClass().getSimpleName() + "', needs to extend SpimData." );
			}
		}
	}
}
