package net.preibisch.stitcher.leap;


import com.leapmotion.leap.Controller;

import java.io.IOException;

public class Sample {
    public static void main(String[] args) {
//        if not found native lib check here
//        System.out.println(System.getProperty("java.library.path"));
        // Create a sample listener and controller

//        SampleListener listener = new SampleListener();
        MotionListener listener = new MotionListener();
        Controller controller = new Controller();

        // Have the sample listener receive events from the controller
        controller.addListener(listener);

        // Keep this process running until Enter is pressed
//        System.out.println("Press Enter to quit...");
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
//
//        // Remove the sample listener when done
//        controller.removeListener(listener);
    }
}