package net.preibisch.stitcher.aws.reader;

import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.bigdistributor.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.google.common.io.CharStreams;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.StAXEventBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.*;

public class AWSSpimReader {
    private static final String defaultName = "dataset.xml";

    private final S3BucketInstance bucketInstance;
    private final String path;
    private final String fileName;
    private Document doc;

    public AWSSpimReader(S3BucketInstance bucketInstance, String path, String fileName) {
        this.bucketInstance = bucketInstance;
        this.path = path;
        this.fileName = fileName;
    }

    public AWSSpimReader(S3BucketInstance s3, String path) {
        this(s3, path, defaultName);
    }

    private void read() throws IOException, JDOMException, XMLStreamException {
        if(doc!=null)
            return;
        S3Object object = bucketInstance.getS3().getObject(new GetObjectRequest(bucketInstance.getBucketName(), path + fileName));
        InputStream objectData = object.getObjectContent();
        String text = null;
        try (Reader reader = new InputStreamReader(objectData)) {
            text = CharStreams.toString(reader);
            System.out.println("XML load !");
            System.out.println(text);
        }
        objectData.close();
        doc =  parseXML(text);
    }

    private Document parseXML(String text) throws XMLStreamException, JDOMException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        XMLEventReader reader = factory.createXMLEventReader(new StringReader(text));
        StAXEventBuilder builder = new StAXEventBuilder();
        return builder.build(reader);
    }

    public SpimData2 getSpim() throws JDOMException, XMLStreamException, IOException, SpimDataException {
        if (doc==null){
            read();
            return getSpim();
        }
        else{
            return new XmlIoSpimData2("").fromXml(doc.getRootElement(),new File(""));
        }
    }

    public String getFile() {
        return fileName;
    }

    public static void main(String[] args) throws IllegalAccessException, IOException, JDOMException, XMLStreamException {

//        AWSCredentialInstance.init(DEFAULT.AWS_CREDENTIALS_PATH);
//
//        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, DEFAULT.bucket_name);
//
//        Document xml = new AWSXMLReader2(S3BucketInstance.get(), "big/").read();
//
//        System.out.println(xml);
    }
}
