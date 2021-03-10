package net.preibisch.stitcher.aws.spimloader;

import com.amazonaws.services.s3.AmazonS3;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import mpicbg.spim.data.SpimDataException;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.stitcher.aws.tools.TempFolder;

import java.io.File;
import java.io.IOException;

public class AWSSpimLoader {

    private final AmazonS3 s3;
    private final String path;
    private final String fileName;
    private final String bucketname;
    //    private Document doc;
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

    public String getFile() {
        return fileName;
    }

    public SpimData2 getSpimdata() {
        try {
            S3BucketInstance.get().downloadFrom(TempFolder.get(), path, new String[]{fileName, "interpolations"});
            this.localFile = new File(TempFolder.get(), fileName);
            return new XmlIoSpimData2("").load(localFile.getAbsolutePath());

        } catch (IllegalAccessException | InterruptedException | SpimDataException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
