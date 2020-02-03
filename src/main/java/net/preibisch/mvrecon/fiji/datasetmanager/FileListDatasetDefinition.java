package net.preibisch.mvrecon.fiji.datasetmanager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import javax.swing.JLabel;

import bdv.export.ExportMipmapInfo;
import bdv.export.ProgressWriter;
import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import ij.io.DirectoryChooser;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.Dimensions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.CheckResult;
import net.preibisch.mvrecon.fiji.datasetmanager.FileListDatasetDefinitionUtil.TileInfo;
import net.preibisch.mvrecon.fiji.datasetmanager.grid.RegularTranformHelpers;
import net.preibisch.mvrecon.fiji.datasetmanager.grid.RegularTranformHelpers.RegularTranslationParameters;
import net.preibisch.mvrecon.fiji.datasetmanager.grid.RegularTranformHelpersGUI;
import net.preibisch.mvrecon.fiji.datasetmanager.patterndetector.FilenamePatternDetector;
import net.preibisch.mvrecon.fiji.datasetmanager.patterndetector.NumericalFilenamePatternDetector;
import net.preibisch.mvrecon.fiji.plugin.resave.GenericResaveHDF5.Parameters;
import net.preibisch.mvrecon.fiji.plugin.resave.Generic_Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.resave.ProgressWriterIJ;
import net.preibisch.mvrecon.fiji.plugin.resave.Resave_HDF5;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.explorer.util.ColorStream;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.LegacyFileMapImgLoaderLOCI;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.filemap2.FileMapGettable;
import net.preibisch.mvrecon.process.interestpointregistration.TransformationTools;
import net.preibisch.mvrecon.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class FileListDatasetDefinition extends FileListDatasetDefinitionCore implements MultiViewDatasetDefinition {

	public static final String[] loadChoices = new String[] {"Re-save as multiresolution HDF5", "Load raw data virtually (with caching)", "Load raw data"};
	public static final String Z_VARIABLE_CHOICE = "Z-Planes (experimental)";
	
	private static ArrayList<FileListChooser> fileListChoosers = new ArrayList<>();
	static
	{
		fileListChoosers.add( new WildcardFileListChooser() );
		//fileListChoosers.add( new SimpleDirectoryFileListChooser() );
	}
	
	private static interface FileListChooser
	{
		public List<File> getFileList();
		public String getDescription();
		public FileListChooser getNewInstance();
	}
	
	private static class WildcardFileListChooser implements FileListChooser
	{

		private static long KB_FACTOR = 1024;
		private static int minNumLines = 10;
		private static String info = "<html> <h1> Select files via wildcard expression </h1> <br /> "
				+ "Use the path field to specify a file or directory to process or click 'Browse...' to select one. <br /> <br />"
				+ "Wildcard (*) expressions are allowed. <br />"
				+ "e.g. '/Users/spim/data/spim_TL*_Angle*.tif' <br /><br />"
				+ "</html>";
		
		
		private static String previewFiles(List<File> files){
			StringBuilder sb = new StringBuilder();
			sb.append("<html><h2> selected files </h2>");
			for (File f : files)
				sb.append( "<br />" + f.getAbsolutePath() );
			for (int i = 0; i < minNumLines - files.size(); i++)
				sb.append( "<br />"  );
			sb.append( "</html>" );
			return sb.toString();
		}
			
		
		@Override
		public List< File > getFileList()
		{

			GenericDialogPlus gdp = new GenericDialogPlus("Pick files to include");

			addMessageAsJLabel(info, gdp);

			gdp.addDirectoryOrFileField( "path", "/", 65);
			gdp.addNumericField( "exclude files smaller than (KB)", 10, 0 );

			// preview selected files - not possible in headless
			if (!PluginHelper.isHeadless())
				{
				// add empty preview
				addMessageAsJLabel(previewFiles( new ArrayList<>()), gdp,  GUIHelper.smallStatusFont);

				JLabel lab = (JLabel)gdp.getComponent( 5 );
				TextField num = (TextField)gdp.getComponent( 4 ); 
				Panel pan = (Panel)gdp.getComponent( 2 );

				num.addTextListener( new TextListener()
				{

					@Override
					public void textValueChanged(TextEvent e)
					{
						String path = ((TextField)pan.getComponent( 0 )).getText();

						System.out.println(path);
						if (path.endsWith( File.separator ))
							path = path.substring( 0, path.length() - File.separator.length() );

						if(new File(path).isDirectory())
							path = String.join( File.separator, path, "*" );

						lab.setText( previewFiles( getFilesFromPattern(path , Long.parseLong( num.getText() ) * KB_FACTOR)));
						lab.setSize( lab.getPreferredSize() );
						gdp.setSize( gdp.getPreferredSize() );
						gdp.validate();
					}
				} );

				((TextField)pan.getComponent( 0 )).addTextListener( new TextListener()
				{

					@Override
					public void textValueChanged(TextEvent e)
					{
						String path = ((TextField)pan.getComponent( 0 )).getText();
						if (path.endsWith( File.separator ))
							path = path.substring( 0, path.length() - File.separator.length() );

						if(new File(path).isDirectory())
							path = String.join( File.separator, path, "*" );

						lab.setText( previewFiles( getFilesFromPattern(path , Long.parseLong( num.getText() ) * KB_FACTOR)));
						lab.setSize( lab.getPreferredSize() );
						gdp.setSize( gdp.getPreferredSize() );
						gdp.validate();
					}
				} );
			}

			GUIHelper.addScrollBars( gdp );
			gdp.showDialog();

			if (gdp.wasCanceled())
				return new ArrayList<>();

			String fileInput = gdp.getNextString();

			if (fileInput.endsWith( File.separator ))
				fileInput = fileInput.substring( 0, fileInput.length() - File.separator.length() );

			if(new File(fileInput).isDirectory())
				fileInput = String.join( File.separator, fileInput, "*" );

			List<File> files = getFilesFromPattern( fileInput, (long) gdp.getNextNumber() * KB_FACTOR );

			files.forEach(f -> System.out.println( "Including file " + f + " in dataset." ));

			return files;
		}

		@Override
		public String getDescription(){return "Choose via wildcard expression";}

		@Override
		public FileListChooser getNewInstance() {return new WildcardFileListChooser();}
		
	}
	
	private static class SimpleDirectoryFileListChooser implements FileListChooser
	{

		@Override
		public List< File > getFileList()
		{
			List< File > res = new ArrayList<File>();
			
			DirectoryChooser dc = new DirectoryChooser ( "pick directory" );
			if (dc.getDirectory() != null)
				try
				{
					res = Files.list( Paths.get( dc.getDirectory() ))
						.filter(p -> {
							try
							{
								if ( Files.size( p ) > 10 * 1024 )
									return true;
								else
									return false;
							}
							catch ( IOException e )
							{
								// TODO Auto-generated catch block
								e.printStackTrace();
								return false;
							}
						}
						).map( p -> p.toFile() ).collect( Collectors.toList() );
					
				}
				catch ( IOException e )
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			return res;
			
			
		}
		
		

		@Override
		public String getDescription()
		{
			// TODO Auto-generated method stub
			return "select a directory manually";
		}

		@Override
		public FileListChooser getNewInstance()
		{
			// TODO Auto-generated method stub
			return new SimpleDirectoryFileListChooser();
		}
		
	}
	
	public static void addMessageAsJLabel(String msg, GenericDialog gd)
	{
		addMessageAsJLabel(msg, gd, null);
	}
	
	public static void addMessageAsJLabel(String msg, GenericDialog gd, Font font)
	{
		addMessageAsJLabel(msg, gd, font, null);
	}

	public static void addMessageAsJLabel(String msg, GenericDialog gd, Font font, Color color)
	{
		gd.addMessage( msg );
		if (!PluginHelper.isHeadless())
		{
			final Component msgC = gd.getComponent(gd.getComponentCount() - 1 );
			final JLabel msgLabel = new JLabel(msg);

			if (font!=null)
				msgLabel.setFont(font);
			if (color!=null)
				msgLabel.setForeground(color);

			gd.add(msgLabel);
			GridBagConstraints constraints = ((GridBagLayout)gd.getLayout()).getConstraints(msgC);
			((GridBagLayout)gd.getLayout()).setConstraints(msgLabel, constraints);

			gd.remove(msgC);
		}
	}
	

	@Override
	public SpimData2 createDataset( )
	{

		FileListChooser chooser = fileListChoosers.get( 0 );

		// only ask how we want to choose files if there are multiple ways
		if (fileListChoosers.size() > 1)
		{
			String[] fileListChooserChoices = new String[fileListChoosers.size()];
			for (int i = 0; i< fileListChoosers.size(); i++)
				fileListChooserChoices[i] = fileListChoosers.get( i ).getDescription();

			GenericDialog gd1 = new GenericDialog( "How to select files" );
			gd1.addChoice( "file chooser", fileListChooserChoices, fileListChooserChoices[0] );
			gd1.showDialog();

			if (gd1.wasCanceled())
				return null;

			chooser = fileListChoosers.get( gd1.getNextChoiceIndex() );
		}

		List<File> files = chooser.getFileList();

		FileListViewDetectionState state = new FileListViewDetectionState();
		FileListDatasetDefinitionUtil.detectViewsInFiles( files, state);

		Map<Class<? extends Entity>, List<Integer>> fileVariableToUse = new HashMap<>();
		List<String> choices = new ArrayList<>();

		FilenamePatternDetector patternDetector = new NumericalFilenamePatternDetector();
		patternDetector.detectPatterns( files );
		int numVariables = patternDetector.getNumVariables();

		StringBuilder inFileSummarySB = new StringBuilder();
		inFileSummarySB.append( "<html> <h2> Views detected in files </h2>" );

		// summary timepoints
		if (state.getMultiplicityMap().get( TimePoint.class ) == CheckResult.SINGLE)
		{
//			inFileSummarySB.append( "<p> No timepoints detected within files </p>" );
			choices.add( "TimePoints" );
		}
		else if (state.getMultiplicityMap().get( TimePoint.class ) == CheckResult.MULTIPLE_INDEXED)
		{
			int numTPs = (Integer) state.getAccumulateMap( TimePoint.class ).keySet().stream().reduce(0, (x,y) -> Math.max( (Integer) x, (Integer) y) );
			inFileSummarySB.append( "<p style=\"color:green\">" + numTPs+ " timepoints detected within files </p>" );
			if (state.getAccumulateMap( TimePoint.class ).size() > 1)
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: Number of timepoints is not the same for all views </p>" );
		}

		inFileSummarySB.append( "<br />" );

		// we might want to know how many channels/illums or tiles/angles to expect even though we have no metadata
		// NB: dont use these results if there IS metadata
		final Pair< Integer, Integer > minMaxNumCannelsIndexed = FileListViewDetectionState.getMinMaxNumChannelsIndexed( state );
		final Pair< Integer, Integer > minMaxNumSeriesIndexed = FileListViewDetectionState.getMinMaxNumSeriesIndexed( state );

		// summary channel
		if (state.getMultiplicityMap().get( Channel.class ) == CheckResult.SINGLE)
		{
			inFileSummarySB.append( !state.getAmbiguousIllumChannel() ? "" : "<p>"+ getRangeRepresentation( minMaxNumCannelsIndexed ) + " Channels OR Illuminations detected within files </p>");
			choices.add( "Channels" );
		}
		else if (state.getMultiplicityMap().get( Channel.class ) == CheckResult.MULTIPLE_INDEXED)
		{

			inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumCannelsIndexed ) + " Channels detected within files </p>" );
			inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no metadata was found for Channels </p>" );
			if (state.getMultiplicityMap().get( Illumination.class ) == CheckResult.MULTIPLE_INDEXED)
			{
				choices.add( "Channels" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no matadata for Illuminations found either, cannot distinguish </p>" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: choose manually whether files contain Channels or Illuminations below </p>" );
			}
		} else if (state.getMultiplicityMap().get( Channel.class ) == CheckResult.MUlTIPLE_NAMED)
		{
			int numChannels = state.getAccumulateMap( Channel.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numChannels + " Channels found within files </p>" );
		}

		//inFileSummarySB.append( "<br />" );

		// summary illum
		if ( state.getMultiplicityMap().get( Illumination.class ) == CheckResult.SINGLE )
		{
			//if (!state.getAmbiguousIllumChannel())
			//	inFileSummarySB.append( "<p> No illuminations detected within files </p>" );
			choices.add( "Illuminations" );
		}
		else if ( state.getMultiplicityMap().get( Illumination.class ) == CheckResult.MULTIPLE_INDEXED )
		{
			if (state.getMultiplicityMap().get( Channel.class ).equals( CheckResult.MULTIPLE_INDEXED ))
				choices.add( "Illuminations" );
			else
				inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumCannelsIndexed ) + " Illuminations detected within files </p>" );
		}
		else if ( state.getMultiplicityMap().get( Illumination.class ) == CheckResult.MUlTIPLE_NAMED )
		{
			int numIllum = state.getAccumulateMap( Illumination.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numIllum + " Illuminations found within files </p>" );
		}

		// summary tile
		if ( state.getMultiplicityMap().get( Tile.class ) == CheckResult.SINGLE )
		{
			//inFileSummarySB.append( "<p> No tiles detected within files </p>" );
			choices.add( "Tiles" );
		}
		else if ( state.getMultiplicityMap().get( Tile.class ) == CheckResult.MULTIPLE_INDEXED )
		{
			inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumSeriesIndexed ) + " Tiles detected within files </p>" );
			inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no metadata was found for Tiles </p>" );
			if (state.getMultiplicityMap().get( Angle.class ) == CheckResult.MULTIPLE_INDEXED)
			{
				choices.add( "Tiles" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: no metadata for Angles found either, cannot distinguish </p>" );
				inFileSummarySB.append( "<p style=\"color:orange\">WARNING: choose manually wether files contain Tiles or Angles below </p>" );
			}
		}
		else if ( state.getMultiplicityMap().get( Tile.class ) == CheckResult.MUlTIPLE_NAMED )
		{
			int numTile = state.getAccumulateMap( Tile.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numTile + " Tiles found within files </p>" );
			
		}
		
		//inFileSummarySB.append( "<br />" );
		
		// summary angle
		if ( state.getMultiplicityMap().get( Angle.class ) == CheckResult.SINGLE )
		{
			//inFileSummarySB.append( "<p> No angles detected within files </p>" );
			choices.add( "Angles" );
		}
		else if ( state.getMultiplicityMap().get( Angle.class ) == CheckResult.MULTIPLE_INDEXED )
		{
			if (state.getMultiplicityMap().get( Tile.class ) == CheckResult.MULTIPLE_INDEXED)
				choices.add( "Angles" );
			else
				inFileSummarySB.append( "<p > " + getRangeRepresentation( minMaxNumSeriesIndexed ) + " Angles detected within files </p>" );
		}
		else if ( state.getMultiplicityMap().get( Angle.class ) == CheckResult.MUlTIPLE_NAMED )
		{
			int numAngle = state.getAccumulateMap( Angle.class ).size();
			inFileSummarySB.append( "<p style=\"color:green\">" + numAngle + " Angles found within files </p>" );
		}

		inFileSummarySB.append( "</html>" );

		GenericDialogPlus gd = new GenericDialogPlus("Define Metadata for Views");
		
		//gd.addMessage( "<html> <h1> View assignment </h1> </html> ");
		//addMessageAsJLabel( "<html> <h1> View assignment </h1> </html> ", gd);
		
		//gd.addMessage( inFileSummarySB.toString() );
		addMessageAsJLabel(inFileSummarySB.toString(), gd);
		
		String[] choicesAngleTile = new String[] {"Angles", "Tiles"};
		String[] choicesChannelIllum = new String[] {"Channels", "Illuminations"};

		//if (state.getAmbiguousAngleTile())
		String preferedAnglesOrTiles = state.getMultiplicityMap().get( Angle.class ) == CheckResult.MULTIPLE_INDEXED ? "Angles" : "Tiles";
		if (state.getAmbiguousAngleTile() || state.getMultiplicityMap().get( Tile.class) == CheckResult.MUlTIPLE_NAMED)
			gd.addChoice( "BioFormats_Series_are?", choicesAngleTile, preferedAnglesOrTiles );
		if (state.getAmbiguousIllumChannel())
			gd.addChoice( "BioFormats_Channels_are?", choicesChannelIllum, choicesChannelIllum[0] );


		// We have grouped files -> detect patterns again, using only master files for group
		// that way, we automatically ignore patterns BioFormats has already grouped
		// e.g. MicroManager _MMSTack_Pos{?}.ome.tif -> positions are treated as series by BF
		// we have to keep the old pattern detector (for all files) -> it will be used for final view assignment
		FilenamePatternDetector patternDetectorOld = null;
		if (state.getGroupedFormat() )
		{
			patternDetectorOld = patternDetector;
			patternDetector = new NumericalFilenamePatternDetector();
			// detect in all unique master files in groupUsageMap := actual file -> (master file, series)
			patternDetector.detectPatterns( state.getGroupUsageMap().values().stream().map( p -> p.getA() ).collect( Collectors.toSet() ).stream().collect( Collectors.toList() ) );
			numVariables = patternDetector.getNumVariables();
		}

		if (numVariables >= 1)
