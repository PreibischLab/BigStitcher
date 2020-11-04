package net.preibisch.stitcher.gui.popup.aws;


import com.amazonaws.regions.Regions;
import com.bigdistributor.dataexchange.aws.s3.func.AWSXMLReader;
import com.bigdistributor.dataexchange.aws.s3.model.AWSCredentialInstance;
import com.bigdistributor.dataexchange.aws.s3.model.S3BucketInstance;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.IOException;

public class AWSXmlIoSpimData2<S extends AbstractSequenceDescription<?, ?, ?>, T extends AbstractSpimData<S>> extends XmlIoSpimData2 {

    protected AWSXMLReader awsio;
    private Document doc;
    private SpimData2 data;
    private XmlIoSpimData2 io;

    public AWSXmlIoSpimData2(String AwsCredentialsPath, String bucketName, String path, String xmlFile) throws IllegalAccessException {
        super("");
        io = new XmlIoSpimData2( "" );
        AWSCredentialInstance.init(AwsCredentialsPath);

        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, bucketName);

        awsio = new AWSXMLReader(S3BucketInstance.get(), path, xmlFile);

    }

    public SpimData2 load() throws SpimDataException {
        Document doc;
        try {
            doc = awsio.read();
        } catch (IOException | JDOMException | XMLStreamException e) {
            throw new SpimDataException("Error AWS Read XML " + e.getCause());
        }
        Element root = doc.getRootElement();
        if (root.getName() != "SpimData") {
            throw new RuntimeException("expected <SpimData> root element. wrong file?");
        } else {
            return this.fromXml(root, new File(awsio.getFile()));
        }

    }

    @Override
    public SpimData2 fromXml(Element root, File xmlFile) throws SpimDataException {
        SpimData2 spimData = super.fromXml(root);
        return spimData;
    }

    public boolean queryXML() {
        try {
            this.data = load();
        } catch (SpimDataException e) {
            System.out.println(e.toString());
            return false;
        }
        return true;
    }

    public SpimData2 getData() {
        return data;
    }


    public XmlIoSpimData2 getIO() {
        return io;
    }
}
