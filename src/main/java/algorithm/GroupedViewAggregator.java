package algorithm;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.scijava.Context;


import ij.ImageJ;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.registration.ViewTransform;
import mpicbg.spim.data.registration.ViewTransformAffine;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.SetupImgLoader;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imagej.ops.OpService;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.Interval;
import net.imglib2.Positionable;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPositionable;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import spim.process.interestpointregistration.pairwise.constellation.grouping.Group;

public class GroupedViewAggregator
{	
	private List<Action<? extends Entity>> actions;
	
	public GroupedViewAggregator()
	{
		this.actions = new ArrayList<>();
	}
		
	public class Action <E extends Entity>{
		ActionType actionType;
		Class<? extends E> entityClass;
		E instance;
		private OpService ops;
		
		Action(ActionType at, Class<? extends E> entityClass, E instance){
			this.actionType = at;
			this.entityClass = entityClass;
			this.instance = instance;
			this.ops = new Context(OpService.class).getService( OpService.class );
		}
		
		public <T extends RealType<T>> Map<BasicViewDescription<?>, RandomAccessibleInterval<T>> aggregate(
				Map<BasicViewDescription< ? >, RandomAccessibleInterval<T>> input)
		{
			Set<Class<? extends Entity>> groupingSet = new HashSet<>();
			groupingSet.add( entityClass );
			List< Group< BasicViewDescription< ? > > > grouped = 
					Group.combineBy( new ArrayList<>(input.keySet()), groupingSet );
			
			
			Map<BasicViewDescription<?>, RandomAccessibleInterval<T>> res = new HashMap<>();			
			
			for (Group< BasicViewDescription< ? > > g : grouped)
			{
				List<RandomAccessibleInterval<T>> rais = new ArrayList<>();
				for (BasicViewDescription< ? > vd : g)
					rais.add(input.get( vd ));
				
				RandomAccessibleInterval< T > resG;
				
				List< BasicViewDescription< ? > > gl = new ArrayList<>(g.getViews());
				
				if (actionType == ActionType.PICK_BRIGHTEST)
					resG = pickBrightest(gl, rais);
				else if (actionType == ActionType.PICK_SPECIFIC)
					resG = pickSpecific(gl, rais);
				else //if (actionType == ActionType.AVERAGE)
					resG = average(gl, rais);
				
				BasicViewDescription< ? > fistVD = gl.get( 0 );
				
				res.put( fistVD, resG );				
			}
						
			return res;
		}
		
		
		public <T extends RealType<T>> RandomAccessibleInterval< T > pickBrightest(List<BasicViewDescription< ? >> vds,
														   List<RandomAccessibleInterval< T >> rais)
		{
			
			if (rais.size() == 1)
				return rais.get( 0 );
			
			Double max = -Double.MAX_VALUE;
			int maxIdx = -1;
						
			for (int i = 0; i < rais.size(); i++){
				
				// a view is missing, do not take it into account
				if (rais.get( i ) == null)
					continue;
				
				Double mean = ops.stats().mean( Views.iterable( rais.get( i ) )).getRealDouble();
				if (mean > max)
				{
					max = mean;
					maxIdx = i;
				}
			}
			
			// all views were missing
			if (maxIdx < 0)
				return null;
			else
				return rais.get( maxIdx );			
		}
		
		
		public <T extends RealType<T>> RandomAccessibleInterval< T > pickSpecific(List<BasicViewDescription< ? >> vds,
				   List<RandomAccessibleInterval< T >> rais)
		{
			for (int i = 0; i< vds.size(); i++)
			{
				if (entityClass == TimePoint.class)
				{
					if (vds.get( i ).getTimePoint() == instance)
						if (vds.get( i ).isPresent())
							return rais.get( i );
					
					continue;
				}
				
				if (vds.get( i ).getViewSetup().getAttribute( entityClass ).equals( instance ))
					if (vds.get( i ).isPresent())
						return rais.get( i );
			}
			
			// this should only be reached if the requested view is not present
			return null;
		}
		
		
		public <T extends RealType<T>> RandomAccessibleInterval< T > average(List<BasicViewDescription< ? >> vds,
				   List<RandomAccessibleInterval< T >> rais)
		{
			AveragedRandomAccessible< T > avg = null;
			
			if (rais.size() == 1)
				return rais.get( 0 );
			
			int nPresent = 0;
			int firstNonNull = -1;
			
			for (int i = 0; i< rais.size(); i++)
			{
				if (rais.get( i ) == null)
					continue;
				
				if (avg == null)
				{
					avg = new AveragedRandomAccessible<>( rais.get( i ).numDimensions() );
					firstNonNull = i;
				}
					
				RandomAccessibleInterval< T > zerod = Views.zeroMin( rais.get( i ));
				avg.addRAble( Views.extendZero( zerod ) );
				nPresent++;
			}
			
			if (nPresent < 1)
				return null;
			
			RandomAccessibleInterval< T > zerod = Views.zeroMin( rais.get( firstNonNull ));
			return Views.interval( avg, zerod );
		}
		
	}
	
