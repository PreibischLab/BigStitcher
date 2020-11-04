package net.preibisch.stitcher.plugin;

import ij.ImageJ;
import ij.plugin.PlugIn;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.gui.StitchingExplorer;
import net.preibisch.stitcher.gui.popup.aws.AWSLoadParseQueryXML;
import org.scijava.command.Command;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>BigStitcher>AWSImport")
public class LoadAWSDataset  implements Command, PlugIn {

    @Override
    public void run() {
        LoadParseQueryXML result = new AWSLoadParseQueryXML();
        if(!result.queryXML()){
            return ;
        }

        final SpimData2 data = result.getData();
        final String xml = result.getXMLFileName();;
        final XmlIoSpimData2 io = result.getIO();

        final StitchingExplorer< SpimData2, XmlIoSpimData2 > explorer =
                new StitchingExplorer< >( data, xml, io );

        explorer.getFrame().toFront();
    }

    @Override
    public void run(String s) {
        run();
    }
    public static void main(String[] args) {
        IOFunctions.printIJLog = true;

        new ImageJ();
        new LoadAWSDataset().run();

    }
}
