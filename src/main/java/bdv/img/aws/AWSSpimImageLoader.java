package bdv.img.aws;

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.SimpleCacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.util.ConstantRandomAccessible;
import bdv.util.MipmapTransforms;
import com.bigdistributor.aws.dataexchange.aws.s3.func.bucket.S3BucketInstance;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.sequence.MultiResolutionImgLoader;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.*;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.basictypeaccess.volatiles.array.*;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.CellImg;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.*;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.*;
import net.imglib2.util.Cast;
import net.imglib2.view.Views;
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.s3.N5AmazonS3Reader;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static bdv.img.n5.BdvN5Format.*;

public class AWSSpimImageLoader implements ViewerImgLoader, MultiResolutionImgLoader
{
    private final File n5File;

    // TODO: it would be good if this would not be needed
    //       find available setups from the n5
    private final AbstractSequenceDescription< ?, ?, ? > seq;

    /**
     * Maps setup id to {@link SetupImgLoader}.
     */
    private final Map< Integer, SetupImgLoader> setupImgLoaders = new HashMap<>();

    public AWSSpimImageLoader( final File n5File, final AbstractSequenceDescription< ?, ?, ? > sequenceDescription )
    {
        this.n5File = n5File;
        this.seq = sequenceDescription;
    }



    public File getN5File()
    {
        return n5File;
    }

    private volatile boolean isOpen = false;
    private FetcherThreads fetchers;
    private VolatileGlobalCellCache cache;
    private N5Reader n5;

    private void open()
    {
        if ( !isOpen )
        {
            synchronized ( this )
            {
                if ( isOpen )
                    return;

                try
                {
                    System.out.println("N5 file = "+n5File.getName());
                    this.n5 = new N5AmazonS3Reader(S3BucketInstance.get().getS3(), S3BucketInstance.get().getBucketName(),n5File.getName());

//                    this.n5 = new N5FSReader( n5File.getAbsolutePath() );

                    int maxNumLevels = 0;
                    final List< ? extends BasicViewSetup> setups = seq.getViewSetupsOrdered();
                    for ( final BasicViewSetup setup : setups )
                    {
                        final int setupId = setup.getId();
                        final SetupImgLoader setupImgLoader = createSetupImgLoader( setupId );
                        setupImgLoaders.put( setupId, setupImgLoader );
                        maxNumLevels = Math.max( maxNumLevels, setupImgLoader.numMipmapLevels() );
                    }

                    final int numFetcherThreads = Math.max( 1, Runtime.getRuntime().availableProcessors() );
                    final BlockingFetchQueues<Callable< ? >> queue = new BlockingFetchQueues<>( maxNumLevels, numFetcherThreads );
                    fetchers = new FetcherThreads( queue, numFetcherThreads );
                    cache = new VolatileGlobalCellCache( queue );
                }
                catch (IOException | IllegalAccessException e )
                {
                    throw new RuntimeException( e );
                }

                isOpen = true;
            }
        }
    }

    /**
     * Clear the cache. Images that were obtained from
     * this loader before {@link #close()} will stop working. Requesting images
     * after {@link #close()} will cause the n5 to be reopened (with a
     * new cache).
     */
    public void close()
    {
        if ( isOpen )
        {
            synchronized ( this )
            {
                if ( !isOpen )
                    return;
                fetchers.shutdown();
                cache.clearCache();
                isOpen = false;
            }
        }
    }

    @Override
    public SetupImgLoader getSetupImgLoader(final int setupId )
    {
        open();
        return setupImgLoaders.get( setupId );
    }

