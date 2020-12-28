package net.preibisch.stitcher.leap;

import com.leapmotion.leap.Controller;
import mpicbg.spim.data.SpimDataException;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws SpimDataException {
        String path = "/Users/Marwan/Desktop/Task/data/hdf5/dataset.xml";
//        System.setProperty("apple.laf.useScreenMenuBar", "true");
        LeapBDV leapBDV = new LeapBDV(path);
        MotionListener listener = new MotionListener(leapBDV);
        Controller controller = new Controller();


        // Have the sample listener receive events from the controller
        controller.addListener(listener);

        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
