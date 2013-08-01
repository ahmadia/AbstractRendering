package ar.glyphsets;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

import ar.Glyph;
import ar.Glyphset;
import ar.glyphsets.implicitgeometry.Indexed;
import ar.glyphsets.implicitgeometry.Shaper;
import ar.glyphsets.implicitgeometry.Valuer;
import ar.util.BigFileByteBuffer;
import ar.util.MemMapEncoder.TYPE;
import ar.util.Util;
import ar.util.IndexedEncoding;

/**Implicit geometry, sequentially arranged glyphset backed by a memory-mapped file.
 * 
 * This glyphset uses 'implicit geometry' in that the geometry is produced just-in-time and
 * discarded immediately.  Implicit geometry significantly reduces the required memory at the
 * cost of speed.  When using implicit geometry, the display window size is the principal 
 * memory consumer (because it determines both the image size and the aggregates set size). 
 * 
 * The memory mapped file must be encoded as fixed-width records for this class.
 * The files may include a header to self-describe or the header information may be supplied.
 * 
 *  The header, when provided, is an integer indicating how many fields are in each record,
 *  followed by a set of characters (one for each field).  
 *  
 *  The characters that describe field types are:
 *  
 *   +   s -- Short (two bytes)
 *   +   i -- Int (four bytes)
 *   +   l -- Long (eight bytes)
 *   +   f -- Float (four bytes)
 *   +   d -- Double (eight bytes)
 *   +   c -- Char (two bytes)
 *   +   b -- Byte (one byte)
 * 
 * TODO: Add skip parameter to skip a certain number of bytes at the start of the file
 * TODO: Add support for strings (type 'V').  Multi-segmented file or multiple files, where one file is the table, the other is a string-table.  Table file stores offsets into string-table for string values
 * TODO: Add support for non-color glyphs (generalize Painter...)
 * 
 * @author jcottam
 *
 */
public class MemMapList<V> implements Glyphset.RandomAccess<V> {
	public static int BUFFER_BYTES = 30000;//Integer.MAX_VALUE added appreciable latency to thread creation, while this smaller number didn't add appreciable latency to runtime...perhaps because multi-threading hid the latency
	public static int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
	private final ForkJoinPool pool = new ForkJoinPool(THREAD_POOL_SIZE);

	private final ThreadLocal<BigFileByteBuffer> buffer = 
			new ThreadLocal<BigFileByteBuffer>() {
		public BigFileByteBuffer initialValue() {
			if (source == null) {return null;}
			try {return new BigFileByteBuffer(source, recordSize, BUFFER_BYTES);}
			catch (Exception e) {throw new RuntimeException(e);}
		}
	};

	private final File source;
	private final TYPE[] types;
	private final Valuer<Indexed,V> valuer;
	private final Shaper<Indexed> shaper;

	private final int recordEntries;
	private final int recordSize;
	private final int headerOffset;
	private final long entryCount;
	private Rectangle2D bounds;

	public MemMapList(File source, Shaper<Indexed> shaper, Valuer<Indexed,V> painter) {
		this(source, null, shaper, painter);
	}

	public MemMapList(File source, TYPE[] types, Shaper<Indexed> shaper, Valuer<Indexed,V> valuer) {
		this.source = source;
		this.valuer = valuer;
		this.shaper = shaper;

		if (source != null && types == null) {
			recordEntries = buffer.get().getInt();

			types = new TYPE[recordEntries];
			for (int i =0; i<recordEntries; i++) {
				char t = buffer.get().getChar();
				if (t=='i') {types[i] = TYPE.INT;}  
				else if (t=='l') {types[i] = TYPE.LONG;}
				else if (t=='s') {types[i] = TYPE.SHORT;}
				else if (t=='d') {types[i] = TYPE.DOUBLE;} 
				else if (t=='f') {types[i] = TYPE.FLOAT;}
				else if (t=='b') {types[i] = TYPE.BYTE;}
				else {throw new RuntimeException(String.format("Unknown type indicator '%s' at position %s", t,i));}
			}
			this.types = types;
			headerOffset = (TYPE.INT.bytes+(types.length*TYPE.CHAR.bytes));  //Int for the header length, one char per entry  

			int acc=0;
			for (TYPE t:this.types) {acc += t.bytes;}
			this.recordSize = acc;
		} else {
			recordEntries = 0;
			headerOffset = 0;
			this.types = null;
			this.recordSize = -1;
		}
		entryCount = buffer.get() == null ? 0 : (buffer.get().fileSize()-headerOffset)/recordSize;

	}

	protected void finalize() {pool.shutdownNow();}

	@Override
	public Collection<Glyph<V>> intersects(Rectangle2D r) {
		ArrayList<Glyph<V>> contained = new ArrayList<Glyph<V>>();
		for (Glyph<V> g: this) {if (g.shape().intersects(r)) {contained.add(g);}}
		return contained;
	}

	@Override
	public Glyph<V> get(long i) {
		Glyph<V> g = new SimpleGlyph<V>(shaper.shape(entry(i)), valuer.value(entry(i)));
		return g;
	}

	protected IndexedEncoding entry(long i) {
		long recordOffset = (i*recordSize)+headerOffset;
		BigFileByteBuffer buffer = this.buffer.get();
		return new IndexedEncoding(types, recordOffset,recordSize,buffer);
	}

	public Valuer<Indexed,V> valuer() {return valuer;}
	public Shaper<Indexed> shaper() {return shaper;}
	public TYPE[] types() {return types;}

	public boolean isEmpty() {return buffer.get() == null || buffer.get().capacity() <= 0;}
	public long size() {return entryCount;}
	public void add(Glyph<V> g) {throw new UnsupportedOperationException();}
	public Iterator<Glyph<V>> iterator() {return new GlyphsetIterator<V>(this);}

	public Rectangle2D bounds() {
		if (bounds == null) {
			bounds = pool.invoke(new BoundsTask(0, this.size()));
		}
		return bounds;
	}

	private final class BoundsTask extends RecursiveTask<Rectangle2D> {
		public static final long serialVersionUID = 1L;
		private static final int TASK_SIZE = 100000;
		private final long low, high;

		public BoundsTask(long low, long high) {
			this.low = low;
			this.high = high;
		}

		@Override
		protected Rectangle2D compute() {
			if (high-low > TASK_SIZE) {return split();}
			else {return local();}
		}

		private Rectangle2D split() {
			long mid = low+((high-low)/2);
			BoundsTask top = new BoundsTask(low, mid);
			BoundsTask bottom = new BoundsTask(mid, high);
			invokeAll(top, bottom);
			Rectangle2D bounds = Util.bounds(top.getRawResult(), bottom.getRawResult());
			return bounds;
		}

		private Rectangle2D local() {
			Rectangle2D bounds = new Rectangle2D.Double(0,0,-1,-1);

			for (long i=low; i<high; i++) {
				IndexedEncoding enc = entry(i);
				Rectangle2D bound = shaper.shape(enc).getBounds2D();
				if (bound != null) {Util.add(bounds, bound);}

			}
			return bounds;
		}

	}

	public long segments() {return size();}

	@Override
	public Glyphset<V> segment(long bottom, long top)
			throws IllegalArgumentException {
		return GlyphSubset.make(this, bottom, top, true);
	}

}
