package ar.ext.spark;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.hadoop.io.LongWritable;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;

import scala.Tuple2;
import ar.Aggregates;
import ar.Aggregator;
import ar.Selector;
import ar.Transfer;
import ar.app.components.sequentialComposer.OptionDataset;
import ar.app.components.sequentialComposer.OptionTransfer;
import ar.app.display.TransferDisplay;
import ar.ext.spark.hbin.DataInputRecord;
import ar.ext.spark.hbin.HBINInputFormat;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.selectors.TouchesPixel;
import ar.util.AggregatesToCSV;
import ar.util.Util;



/**Main class for driving an ARSpark application.**/
public class SparkDemoApp {
	private static String arg(String[] args, String flag, String def) {
		flag = flag.toUpperCase();
		for (int i=0; i<args.length; i++) {
			if (args[i].toUpperCase().equals(flag)) {return args[i+1];}
		}
		return def;
	}
	
	private static String configList() {
		try {
			StringBuilder b = new StringBuilder();
			for(Field f: OptionDataset.class.getFields()) {
				
				if (!Modifier.isStatic(f.getModifiers())) {continue;}
				
				Object value = f.get(null);
				if (value != null 
						&& value instanceof OptionDataset
						&& ((OptionDataset<?,?>) value).sourceFile != null) {
					
					b.append(f.getName());
					b.append(", ");
				}
			}
			b.delete(b.length()-2, b.length());
			return b.toString();
		} catch (Exception e) {return "Error generating config list: " + e.getMessage();}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <G,I,A> void main(String[] args) throws IOException{
		if (args.length >0) {
			String first = args[0].toLowerCase();
			if (first.equals("-h") || first.equals("-help") || first.equals("--help")) {
				System.err.println("Parameters: -server <server> -config <config> -out <out> -spark <spark-home> -jars <jar:jar...> -partitions <true|false>");
				System.err.println("Parameters are order independent and all have reasonable defaults.");
				System.err.println("Valid configurations are: " + configList());
				System.exit(1);
			}
		}
		
		int width = Integer.parseInt(arg(args, "-width", "500"));
		int height = Integer.parseInt(arg(args, "-height", "500"));
		String host = arg(args, "-server", "local");
		String config = arg(args, "-config", "CIRCLE_SCATTER");
		String outFile= arg(args, "-out", null);
		String sparkhome = arg(args,  "-spark", System.getenv("SPARK_HOME"));
		String jars[] = arg(args, "-jars", "AR.jar:ARApp.jar:ARExt.jar").split(":");
		boolean partition = Boolean.parseBoolean(arg(args, "-partitions", "true"));
		
		JavaSparkContext ctx = new JavaSparkContext(host, "Abstract-Rendering", sparkhome, jars);
		
		
		OptionDataset<G,I> dataset;
		try {
			dataset= (OptionDataset) OptionDataset.class.getField(config).get(null);
		} catch (
				IllegalAccessException |
				IllegalArgumentException |
				NoSuchFieldException | NullPointerException | SecurityException e) {
			throw new IllegalArgumentException("Could not find -config indicated: " + config);
		}
		
		JavaRDD<Indexed> base;
		File sourceFile = dataset.sourceFile;
		if (!sourceFile.getName().endsWith(".csv")) {
			JavaPairRDD<LongWritable, DataInputRecord> source = ctx.hadoopFile(sourceFile.getPath(), HBINInputFormat.class, LongWritable.class, DataInputRecord.class);
			base = (JavaRDD<Indexed>) (JavaRDD) source.map(new Function<Tuple2<LongWritable, DataInputRecord>, DataInputRecord>() {
				public DataInputRecord call(Tuple2<LongWritable, DataInputRecord> pair) throws Exception {return pair._2;}
			});
		} else {
			JavaRDD<String> source = ctx.textFile(sourceFile.getCanonicalPath());
			base = source.map(new StringToIndexed("\\s*,\\s*"));
		}

		Glypher<G,I> glypher = new Glypher<G,I>(dataset.shaper,dataset.valuer);
		GlyphsetRDD<G, I> glyphs = new GlyphsetRDD<>(base.map(glypher), true, partition);
		AffineTransform view = Util.zoomFit(glyphs.bounds(), width, height);
 		Selector selector = TouchesPixel.make(glyphs.exemplar().shape().getClass());

		Aggregator<I, A> aggregator = (Aggregator<I, A>) dataset.defaultAggregator.aggregator();
		Transfer transfer = OptionTransfer.toTransfer(((OptionDataset) dataset).defaultTransfers, null);
		
 		RDDRender render = new RDDRender();

 		Aggregates<A> aggs = render.aggregate(glyphs, selector, aggregator, view);
 		
		if (outFile == null) {
			TransferDisplay.show("", width, height, aggs, transfer);
		} else {
			AggregatesToCSV.export(aggs, new File(outFile));
		}
	}
}
