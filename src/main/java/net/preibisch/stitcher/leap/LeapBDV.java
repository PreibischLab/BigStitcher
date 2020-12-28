package net.preibisch.stitcher.leap;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.viewer.ViewerOptions;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import net.imglib2.Dimensions;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.realtransform.Scale3D;
import net.preibisch.legacy.io.IOFunctions;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.XmlIoSpimData2;
import net.preibisch.mvrecon.fiji.spimdata.boundingbox.BoundingBox;

import java.io.File;
import java.util.HashMap;

import static net.preibisch.legacy.io.IOFunctions.printRealInterval;

public class LeapBDV implements MotionHandler {

    private final AffineTransform3D basicTransform;
    private BigDataViewer bdv;
    private SpimData2 spimdata;
    private AffineTransform3D currentViewerTransform;

    public LeapBDV(String path) throws SpimDataException {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        spimdata = new XmlIoSpimData2("").load(path);
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
    public void translate(float[] vector) {
        currentViewerTransform.translate(vector[0],vector[1],vector[2]);
        ;
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

    @Override
    public void rotate(float[] vector) {
        for (int i = 0; i < 3; i++)
            currentViewerTransform.rotate(i, vector[i]);
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

    @Override
    public void reset() {
        currentViewerTransform = basicTransform.copy();
        bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);
    }

    public void recenter(){
        {
            AffineTransform3D currentViewerTransform = ((AffineTransform3D) bdv.getViewer().getDisplay().getTransformEventHandler().getTransform()).copy();
            int cX = bdv.getViewer().getWidth() / 2;
            int cY = bdv.getViewer().getHeight() / 2;
            IOFunctions.println(bdv.getViewer().getWidth() + " " + bdv.getViewer().getHeight());
            HashMap<BasicViewDescription<?>, Dimensions> dimensions = new HashMap();
            HashMap<BasicViewDescription<?>, AffineTransform3D> registrations = new HashMap();

            BoundingBox bb = spimdata.getBoundingBoxes().getBoundingBoxes().get(0);
            double[] com = new double[]{(double) ((bb.max(0) - bb.min(0)) / 2L + bb.min(0)), (double) ((bb.max(1) - bb.min(1)) / 2L + bb.min(1)), (double) ((bb.max(2) - bb.min(2)) / 2L + bb.min(2))};
            RealInterval bounds = currentViewerTransform.estimateBounds(bb);
            IOFunctions.println(printRealInterval(bounds));
            double currentScale = Math.max((bounds.realMax(0) - bounds.realMin(0)) / (double) bdv.getViewer().getWidth(), (bounds.realMax(1) - bounds.realMin(1)) / (double) bdv.getViewer().getHeight());
            Scale3D scale = new Scale3D(1.0D / currentScale, 1.0D / currentScale, 1.0D / currentScale);
            double oldZ = currentViewerTransform.get(2, 3);
            currentViewerTransform.set(0.0D, 0, 3);
            currentViewerTransform.set(0.0D, 1, 3);
            currentViewerTransform.set(0.0D, 2, 3);
            currentViewerTransform.preConcatenate(scale);
            currentViewerTransform.apply(com, com);
            currentViewerTransform.set(-com[0] + (double) cX, 0, 3);
            currentViewerTransform.set(-com[1] + (double) cY, 1, 3);

            boolean allViews2D = false;

            IOFunctions.println("All views 2d: " + allViews2D);
            if (allViews2D) {
                currentViewerTransform.set(oldZ * scale.getScale(2), 2, 3);
            } else {
                currentViewerTransform.set(-com[2], 2, 3);
            }

            bdv.getViewer().setCurrentViewerTransform(currentViewerTransform);

        }
    }

}
