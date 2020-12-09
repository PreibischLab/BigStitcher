package net.preibisch.stitcher.aws.reader;


import com.amazonaws.regions.Regions;
import com.bigdistributor.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.dataexchange.aws.s3.func.read.AWSXMLReader;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class AWSXmlIoSpimData2<S extends AbstractSequenceDescription<?, ?, ?>, T extends AbstractSpimData<S>> extends XmlIoAbstractSpimData {

    protected AWSXMLReader awsio;
    private Document doc;
    private SpimData2 data;
    private XmlIoSpimData2 io;

    public AWSXmlIoSpimData2(String AwsCredentialsPath, String bucketName, String path, String xmlFile) throws IllegalAccessException {
        super(AWSXmlIoSpimData2.class, new XmlIoSequenceDescription(), new XmlIoViewRegistrations());
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
        System.out.println(root.getName());
        if (root.getName() != "SpimData") {
            throw new RuntimeException("expected <SpimData> root element. wrong file?");
        } else {
            return (SpimData2) this.fromXml(root);
        }
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