	public enum ActionType {
		PICK_BRIGHTEST, AVERAGE, PICK_SPECIFIC
	}
	
	public <E extends Entity> void addAction(ActionType at, Class<? extends E> entityClass, E instance)
	{
		Action<E> newAc = new Action<E>( at, entityClass, instance );
		actions.add(newAc);
	}
	
	public <T extends RealType<T>> RandomAccessibleInterval< T > aggregate(
			List<RandomAccessibleInterval< T >> rais,
			List<? extends ViewId> vids,
			AbstractSequenceDescription< ?, ? extends BasicViewDescription< ? >, ? > sd
			)
	{
		Map<BasicViewDescription< ? >, RandomAccessibleInterval<T>> map = new HashMap<>();
		
		for (int i = 0; i < vids.size(); i++)
		{
			ViewId vid = vids.get( i );
			BasicViewDescription< ? > vd = sd.getViewDescriptions().get( vid );
			map.put( vd, rais.get( i ) );
		}
		
		for (Action< ? > action : actions)
		{
			map = action.aggregate( map );
		}
		
		// return the first RAI still present
		// ideally, there should be only one left
		return map.values().iterator().next();
		
	}
	
	public <T extends RealType<T>> RandomAccessibleInterval< T > aggregate(Group<? extends ViewId> gv, 
												AbstractSequenceDescription< ?, ? extends BasicViewDescription< ? >, ? > sd,
												long[] downsampleFactors,
												final AffineTransform3D dsCorrectionT){
		
		Map<BasicViewDescription< ? >, RandomAccessibleInterval<T>> map = new HashMap<>();
		
		for (ViewId vid : gv.getViews())
		{
			
			BasicViewDescription< ? > vd = sd.getViewDescriptions().get( vid );
			
			// if view is not present, add null as the RAIProxy
			RandomAccessibleInterval< T > rai = vd.isPresent() ?
					new RAIProxy< T >(sd.getImgLoader(), vid, downsampleFactors, dsCorrectionT) : null; 
			
			map.put( vd, rai );		
		}
		
		for (Action< ? > action : actions)
		{
			map = action.aggregate( map );
		}
		
		// return the first RAI still present
		// ideally, there should be only one left
		return map.values().iterator().next();
		
	}
	
	
	public static void main(String[] args)
	{
		final OpService ops = new Context(OpService.class).getService( OpService.class );
		
		final ArrayList< ViewSetup > setups = new ArrayList< ViewSetup >();
		final ArrayList< ViewRegistration > registrations = new ArrayList< ViewRegistration >();

		final Channel c0 = new Channel( 0, "RFP" );
		final Channel c1 = new Channel( 1, "YFP" );

		final Angle a0 = new Angle( 0 );
		final Illumination i0 = new Illumination( 0 );
		final Illumination i1 = new Illumination( 1 );		

		final Tile t0 = new Tile( 0, "Tile0", new double[]{ 0.0, 0.0, 0.0 } );
		
		final Dimensions d0 = new FinalDimensions( 512l, 512l, 1l );
		final VoxelDimensions vd0 = new FinalVoxelDimensions( "px", 0.4566360, 0.4566360, 2.0000000 );

		setups.add( new ViewSetup( 0, "setup 0", d0, vd0, t0, c0, a0, i0 ) );
		setups.add( new ViewSetup( 1, "setup 1", d0, vd0, t0, c1, a0, i0 ) );
		setups.add( new ViewSetup( 2, "setup 2", d0, vd0, t0, c0, a0, i1 ) );
		setups.add( new ViewSetup( 3, "setup 3", d0, vd0, t0, c1, a0, i1 ) );

		final ArrayList< TimePoint > t = new ArrayList< TimePoint >();
		t.add( new TimePoint( 0 ) );
		final TimePoints timepoints = new TimePoints( t );
		
		final ArrayList< ViewId > missing = new ArrayList< ViewId >();
		missing.add( new ViewId(0,0) );
		final MissingViews missingViews = new MissingViews( missing );

		final ImgLoader imgLoader = new ImgLoader()
		{
			@Override
			public SetupImgLoader< ? > getSetupImgLoader( final int setupId )
			{
				return new SetupImgLoader<UnsignedShortType>()
				{

					@Override
					public RandomAccessibleInterval< UnsignedShortType > getImage(int timepointId,
							ImgLoaderHint... hints)
					{
						RandomAccessibleInterval< UnsignedShortType > rai = ops.create().img( d0, new UnsignedShortType() );
						RandomAccessibleInterval< UnsignedShortType > raiout = ops.create().img( d0, new UnsignedShortType() );
						raiout = ops.math().add( raiout, Views.iterable( rai ), new UnsignedShortType( setupId ) );
						return raiout;
						
					}

					@Override
					public UnsignedShortType getImageType(){return new UnsignedShortType();}

					@Override
					public RandomAccessibleInterval< FloatType > getFloatImage(int timepointId, boolean normalize,
							ImgLoaderHint... hints)
					{
						RandomAccessibleInterval< FloatType > rai = ops.create().img( d0, new FloatType() );
						RandomAccessibleInterval< FloatType > raiout = ops.create().img( d0, new FloatType() );
						raiout = ops.math().add( raiout, Views.iterable( rai ), new FloatType( setupId ) );
						return raiout;
					}

					@Override
					public Dimensions getImageSize(int timepointId){return d0;}
					@Override
					public VoxelDimensions getVoxelSize(int timepointId){return vd0;}
					
				};
			}
		};
		
		for ( final ViewSetup vs : setups )
		{
			final ViewRegistration vr = new ViewRegistration( t.get( 0 ).getId(), vs.getId() );
			registrations.add( vr );
		}

		final SequenceDescription sd = new SequenceDescription( timepoints, setups, imgLoader, missingViews );
		final SpimData data = new SpimData( new File( "" ), sd, new ViewRegistrations( registrations ) );
		
		
		final GroupedViewAggregator gva = new GroupedViewAggregator();
		//gva.addAction( ActionType.PICK_SPECIFIC, Illumination.class, new Illumination( 0 ) );
		//gva.addAction( ActionType.PICK_SPECIFIC, Illumination.class, new Illumination( 1 ) );
		gva.addAction( ActionType.PICK_SPECIFIC, Channel.class, c0 );
		gva.addAction( ActionType.PICK_SPECIFIC, Illumination.class, i0 );
		
		List<ViewId> setupsVID = new ArrayList<>();
		setupsVID.add( new ViewId(0,0) );
		setupsVID.add( new ViewId(0,1) );
		setupsVID.add( new ViewId(0,2) );
		setupsVID.add( new ViewId(0,3) );
		Group<ViewId> gv = new Group<>( setupsVID );
		
		
		
		gva.aggregate( gv, sd, new long[] {1,1,1} , new AffineTransform3D());
		
		ImageJFunctions.show( (RandomAccessibleInterval< FloatType >)gva.aggregate( gv, sd , new long[] {1,1,1}, new AffineTransform3D()));
		
		new ImageJ();
		
	}
	

}
