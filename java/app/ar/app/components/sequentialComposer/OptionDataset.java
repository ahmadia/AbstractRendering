package ar.app.components.sequentialComposer;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.Arrays;
import java.util.List;

import ar.Glyphset;
import ar.glyphsets.MemMapList;
import ar.glyphsets.SyntheticGlyphset;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.glyphsets.implicitgeometry.Shaper;
import ar.glyphsets.implicitgeometry.Valuer;
import ar.glyphsets.implicitgeometry.Indexed.ToValue;
import ar.glyphsets.implicitgeometry.Valuer.Binary;
import ar.rules.CategoricalCounts;
import ar.util.Util;

public final class OptionDataset<G,I> {	
	public final String name;
	public final Glyphset<G,I> glyphset;
	public final File sourceFile;
	public final Shaper<Indexed, G> shaper;
	public final Valuer<Indexed, I> valuer;
	public final OptionAggregator<? super I,?> defaultAggregator;
	public final List<OptionTransfer<?>> defaultTransfers;
	
	public OptionDataset(
			String name, File file, 
			Shaper<Indexed, G> shaper, Valuer<Indexed,I> valuer, 
			OptionAggregator<? super I,?> defAgg,
			OptionTransfer<?>... defTrans) {
		this(name, new MemMapList<>(file, shaper, valuer), file, shaper, valuer, defAgg, defTrans);
	}
	
	public OptionDataset(
			String name, 
			Glyphset<G,I> glyphset,
			OptionAggregator<? super I,?> defAgg,
			OptionTransfer<?>... defTrans) {
		this(name, glyphset, null, null, null, defAgg, defTrans);
	}
	
	private OptionDataset(
			String name, 
			Glyphset<G,I> glyphset,
			File file, Shaper<Indexed,G> shaper, Valuer<Indexed,I> valuer, 
			OptionAggregator<? super I,?> defAgg,
			OptionTransfer<?>... defTrans) {
		this.name = name;
		this.sourceFile = file;
		this.shaper = shaper;
		this.valuer = valuer;
		this.glyphset = glyphset;
		this.defaultAggregator = defAgg;
		this.defaultTransfers = Arrays.asList(defTrans);
	
	}
	
	public String toString() {return name;}
	public OptionAggregator<? super I,?> defaultAggregator() {return defaultAggregator;}
	public List<OptionTransfer<?>> defaultTransfers() {return defaultTransfers;}


//	public static final OptionDataset<Point2D, Integer> WIKIPEDIA_TXT;
//	static {
//		OptionDataset<Point2D, Integer> temp;
//		try {
//			temp = new OptionDataset<>(
//				"Wikipedia BFS adjacnecy (Commons txt)",
//				new DelimitedFile<>(
//						new File("../data/wiki.full.txt"), ',', new Converter.TYPE[]{Converter.TYPE.LONG,Converter.TYPE.LONG, Converter.TYPE.COLOR}, 
//						new Indexed.ToPoint(false, 0,1), new Valuer.Constant<Indexed,Integer>(1)),
//				OptionAggregator.COUNT,
//				new OptionTransfer.MathTransfer(),
//				new OptionTransfer.Interpolate());
//		} catch (Exception e) {temp = null;}
//		WIKIPEDIA_TXT = temp;
//	}
	
	public static final OptionDataset<Point2D, String> BOOST_MEMORY;
	static {
		OptionDataset<Point2D, String> temp;
		try {
			temp = new OptionDataset<> (
					"BGL Memory", 
					new File("../data/MemVisScaled.hbin"), 
					new Indexed.ToPoint(true, 0, 1),
					new ToValue<>(2, new Binary<Integer,String>(0, "Hit", "Miss")),
					OptionAggregator.COC_COMP,
					new OptionTransfer.ColorKey(),
					new OptionTransfer.ColorCatInterpolate());
		} catch (Exception e) {temp = null;}
		BOOST_MEMORY = temp;
	}
	
