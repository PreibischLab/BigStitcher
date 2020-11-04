package net.preibisch.stitcher.plugin;

import ij.ImageJ;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.ViewId;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.real.FloatType;
import net.preibisch.mvrecon.fiji.plugin.queryXML.LoadParseQueryXML;
import net.preibisch.mvrecon.fiji.spimdata.SpimData2;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield.DefaultFlatfieldCorrectionWrappedImgLoader;
import net.preibisch.mvrecon.fiji.spimdata.imgloaders.flatfield.MultiResolutionFlatfieldCorrectionWrappedImgLoader;
import net.preibisch.stitcher.gui.popup.aws.AWSLoadParseQueryXML;

import java.io.File;

//TODO load data here from AWS, Goal test load and GUI and undrestand cycle
public class TestSpim  {
    public static void main(String[] args) {
        LoadParseQueryXML lpq = new AWSLoadParseQueryXML();;
        lpq.queryXML();
        SpimData2 data = lpq.getData();
        System.out.println(data.getBoundingBoxes().toString());

        ImgLoader il = data.getSequenceDescription().getImgLoader();
        DefaultFlatfieldCorrectionWrappedImgLoader ffcil = new DefaultFlatfieldCorrectionWrappedImgLoader( il );
        ffcil.setDarkImage( new ViewId( 0, 0 ), new File( "/Users/david/desktop/ff.tif" ) );

        data.getSequenceDescription().setImgLoader( ffcil );

        new ImageJ();

        RandomAccessibleInterval<FloatType> image = data.getSequenceDescription().getImgLoader()
                .getSetupImgLoader( 0 ).getFloatImage( 0, false );

        RandomAccessibleInterval< FloatType > downsampleBlock = MultiResolutionFlatfieldCorrectionWrappedImgLoader
                .downsampleHDF5( image, new int[] { 3, 3, 2 } );
        ImageJFunctions.show( downsampleBlock, "" );
    }
}
