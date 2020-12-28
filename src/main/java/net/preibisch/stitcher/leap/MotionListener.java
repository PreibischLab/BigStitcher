package net.preibisch.stitcher.leap;

import com.leapmotion.leap.*;


/**
 * Motion listener for Leapmotion used for bigdataviewer
 * Right hand: translation and rotation
 * Left hand: scale
 */
public class MotionListener extends Listener {
    private static final double TRANSLATION_THRESHOLD = 0.3;
    Frame oldframe;

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
                System.out.println("Frame id: " + frame.id()
                + ", timestamp: " + frame.timestamp()
                + ", hands: " + frame.hands().count()
                + ", fingers: " + frame.fingers().count()
                + ", tools: " + frame.tools().count()
                + ", gestures " + frame.gestures().count());
        if (oldframe == null) {
            oldframe = frame;
            return;
        }

        //Get hands

        for (Hand hand : frame.hands()) {
            if (hand.isLeft()) {
                processLeftHand(oldframe, frame);
            } else {
                processRightHand(oldframe, frame);
            }
        }
        oldframe = frame;
    }

    private void processRightHand(Frame oldFrame, Frame frame) {

        float angle = frame.rotationAngle(oldFrame);
        Vector axis = frame.rotationAxis(oldFrame);
        System.out.println("rotation angle: " + angle + " axis:" + axis.toString());

        Vector translation = frame.translation(oldFrame);
        float[] normalized = threshold(translation, TRANSLATION_THRESHOLD);
        if (normalized != null)
//        System.out.println("Translation :"+translation);
            System.out.println("Translation :" + normalized[0] + "," + normalized[1] + "," + normalized[2]);


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
        float scale = frame.scaleFactor(oldFrame);

        System.out.println("Scale: " + scale);
        scale(scale);
    }

    private void scale(float scale) {
        LeapBDV.scale(scale);
    }

}


