package net.preibisch.stitcher.leap;

import com.leapmotion.leap.*;


/**
 * Motion listener for Leapmotion used for bigdataviewer
 * Right hand: translation and rotation
 * Left hand: scale
 */
public class MotionListener extends Listener {
    private static final int RESET_PERIOD = 200;
    private static final double[] TRANSLATION_THRESHOLD = new double[]{1.3f, 4.0f};
    private static final double[] ROTATION_THRESHOLD = new double[]{0.8f, 4.0f};
    private static final double[] SCALE_THRESHOLD = new double[]{0.4f, 50.8f};
    private final MotionHandler motionHandler;
    Frame oldFrame;
    int resetCounter = 0;

    public void onFrame(Controller controller) {
        Frame frame = controller.frame();
        if (oldFrame == null) {
            oldFrame = frame;
            return;
        }
        for (Hand hand : frame.hands()) {
            if (hand.isLeft())
                processLeftHand(oldFrame, frame);
            else
                processRightHand(oldFrame, frame);
        }
        oldFrame = frame;
    }

    private void processLeftHand(Frame oldFrame, Frame frame) {

        Vector rotationAxis = frame.rotationAxis(oldFrame);
        double[] normalizedRotation = threshold(rotationAxis, ROTATION_THRESHOLD);

        if (normalizedRotation != null) {
            System.out.println("rotation angles: " + getString(normalizedRotation));
            rotate(normalizedRotation);
        }
        double scale = frame.scaleFactor(oldFrame);
        double normalizedScale = threshold((float) scale, SCALE_THRESHOLD);
        if (normalizedScale > 0) {
            System.out.println("Scale: " + normalizedScale);
            scale(scale);
        }
    }

    private void processRightHand(Frame oldFrame, Frame frame) {

        Vector translation = frame.translation(oldFrame);
        double[] normalizedTranslation = threshold(translation, TRANSLATION_THRESHOLD);

        if (normalizedTranslation != null) {
            resetCounter = 0;
            System.out.println("Translations : " + getString(normalizedTranslation));
            translate(normalizedTranslation);
        } else {
            resetCounter += 1;
        }
        if (resetCounter > RESET_PERIOD) {
            resetCounter = 0;
            reset();
        }

    }

    private void reset() {
        System.out.println("Reset");
        motionHandler.reset();
    }

    private String getString(double[] list) {
        String s = "";
        for (double l : list)
            s += (l + ",");
        return s;
    }


    private double[] threshold(Vector vector, double[] threshVal) {
        float[] vals = vector.toFloatArray();
        double[] result = new double[]{0, 0, 0};
        boolean valid = false;
        for (int i = 0; i < vals.length; i++) {
            double v = threshold(vals[i], threshVal);
            result[i] = v;
            if (v != 0) {
                valid = true;
            }
        }
        return (valid) ? result : null;
    }

    private double threshold(double val, double[] threshVal) {
        if ((val > (-threshVal[0])) && (val < threshVal[0])) {
            return 0;
        } else if ((val < (-threshVal[1])) || (val > threshVal[1])) {
            return 0;
        } else {
            return val;
        }
    }

    private void scale(double scale) {
        motionHandler.scale(scale);
    }

    private void translate(double[] vector) {
        motionHandler.translate(vector);
    }

    private void rotate(double[] vector) {
        motionHandler.rotate(vector);
    }


    public MotionListener(MotionHandler handler) {
        this.motionHandler = handler;
    }

    public void onInit(Controller controller) {
        System.out.println("Initialized");
    }

    public void onConnect(Controller controller) {
        System.out.println("Connected");
    }

    public void onDisconnect(Controller controller) {
        System.out.println("Disconnected");
    }

    public void onExit(Controller controller) {
        System.out.println("Exited");
    }
}


