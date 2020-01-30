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
package net.preibisch.mvrecon.fiji.plugin.queryXML;

import java.awt.Button;
import java.awt.Color;
import java.awt.Label;
import java.awt.TextField;
import java.awt.event.ActionListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.Toggle_Cluster_Options;
import net.preibisch.mvrecon.fiji.plugin.resave.PluginHelper;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.EmptyEntity;
import net.preibisch.mvrecon.fiji.spimdata.NamePattern;

import fiji.util.gui.GenericDialogPlus;
import ij.gui.GenericDialog;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.Version;
import mpicbg.spim.data.XmlKeys;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.base.NamedEntity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;

/**
 * Interface for interactive parsing of spimdata XMLs
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 */
public class GenericLoadParseQueryXML<
		AS extends AbstractSpimData< S >,
		S extends AbstractSequenceDescription< V, D, L >,
		V extends BasicViewSetup,
		D extends BasicViewDescription< V >,
		L extends BasicImgLoader,
		X extends XmlIoAbstractSpimData< S, AS > >
{
	public static String defaultXMLfilename = "";
	public static boolean debugRandomClusterHash = false;

	protected static String goodMsg1 = "The selected XML file was parsed successfully";
	protected static String warningMsg1 = "The selected file does not appear to be an xml. Press OK to try to parse anyways.";
	protected static String errorMsg1 = "An ERROR occured parsing this XML file! Please select a different XML (see log)";
	protected static String neutralMsg1a = "Please select an existing XML - or - Define a new dataset by clicking just below.";
	protected static String neutralMsg1b = "Please select an existing XML.";
	protected static String noMsg2 = " ";
	protected static String[] attributeChoiceList = new String[]{ "All *s", "Single * (Select from List)", "Multiple *s (Select from List)", "Range of *s (Specify by Name)" };

	public static final String[] clusterOptions1 = new String[]{
		"Do not process on cluster",
		"Save every XML with unique id generated from processed subset",
		"Save every XML with user-provided unique id" };

	public static int defaultClusterOption1 = 1;

	// remember as default
	public static HashMap< String, Integer > defaultAttributeChoice = new HashMap< String, Integer >();
	public static HashMap< String, Integer > defaultAttributeEntityIndex = new HashMap< String, Integer >();
	public static HashMap< String, String > defaultAttributePatternString = new HashMap< String, String >();
	public static HashMap< String, boolean[] > defaultAttributeSelectedEntities = new HashMap< String, boolean[] >();

	// the current instance
	protected HashMap< String, Integer > attributeChoice = new HashMap< String, Integer >();

	// how to load the XML
	protected X io;

	// how to sort the attributes (if not by name)
	protected Comparator< String > comparator = null;

	// local variables for display
	protected String message1, message2;
	protected Color color;
	
	// result variables
	protected AS data;
	protected String xmlfilename;
	protected ArrayList< TimePoint > timepointsToProcess;
	
	// which attributes are there
	protected ArrayList< String > attributes;
	
	// all instances of all entities per attribute
	protected HashMap< String, List< Entity > > allAttributeInstances;

	// all instances of all entities per attribute
	protected HashMap< String, List< Entity > > attributeInstancesToProcess;

	// extension for the XML when saving
	protected String clusterExt = null;

	// add a button on demand
	protected ArrayList< String > buttonText = null;
	protected ArrayList< ActionListener > listener = null;
	protected GenericDialog gd = null;

	public Button defineNewDataset = null;

	/*
	 * Constructor for the class needs an appropriate IO module
	 * @param io
	 */
	public GenericLoadParseQueryXML( final X io )
	{
		this.io = io;
		IOFunctions.println( "Using spimdata version: " + Version.getVersion() );
		IOFunctions.println( "Using spimreconstruction version: " + net.preibisch.mvrecon.Version.getVersion() );
	}

	/**
	 * @return the i/o object used to parse the XML
	 */
	public X getIO() { return io; }
	
	/**
	 * @return the SpimData object parsed from the xml
	 */
	public AS getData() { return data; }
	
	/**
	 * @return The location of the xml file
	 */
	public String getXMLFileName() { return xmlfilename; }
	
	/**
	 * @return All timepoints that should be processed
	 */
	public List< TimePoint > getTimePointsToProcess() { return timepointsToProcess; }		

	/**
	 * @return All ViewSetups that should be processed per timepoint (based on the selected attributes)
	 */
	public ArrayList< V > getViewSetupsToProcess()
	{
		final ArrayList< V > viewSetups = new ArrayList< V >();

		for ( final V v : data.getSequenceDescription().getViewSetupsOrdered() )
		{
			boolean contained = true;
			
			// check over all attributes if they are part of the attributeInstancesToProcess
			for ( final String attribute : attributes )
			{
				final Entity attributeEntityForViewSetup = v.getAttributes().get( attribute );
				
				if ( !attributeInstancesToProcess.get( attribute ).contains( attributeEntityForViewSetup ) )
					contained = false;
			}
			
			if ( contained )
				viewSetups.add( v );
		}

		return viewSetups;
	}

	/*
	 * Sets the comparator used to sort the Attributes in a specific order for display, can be null
	 * Note that Timepoint (XmlKeys.TIMEPOINTS_TIMEPOINT_TAG) is always the last entry no matter what as it is special
	 * @param comparator
	 */
	public void setComparator( final Comparator< String > comparator ) { this.comparator = comparator; }
	public Comparator< String > getComparator() { return comparator; }
	
	public boolean queryXML() { return queryXML( "", "Process", null ); } 
	public boolean queryXML( final List< String > specifyAttributes ) { return queryXML( "", "Process", specifyAttributes ); } 
	public boolean queryXML( final String query ) { return queryXML( "", query, null ); } 
	public boolean queryXML( final String query, final List< String > specifyAttributes ) { return queryXML( "", query, specifyAttributes ); } 

	public void addButton( final String buttonText, final ActionListener listener )
	{
		if ( this.buttonText == null )
		{
			this.buttonText = new ArrayList<>();
			this.listener = new ArrayList<>();
		}
		this.buttonText.add( buttonText );
		this.listener.add( listener );
	}

	public GenericDialog getGenericDialog() { return gd; }

	/*
	 * Asks the user for a valid XML (real time parsing)
	 * 
	 * @param specifyAttributes - set of attributes the user should be asked if he/she wants to select a subset of them
	 * @return null if cancelled or timepointlistsize = 0
	 */
	public boolean queryXML(
			final String additionalTitle,
			String query,
			List< String > specifyAttributes )
	{
		// should not be null, just empty
		if ( specifyAttributes == null )
			specifyAttributes = new ArrayList< String >();
		
		// they are ordered by alphabet (or user defined) so that the details and then queried in the same order
		if ( comparator == null )
			Collections.sort( specifyAttributes );
		else
			Collections.sort( specifyAttributes, comparator );
		
		// timepoint is always last
		if ( specifyAttributes.contains( XmlKeys.TIMEPOINTS_TIMEPOINT_TAG ) )
		{
			specifyAttributes.remove( XmlKeys.TIMEPOINTS_TIMEPOINT_TAG );
			specifyAttributes.add( XmlKeys.TIMEPOINTS_TIMEPOINT_TAG );
		}
		
		// adjust query to support recording
		if ( query.contains( " " ) )
			query = query.replaceAll( " ", "_" );
		
		this.attributeChoice = new HashMap< String, Integer >();
		
		// try parsing if it ends with XML
		tryParsing( defaultXMLfilename, false );
		
		final GenericDialogPlus gd;
		
		if ( additionalTitle != null && additionalTitle.length() > 0 )
			gd = new GenericDialogPlus( "Select dataset for " + additionalTitle );
		else
			gd = new GenericDialogPlus( "Select Dataset" );

		final String text = "Select";

		gd.addFileField( text, defaultXMLfilename, 65 );
		gd.addMessage( this.message1, GUIHelper.largestatusfont, this.color );
		Label l1 = (Label)gd.getMessage();
		
		gd.addMessage( this.message2, GUIHelper.smallStatusFont, this.color );
		Label l2 = (Label)gd.getMessage();
		
		if ( specifyAttributes.size() > 0 )
			gd.addMessage( " " );
		
		for ( int i = 0; i < specifyAttributes.size(); ++i )
		{
			final String attribute = specifyAttributes.get( i );
			final String[] choices = makeChoiceList( attribute );
			final int defaultChoice = defaultAttributeChoice.containsKey( attribute ) ? defaultAttributeChoice.get( attribute ) : 0;
			
			gd.addChoice( query + "_" + attribute, choices, choices[ defaultChoice ] );
		}

		if ( Toggle_Cluster_Options.displayClusterProcessing )
		{
			gd.addMessage( "" );
			gd.addChoice( "XML_Output", clusterOptions1, clusterOptions1[ defaultClusterOption1 ] );
			gd.addMessage( "Note: Later on you need to merge the different XML's using Plugins>MultiView Reconstruction>Tools>Cluster>Merge Cluster Jobs", GUIHelper.smallStatusFont );
		}

		if ( !PluginHelper.isHeadless() )
			addListeners( gd, (TextField)gd.getStringFields().firstElement(), l1, l2 );

		if ( buttonText != null && listener != null )
		{
			for ( int i = 0; i < buttonText.size(); ++i )
			{
				gd.addMessage( "", GUIHelper.smallStatusFont );
				gd.addButton( buttonText.get( i ), listener.get( i ) );

				try
				{
					if ( buttonText.get( i ).equals( "Define a new dataset" ) )
						defineNewDataset = ((Button)gd.getComponent( gd.getComponentCount() - 1 ));
				}
				catch (Exception e) { defineNewDataset = null; }
			}
		}

		this.gd = gd;

		gd.addMessage( "" );
		GUIHelper.addPreibischLabWebsite( gd );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return false;
		
		String xmlFilename = defaultXMLfilename = gd.getNextString();

		// try to parse the file anyways
		tryParsing( xmlFilename, true );

		if ( buttonText != null && xmlFilename.toLowerCase().equals( "define" ) && buttonText.get( 0 ).equals( "Define a new dataset" ) )
		{
			this.data = null;
			this.attributes = null;
			return true;
		}

		for ( int i = 0; i < specifyAttributes.size(); ++i )
		{
			final String attribute = specifyAttributes.get( i );
			final int choice = gd.getNextChoiceIndex();
			
			defaultAttributeChoice.put( attribute, choice );
			attributeChoice.put( attribute, choice );
		}

		final int clusterSaving;

		// check for cluster options if selected
		if ( Toggle_Cluster_Options.displayClusterProcessing )
			clusterSaving = defaultClusterOption1 = gd.getNextChoiceIndex();
		else
			clusterSaving = 0;

		// fill up angles, channels, illuminations, timepoints (all, if there is no further dialog)
		if ( !queryDetails() )
			return false;

		if ( clusterSaving == 0 )
		{
			this.clusterExt = "";
		}
		else if ( clusterSaving == 1 )
		{
			this.clusterExt = "job_" + createUniqueName();
		}
		else
		{
			final GenericDialog gdCluster = new GenericDialog( "Define unique ID" );
			gdCluster.addStringField( "UNIQUE_ID", "" );
			gdCluster.addMessage( "Note: Using an ID twice might result in overwriting of the XML files.", GUIHelper.smallStatusFont );

			gdCluster.showDialog();

			if ( gdCluster.wasCanceled() )
				return false;

			this.clusterExt = "job_" + gdCluster.getNextString();
		}

		return true;
	}

	public String getClusterExtension() { return this.clusterExt; }

	protected String createUniqueName()
	{
		long idSum = 1;

		for ( final TimePoint t : getTimePointsToProcess() )
			idSum *= t.getId();

		for ( final BasicViewSetup v : getViewSetupsToProcess() )
			idSum += v.getId();

		long nano = System.nanoTime();
		long millis = System.currentTimeMillis();
		long finalHash = nano + millis + idSum;

		if ( debugRandomClusterHash )
		{
			IOFunctions.println( "idsum=" + idSum );
			IOFunctions.println( "nano=" + nano );
			IOFunctions.println( "millis=" + millis );
			IOFunctions.println( "final=" + finalHash );
		}

		return "" + finalHash;
	}

	/**
	 * Querys a single element from the list
	 * 
	 * @param name - type of elements (e.g. "Timepoint")
	 * @param list - list of available elements
	 * @param defaultSelection - default selection
	 * @return the selection or -1 if cancelled
	 */
	public static int queryIndividualEntry( final String name, final String[] list, int defaultSelection )
	{
		if ( defaultSelection >= list.length )
			defaultSelection = 0;

		final GenericDialog gd = new GenericDialog( "Select Single " + name );
		gd.addChoice( "Processing_" + name, list, list[ defaultSelection ] );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return -1;
		
		return gd.getNextChoiceIndex();
	}
	
	/**
	 * Querys a multiple element from the list
	 * 
	 * @param name - type of elements (e.g. "Timepoints")
	 * @param list - list of available elements
	 * @param defaultSelection - default selection
	 * @return the selection or null if cancelled
	 */
	public static boolean[] queryMultipleEntries( final String name, final String[] list, boolean[] defaultSelection )
	{
		if ( defaultSelection == null || defaultSelection.length != list.length )
		{
			defaultSelection = new boolean[ list.length ];
			defaultSelection[ 0 ] = true;
			for ( int i = 1; i < list.length; ++i )
				defaultSelection[ i ] = false;
			
			// by default select first two
			if ( defaultSelection.length > 1 )
				defaultSelection[ 1 ] = true;
		}
		
		for ( int i = 0; i < list.length; ++i )
			list[ i ] = list[ i ].replace( " ", "_" );
		
		final GenericDialog gd = new GenericDialog( "Select Multiple " + name );
		
		gd.addMessage( "" );
		for ( int i = 0; i < list.length; ++i )
			gd.addCheckbox( list[ i ], defaultSelection[ i ] );
		gd.addMessage( "" );

		GUIHelper.addScrollBars( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;

		for ( int i = 0; i < list.length; ++i )
		{
			if ( gd.getNextBoolean() )
				defaultSelection[ i ] = true;
			else
				defaultSelection[ i ] = false;
		}

		return defaultSelection;
	}
	
	/**
	 * Querys a pattern of element from the list
	 * 
	 * @param name - type of elements (e.g. "Timepoints")
	 * @param list - list of available elements
	 * @param defaultSelectionArray - default selection (array of size 1 to be able to return it)
	 * @return the selection or null if cancelled
	 */
	public static boolean[] queryPattern( final String name, final String[] list, final String[] defaultSelectionArray )
	{
		String defaultSelection = defaultSelectionArray[ 0 ];

		if ( defaultSelection == null || defaultSelection.length() == 0 )
		{
			defaultSelection = list[ 0 ];
			
			for ( int i = 1; i < Math.min( list.length, 3 ); ++i )
				defaultSelection += "," + list[ i ];
		}
		
		final GenericDialog gd = new GenericDialog( "Select Range of " + name );
		
		gd.addMessage( "" );
		gd.addStringField( "Process_following_" + name, defaultSelection, 30 );
		gd.addMessage( "" );
		gd.addMessage( "Available " + name + ":" );
		
		final String singular = name.substring( 0, name.length() - 1 ) + " ";
		String allTps = singular + list[ 0 ];
		
		for ( int i = 1; i < list.length; ++i )
			allTps += "\n" + singular + list[ i ];
		
		gd.addMessage( allTps, GUIHelper.smallStatusFont );
		
		GUIHelper.addScrollBars( gd );
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		// the result
		final boolean[] selected = new boolean[ list.length ];
		
		for ( int i = 0; i < list.length; ++i )
			selected[ i ] = false;
		
		try 
		{
			final ArrayList< String > nameList = NamePattern.parseNameString( defaultSelection = gd.getNextString(), true );
			
			for ( final String entry : nameList )
			{
				boolean found = false;
				
				for ( int i = 0; i < list.length && !found; ++i )
				{
					if ( entry.equals( list[ i ] ) )
					{
						selected[ i ] = true;
						found = true;
					}
				}
				
				if ( !found )
					IOFunctions.println( name + " " + entry + " not part of the list of " + name + "s. Ignoring it." );
			}				
		} 
		catch ( final ParseException e ) 
		{
			IOFunctions.println( "Cannot parse pattern '" + defaultSelection + "': " + e );
			return null;
		}
		
		defaultSelectionArray[ 0 ] = defaultSelection;
		
		return selected;
	}
	
	protected < E extends Entity > boolean query( final int choice, final String attribute, final List< E > allEntities, final List< E > entitiesToProcess )
	{
		if ( choice == 1 ) // choose a single entry
		{
			int defaultSelection = defaultAttributeEntityIndex.containsKey( attribute ) ? defaultAttributeEntityIndex.get( attribute ) : 0;
			
			if ( defaultSelection >= allEntities.size() )
				defaultSelection = 0;
			
			final int selection = queryIndividualEntry( attribute, buildEntityList( attribute, allEntities, true ), defaultSelection );
			
			if ( selection >= 0 )
				entitiesToProcess.add( allEntities.get( selection ) );
			else
				return false;
			
			defaultAttributeEntityIndex.put( attribute, selection );
		}
		else if ( choice == 2 || choice == 3 ) // choose multiple angles or angles defined by pattern
		{
			final boolean[] selection;
			String[] defaultPattern = new String[]{ defaultAttributePatternString.get( attribute ) };
			boolean[] defaultSelectedEntities = defaultAttributeSelectedEntities.get( attribute );
			
			if ( choice == 2 )
				selection = queryMultipleEntries( attribute + "s", buildEntityList( attribute, allEntities, true ), defaultSelectedEntities );
			else
				selection = queryPattern( attribute + "s", buildEntityList( attribute, allEntities, false ), defaultPattern );
			
			if ( selection == null )
				return false;
			else
			{
				defaultAttributeSelectedEntities.put( attribute, selection );
				
				if ( choice == 3 )
					defaultAttributePatternString.put( attribute, defaultPattern[ 0 ] );
				
				for ( int i = 0; i < selection.length; ++i )
					if ( selection[ i ] )
						entitiesToProcess.add( allEntities.get( i ) );
			}
		}
		else
		{
			entitiesToProcess.addAll( allEntities );
		}
		
		if ( entitiesToProcess.size() == 0 )
		{
			throw new RuntimeException( "List of " + attribute + "s is empty. Stopping." );
			//IOFunctions.println( "WARNING: List of " + attribute + "s is empty." );
			//return true;
		}
		else
		{
			String selected = "";
			
			for ( int e = 0; e < entitiesToProcess.size(); ++e )
			{
				if ( entitiesToProcess.get( e ) instanceof NamedEntity )
					selected += ((NamedEntity) entitiesToProcess.get( e )).getName();
				else
					selected += entitiesToProcess.get( e ).getId();
				
				if ( e != entitiesToProcess.size() - 1 )
					selected += ", ";
			}
			
			IOFunctions.println( attribute + "s selected: " + selected );
		}
		
		return true;
	}
	
	protected boolean queryDetails()
	{
		// all attibutes
		this.attributeInstancesToProcess = new HashMap< String, List< Entity > >();

		for ( int attributeIndex = 0; attributeIndex < this.attributes.size(); ++attributeIndex )
			this.attributeInstancesToProcess.put( this.attributes.get( attributeIndex ), new ArrayList< Entity >() );

		for ( int i = 0; i < this.attributes.size(); ++i )
		{
			final String attribute = this.attributes.get( i );
			final int choice = attributeChoice.containsKey( attribute ) ? attributeChoice.get( attribute ) : 0;

			if ( !query( choice, attribute, this.allAttributeInstances.get( attribute ), this.attributeInstancesToProcess.get( attribute ) ) )
				return false;
		}

		// timepoints
		this.timepointsToProcess = new ArrayList< TimePoint >();

		final String attribute = XmlKeys.TIMEPOINTS_TIMEPOINT_TAG;
		final int choice = attributeChoice.containsKey( attribute ) ? attributeChoice.get( attribute ) : 0;
		
		if ( !query( choice, attribute, this.data.getSequenceDescription().getTimePoints().getTimePointsOrdered(), this.timepointsToProcess ) )
			return false;

		return true;
	}
	
	protected static String[] buildTimepointList( final List< TimePoint > tpList, final boolean addTitle )
	{
		final String[] timepoints = new String[ tpList.size() ];
		
		for ( int i = 0; i < timepoints.length; ++i )
			if ( addTitle )
				timepoints[ i ] = "Timepoint " + tpList.get( i ).getName();
			else
				timepoints[ i ] = tpList.get( i ).getName();
		
		return timepoints;
	}

	protected static String[] buildEntityList( final String attributeName, final List< ? extends Entity > entityList, final boolean addTitle )
	{
		final String[] entities = new String[ entityList.size() ];

		for ( int i = 0; i < entities.length; ++i )
		{
			final Entity e = entityList.get( i );
			final String entityName;
			
			if ( e instanceof NamedEntity )
				entityName = ((NamedEntity)e).getName();
			else
				entityName = Integer.toString( e.getId() );

			if ( addTitle )
				entities[ i ] = attributeName + " " + entityName;
			else
				entities[ i ] = entityName;
		}
		
		return entities;
	}

	protected static String[] makeChoiceList( final String attribute )
	{
		final String[] choiceList = new String[ attributeChoiceList.length ];
		
		for ( int i = 0; i < choiceList.length; ++i )
			choiceList[ i ] = attributeChoiceList[ i ].replace( "*", attribute );
		
		return choiceList;
	}

	protected boolean tryParsing( final String xmlfile, final boolean parseAllTypes )
	{
		this.xmlfilename = xmlfile;
		if ( buttonText != null && buttonText.get( 0 ).equals( "Define a new dataset" ) )
			this.message1 = neutralMsg1a;
		else
			this.message1 = neutralMsg1b;
		this.message2 = noMsg2;
		this.color = GUIHelper.neutral;
		this.data = null;
		
		if ( parseAllTypes || ( !parseAllTypes && xmlfile.endsWith( ".xml" ) ) )
		{
			try 
			{
				this.data = parseXML( xmlfile );

				// which attributes
				this.attributes = getAttributes( data, comparator );

				// get the list of entity instances per attribute
				final int numAttributes = this.attributes.size();
				this.allAttributeInstances = new HashMap< String, List< Entity > >();

				// count number of entity id's per attribute and make sure we add them only once
				// also populates this.allAttributeInstances
				final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = entitiesPerAttribute();

				// populate entity lists
				populateAttributesEntities( numAttributes, numEntitiesPerAttrib );

				this.message1 = goodMsg1;
				this.message2 = getSpimDataDescription( this.data, this.attributes, numEntitiesPerAttrib, numAttributes );
				this.color = GUIHelper.good;
			}
			catch ( final Exception e )
			{
				this.message1 = errorMsg1;
				this.message2 = noMsg2;
				this.color = GUIHelper.error;

				if ( defineNewDataset != null )
					defineNewDataset.setForeground( Color.BLACK );

				IOFunctions.println( "Cannot parse '" + xmlfile + "': " + e );
				e.printStackTrace();
				return false;
			}
		}
		else if ( xmlfile.length() > 0 )
		{
			this.message1 = warningMsg1;
			this.message2 = noMsg2;
			this.color = GUIHelper.warning;

			if ( defineNewDataset != null )
				defineNewDataset.setForeground( Color.BLACK );

			return false;
		}

		if ( defineNewDataset != null )
		{
			if ( message1.equals( neutralMsg1a ) )
				defineNewDataset.setForeground( Color.RED );
			else
				defineNewDataset.setForeground( Color.BLACK );
		}

		return true;
	}

	protected void populateAttributesEntities( final int numAttributes, final ArrayList< HashSet< Integer > > numEntitiesPerAttrib )
	{
		for ( final V viewSetup : this.data.getSequenceDescription().getViewSetupsOrdered() )
		{ 
			for ( int attributeIndex = 0; attributeIndex < numAttributes; ++attributeIndex )
			{
				final String attribute = this.attributes.get( attributeIndex );

				// the number of id's per attribute
				final HashSet< Integer > numEntityIds = numEntitiesPerAttrib.get( attributeIndex );

				// the entity instance (could be Angle, Channel, etc ... )
				Entity e = viewSetup.getAttributes().get( attribute );

				if ( e == null )
				{
					if ( attribute.equals( "angle" ) )
					{
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": 'angle' attribute undefined, using Angle 0 to support it." );
						e = new Angle( 0 );
						viewSetup.setAttribute( e );
					}
					else if ( attribute.equals( "channel" ) )
					{
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": 'channel' attribute undefined, using Channel 0 to support it." );
						e = new Channel( 0 );
						viewSetup.setAttribute( e );
					}
					else if ( attribute.equals( "illumination" ) )
					{
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": 'illumination' attribute undefined, using Illumination 0 to support it." );
						e = new Illumination( 0 );
						viewSetup.setAttribute( e );
					}
					else if ( attribute.equals( "tile" ) )
					{
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": 'tile' attribute undefined, using Tile 0 to support it." );
						e = new Tile( 0 );
						viewSetup.setAttribute( e );
					}
					else
					{
						// something new we do not know
						IOFunctions.println( new Date( System.currentTimeMillis() ) + ": Unknown entity '" + attribute + "', adding placeholder entity to support it." );
						e = new EmptyEntity( 0, attribute );
						viewSetup.getAttributes().put( attribute, e );
					}
				}

				final int id = e.getId();

				if ( !numEntityIds.contains( id ) )
				{
					numEntityIds.add( id );
					this.allAttributeInstances.get( attribute ).add( e );
				}
			}
		}

		// sort all entity lists
		for ( final String attribute : this.allAttributeInstances.keySet() )
			Collections.sort( this.allAttributeInstances.get( attribute ),
					new Comparator<Entity>()
					{
						@Override
						public int compare( final Entity o1, final Entity o2 )
						{
							return o1.getId() - o2.getId();
						}
					});
	}

	// count number of entity id's per attribute and make sure we add them only once
	protected final ArrayList< HashSet< Integer > > entitiesPerAttribute()
	{
		final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = new ArrayList< HashSet< Integer > >( this.attributes.size() );

		if (allAttributeInstances == null)
			allAttributeInstances = new HashMap<>();
		
		for ( int attributeIndex = 0; attributeIndex < this.attributes.size(); ++attributeIndex )
		{
			this.allAttributeInstances.put( this.attributes.get( attributeIndex ), new ArrayList< Entity >() );
			numEntitiesPerAttrib.add( new HashSet< Integer >() );
		}

		return numEntitiesPerAttrib;
	}

	public static < AS extends AbstractSpimData< S >,
					S extends AbstractSequenceDescription< V, D, L >,
					V extends BasicViewSetup,
					D extends BasicViewDescription< V >,
					L extends BasicImgLoader,
					X extends XmlIoAbstractSpimData< S, AS > > ArrayList< String > getAttributes(
							final AS data,
							final Comparator< String > comparator )
	{
		final ArrayList< String > attributes = new ArrayList< String >();
		attributes.addAll( data.getSequenceDescription().getViewSetupsOrdered().get( 0 ).getAttributes().keySet() );

		// the attributes are ordered by alphabet (or user defined) so that the details and then queried in the same order
		if ( comparator == null )
			Collections.sort( attributes );
		else
			Collections.sort( attributes, comparator );

		return attributes;
	}

	public static < AS extends AbstractSpimData< S >,
					S extends AbstractSequenceDescription< V, D, L >,
					V extends BasicViewSetup,
					D extends BasicViewDescription< V >,
					L extends BasicImgLoader,
					X extends XmlIoAbstractSpimData< S, AS > > String getSpimDataDescription(
							final AS data,
							final ArrayList< String > attributes,
							final ArrayList< HashSet< Integer > > numEntitiesPerAttrib,
							final int numAttributes )
	{
		int countMissingViews = 0;
		
		for ( final D v : data.getSequenceDescription().getViewDescriptions().values() )
			if ( !v.isPresent() )
				++countMissingViews;

		final int timepoints = data.getSequenceDescription().getTimePoints().size();

		String message2 = "";

		for ( int attributeIndex = 0; attributeIndex < numAttributes; ++attributeIndex )
		{
			final int numEntities = numEntitiesPerAttrib.get( attributeIndex ).size();
			String entityName = attributes.get( attributeIndex );
			
			if ( numEntities > 1 )
				entityName += "s";

			message2 += numEntities + " " + entityName +  ", ";
		}
		
		if ( timepoints > 1 )
			message2 += timepoints + " timepoints, ";
		else
			message2 += timepoints + " timepoint, ";
		
		if ( countMissingViews == 1 )
			message2 += countMissingViews + " missing view";
		else
			message2 += countMissingViews + " missing views";

		return message2;
	}

	protected AS parseXML( final String xmlFilename ) throws SpimDataException
	{
		return io.load( xmlFilename );
	}

	protected void addListeners( final GenericDialog gd, final TextField tf, final Label label1, final Label label2  )
	{
		final GenericLoadParseQueryXML< ?,?,?,?,?,? > lpq = this;
		
		// using TextListener instead
		tf.addTextListener( new TextListener()
		{	
			@Override
			public void textValueChanged( final TextEvent t )
			{
				if ( t.getID() == TextEvent.TEXT_VALUE_CHANGED )
				{
					final String xmlFilename = tf.getText();
					
					// try parsing if it ends with XML
					tryParsing( xmlFilename, false );
					
					label1.setText( lpq.message1 );
					label2.setText( lpq.message2 );
					label1.setForeground( lpq.color );
					label2.setForeground( lpq.color );
				}
			}
		});
	}
}
