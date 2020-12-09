package net.preibisch.stitcher.aws.reader;


import com.amazonaws.regions.Regions;
import com.bigdistributor.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.dataexchange.job.model.Params;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.registration.XmlIoViewRegistrations;
import mpicbg.spim.data.sequence.XmlIoSequenceDescription;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.jdom2.Document;
import org.jdom2.JDOMException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class AWSXmlIoSpimData2<S extends AbstractSequenceDescription<?, ?, ?>, T extends AbstractSpimData<S>> extends XmlIoAbstractSpimData {

    protected AWSSpimReader awsio;
    private Document doc;
    private SpimData2 data;
    private XmlIoSpimData2 io;
    private final Params params;

    public AWSXmlIoSpimData2() throws IllegalAccessException {
        super(AWSXmlIoSpimData2.class, new XmlIoSequenceDescription(), new XmlIoViewRegistrations());
        io = new XmlIoSpimData2("");
        this.params = Params.get();
        AWSCredentialInstance.init(params.getCredentialPath());
        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, params.getBucketName());
//        awsio = new AWSXMLReader(S3BucketInstance.get(), params.getPath(), params.getXmlFile());
        awsio = new AWSSpimReader(S3BucketInstance.get(),params.getPath(), params.getXmlFile());

    }

    public SpimData2 load() throws SpimDataException, JDOMException, XMLStreamException, IOException {
        return awsio.getSpim();
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
