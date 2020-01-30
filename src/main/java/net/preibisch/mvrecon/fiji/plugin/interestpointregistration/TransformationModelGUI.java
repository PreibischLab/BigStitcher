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
package net.preibisch.mvrecon.fiji.plugin.interestpointregistration;

import ij.gui.GenericDialog;
import mpicbg.models.AbstractModel;
import mpicbg.models.AffineModel3D;
import mpicbg.models.IdentityModel;
import mpicbg.models.InterpolatedAffineModel3D;
import mpicbg.models.RigidModel3D;
import mpicbg.models.TranslationModel3D;

public class TransformationModelGUI
{
	public static String modelChoice[] = new String[] { "Translation", "Rigid", "Affine" };
	public static String regularizationModelChoice[] = new String[] { "Identity", "Translation", "Rigid", "Affine" };

	public static double defaultLambda = 0.1;
	public static int defaultRegularizationModelIndex = 2;

	int modelIndex, regularizedModelIndex;
	double lambda;
	boolean regularize;

	public TransformationModelGUI( final int modelIndex )
	{
		this( modelIndex, -1, 0.1, false );
	}

	public TransformationModelGUI( final int modelIndex, final int regularizedModelIndex, final double lambda, final boolean regularize )
	{
		this.modelIndex = modelIndex;
		this.regularize = regularize;
		this.regularizedModelIndex = regularizedModelIndex;
		this.lambda = lambda;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public AbstractModel getModel()
	{
		AbstractModel<?> model;
		
		if ( modelIndex == 0 )
			model = new TranslationModel3D();
		else if ( modelIndex == 1 )
			model = new RigidModel3D();
		else
			model = new AffineModel3D();

		if ( regularize )
		{
			if ( regularizedModelIndex == 0 )
				model = new InterpolatedAffineModel3D( model, new IdentityModel(), (float)lambda );
			else if ( regularizedModelIndex == 1 )
				model = new InterpolatedAffineModel3D( model, new TranslationModel3D(), (float)lambda );
			else if ( regularizedModelIndex == 2 )
				model = new InterpolatedAffineModel3D( model, new RigidModel3D(), (float)lambda );
			else if ( regularizedModelIndex == 3 )
				model = new InterpolatedAffineModel3D( model, new AffineModel3D(), (float)lambda );			 
		}

		return model;
	}

	public boolean queryRegularizedModel()
	{
		final GenericDialog gd = new GenericDialog( "Regularization Parameters" );

		gd.addChoice( "Model_to_regularize_with", regularizationModelChoice, regularizationModelChoice[ defaultRegularizationModelIndex ] );
		gd.addNumericField( "Lamba", defaultLambda, 2 );

		gd.showDialog();

		if ( gd.wasCanceled() )
		{
			this.regularize = false;
			return false;
		}

		this.regularizedModelIndex = defaultRegularizationModelIndex = gd.getNextChoiceIndex();
		this.lambda = defaultLambda = gd.getNextNumber();
		this.regularize = true;

		return true;
	}

	public String getDescription()
	{
		String d;
		
		if ( modelIndex == 0 )
			d = "TranslationModel3D";
		else if ( modelIndex == 1 )
			d = "RigidModel3D";
		else
			d = "AffineModel3D";
		
		
		if ( regularize )
		{
			if ( regularizedModelIndex == 0 )
				d += " regularized with an IdentityModel, lambda = " + lambda;
			else if ( regularizedModelIndex == 1 )
				d += " regularized with an TranslationModel3D, lambda = " + lambda;
			else if ( regularizedModelIndex == 2 )
				d += " regularized with an RigidModel3D, lambda = " + lambda;
			else if ( regularizedModelIndex == 3 )
				d += " regularized with an AffineModel3D, lambda = " + lambda;			 
		}

		return d;
	}
}
