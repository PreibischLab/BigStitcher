package net.preibisch.stitcher.leap;

import com.leapmotion.leap.*;


/**
 * Motion listener for Leapmotion used for bigdataviewer
 * Right hand: translation and rotation
 * Left hand: scale
 */
public class MotionListener extends Listener {
    private static final int RESET_PERIOD = 300;
    private static final float[] TRANSLATION_THRESHOLD = new float[]{1.3f, 4.0f};
    private static final float[] ROTATION_THRESHOLD = new float[]{0.8f, 4.0f};
    private static final float[] SCALE_THRESHOLD = new float[]{0.4f, 50.8f};
    private final MotionHandler motionHandler;
    Frame oldFrame;
    int resetCounter = 0;


    private  float[] max_rotation = new float[]{0,0, 0};
    private  float[] min_rotation = new float[]{0,0, 0};

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

    private void processLeftHand(Frame oldFrame, Frame frame) {

        Vector rotationAxis = frame.rotationAxis(oldFrame);
        float[] normalizedRotation = threshold(rotationAxis, ROTATION_THRESHOLD);

        if (normalizedRotation != null) {
            System.out.println("rotation angles: " + getString(normalizedRotation));
            rotate(normalizedRotation);
        }
        double scale = frame.scaleFactor(oldFrame);
        float normalizedScale = threshold((float) scale, SCALE_THRESHOLD);
        if(normalizedScale>0){
            System.out.println("Scale: " + normalizedScale);
            scale(scale);
        }
    }

    private void processRightHand(Frame oldFrame, Frame frame) {

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
            resetCounter = 0;
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


    private float[] threshold(Vector vector, float[] threshVal) {
        float[] vals = vector.toFloatArray();
        boolean valid = false;
        for (int i = 0; i < vals.length; i++) {
            float v = threshold(vals[i], threshVal);
            vals[i] = v;
            if (v > 0) {
                valid = true;
            }
        }
        return (valid) ? vals : null;
    }

    private float threshold(float val, float[] threshVal) {
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

    private void translate(float[] vector) {
        motionHandler.translate(vector);
    }

    private void rotate(float[] vector) {
        updateminmax(vector);
        motionHandler.rotate(vector);
    }

    private void updateminmax(float[] vector) {
        for(int i =0;i<3;i++){
            if(vector[i]<min_rotation[i])
                min_rotation[i]=vector[i];
            if(vector[i]>max_rotation[i])
                max_rotation[i]=vector[i];
        }
        System.out.println("Rotation: min: "+getString(min_rotation)+"   -Max: "+getString(max_rotation));
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


