package net.preibisch.stitcher.aws.reader;

import com.amazonaws.regions.Regions;

public class AWSDataParam {
    private static AWSDataParam instance;
    private final String bucketName, path, xmlFile;
    private final Regions region;

    private AWSDataParam(String bucketName, String path, String xmlFile, Regions region) {
        this.bucketName = bucketName;
        this.path = path;
        this.xmlFile = xmlFile;
        this.region = region;
    }

    public static AWSDataParam init(String bucketName, String path, String xmlFile, Regions region) {
        instance = new AWSDataParam(bucketName, path, xmlFile, region);
        return instance;
    }

    public static AWSDataParam init(String bucketName, String path, String xmlFile) {
        return init(bucketName, path, xmlFile, Regions.EU_CENTRAL_1);
    }

    public static AWSDataParam get() {
        return instance;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getPath() {
        return path;
    }

    public String getXmlFile() {
        return xmlFile;
    }

    public Regions getRegion() {
        return region;
    }
}
