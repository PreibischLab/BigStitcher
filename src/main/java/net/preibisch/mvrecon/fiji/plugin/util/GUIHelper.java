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
package net.preibisch.mvrecon.fiji.plugin.util;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.gui.MultiLineLabel;
import ij.plugin.BrowserLauncher;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Panel;
import java.awt.ScrollPane;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewId;

public class GUIHelper
{
	public static Color good = new Color( 0, 139, 14 );
	public static Color warning = new Color( 255, 100, 0 );
	public static Color error = new Color( 255, 0, 0 );
	public static Color neutral = new Color( 0, 0, 0 );
	
	public static Font largestatusfont = new Font( Font.SANS_SERIF, Font.BOLD + Font.ITALIC, 14 );
	public static Font largefont = new Font( Font.SANS_SERIF, Font.BOLD, 14 );
	public static Font mediumstatusfont = new Font( Font.SANS_SERIF, Font.BOLD + Font.ITALIC, 12 );
	public static Font mediumstatusNonItalicfont = new Font( Font.SANS_SERIF, Font.BOLD, 12 );
	public static Font headline = new Font( Font.SANS_SERIF, Font.BOLD, 12 );
	public static Font smallStatusFont = new Font( Font.SANS_SERIF, Font.ITALIC, 11 );

	public static Font staticfont = new Font( Font.MONOSPACED, Font.PLAIN, 12 );

	final public static String myURL = "http://preibischlab.mdc-berlin.de/";
	final public static String paperURL = "http://www.nature.com/nmeth/journal/v7/n6/full/nmeth0610-418.html";
	final public static String messagePaper = "Please note that the SPIM Registration is based on a publication.\n" +
											  "If you use it successfully for your research please be so kind to cite our work:\n" +
											  "Preibisch et al., Nature Methods (2010), 7(6):418-419\n";

	final public static String messageWebsite = "This plugin is written and maintained by the Preibisch Lab (click for webpage)\n";

	public static void addNatMethBeadsPaper( final GenericDialog gd ) { addNatMethBeadsPaper( gd, messagePaper ); }
	public static void addNatMethBeadsPaper( final GenericDialog gd, final String msg )  { addHyperLink( gd, msg, paperURL ); }

	public static void addWebsite( final GenericDialog gd ) { addWebsite( gd, messageWebsite ); }
	public static void addWebsite( final GenericDialog gd, final String msg ) { addHyperLink( gd, msg, myURL ); }

	public static final void addHyperLink( final GenericDialog gd, final String msg, final String url )
	{
		gd.addMessage( msg, new Font( Font.SANS_SERIF, Font.ITALIC + Font.BOLD, 12 ) );
		MultiLineLabel text =  (MultiLineLabel) gd.getMessage();
		GUIHelper.addHyperLinkListener( text, url );
	}

	public static final void addPreibischLabWebsite( final GenericDialog gd )
	{
		gd.addMessage( "This software is developed by the Preibisch Lab in collaboration with the ImgLib2 and Fiji team\nhttp://preibischlab.mdc-berlin.de/", new Font( Font.SANS_SERIF, Font.BOLD, 12 ) );
		MultiLineLabel text =  (MultiLineLabel) gd.getMessage();
		GUIHelper.addHyperLinkListener( text, "http://preibischlab.github.io/preibisch-labsite" );
	}

	public static void displayRegistrationNames( final GenericDialog gd, final HashMap< String, Integer > names )
	{
		if ( names.keySet().size() == 0 )
		{
			gd.addMessage( "View Registrations could not be read. This should not happen." );
			return;
		}

		gd.addMessage( "Title of last View Registrations", headline );

		final ArrayList< String > n = new ArrayList< String >();

		for ( final String name : names.keySet() )
			n.add( name  + " (" + names.get( name ) + " views)" );

		Collections.sort( n );

		String text = n.get( 0 );

		for ( int i = 1; i < n.size(); ++i )
			text += "\n" + n.get( i );

		gd.addMessage( text, smallStatusFont );
	}

	public static void displayBoundingBoxes( final GenericDialog gd, final List< BoundingBox > bbs )
	{
		gd.addMessage( "Existing Bounding Boxes", headline );
		gd.addMessage( "", smallStatusFont );

		if ( bbs == null || bbs.size() == 0 )
		{
			gd.addMessage( "No Bounding Boxes defined yet." );
			return;
		}

		final ArrayList< String > n = new ArrayList< String >();

		for ( final BoundingBox bb : bbs )
			n.add( BoundingBox.getBoundingBoxDescription( bb ) );

		Collections.sort( n );

		String text = n.get( 0 );

		for ( int i = 1; i < n.size(); ++i )
			text += "\n" + n.get( i );

		gd.addMessage( text, smallStatusFont );
	}

	public static HashMap< String, Integer > assembleRegistrationNames( final SpimData data, final List< ViewId > viewIds )
	{
		final ViewRegistrations vr = data.getViewRegistrations();
		final SequenceDescription sd = data.getSequenceDescription();

		final HashMap< String, Integer > names = new HashMap< String, Integer >();

		for ( final ViewId viewId: viewIds )
		{
			final ViewDescription vd = sd.getViewDescription( viewId );

			if ( !vd.isPresent() )
				continue;

			final ViewRegistration r = vr.getViewRegistration( vd );
			final String rName;

			if ( r.getTransformList().size() == 0 )
				rName = null;
			else
				rName = r.getTransformList().get( 0 ).getName();

			if ( rName != null )
			{
				if ( names.containsKey( rName ) )
					names.put( rName, names.get( rName ) + 1 );
				else
					names.put( rName, 1 );
			}
		}

		return names;
	}

