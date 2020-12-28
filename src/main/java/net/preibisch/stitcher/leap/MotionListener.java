package net.preibisch.stitcher.leap;

import com.leapmotion.leap.*;


/**
 * Motion listener for Leapmotion used for bigdataviewer
 * Right hand: translation and rotation
 * Left hand: scale
 */
public class MotionListener extends Listener {
    private static final int RESET_PERIOD = 300;
    private static final double TRANSLATION_THRESHOLD = 1.3;
    private static final double ROTATION_THRESHOLD = 0.98;
    private static final double SCALE_THRESHOLD = 0.4;
    private final MotionHandler motionHandler;
    Frame oldFrame;
    int resetCounter = 0;

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
        //Note: not dispatched when running in a debugger.
        System.out.println("Disconnected");
    }

    public void onExit(Controller controller) {
        System.out.println("Exited");
    }

    public void onFrame(Controller controller) {
        Frame frame = controller.frame();
        if (oldFrame == null) {
            oldFrame = frame;
            return;
        }
        for (Hand hand : frame.hands()) {
            if (hand.isLeft()) {
                processLeftHand(oldFrame, frame);
            } else {
                processRightHand(oldFrame, frame);
            }
        }
        oldFrame = frame;
    }

    private void processRightHand(Frame oldFrame, Frame frame) {

//        Vector rotationAxis = frame.rotationAxis(oldFrame);
//        float[] normalizedRotation = threshold(rotationAxis, ROTATION_THRESHOLD);
//
//        if (normalizedRotation != null) {
//            System.out.println("rotation angles: " + getString(normalizedRotation));
//            rotate(normalizedRotation);
//        }

        Vector translation = frame.translation(oldFrame);
        float[] normalizedTranslation = threshold(translation, TRANSLATION_THRESHOLD);

        if (normalizedTranslation != null) {
            resetCounter = 0;
            System.out.println("Translations : " + getString(normalizedTranslation));
            translate(normalizedTranslation);
        } else {
            resetCounter += 1;
        }
        if (resetCounter > RESET_PERIOD) {
            resetCounter=0;
            reset();
        }

    }

    private void reset() {
        System.out.println("Reset");
        motionHandler.reset();
    }

    private String getString(float[] list) {
        String s = "";
        for (float l : list)
            s += (l + ",");
        return s;
    }


    private float[] threshold(Vector vector, double threshVal) {
        float[] vals = vector.toFloatArray();
        boolean valid = false;
        for (int i = 0; i < vals.length; i++) {
            if ((vals[i] > (-threshVal)) && (vals[i] < threshVal)) {
                vals[i] = 0;
            } else {
                valid = true;
            }
        }
        return (valid) ? vals : null;
    }

    private void processLeftHand(Frame oldFrame, Frame frame) {
        double scale = frame.scaleFactor(oldFrame);
        if ((scale > SCALE_THRESHOLD) || (scale < -SCALE_THRESHOLD)) {
            System.out.println("Scale: " + scale);
            scale(scale);
        }
    }

    private void scale(double scale) {
        motionHandler.scale(scale);
    }

    private void translate(float[] vector) {
        motionHandler.translate(vector);
    }

    private void rotate(float[] vector) {
        motionHandler.rotate(vector);
    }
}


