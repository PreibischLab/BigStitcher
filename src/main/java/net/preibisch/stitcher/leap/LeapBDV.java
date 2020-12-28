package net.preibisch.stitcher.leap;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;

import java.io.File;

public class LeapBDV implements MotionHandler {

    private final AffineTransform3D basicTransform;
    private BigDataViewer bdv;
    private AffineTransform3D currentViewerTransform;

    public LeapBDV(BigDataViewer bdv) throws SpimDataException {
        this.bdv = bdv;
        currentViewerTransform = bdv.getViewer().getDisplay().getTransformEventHandler().getTransform();
        basicTransform = currentViewerTransform.copy();
    }

    public LeapBDV(String path) throws SpimDataException {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
//        spimdata = new XmlIoSpimData2("").load(path);
        bdv = BigDataViewer.open(path, new File(path).getName(), new ProgressWriterConsole(), ViewerOptions.options());
        currentViewerTransform = bdv.getViewer().getDisplay().getTransformEventHandler().getTransform();
        basicTransform = currentViewerTransform.copy();
    }

    @Override
    public void scale(double scaleVal) {
        Scale3D scale = new Scale3D(1.0D / scaleVal, 1.0D / scaleVal, 1.0D / scaleVal);
        currentViewerTransform.preConcatenate(scale);
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

    @Override
    public void translate(double[] vector) {
        currentViewerTransform.translate(vector[0],vector[2],vector[1]);
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

    @Override
    public void rotate(double[] vector) {
        for (int i = 0; i < 3; i++)
        currentViewerTransform.rotate(i, vector[i]/300);
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

    @Override
    public void reset() {
        currentViewerTransform = basicTransform.copy();
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

}
