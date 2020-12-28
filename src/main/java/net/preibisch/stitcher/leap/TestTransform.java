package net.preibisch.stitcher.leap;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.realtransform.AffineTransform3D;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class TestTransform {
    public static void main(String[] args) throws SpimDataException, InterruptedException {

        String path = "/Users/Marwan/Desktop/Task/data/hdf5/dataset.xml";
        System.setProperty("apple.laf.useScreenMenuBar", "true");

        final BigDataViewer bdv = BigDataViewer.open(path, new File(path).getName(), new ProgressWriterConsole(), ViewerOptions.options());

        AffineTransform3D currentViewerTransform = bdv.getViewer().getDisplay().getTransformEventHandler().getTransform();

        while (true) {
            TimeUnit.SECONDS.sleep(1);
            currentViewerTransform.rotate(0,0.08);
            bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);

        }
    }

}
