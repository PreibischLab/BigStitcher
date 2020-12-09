package net.preibisch.stitcher.gui.aws.test;

import mpicbg.spim.data.sequence.ImgLoader;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;

//TODO load data here from AWS, Goal test load and GUI and undrestand cycle
public class TestSpim {
    public static void main(String[] args) {
//        LoadParseQueryXML lpq = new AWSLoadParseQueryXML();;

        final LoadParseQueryXML lpq = new LoadParseQueryXML();
        if (!lpq.queryXML())
            return;
        SpimData2 data = lpq.getData();

//        System.out.println(data.getBoundingBoxes().toString());

        ImgLoader il = data.getSequenceDescription().getImgLoader();
        System.out.println("Class: " + il.getClass());
//        DefaultFlatfieldCorrectionWrappedImgLoader ffcil = new DefaultFlatfieldCorrectionWrappedImgLoader(il);
//        ffcil.setDarkImage(new ViewId(0, 0), new File("/Users/david/desktop/ff.tif"));
//
//        data.getSequenceDescription().setImgLoader(ffcil);
//
//        new ImageJ();
//
//        RandomAccessibleInterval<FloatType> image = data.getSequenceDescription().getImgLoader()
//                .getSetupImgLoader(0).getFloatImage(0, false);
//
//        RandomAccessibleInterval<FloatType> downsampleBlock = MultiResolutionFlatfieldCorrectionWrappedImgLoader
//                .downsampleHDF5(image, new int[]{3, 3, 2});
//        ImageJFunctions.show(downsampleBlock, "");
    }
}
