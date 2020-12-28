package net.preibisch.stitcher.leap;

public interface MotionHandler {
    void scale(double scale);

    void translate(float[] vector);

    void rotate(float[] vector);

    void reset();
}
