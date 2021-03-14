package net.preibisch.stitcher.aws.tools;

import com.amazonaws.regions.Regions;

import java.io.File;

public class AWSDataParam {
    private static boolean cloudMode;

    public static boolean isCloudMode() {
        return cloudMode;
    }

    public static void setCloudMode(boolean cloudMode) {
        if (cloudMode) System.out.println("AWS mode activated..");
        AWSDataParam.cloudMode = cloudMode;
    }

    private static AWSDataParam instance;
    private final String bucketName, path, xmlFile;
    private final Regions region;
    private final String[] extraFiles;
    private File localFolder;

    private AWSDataParam(String bucketName, String path, String xmlFile, Regions region, String[] extraFiles) {
        this.bucketName = bucketName;
        this.path = path;
        this.xmlFile = xmlFile;
        this.region = region;
        this.localFolder = TempFolder.get();
        this.extraFiles = extraFiles;
    }

    public static boolean isInitiated() {
        return instance != null;
    }

    public static AWSDataParam init(String bucketName, String path, String xmlFile, Regions region, String[] extraFiles) {
        instance = new AWSDataParam(bucketName, path, xmlFile, region, extraFiles);
        return instance;
    }

    public static AWSDataParam init(String bucketName, String path, String xmlFile) {
        return init(bucketName, path, xmlFile, Regions.EU_CENTRAL_1, new String[]{});
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

    public File getLocalFolder() {
        return localFolder;
    }

    public String[] getExtraFiles() {
        return extraFiles;
    }
}
