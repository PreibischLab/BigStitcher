package net.preibisch.stitcher.aws.spimloader;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.google.common.io.CharStreams;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.aws.tools.TempFolder;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.StAXEventBuilder;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.*;

public class AWSSpimLoader {


    private final AmazonS3 s3;
    private final String path;
    private final String fileName;
    private final String bucketname;
    private Document doc;
    private File localFile;

    public AWSSpimLoader(AmazonS3 s3, String bucketName, String path, String fileName) {
        this.s3 = s3;
        this.bucketname = bucketName;
        this.path = path;
        this.fileName = fileName;
//        ImgLoaders.registerManually(XmlIoAWSSpimImageLoader.class);
    }

    public AWSSpimLoader(S3BucketInstance instance, String path, String fileName) {
        this(instance.getS3(), instance.getBucketName(), path, fileName);
    }

    private void read() throws IOException, JDOMException, XMLStreamException {
        if (doc != null)
            return;
        S3Object object = s3.getObject(new GetObjectRequest(bucketname, path + fileName));
        InputStream objectData = object.getObjectContent();
        this.localFile = new File(TempFolder.get(), fileName);
        String text = null;
        try (Reader reader = new InputStreamReader(objectData)) {
            text = CharStreams.toString(reader);
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(localFile)));
            pw.write(text);
            pw.flush();
            pw.close();
        }
        objectData.close();
        doc = parseXML(text);
    }

    private Document parseXML(String text) throws XMLStreamException, JDOMException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        XMLEventReader reader = factory.createXMLEventReader(new StringReader(text));
        StAXEventBuilder builder = new StAXEventBuilder();
        return builder.build(reader);
    }

    public String getFile() {
        return fileName;
    }

    public SpimData2 getSpimdata() {
        if (doc == null) {
            try {
                read();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (XMLStreamException e) {
                e.printStackTrace();
            }
            return getSpimdata();
        } else {
            try {
                return new XmlIoSpimData2("").load(localFile.getAbsolutePath());
//                return new XmlIoSpimData2("").fromXml(doc.getRootElement(), new File(""));
            } catch (SpimDataException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
