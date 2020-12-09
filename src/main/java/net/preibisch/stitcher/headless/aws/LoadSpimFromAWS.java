package net.preibisch.stitcher.headless.aws;

import bdv.img.aws.AWSSpimImageLoader;
import com.amazonaws.regions.Regions;
import com.bigdistributor.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.dataexchange.job.model.JobID;
import com.bigdistributor.dataexchange.utils.DEFAULT;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;

import java.io.File;

public class LoadSpimFromAWS {
    private static String path = "/Users/Marwan/Desktop/Task/data/n5aws/dataset-n5.xml";

    public static void main(String[] args) throws SpimDataException, IllegalAccessException {


        JobID.set(DEFAULT.id);
        AWSCredentialInstance.init(DEFAULT.AWS_CREDENTIALS_PATH);

        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, DEFAULT.id);
        SpimData2 data = new XmlIoSpimData2("").load(path);
        final AbstractSequenceDescription<?, ?, ?> seq = data.getSequenceDescription();

        AWSSpimImageLoader il = new AWSSpimImageLoader(new File("dataset.n5"), seq);
        data.getSequenceDescription().setImgLoader(il);

        System.out.println("Class: " + il.getClass());

        RandomAccessibleInterval<UnsignedShortType> img = (RandomAccessibleInterval< UnsignedShortType >) data.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 ).getImage( 1, null );
        ImageJFunctions.show( img );

    }
}