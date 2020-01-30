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
package net.preibisch.mvrecon.fiji.datasetmanager.grid;

import java.awt.Choice;

import javax.swing.ImageIcon;
import javax.swing.JLabel;

import fiji.util.gui.GenericDialogPlus;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;

public class RegularTranformHelpersGUI extends RegularTranformHelpers{
	
	public static int lastPresetIndex = 0;
	
	final public static String[] presetNames = 
			new String[]{ 	"Right & Down             ", "Left & Down", "Right & Up", "Left & Up" ,
							"Down & Right             ", "Down & Left", "Up & Right", "Up & Left" ,
							"Snake: Right & Down      ", "Snake: Left & Down", "Snake: Right & Up", "Snake: Left & Up",
							"Snake: Down & Right      ", "Snake: Down & Left", "Snake: Up & Right", "Snake: Up & Left" };

	final public static ImageIcon[] images = new ImageIcon[ presetNames.length ];
	static{
		images[ 0 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/row1.png" ) );
		images[ 1 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/row2.png" ) );
		images[ 2 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/row3.png" ) );
		images[ 3 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/row4.png" ) );
	
		images[ 4 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/column1.png" ) );
		images[ 5 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/column2.png" ) );
		images[ 6 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/column3.png" ) );
		images[ 7 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/column4.png" ) );
	
		images[ 8 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake1.png" ) );
		images[ 9 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake3.png" ) );
		images[ 10 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake5.png" ) );
		images[ 11 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake7.png" ) );
	
		images[ 12 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake2.png" ) );
		images[ 13 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake4.png" ) );
		images[ 14 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake6.png" ) );
		images[ 15 ] = GenericDialogPlus.createImageIcon( RegularTranformHelpers.class.getResource( "/images/snake8.png" ) );
	}
	
	public static RegularTranslationParameters queryParameters(String dialogTitle, int nTiles)
	{
		final GenericDialogPlus gd = new GenericDialogPlus( dialogTitle );
		gd.addChoice( "Grid_type", presetNames, presetNames[lastPresetIndex] );

		if (!PluginHelper.isHeadless())
		{
			final ImageIcon display = new ImageIcon( images[ lastPresetIndex ].getImage() );//.getScaledInstance( 200, 200, Image.SCALE_SMOOTH ) );
			final JLabel label = gd.addImage( display );

			( (Choice) gd.getChoices().get( 0 )).addItemListener( e -> {
				int selected = ( (Choice) gd.getChoices().get( 0 )).getSelectedIndex();
				display.setImage( images[ selected ].getImage() ); //.getScaledInstance( 200, 200, Image.SCALE_SMOOTH ) );
				label.update( label.getGraphics() );
			});
		}

		gd.addNumericField( "Tiles_X", suggestTiles( nTiles ).getA(), 0 );
		gd.addNumericField( "Tiles_Y", suggestTiles( nTiles ).getB(), 0 );
		gd.addNumericField( "Tiles_Z", 1, 0 );

		gd.addNumericField( "Overlap_X_(%)", 10, 0 );
		gd.addNumericField( "Overlap_Y_(%)", 10, 0 );
		gd.addNumericField( "Overlap_Z_(%)", 10, 0 );

		gd.addCheckbox( "Keep_Metadata_Rotation", true );

		gd.showDialog();
		if( gd.wasCanceled())
			return null;

		final RegularTranslationParameters res = new RegularTranslationParameters();
		res.nDimensions = 3;

		// get grid preset
		lastPresetIndex = gd.getNextChoiceIndex();
		GridPreset preset = presets.get( lastPresetIndex );
		res.alternating = preset.alternating.clone();
		res.increasing = preset.increasing.clone();
		res.dimensionOrder = getDimensionOrder( preset.dimensionOrder );

		// get nTiles
		int tilesX = (int) gd.getNextNumber();
		int tilesY = (int) gd.getNextNumber();
		int tilesZ = (int) gd.getNextNumber();
		res.nSteps = new int[] {tilesX, tilesY, tilesZ};

		// get overlap
		double overlapX = gd.getNextNumber() / 100.0;
		double overlapY = gd.getNextNumber() / 100.0;
		double overlapZ = gd.getNextNumber() / 100.0;
		res.overlaps = new double[] {overlapX, overlapY, overlapZ};

		// get keepRotation
		boolean rotate = gd.getNextBoolean();
		res.keepRotation = rotate;

		return res;
	}
	
	public static void main(String[] args)
	{
		/*
		RegularTranslationParameters params = new RegularTranslationParameters();
		params.nDimensions = 3;
		params.alternating = new boolean[] {true, true, true};
		params.dimensionOrder = new int[] {0, 1, 2};
		params.increasing = new boolean[] {true, true, true};
		params.overlaps = new double[] {0.0, 0.0, 0.2};
		params.nSteps = new int[] {2, 2, 1};
		params.keepRotation = true;
		
		Dimensions dims = new FinalInterval( new long[] {100, 100, 100} );
		
		List< Translation3D > res = generateRegularGrid( params, dims );
		res.forEach( t -> {System.out.println( Util.printCoordinates( t.getTranslationCopy() ) );} );
		*/
		queryParameters( "Tiling Parameters", 8 );
		
	}
}
