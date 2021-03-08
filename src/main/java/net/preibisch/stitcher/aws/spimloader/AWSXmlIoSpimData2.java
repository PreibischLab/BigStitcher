package net.preibisch.stitcher.aws.spimloader;


import com.bigdistributor.aws.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.aws.reader.AWSDataParam;
import org.jdom2.Document;
import org.jdom2.JDOMException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class AWSXmlIoSpimData2<S extends AbstractSequenceDescription<?, ?, ?>, T extends AbstractSpimData<S>> extends XmlIoAbstractSpimData {

    protected AWSSpimLoader awsio;
    private Document doc;
    private SpimData2 data;
    private XmlIoSpimData2 io;
    private final AWSDataParam params;

    public AWSXmlIoSpimData2() throws IllegalAccessException {
        super(AWSXmlIoSpimData2.class, new XmlIoSequenceDescription(), new XmlIoViewRegistrations());
        io = new XmlIoSpimData2("");

        this.params = AWSDataParam.get();
        if(!AWSCredentialInstance.isInitiated()){
            throw new IllegalAccessException("Initiate Credential before request SpimData !");
        }
        S3BucketInstance.init(AWSCredentialInstance.get(), params.getRegion(), params.getBucketName());
        awsio = new AWSSpimLoader(S3BucketInstance.get(), params.getPath(), params.getXmlFile());

    }

    public SpimData2 load() throws SpimDataException, JDOMException, XMLStreamException, IOException {
        return awsio.getSpimdata();
    }


    public boolean queryXML() {
        try {
            this.data = load();
        } catch (SpimDataException | JDOMException | XMLStreamException | IOException e) {
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