//		sbfilePatterns.append( "<p> No numerical patterns found in filenames</p>" );
//		else
		{
			final Pair< String, String > prefixAndPattern = splitIntoPrefixAndPattern( patternDetector );
			final StringBuilder sbfilePatterns = new StringBuilder();
			sbfilePatterns.append(  "<html> <h2> Patterns in filenames </h2> " );
			sbfilePatterns.append( "<h3 style=\"color:green\"> " + numVariables + ""
					+ " numerical pattern" + ((numVariables > 1) ? "s": "") + " found in filenames</h3>" );
			sbfilePatterns.append( "</br><p> Patterns: " + getColoredHtmlFromPattern( prefixAndPattern.getB(), false ) + "</p>" );
			sbfilePatterns.append( "</html>" );
			addMessageAsJLabel(sbfilePatterns.toString(), gd);
		}

		//gd.addMessage( sbfilePatterns.toString() );

		choices.add( "-- ignore this pattern --" );
		choices.add( Z_VARIABLE_CHOICE );
		String[] choicesAll = choices.toArray( new String[]{} );

		for (int i = 0; i < numVariables; i++)
		{
			gd.addChoice( "Pattern_" + i + " represents", choicesAll, choicesAll[0] );
			//do not fail just due to coloring
			try
			{
				((Label) gd.getComponent( gd.getComponentCount() - 2 )).setForeground( getColorN( i ) );
			}
			catch (Exception e) {}
		}

		addMessageAsJLabel(  "<html> <h2> Voxel Size calibration </h2> </html> ", gd );
		final boolean allVoxelSizesTheSame = FileListViewDetectionState.allVoxelSizesTheSame( state );
		if(!allVoxelSizesTheSame)
			addMessageAsJLabel(  "<html> <p style=\"color:orange\">WARNING: Voxel Sizes are not the same for all views, modify them at your own risk! </p> </html> ", gd );

		final VoxelDimensions someCalib = state.getDimensionMap().values().iterator().next().getB();

		gd.addCheckbox( "Modify_voxel_size?", false );
		gd.addNumericField( "Voxel_size_X", someCalib.dimension( 0 ), 4 );
		gd.addNumericField( "Voxel_size_Y", someCalib.dimension( 1 ), 4 );
		gd.addNumericField( "Voxel_size_Z", someCalib.dimension( 2 ), 4 );
		gd.addStringField( "Voxel_size_unit", someCalib.unit() );

		// try to guess if we need to move to grid
		// we suggest move if: we have no tile metadata
		addMessageAsJLabel(  "<html> <h2> Move to Grid </h2> </html> ", gd );
		boolean haveTileLoc = state.getAccumulateMap( Tile.class ).keySet().stream().filter( t -> ((TileInfo)t).locationX != null && ((TileInfo)t).locationX != 0.0 ).findAny().isPresent();
		
		String[] choicesGridMove = new String[] {"Do not move Tiles to Grid (use Metadata if available)",
				"Move Tiles to Grid (interactive)", "Move Tile to Grid (Macro-scriptable)"};
		gd.addChoice( "Move_Tiles_to_Grid_(per_Angle)?", choicesGridMove, choicesGridMove[!haveTileLoc ? 1 : 0] );

		gd.showDialog();

		if (gd.wasCanceled())
			return null;

		boolean preferAnglesOverTiles = true;
		boolean preferChannelsOverIlluminations = true;
		if (state.getAmbiguousAngleTile() || state.getMultiplicityMap().get( Tile.class) ==  CheckResult.MUlTIPLE_NAMED)
			preferAnglesOverTiles = gd.getNextChoiceIndex() == 0;
		if (state.getAmbiguousIllumChannel())
			preferChannelsOverIlluminations = gd.getNextChoiceIndex() == 0;

		fileVariableToUse.put( TimePoint.class, new ArrayList<>() );
		fileVariableToUse.put( Channel.class, new ArrayList<>() );
		fileVariableToUse.put( Illumination.class, new ArrayList<>() );
		fileVariableToUse.put( Tile.class, new ArrayList<>() );
		fileVariableToUse.put( Angle.class, new ArrayList<>() );

		final List<Integer> zVariables = new ArrayList<>();
		for (int i = 0; i < numVariables; i++)
		{
			String choice = gd.getNextChoice();
			if (choice.equals( "TimePoints" ))
				fileVariableToUse.get( TimePoint.class ).add( i );
			else if (choice.equals( "Channels" ))
				fileVariableToUse.get( Channel.class ).add( i );
			else if (choice.equals( "Illuminations" ))
				fileVariableToUse.get( Illumination.class ).add( i );
			else if (choice.equals( "Tiles" ))
				fileVariableToUse.get( Tile.class ).add( i );
			else if (choice.equals( "Angles" ))
				fileVariableToUse.get( Angle.class ).add( i );
			else if (choice.equals( Z_VARIABLE_CHOICE ))
				zVariables.add( i );
		}

		// TODO handle Angle-Tile swap here	
		FileListDatasetDefinitionUtil.resolveAmbiguity( state.getMultiplicityMap(), state.getAmbiguousIllumChannel(), preferChannelsOverIlluminations, state.getAmbiguousAngleTile(), !preferAnglesOverTiles );

		// if we have used a grouped pattern
		// we will still have to use the old pattern detector (containing all files) in the next step
		// update fileVariableToUse so all grouped patterns are ignored
		if (patternDetectorOld != null)
		{
			// ungrouped variables have more than one match in master files
			final boolean[] ungroupedVariable = new boolean[patternDetectorOld.getNumVariables()];
			final String[] variableInstances = new String[patternDetectorOld.getNumVariables()];
			for (final File masterFile : state.getGroupUsageMap().values().stream().map( p -> p.getA() ).collect( Collectors.toSet() ).stream().collect( Collectors.toList() ))
			{
				final Matcher m = patternDetectorOld.getPatternAsRegex().matcher( masterFile.getAbsolutePath() );
				m.matches();
				for (int i = 0; i<patternDetectorOld.getNumVariables(); i++)
				{
					final String variableInstance = m.group( i + 1 );
					if (variableInstances[i] == null)
						variableInstances[i] = variableInstance;
					// we found an instance != first
					if (!variableInstances[i].equals( variableInstance ) )
						ungroupedVariable[i] = true;
				}
			}

			// update fileVariablesToUse
			// idx of pattern in grouped files -> idx in all files
			for (final AtomicInteger oldIdx = new AtomicInteger(); oldIdx.get()<patternDetectorOld.getNumVariables(); oldIdx.incrementAndGet())
			{
				if (!ungroupedVariable[oldIdx.get()])
					fileVariableToUse.forEach( (k, v) -> {
							fileVariableToUse.put( k, v.stream().map( (idx) -> ((idx >= oldIdx.get()) ? idx + 1 : idx )).collect( Collectors.toList() ) );
					});
			}
		}

		FileListDatasetDefinitionUtil.expandAccumulatedViewInfos(
				fileVariableToUse, 
				patternDetectorOld == null ? patternDetector : patternDetectorOld,
				state);

		// here, we concatenate Z-grouped files
		if (zVariables.size() > 0)
			FileListDatasetDefinitionUtil.groupZPlanes( state, patternDetector, zVariables );

		// query modified calibration
		final boolean modifyCalibration = gd.getNextBoolean();
		if (modifyCalibration)
		{
			final double calX = gd.getNextNumber();
			final double calY = gd.getNextNumber();
			final double calZ = gd.getNextNumber();
			final String calUnit = gd.getNextString();

			for (final Pair< File, Pair< Integer, Integer > > key : state.getDimensionMap().keySet())
			{
				final Pair< Dimensions, VoxelDimensions > pairOld = state.getDimensionMap().get( key );
				final Pair< Dimensions, VoxelDimensions > pairNew = new ValuePair< Dimensions, VoxelDimensions >( pairOld.getA(), new FinalVoxelDimensions( calUnit, calX, calY, calZ ) );
				state.getDimensionMap().put( key, pairNew );
			}
		}

		final int gridMoveType = gd.getNextChoiceIndex();

		// we create a virtual SpimData at first
		SpimData2 data = buildSpimData( state, true );

		// we move to grid, collect parameters first
		final List<RegularTranslationParameters> gridParams = new ArrayList<>();
		if (gridMoveType == 2)
		{
			final ArrayList<ViewDescription> vds = new ArrayList<>(data.getSequenceDescription().getViewDescriptions().values());

			final Set<Class<? extends Entity>> angleClassSet = new HashSet<>();
			angleClassSet.add( Angle.class );
			final Set<Class<? extends Entity>> tileClassSet = new HashSet<>();
			tileClassSet.add( Tile.class );

			// first, split by angles (we process each angle separately)
			final List< Group< ViewDescription > > vdsAngleGrouped = Group.splitBy( vds , angleClassSet );
			for (Group<ViewDescription> vdsAngle : vdsAngleGrouped)
			{
				// second, we split by tiles (all channels/illums/tps of a tile are grouped)
				final List< Group< ViewDescription > > tilesGrouped = Group.splitBy( new ArrayList<>( vdsAngle.getViews() ), tileClassSet );
				final String angleName = vdsAngle.getViews().iterator().next().getViewSetup().getAngle().getName();
				if (tilesGrouped.size() < 2)
					continue;

				final RegularTranslationParameters params = RegularTranformHelpersGUI.queryParameters( "Move Tiles of Angle " + angleName, tilesGrouped.size() );
				
				if ( params == null )
					return null;

				gridParams.add( params );
			}
		}

		GenericDialogPlus gdSave = new GenericDialogPlus( "Save dataset definition" );

		addMessageAsJLabel("<html> <h1> Loading options </h1> <br /> </html>", gdSave);
		gdSave.addChoice( "how_to_load_images", loadChoices, loadChoices[0] );

		addMessageAsJLabel("<html><h2> Save path </h2></html>", gdSave);

		// get default save path := deepest parent directory of all files in dataset
		final Set<String> filenames = new HashSet<>();
		((FileMapGettable)data.getSequenceDescription().getImgLoader() ).getFileMap().values().stream().forEach(
				p -> 
				{
					filenames.add( p.getA().getAbsolutePath());
				});
		final File prefixPath;
		if (filenames.size() > 1)
			prefixPath = getLongestPathPrefix( filenames );
		else
		{
			String fi = filenames.iterator().next();
			prefixPath = new File((String)fi.subSequence( 0, fi.lastIndexOf( File.separator )));
		}

		gdSave.addDirectoryField( "dataset_save_path", prefixPath.getAbsolutePath(), 55 );

		// check if all stack sizes are the same (in each file)
		boolean zSizeEqualInEveryFile = LegacyFileMapImgLoaderLOCI.isZSizeEqualInEveryFile( data, (FileMapGettable)data.getSequenceDescription().getImgLoader() );
		// only consider if there are actually multiple angles/tiles
		zSizeEqualInEveryFile = zSizeEqualInEveryFile && !(data.getSequenceDescription().getAllAnglesOrdered().size() == 1 && data.getSequenceDescription().getAllTilesOrdered().size() == 1);
		// notify user if all stacks are equally size (in every file)
		if (zSizeEqualInEveryFile)
		{
			addMessageAsJLabel( "<html><p style=\"color:orange\">WARNING: all stacks have the same size, this might be caused by a bug"
					+ " in BioFormats. </br> Please re-check stack sizes if necessary.</p></html>", gdSave );
			// default choice for size re-check: do it if all stacks are the same size
			gdSave.addCheckbox( "check_stack_sizes", zSizeEqualInEveryFile );
		}


		boolean multipleAngles = data.getSequenceDescription().getAllAnglesOrdered().size() > 1;
		if (multipleAngles)
			gdSave.addCheckbox( "apply_angle_rotation", true );

		gdSave.showDialog();

		if ( gdSave.wasCanceled() )
			return null;

		final int loadChoice = gdSave.getNextChoiceIndex();
		final boolean useVirtualLoader = loadChoice == 1;
		// re-build the SpimData if user explicitly doesn't want virtual loading
		if (!useVirtualLoader)
			data = buildSpimData( state, useVirtualLoader );

		File chosenPath = new File( gdSave.getNextString());
		data.setBasePath( chosenPath );

		// check and correct stack sizes (the "BioFormats bug")
		// TODO: remove once the bug is fixed upstream
		if (zSizeEqualInEveryFile)
		{
			final boolean checkSize = gdSave.getNextBoolean();
			if (checkSize)
			{
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Checking file sizes ... " );
				LegacyFileMapImgLoaderLOCI.checkAndRemoveZeroVolume( data, (ImgLoader & FileMapGettable) data.getSequenceDescription().getImgLoader(), zVariables.size() > 0 );
				IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Finished." );
			}
		}

		// now, we have a working SpimData and have corrected for unequal z sizes -> do grid move if necessary
		if (gridMoveType == 2)
		{
			final ArrayList<ViewDescription> vds = new ArrayList<>(data.getSequenceDescription().getViewDescriptions().values());

			final Set<Class<? extends Entity>> angleClassSet = new HashSet<>();
			angleClassSet.add( Angle.class );
			final Set<Class<? extends Entity>> tileClassSet = new HashSet<>();
			tileClassSet.add( Tile.class );

			// first, split by angles (we process each angle separately)
			final List< Group< ViewDescription > > vdsAngleGrouped = Group.splitBy( vds , angleClassSet );
			int i = 0;
			for (Group<ViewDescription> vdsAngle : vdsAngleGrouped)
			{
				// second, we split by tiles (all channels/illums/tps of a tile are grouped)
				final List< Group< ViewDescription > > tilesGrouped = Group.splitBy( new ArrayList<>( vdsAngle.getViews() ), tileClassSet );
				if (tilesGrouped.size() < 2)
					continue;

				// sort by tile id of first view in groups
				Collections.sort( tilesGrouped, new Comparator< Group< ViewDescription > >()
				{
					@Override
					public int compare(Group< ViewDescription > o1, Group< ViewDescription > o2)
					{
						if (o1.size() == 0)
							return -o2.size();
						return o1.getViews().iterator().next().getViewSetup().getTile().getId() - o2.getViews().iterator().next().getViewSetup().getTile().getId();
					}
				} );

				RegularTranslationParameters gridParamsI = gridParams.get( i++ );
				RegularTranformHelpers.applyToSpimData( data, tilesGrouped, gridParamsI, true );
			}
		}

		boolean applyAxis = false;
		if (multipleAngles)
			applyAxis = gdSave.getNextBoolean();

		// View Registrations should now be complete
		// with translated tiles, we also have to take the center of rotation into account
		if (applyAxis)
			TransformationTools.applyAxisGrouped( data );

		boolean resaveAsHDF5 = loadChoice == 0;
		if (resaveAsHDF5)
		{
			final Map< Integer, ExportMipmapInfo > perSetupExportMipmapInfo = Resave_HDF5.proposeMipmaps( data.getSequenceDescription().getViewSetupsOrdered() );
			final int firstviewSetupId = data.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getId();
			Generic_Resave_HDF5.lastExportPath = String.join( File.separator, chosenPath.getAbsolutePath(), "dataset");
			final Parameters params = Generic_Resave_HDF5.getParameters( perSetupExportMipmapInfo.get( firstviewSetupId ), true, true );

			// HDF5 options dialog was cancelled
			if (params == null)
				return null;

			final ProgressWriter progressWriter = new ProgressWriterIJ();
			progressWriter.out().println( "starting export..." );
			
			Generic_Resave_HDF5.writeHDF5( data, params, progressWriter );
			
			System.out.println( "HDF5 resave finished." );
			
			net.preibisch.mvrecon.fiji.ImgLib2Temp.Pair< SpimData2, List< String > > result = Resave_HDF5.createXMLObject( data, new ArrayList<>(data.getSequenceDescription().getViewDescriptions().keySet()), params, progressWriter, true );

			// ensure progressbar is gone
			progressWriter.setProgress( 1.0 );

			data = result.getA();
		}
		
		if (gridMoveType == 1)
		{
			data.gridMoveRequested = true;
		}
		
		return data;
		
	}
	
	public static String getColoredHtmlFromPattern(String pattern, boolean withRootTag)
	{
		final StringBuilder sb = new StringBuilder();
		if (withRootTag)
			sb.append( "<html>" );
		int n = 0;
		for (int i = 0; i<pattern.length(); i++)
		{
			if (pattern.charAt( i ) == '{')
			{
				Color col = getColorN( n++ );
				sb.append( "<span style=\"color: rgb("+ col.getRed() + "," + col.getGreen() + "," + col.getBlue()   +")\">{" );
			}
			else if (pattern.charAt( i ) == '}')
				sb.append( "}</span>");
			else
				sb.append( pattern.charAt( i ) );
		}
		if (withRootTag)
			sb.append( "</html>" );
		return sb.toString();
	}
	
	public static Color getColorN(long n)
	{
		Iterator< ARGBType > iterator = ColorStream.iterator();
		ARGBType c = new ARGBType();
		for (int i = 0; i<n+43; i++)
			for (int j = 0; j<3; j++)
				c = iterator.next();
		return new Color( ARGBType.red( c.get() ), ARGBType.green( c.get() ), ARGBType.blue( c.get() ) );
	}

	@Override
	public String getTitle() { return "Automatic Loader (Bioformats based)"; }
	
	@Override
	public String getExtendedDescription()
	{
		return "This datset definition tries to automatically detect views in a\n" +
				"list of files openable by BioFormats. \n" +
				"If there are multiple Images in one file, it will try to guess which\n" +
				"views they belong to from meta data or ask the user for advice.\n";
	}


	@Override
	public MultiViewDatasetDefinition newInstance()
	{
		return new FileListDatasetDefinition();
	}
	
	public static void main(String[] args)
	{
		//new FileListDatasetDefinition().createDataset();
		//new WildcardFileListChooser().getFileList().forEach( f -> System.out.println( f.getAbsolutePath() ) );
		GenericDialog gd = new GenericDialog( "A" );
		gd.addMessage( getColoredHtmlFromPattern( "a{b}c{d}e{aaaaaaaaaa}aa{bbbbbbbbbbbb}ccccc{ddddddd}", true ) );
		System.out.println( getColoredHtmlFromPattern( "a{b}c{d}e", false ) );
		gd.showDialog();
	}
}
