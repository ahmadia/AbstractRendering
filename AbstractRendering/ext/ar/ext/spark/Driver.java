package ar.ext.spark;

import java.awt.geom.AffineTransform;
import java.io.File;

import spark.api.java.JavaRDD;
import spark.api.java.JavaSparkContext;
import ar.Aggregates;
import ar.app.util.AggregatesToCSV;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.glyphsets.implicitgeometry.Shaper;
import ar.glyphsets.implicitgeometry.Valuer;
import ar.glyphsets.implicitgeometry.Indexed.*;
import ar.rules.Numbers;

/**Main class for driving an ARSpark application.**/
public class Driver {
	private static String arg(String[] args, String flag, String def) {
		flag = flag.toUpperCase();
		for (int i=0; i<args.length; i++) {
			if (args[i].toUpperCase().equals(flag)) {return args[i+1];}
		}
		return def;
	}
	
	/* Get an RDD
	 * 
	 * Aggregates: FlatAggregates, probably wrapped up in something
	 * 

	 * Transfer aggregates back with
	 *   <<Aggregates list RDD>>.reduce(<aggregator>.Rollup)
	 * 
	 * Do transfer locally (in an ARPanel)
	 **/
	public static void main(String[] args){
		if (args.length == 0) {
			System.err.println("Usage: JavaTC -host <host> -in <data> -out <out> -sh <spark-home> -jars <jars>");
			System.exit(1);
		}
		
		String host = arg(args, "-host", "localhost");
		String path = arg(args, "-in", null);
		String outFile= arg(args, "-out", null);
		String sh= arg(args, "-sh", System.getenv("SPARK_HOME"));
		String jars[] = arg(args, "-out", "").split(":");

		JavaSparkContext sc = new JavaSparkContext(host, "SparkAbstractRendering",sh, jars);
		JavaRDD<Indexed> base = sc.textFile(path).map(new StringToIndexed("\\s*,\\s*"));
		Shaper<Indexed> shaper = new ToRect(.01, .01, false, 0, 1);
		Valuer<Indexed,?> valuer = new ToValue(2);

		JavaRDD glyphs = RDDRender.glyphs(base, shaper, valuer);
		
		//TODO: get glyphset bounds, calc zoom-fit and share it out.  Mark the realized glyphs as needing to be retained
		AffineTransform vt = new AffineTransform();
		JavaRDD aggset = RDDRender.renderAll(vt, glyphs);
		Aggregates aggs = RDDRender.collect(aggset, new Numbers.Count());
		
		AggregatesToCSV.export(aggs, new File(outFile));
	}
}
