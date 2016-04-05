package gui.popup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;

import algorithm.PairwiseStitching;
import algorithm.SpimDataTools;
import algorithm.TransformTools;
import gui.popup.StitchPairwisePopup.MyActionListener;
import ij.gui.GenericDialog;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.base.Entity;
import mpicbg.spim.data.generic.sequence.AbstractSequenceDescription;
import mpicbg.spim.data.generic.sequence.BasicViewDescription;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.Tile;
import mpicbg.spim.data.sequence.ViewId;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AbstractTranslation;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import spim.fiji.spimdata.explorer.ExplorerWindow;
import spim.fiji.spimdata.explorer.popup.ExplorerWindowSetable;

public class TestPopup extends JMenuItem implements ExplorerWindowSetable {
	
	private static final long serialVersionUID = 5234649267634013390L;
	public static boolean showWarning = true;

	ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel;

	public TestPopup() 
	{
		super( "Test..." );
		this.addActionListener( new MyActionListener() );
	}

	@Override
	public JMenuItem setExplorerWindow( final ExplorerWindow< ? extends AbstractSpimData< ? extends AbstractSequenceDescription< ?, ?, ? > >, ? > panel )
	{
		this.panel = panel;
		return this;
	}

	public class MyActionListener implements ActionListener
	{
		@Override
		public void actionPerformed( final ActionEvent e )
		{
			if ( panel == null )
			{
				IOFunctions.println( "Panel not set for " + this.getClass().getSimpleName() );
				return;
			}

			ArrayList<Tile> tileFilter = new ArrayList<>();
			tileFilter.add(new Tile(0));
			tileFilter.add(new Tile(2));
			ArrayList<Channel> channelFilter = new ArrayList<>();
			channelFilter.add(new Channel(1));
						
			Map<Class<? extends Entity>, List<? extends Entity>> filters = new HashMap<>();
			//filters.put(Tile.class, tileFilter);
			//filters.put(Channel.class, channelFilter);
			
			List<BasicViewDescription<?>> res = SpimDataTools.getFilteredViewDescriptions(panel.getSpimData().getSequenceDescription(), filters);
			
			Set<Class<? extends Entity>> groupingFactors = new HashSet<>();
			groupingFactors.add(Channel.class);			
			
			List<List<BasicViewDescription<?>>> res2 = SpimDataTools.groupByAttributes(res, groupingFactors);
			
			
			for (List<? extends BasicViewDescription<?>> vd2 : res2)
			{
				System.out.println(vd2);
				
			}
			
			
		}
	}

}
