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
package net.preibisch.mvrecon.headless.resave;

import net.preibisch.mvrecon.fiji.plugin.queryXML.GenericLoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.ViewDescription;
import mpicbg.spim.data.sequence.ViewSetup;

/**
 * Parse and load xml file
 * For the cluster process, it uses clusterExtention.
 */
public class HeadlessParseQueryXML extends GenericLoadParseQueryXML<SpimData2, SequenceDescription, ViewSetup, ViewDescription, ImgLoader, XmlIoSpimData2> {
    public HeadlessParseQueryXML() {
        super(new XmlIoSpimData2(""));
    }

    public boolean loadXML(final String xmlFilename, final boolean useCluster) {
        // try to parse the file anyways
        if (!tryParsing(xmlFilename, true))
            return false;

        // Process attribute choices
//		for ( int i = 0; i < specifyAttributes.size(); ++i )
//		{
//			final String attribute = specifyAttributes.get( i );
//			final int choice = gd.getNextChoiceIndex();
//
//			defaultAttributeChoice.put( attribute, choice );
//			attributeChoice.put( attribute, choice );
//		}

        if (!queryDetails())
            return false;

        if (useCluster)
            this.clusterExt = "job_" + createUniqueName();
        else
            this.clusterExt = "";

        return true;
    }
}
