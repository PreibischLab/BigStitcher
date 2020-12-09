package net.preibisch.stitcher.aws.test;

import bdv.img.awsspim.AWSSpimImageLoader;
import com.amazonaws.regions.Regions;
import com.bigdistributor.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.dataexchange.job.model.JobID;
import com.bigdistributor.dataexchange.utils.DEFAULT;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.stitcher.aws.reader.AWSSpimReader;
import org.jdom2.JDOMException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class LoadSpimFromAWS {

    public static void main(String[] args) throws SpimDataException, IllegalAccessException, JDOMException, XMLStreamException, IOException {

          // Init S3
        JobID.set(DEFAULT.bucket_name);
        AWSCredentialInstance.init(DEFAULT.AWS_CREDENTIALS_PATH);

        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, DEFAULT.id);

        // Init XML
        AWSSpimReader reader = new AWSSpimReader(S3BucketInstance.get(),"","dataset-n5.xml");

        //Init Spim

        SpimData2 data = reader.getSpim();

        System.out.println("Class: " + data.getSequenceDescription().getImgLoader().getClass());

//
        AWSSpimImageLoader loader = (AWSSpimImageLoader) data.getSequenceDescription().getImgLoader();

        RandomAccessibleInterval<UnsignedShortType> img = (RandomAccessibleInterval<UnsignedShortType>) loader.getSetupImgLoader(0).getImage(1, null);
        ImageJFunctions.show(img);

    }
}