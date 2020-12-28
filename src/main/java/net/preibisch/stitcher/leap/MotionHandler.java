package net.preibisch.stitcher.leap;

public interface MotionHandler {
    void scale(double scale);

    void translate(double[] vector);

    void rotate(double[] vector);

    void reset();
}
