package spim.fiji.plugin;

import java.util.ArrayList;
import java.util.HashSet;

import input.FractalSpimDataGenerator;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader;
import net.preibisch.simulation.imgloader.SimulatedBeadsImgLoader2;
import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.plugin.util.GUIHelper;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

public class EasterEggLoadParseQueryXML extends LoadParseQueryXML
{
	SpimData2 virtual;
	boolean isEasterEgg;

	@Override
	protected boolean tryParsing( final String xmlfile, final boolean parseAllTypes )
	{
		final String input = xmlfile.toLowerCase().trim();

		if ( input.equals( "define" ) )
		{
			this.message1 = "This will define a new dataset, press OK to continue";
			this.message2 = "";
			this.color = GUIHelper.good;

			this.data = null;
			this.attributes = null;

			return true;
		}
		else if ( input.equals( "fractal" ) )
		{
			this.data = FractalSpimDataGenerator.createVirtualSpimData();
			this.attributes = getAttributes( data, comparator );

			final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = entitiesPerAttribute();
			populateAttributesEntities( attributes.size(), numEntitiesPerAttrib );

			this.message1 = "This is a simulated fractal for testing.";
			this.message2 = GenericLoadParseQueryXML.getSpimDataDescription( data, attributes, numEntitiesPerAttrib, attributes.size() );
			this.color = GUIHelper.good;

			return true;
		}
		else if ( input.equals( "genericbeads" ) )
		{
			this.data = SpimData2.convert( SimulatedBeadsImgLoader2.createSpimDataFromUserInput() );
			this.attributes = getAttributes( data, comparator );

			final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = entitiesPerAttribute();
			populateAttributesEntities( attributes.size(), numEntitiesPerAttrib );

			this.message1 = "These are simulated beads for testing.";
			this.message2 = GenericLoadParseQueryXML.getSpimDataDescription( data, attributes, numEntitiesPerAttrib, attributes.size() );
			this.color = GUIHelper.good;

			return true;
		}
		else if ( input.equals( "beads" ) )
		{
			this.data = SpimData2.convert( SimulatedBeadsImgLoader.spimdataExample() );
			this.attributes = getAttributes( data, comparator );

			final ArrayList< HashSet< Integer > > numEntitiesPerAttrib = entitiesPerAttribute();
			populateAttributesEntities( attributes.size(), numEntitiesPerAttrib );

			this.message1 = "These are simulated beads for testing.";
			this.message2 = GenericLoadParseQueryXML.getSpimDataDescription( data, attributes, numEntitiesPerAttrib, attributes.size() );
			this.color = GUIHelper.good;

			return true;
		}
		else if ( input.equals( "easter" ) )
		{
			this.message1 = "Following options exists:";
			this.message2 = "fractal, beads, genericbeads";

			this.color = GUIHelper.warning;
			return false;
		}
		else
		{
			return super.tryParsing( xmlfile, parseAllTypes );
		}
	}
}
