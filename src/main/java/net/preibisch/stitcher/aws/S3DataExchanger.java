package net.preibisch.stitcher.aws;

import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import net.preibisch.stitcher.aws.reader.AWSDataParam;

import java.io.File;
import java.io.IOException;

public class S3DataExchanger {
    public static void send(File file) throws IllegalAccessException, IOException, InterruptedException {

        System.out.println("Uploading " + file);
        for (File f : file.listFiles())
            S3BucketInstance.get().upload(f, AWSDataParam.get().getPath());
    }
}
