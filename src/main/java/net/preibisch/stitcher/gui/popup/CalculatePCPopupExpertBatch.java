/*-
 * #%L
 * Multiview stitching of large datasets.
 * %%
 * Copyright (C) 2016 - 2023 Big Stitcher developers.
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
package net.preibisch.stitcher.gui.popup;

import javax.swing.JComponent;
import javax.swing.JMenu;

import net.preibisch.mvrecon.fiji.spimdata.explorer.ExplorerWindow;
import net.preibisch.mvrecon.fiji.spimdata.explorer.popup.ExplorerWindowSetable;
import net.preibisch.stitcher.gui.popup.CalculatePCPopup.Method;

public class CalculatePCPopupExpertBatch extends JMenu implements ExplorerWindowSetable
{

	final CalculatePCPopup phaseCorrSimple;
	final CalculatePCPopup phaseCorr;
	final CalculatePCPopup lucasKanade;
	final PairwiseInterestPointRegistrationPopup interestPoint;
	boolean wizardMode;

	public CalculatePCPopupExpertBatch( String description, boolean wizardMode )
	{
		super( description );

		this.wizardMode = wizardMode;

		if (!wizardMode)
			phaseCorrSimple = new CalculatePCPopup( "Phase Correlation", true, Method.PHASECORRELATION, wizardMode );
		else
			phaseCorrSimple = null;
			
		phaseCorr = new CalculatePCPopup( wizardMode ? "Phase Correlation" : "Phase Correlation (expert)", false, Method.PHASECORRELATION, wizardMode );
		lucasKanade = new CalculatePCPopup( "Lucas-Kanade", false, Method.LUCASKANADE, wizardMode );
		interestPoint = new PairwiseInterestPointRegistrationPopup( "Interest point based", wizardMode );

		if(!wizardMode)
			this.add(phaseCorrSimple);
		this.add( phaseCorr );
		this.add( lucasKanade );
		this.add( interestPoint );
	}

	@Override
	public JComponent setExplorerWindow( ExplorerWindow< ?, ? > panel )
	{
		if ( !wizardMode )
			this.phaseCorrSimple.setExplorerWindow( panel );
		this.phaseCorr.setExplorerWindow( panel );
		this.lucasKanade.setExplorerWindow( panel );
		this.interestPoint.setExplorerWindow( panel );
		return this;
	}

}
