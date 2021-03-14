package net.preibisch.stitcher.aws.spimloader;


import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.aws.tools.AWSDataParam;

import java.io.File;
import java.io.IOException;

public class AWSLoadParseQueryXML extends LoadParseQueryXML {

    public boolean queryXML(S3BucketInstance s3, File tmpFolder, AWSDataParam params) {
        try {
            this.xmlfilename = params.getXmlFile();
            this.io = new XmlIoSpimData2("");
            this.data = io.load(s3.download(tmpFolder, params.getXmlFile(), params.getPath()).getAbsolutePath());
            s3.downloadFrom(tmpFolder, params.getPath(), params.getExtraFiles());
        } catch (SpimDataException | InterruptedException | IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