	public static final OptionDataset<Point2D, CategoricalCounts<String>> CENSUS_TRACTS;
	static {
		OptionDataset<Point2D, CategoricalCounts<String>>  temp;
		try {
			temp = new OptionDataset<>(
				"US Census Tracts", 
				new File("../data/2010Census_RaceTract.hbin"), 
				new Indexed.ToPoint(true, 0, 1),
				new Valuer.CategoryCount<>(new Util.ComparableComparator<String>(), 3,2),
				OptionAggregator.MERGE_CATS,
				new OptionTransfer.Spread(),
				new OptionTransfer.ToCount(),
				new OptionTransfer.MathTransfer(),
				new OptionTransfer.Interpolate());
		} catch (Exception e) {temp = null;}
		CENSUS_TRACTS = temp;
	}

	
	public static final OptionDataset<Point2D, Character> CENSUS_SYN_PEOPLE;
	static {
		OptionDataset<Point2D, Character> temp;
		try {
			temp = new OptionDataset<>(
				"US Census Synthetic People", 
				new File("../data/2010Census_RacePersonPoints.hbin"), 
				new Indexed.ToPoint(true, 0, 1),
				new Indexed.ToValue<Indexed,Character>(2),
				OptionAggregator.COC_COMP,
				new OptionTransfer.ColorKey(),
				new OptionTransfer.ColorCatInterpolate());
		} catch (Exception e) {temp = null;}
		CENSUS_SYN_PEOPLE = temp;
	}
	

	public static final OptionDataset<Point2D, Character> CENSUS_NY_SYN_PEOPLE;
	static {
		OptionDataset<Point2D, Character> temp;
		try {
			temp = new OptionDataset<>(
				"US Census Synthetic People (NY)", 
				new File("../data/2010Census_RacePersonPoints_NY.hbin"), 
				new Indexed.ToPoint(true, 0, 1),
				new Indexed.ToValue<Indexed,Character>(2),
				OptionAggregator.COC_COMP,
				new OptionTransfer.ColorKey(),
				new OptionTransfer.ColorCatInterpolate());
		} catch (Exception e) {temp = null;}
		CENSUS_NY_SYN_PEOPLE = temp;
	}
	
	public static final OptionDataset<Point2D, Color> WIKIPEDIA;
	static {
		OptionDataset<Point2D, Color> temp;
		try {
			temp = new OptionDataset<>(
				"Wikipedia BFS adjacnecy", 
				new File("../data/wiki-adj.hbin"), 
				new Indexed.ToPoint(false, 0, 1),
				new Valuer.Constant<Indexed, Color>(Color.RED),
				OptionAggregator.COUNT,
				new OptionTransfer.MathTransfer(),
			new OptionTransfer.Interpolate());
		} catch (Exception e) {temp = null;}
		WIKIPEDIA = temp;
	}
	
	public static final OptionDataset<Point2D, Color> KIVA;
	static {
		OptionDataset<Point2D, Color> temp;
		try {
			temp = new OptionDataset<>(
				"Kiva", 
				new File("../data/kiva-adj.hbin"),
				new Indexed.ToPoint(false, 0, 1),
				new Valuer.Constant<Indexed, Color>(Color.RED),
				OptionAggregator.COUNT,
				new OptionTransfer.MathTransfer(),
				new OptionTransfer.Interpolate());
		} catch (Exception e) {temp = null;}
		KIVA = temp;
	}
	
//	public static final OptionDataset<Rectangle2D, Color> CIRCLE_SCATTER;
//	static {
//		OptionDataset<Rectangle2D, Color> temp;
//		try {
//			temp = new OptionDataset<>(
//			"Circle Scatter",
//			GlyphsetUtils.autoLoad(new File("../data/circlepoints.csv"), .1, DynamicQuadTree.<Rectangle2D, Color>make()),
//			OptionAggregator.COUNT,
//			new OptionTransfer.Interpolate());
//		} catch (Exception e) {temp = null;}
//		CIRCLE_SCATTER = temp;
//	}
	
	
	public static final OptionDataset<Rectangle2D, Integer> CIRCLE_SCATTER;
	static {
		OptionDataset<Rectangle2D, Integer> temp;
		try {
			temp = new OptionDataset<>(
			"Circle Scatter (HBIN)",
			new File("../data/circlepoints.hbin"),
			new Indexed.ToRect(.1,0,1),
			new Valuer.Constant<Indexed, Integer>(1),
			OptionAggregator.COUNT,
			new OptionTransfer.Interpolate());
		} catch (Exception e) {
			e.printStackTrace();
			temp = null;}
		CIRCLE_SCATTER = temp;
	}

	
	private static int SYNTHETIC_POINT_COUNT = 100_000_000;
	public static  OptionDataset<Point2D, Integer> SYNTHETIC = syntheticPoints(SYNTHETIC_POINT_COUNT);
	public static OptionDataset<Point2D, Integer> syntheticPoints(int size) {
		return new OptionDataset<>(
				String.format("Synthetic Points (%,d points)", size),
				new SyntheticGlyphset<>(size, new SyntheticGlyphset.UniformPoints(), c->0),
				OptionAggregator.COUNT,
				new OptionTransfer.Interpolate());
	}

}