    private < T extends NativeType< T >, V extends Volatile< T > & NativeType< V > > SetupImgLoader< T, V > createSetupImgLoader(final int setupId ) throws IOException
    {
        final String pathName = getPathName( setupId );
        final DataType dataType = n5.getAttribute( pathName, DATA_TYPE_KEY, DataType.class );
        switch ( dataType )
        {
            case UINT8:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new UnsignedByteType(), new VolatileUnsignedByteType() ) );
            case UINT16:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new UnsignedShortType(), new VolatileUnsignedShortType() ) );
            case UINT32:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new UnsignedIntType(), new VolatileUnsignedIntType() ) );
            case UINT64:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new UnsignedLongType(), new VolatileUnsignedLongType() ) );
            case INT8:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new ByteType(), new VolatileByteType() ) );
            case INT16:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new ShortType(), new VolatileShortType() ) );
            case INT32:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new IntType(), new VolatileIntType() ) );
            case INT64:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new LongType(), new VolatileLongType() ) );
            case FLOAT32:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new FloatType(), new VolatileFloatType() ) );
            case FLOAT64:
                return Cast.unchecked( new SetupImgLoader<>( setupId, new DoubleType(), new VolatileDoubleType() ) );
        }
        return null;
    }

    @Override
    public CacheControl getCacheControl()
    {
        open();
        return cache;
    }

    public class SetupImgLoader< T extends NativeType< T >, V extends Volatile< T > & NativeType< V > >
            extends AbstractViewerSetupImgLoader< T, V >
            implements MultiResolutionSetupImgLoader< T >
    {
        private final int setupId;

        private final double[][] mipmapResolutions;

        private final AffineTransform3D[] mipmapTransforms;

        public SetupImgLoader( final int setupId, final T type, final V volatileType ) throws IOException
        {
            super( type, volatileType );
            this.setupId = setupId;
            final String pathName = getPathName( setupId );
            mipmapResolutions = n5.getAttribute( pathName, DOWNSAMPLING_FACTORS_KEY, double[][].class );
            mipmapTransforms = new AffineTransform3D[ mipmapResolutions.length ];
            for ( int level = 0; level < mipmapResolutions.length; level++ )
                mipmapTransforms[ level ] = MipmapTransforms.getMipmapTransformDefault( mipmapResolutions[ level ] );
        }

        @Override
        public RandomAccessibleInterval< V > getVolatileImage(final int timepointId, final int level, final ImgLoaderHint... hints )
        {
            return prepareCachedImage( timepointId, level, LoadingStrategy.BUDGETED, volatileType );
        }

        @Override
        public RandomAccessibleInterval< T > getImage( final int timepointId, final int level, final ImgLoaderHint... hints )
        {
            return prepareCachedImage( timepointId, level, LoadingStrategy.BLOCKING, type );
        }

        @Override
        public Dimensions getImageSize(final int timepointId, final int level )
        {
            try
            {
                final String pathName = getPathName( setupId, timepointId, level );
                final DatasetAttributes attributes = n5.getDatasetAttributes( pathName );
                return new FinalDimensions( attributes.getDimensions() );
            }
            catch( Exception e )
            {
                return null;
            }
        }

        @Override
        public double[][] getMipmapResolutions()
        {
            return mipmapResolutions;
        }

        @Override
        public AffineTransform3D[] getMipmapTransforms()
        {
            return mipmapTransforms;
        }

        @Override
        public int numMipmapLevels()
        {
            return mipmapResolutions.length;
        }

        @Override
        public VoxelDimensions getVoxelSize(final int timepointId )
        {
            return null;
        }

        /**
         * Create a {@link CellImg} backed by the cache.
         */
        private < T extends NativeType< T > > RandomAccessibleInterval< T > prepareCachedImage( final int timepointId, final int level, final LoadingStrategy loadingStrategy, final T type )
        {
            try
            {
                final String pathName = getPathName( setupId, timepointId, level );
                final DatasetAttributes attributes = n5.getDatasetAttributes( pathName );
                final long[] dimensions = attributes.getDimensions();
                final int[] cellDimensions = attributes.getBlockSize();
                final CellGrid grid = new CellGrid( dimensions, cellDimensions );

                final int priority = numMipmapLevels() - 1 - level;
                final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );

                final SimpleCacheArrayLoader< ? > loader = createCacheArrayLoader( n5, pathName );
                return cache.createImg( grid, timepointId, setupId, level, cacheHints, loader, type );
            }
            catch ( IOException e )
            {
                System.err.println( String.format(
                        "image data for timepoint %d setup %d level %d could not be found.",
                        timepointId, setupId, level ) );
                return Views.interval(
                        new ConstantRandomAccessible<>( type.createVariable(), 3 ),
                        new FinalInterval( 1, 1, 1 ) );
            }
        }
    }

    private static class N5CacheArrayLoader< A > implements SimpleCacheArrayLoader< A >
    {
        private final N5Reader n5;
        private final String pathName;
        private final DatasetAttributes attributes;
        private final Function< DataBlock< ? >, A > createArray;

        N5CacheArrayLoader( final N5Reader n5, final String pathName, final DatasetAttributes attributes, final Function< DataBlock< ? >, A > createArray )
        {
            this.n5 = n5;
            this.pathName = pathName;
            this.attributes = attributes;
            this.createArray = createArray;
        }

        @Override
        public A loadArray( final long[] gridPosition ) throws IOException
        {
            final DataBlock< ? > dataBlock = n5.readBlock( pathName, attributes, gridPosition );

            if ( dataBlock == null )
                return createEmptyArray( gridPosition );
            else
                return createArray.apply( dataBlock );
        }

        private A createEmptyArray( long[] gridPosition )
        {
            final int[] blockSize = attributes.getBlockSize();
            final int n = blockSize[ 0 ] * blockSize[ 1 ] * blockSize[ 2 ];
            switch ( attributes.getDataType() )
            {
                case UINT8:
                case INT8:
                    return createArray.apply( new ByteArrayDataBlock( blockSize, gridPosition, new byte[ n ] ) );
                case UINT16:
                case INT16:
                    return createArray.apply( new ShortArrayDataBlock( blockSize, gridPosition, new short[ n ] ) );
                case UINT32:
                case INT32:
                    return createArray.apply( new IntArrayDataBlock( blockSize, gridPosition, new int[ n ] ) );
                case UINT64:
                case INT64:
                    return createArray.apply( new LongArrayDataBlock( blockSize, gridPosition, new long[ n ] ) );
                case FLOAT32:
                    return createArray.apply( new FloatArrayDataBlock( blockSize, gridPosition, new float[ n ] ) );
                case FLOAT64:
                    return createArray.apply( new DoubleArrayDataBlock( blockSize, gridPosition, new double[ n ] ) );
                default:
                    throw new UnsupportedOperationException("Data type not supported: " + attributes.getDataType());
            }
        }
    }

    public static SimpleCacheArrayLoader< ? > createCacheArrayLoader(final N5Reader n5, final String pathName ) throws IOException
    {
        final DatasetAttributes attributes = n5.getDatasetAttributes( pathName );
        switch ( attributes.getDataType() )
        {
            case UINT8:
            case INT8:
                return new N5CacheArrayLoader<>( n5, pathName, attributes,
                        dataBlock -> new VolatileByteArray( Cast.unchecked( dataBlock.getData() ), true ) );
            case UINT16:
            case INT16:
                return new N5CacheArrayLoader<>( n5, pathName, attributes,
                        dataBlock -> new VolatileShortArray( Cast.unchecked( dataBlock.getData() ), true ) );
            case UINT32:
            case INT32:
                return new N5CacheArrayLoader<>( n5, pathName, attributes,
                        dataBlock -> new VolatileIntArray( Cast.unchecked( dataBlock.getData() ), true ) );
            case UINT64:
            case INT64:
                return new N5CacheArrayLoader<>( n5, pathName, attributes,
                        dataBlock -> new VolatileLongArray( Cast.unchecked( dataBlock.getData() ), true ) );
            case FLOAT32:
                return new N5CacheArrayLoader<>( n5, pathName, attributes,
                        dataBlock -> new VolatileFloatArray( Cast.unchecked( dataBlock.getData() ), true ) );
            case FLOAT64:
                return new N5CacheArrayLoader<>( n5, pathName, attributes,
                        dataBlock -> new VolatileDoubleArray( Cast.unchecked( dataBlock.getData() ), true ) );
            default:
                throw new IllegalArgumentException();
        }
    }
}