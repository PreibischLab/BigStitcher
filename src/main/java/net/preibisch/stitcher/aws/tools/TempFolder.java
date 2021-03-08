package net.preibisch.stitcher.aws.tools;

import com.google.common.io.Files;

import java.io.File;

public class TempFolder {
    private File dir;
    private static TempFolder instance;

    private TempFolder(File dir) {
        this.dir = dir;
    }

    public File getDir() {
        return dir;
    }

    public static File get() {
        if (instance == null) {
            System.out.println("Setting tmp dir..");
            File folder = Files.createTempDir();
            instance = new TempFolder(folder);
            System.out.println("tmp Dir: " + folder.getAbsolutePath());

        }
        return instance.getDir();
    }
}
