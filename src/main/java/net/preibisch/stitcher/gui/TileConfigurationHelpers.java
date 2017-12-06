package net.preibisch.stitcher.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.realtransform.Translation;
import net.imglib2.realtransform.TranslationGet;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class TileConfigurationHelpers
{

	public static Map< Pair< File, Integer >, TranslationGet > parseTileConfigurationOld(final File tcFile)
	{
		final Map< Pair< File, Integer >, TranslationGet > res = new HashMap<>();

		final File parentDir = tcFile.getParentFile();

		try
		{
			final BufferedReader reader = new BufferedReader( new FileReader( tcFile ) );
			int dims = -1;
			boolean multiseries = false;
			boolean absolutePath = false;

			while ( reader.ready() )
			{
				String nextLine = reader.readLine().trim();
				nextLine = nextLine.split( "#" )[0].trim();

				if ( nextLine.length() < 3 )
					continue;

				// read dim=n header
				if ( nextLine.startsWith( "dim" ) )
				{
					List< String > splitLine = Arrays.asList( nextLine.split( "=" ) );
					if ( splitLine.size() != 2 )
					{
						reader.close();
						return null; // warn here?
					}
					dims = Integer.parseInt( splitLine.get( 1 ).trim() );
				}
				// read multiseries=true header
				else if ( nextLine.startsWith( "multiseries" ) )
				{
					String entries[] = nextLine.split( "=" );
					if ( entries.length != 2 )
					{
						reader.close();
						return null;
					}

					if ( entries[1].trim().equals( "true" ) )
					{
						multiseries = true;
					}
				}
				// read absolutepath=true header
				else if ( nextLine.startsWith( "absolutepath" ) )
				{
					String entries[] = nextLine.split( "=" );
					if ( entries.length != 2 )
					{
						reader.close();
						return null;
					}

					if ( entries[1].trim().equals( "true" ) )
					{
						absolutePath = true;
					}
				}
				else
				{
					List< String > splitLine = Arrays.asList( nextLine.split( ";" ) );
					if ( splitLine.size() != 3 )
					{
						reader.close();
						return null; // warn here?
					}

					String imageName = splitLine.get( 0 ).trim();
					if ( imageName.length() == 0 )
					{
						reader.close();
						return null;
					}

					int seriesNr = -1;
					if ( multiseries )
					{
						String imageSeries = splitLine.get( 1 ).trim(); // sub-volume (series nr)
						if ( imageSeries.length() == 0 )
						{
							{
								reader.close();
								return null;
							}
						}
						else
						{
							try
							{
								seriesNr = Integer.parseInt( imageSeries );
							}
							catch ( NumberFormatException e )
							{
								reader.close();
								return null;
							}
						}
					}

					String loc = splitLine.get( 2 ).trim();
					if ( !loc.startsWith( "(" ) || !loc.endsWith( ")" ) )
					{
						reader.close();
						return null; // warn here?
					}
					loc = loc.substring( 1, loc.length() - 1 );

					List< String > locs = Arrays.asList( loc.split( "," ) );

					if ( locs.size() != dims )
					{
						reader.close();
						return null; // warn here?
					}

					double[] tr = new double[dims];
					for ( int i = 0; i < dims; i++ )
					{
						tr[i] = Double.parseDouble( locs.get( i ).trim() );
					}

					File imageFile = absolutePath ? new File(imageName) : new File(parentDir.getAbsolutePath(), imageName);
					res.put( new ValuePair<>( imageFile, seriesNr ), new Translation( tr ) );
				}

			}
			reader.close();
		}
		catch ( IOException | NumberFormatException e )
		{
			e.printStackTrace();
			return null;
		}

		return res;
	}

	public static Map< ViewId, TranslationGet > parseTileConfiguration(final File tcFile)
	{

		final Map< ViewId, TranslationGet > res = new HashMap<>();

		try
		{
			final BufferedReader reader = new BufferedReader( new FileReader( tcFile ) );
			int dims = -1;

			while ( reader.ready() )
			{
				String nextLine = reader.readLine().trim();
				nextLine = nextLine.split( "#" )[0].trim();

				if ( nextLine.length() < 3 )
					continue;

				// reader dim=n header
				if ( nextLine.startsWith( "dim" ) )
				{
					List< String > splitLine = Arrays.asList( nextLine.split( "=" ) );
					if ( splitLine.size() != 2 )
					{
						reader.close();
						return null; // warn here?
					}
					dims = Integer.parseInt( splitLine.get( 1 ).trim() );
				}
				else
				{
					List< String > splitLine = Arrays.asList( nextLine.split( ";" ) );
					if ( splitLine.size() != 3 )
					{
						reader.close();
						return null; // warn here?
					}
					int vsId = Integer.parseInt( splitLine.get( 0 ).trim() );
					int tpId = Integer.parseInt( splitLine.get( 1 ).trim() );

					String loc = splitLine.get( 2 ).trim();
					if ( !loc.startsWith( "(" ) || !loc.endsWith( ")" ) )
					{
						reader.close();
						return null; // warn here?
					}
					loc = loc.substring( 1, loc.length() - 1 );

					List< String > locs = Arrays.asList( loc.split( "," ) );

					if ( locs.size() != dims )
					{
						reader.close();
						return null; // warn here?
					}

					double[] tr = new double[dims];
					for ( int i = 0; i < dims; i++ )
					{
						tr[i] = Double.parseDouble( locs.get( i ).trim() );
					}

					res.put( new ViewId( tpId, vsId ), new Translation( tr ) );
				}

			}
			reader.close();
		}
		catch ( IOException | NumberFormatException e )
		{
			e.printStackTrace();
			return null;
		}

		return res;
	}

	public static void main(String[] args) throws Exception
	{
		System.out.println( "== Old style:" );
		Map< Pair< File, Integer >, TranslationGet > res = parseTileConfigurationOld(
				new File( "/Users/david/Desktop/tileConfigOld.txt" ) );

		if ( res != null )
			res.entrySet().forEach( e -> {
				System.out.println( e.getKey().getA() + ", series " + e.getKey().getB() + ": "
						+ Util.printCoordinates( e.getValue().getTranslationCopy() ) );
			} );

		System.out.println( "== New style:" );
		Map< ViewId, TranslationGet > res2 = parseTileConfiguration(
				new File( "/Users/david/Desktop/tileConfig.txt" ) );

		if ( res2 != null )
			res2.entrySet().forEach( e -> {
				System.out.println( "View: " + e.getKey().getViewSetupId() + ", TP: " + e.getKey().getTimePointId()
						+ ": " + Util.printCoordinates( e.getValue().getTranslationCopy() ) );
			} );
	}
}