	public static final void addHyperLinkListener( final MultiLineLabel text, final String myURL )
	{
		if ( text != null && myURL != null )
		{
			text.addMouseListener( new MouseAdapter()
			{
				@Override
				public void mouseClicked( final MouseEvent e )
				{
					try
					{
						BrowserLauncher.openURL( myURL );
					}
					catch ( Exception ex )
					{
						IJ.log( "" + ex);
					}
				}
	
				@Override
				public void mouseEntered( final MouseEvent e )
				{
					text.setForeground( Color.BLUE );
					text.setCursor( new Cursor( Cursor.HAND_CURSOR ) );
				}
	
				@Override
				public void mouseExited( final MouseEvent e )
				{
					text.setForeground( Color.BLACK );
					text.setCursor( new Cursor( Cursor.DEFAULT_CURSOR ) );
				}
			});
		}
	}

	/**
	 * A copy of Curtis's method
	 * 
	 * https://github.com/openmicroscopy/bioformats/blob/v4.4.8/components/loci-plugins/src/loci/plugins/util/WindowTools.java#L72
	 * 
	 *
	 * @param obj - the Container to add the scroll bar to
	 */
	public static void addScrollBars(Object obj) {
//        * <dependency>
//        * <groupId>${bio-formats.groupId}</groupId>
//        * <artifactId>loci_plugins</artifactId>
//        * <version>${bio-formats.version}</version>
//        * </dependency>

		if (!(obj instanceof Container))
		{
			IOFunctions.println( "Cannot add scrollbars, it's not a Container but a " + obj.getClass().getSimpleName() );
			return;
		}

		final Container pane = (Container) obj;

		if (!(pane.getLayout() instanceof GridBagLayout))
		{
			IOFunctions.println( "Cannot add scrollbars, it's not a GridBagLayout but a " + pane.getLayout().getClass().getSimpleName() );
			return;
		}

		GridBagLayout layout = (GridBagLayout) pane.getLayout();

		// extract components
		int count = pane.getComponentCount();
		Component[] c = new Component[count];
		GridBagConstraints[] gbc = new GridBagConstraints[count];
		for (int i = 0; i < count; i++) {
			c[i] = pane.getComponent(i);
			gbc[i] = layout.getConstraints(c[i]);
		}

		// clear components
		pane.removeAll();
		layout.invalidateLayout(pane);

		// create new container panel
		Panel newPane = new Panel();
		GridBagLayout newLayout = new GridBagLayout();
		newPane.setLayout(newLayout);
		for (int i = 0; i < count; i++) {
			newLayout.setConstraints(c[i], gbc[i]);
			newPane.add(c[i]);
		}

		// HACK - get preferred size for container panel
		// NB: don't know a better way:
		// - newPane.getPreferredSize() doesn't work
		// - newLayout.preferredLayoutSize(newPane) doesn't work
		Frame f = new Frame();
		f.setLayout(new BorderLayout());
		f.add(newPane, BorderLayout.CENTER);
		f.pack();
		final Dimension size = newPane.getSize();
		f.remove(newPane);
		f.dispose();

		// compute best size for scrollable viewport
		size.width += 25;
		size.height += 15;
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int maxWidth = 7 * screen.width / 8;
		int maxHeight = 3 * screen.height / 4;
		if (size.width > maxWidth)
			size.width = maxWidth;
		if (size.height > maxHeight)
			size.height = maxHeight;

		// create scroll pane
		ScrollPane scroll = new ScrollPane() {
			private static final long serialVersionUID = 1L;

			public Dimension getPreferredSize() {
				return size;
			}
		};
		scroll.add(newPane);

		// add scroll pane to original container
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridwidth = GridBagConstraints.REMAINDER;
		constraints.fill = GridBagConstraints.BOTH;
		constraints.weightx = 1.0;
		constraints.weighty = 1.0;
		layout.setConstraints(scroll, constraints);
		pane.add(scroll);
	}	

	/**
	 * Removes any of those characters from a String: (, ), [, ], {, }, &lt;, &gt;
	 * 
	 * @param entry input (with brackets)
	 * @return input, but without any brackets
	 */
	public static String removeBrackets( String entry )
	{
		return removeSequences( entry, new String[] { "(", ")", "{", "}", "[", "]", "<", ">" } );
	}
	
	public static String removeSequences( String entry, final String[] sequences )
	{
		while ( contains( entry, sequences ) )
		{
			for ( final String s : sequences )
			{
				final int index = entry.indexOf( s );

				if ( index == 0 )
					entry = entry.substring( s.length(), entry.length() );
				else if ( index == entry.length() - s.length() )
					entry = entry.substring( 0, entry.length() - s.length() );
				else if ( index > 0 )
					entry = entry.substring( 0, index ) + entry.substring( index + s.length(), entry.length() );
			}
		}

		return entry;		
	}
	
	public static boolean contains( final String entry, final String[] sequences )
	{
		for ( final String seq : sequences )
			if ( entry.contains( seq ) )
				return true;
		
		return false;
	}
}
