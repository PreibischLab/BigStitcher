package net.preibisch.stitcher.gui.popup.aws;

import fiji.util.gui.GenericDialogPlus;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;

public class AWSLoadParseQueryXML extends LoadParseQueryXML {
    private final static String defaultBucketName = "bigstitcher";
    private final static String defaultKeyPath = "/Users/Marwan/Desktop/BigDistributer/aws_credentials/bigdistributer.csv";
    private final static String defaultPath = "big/";
    private final static String defaultFileName = "dataset.xml";


    public boolean queryXML() {
        final GenericDialogPlus gd = new GenericDialogPlus("AWS Input");
        gd.addFileField("Key: ", defaultKeyPath, 45);
        gd.addMessage("");
        gd.addStringField("Bucket name: ", defaultBucketName, 30);
        gd.addStringField("Path: ", defaultPath, 30);
        gd.addStringField("XML File: ", defaultFileName, 30);
        gd.showDialog();

        if (gd.wasCanceled())
            return false;

        String keyPath = gd.getNextString();
        String bucketName = gd.getNextString();
        String path = gd.getNextString();
        String xmlFile = gd.getNextString();
        System.out.println("Inputs: keyPath: " + keyPath + " | " +
                "bucketName: " + bucketName + " | " +
                "path: " + path + " | " +
                "xmlFile: " + xmlFile);

        AWSXmlIoSpimData2 result = null;
        try {
            result = new AWSXmlIoSpimData2(keyPath, bucketName, path, xmlFile);
        } catch (IllegalAccessException e) {
            System.out.println(e.toString());
            return false;
        }

        if (!result.queryXML()) {
            return false;
        }
        this.data  = result.getData();
        this.xmlfilename = xmlFile;
        this.io = result.getIO();

        return true;


    }

}
