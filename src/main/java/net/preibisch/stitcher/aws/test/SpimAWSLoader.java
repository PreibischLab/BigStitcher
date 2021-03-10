package net.preibisch.stitcher.aws.test;

import bdv.img.aws.AWSSpimImageLoader;
import com.amazonaws.regions.Regions;
import com.bigdistributor.aws.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.aws.dataexchange.utils.AWS_DEFAULT;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.aws.reader.AWSDataParam;

public class SpimAWSLoader {
    public static void main(String[] args) throws SpimDataException {

//        String bucketname = AWS_DEFAULT.bucket_name;
        String bucketname = "bigstitcher";
        final String path = "/Users/Marwan/Desktop/dataset-n5.xml";
        AWSCredentialInstance.init(AWS_DEFAULT.AWS_CREDENTIALS_PATH);

        S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, bucketname);

        AWSDataParam.init(bucketname,"","dataset-n5.xml");
        SpimData2 data = new XmlIoSpimData2("").load(path);

        System.out.println(data.toString());


        System.out.println("Class: " + data.getSequenceDescription().getImgLoader().getClass());

//
        AWSSpimImageLoader loader = (AWSSpimImageLoader) data.getSequenceDescription().getImgLoader();

        RandomAccessibleInterval<UnsignedShortType> img = (RandomAccessibleInterval<UnsignedShortType>) loader.getSetupImgLoader(0).getImage(1, null);
        ImageJFunctions.show(img);
    }
}
