package net.preibisch.stitcher.aws.test;

import com.amazonaws.regions.Regions;
import com.bigdistributor.aws.dataexchange.aws.s3.func.auth.AWSCredentialInstance;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import com.bigdistributor.aws.dataexchange.utils.AWS_DEFAULT;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;

import java.io.IOException;

public class LoadN5 {
    public static void main(String[] args) throws IllegalAccessException, IOException {

//        String bucketname = AWS_DEFAULT.bucket_name;
            String bucketname = "bigstitcher";
            final String path = "data/dataset.n5";
            AWSCredentialInstance.init(AWS_DEFAULT.AWS_CREDENTIALS_PATH);

            S3BucketInstance.init(AWSCredentialInstance.get(), Regions.EU_CENTRAL_1, bucketname);

//            AWSDataParam.init(bucketname,"","dataset-n5.xml");

            N5AmazonS3Reader n5 = new N5AmazonS3Reader(S3BucketInstance.get().getS3(), S3BucketInstance.get().getBucketName(), path);

            System.out.println(n5.getVersion());
        }
    }
