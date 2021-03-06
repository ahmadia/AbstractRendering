package ar;

import java.awt.geom.Rectangle2D;
import java.util.List;

import ar.util.axis.DescriptorPair;

/**
 * A collection of glyphs for rendering. 
 * A glyph is a geometric description and accompanying data values.
 * 
 * Segmentation
 * ------------
 * 
 * To support parallelization, the glyphset interface supports
 * "segmentation".  This enables sub-setting without the implication of
 * precise divisions.  The basic idea is that a glyphset can report how many
 * divisions it can easily provide, and supports returning a subset of those divisions.
 * The number of divisions is returned by "segments" and the "segment" method
 * returns a subset of those divisions.  
 * 
 * Some parallel processing strategies work best on a subset of the data. The
 * simplest way to subset data is to find out how much data there is and exactly
 * divide it among the parts. For example, if there are 100 data points and 10
 * tests, give task 0 gets data values 0-9, task 1 values 10-19, etc. This type
 * of division is easy to do with random access data (as are other convenient
 * strategies), but not always feasible in other container types.
 * 
 * Concretely, the quad-tree containers don't impose a full ordering on the data
 * set. So providing and index into the data isn't convenient. However, it is
 * discrete at the level of leaf-quads. Similarly, the Avro serialization format
 * provides segments, but not random-access to those segments. The data can
 * still be divided, but providing the level of control over what falls into which
 * division random access structures provide is expensive and unnecessary. 
 * The segment system provides a vocabulary for specifying data subsets without implying precision.
 * 
 * 
 * Random Access
 * -------------
 * A particularly common way to support segmentation is to support random access.
 * When random access is present, a number of additional classes for segmentation
 * can be used.
 * 
 * @param <I> The type of the information associated with a glyph entry.
 */
public interface Glyphset<G,I> extends Iterable<Glyph<G,I>> {	
	/**Is this glyphset empty?*/
	public boolean isEmpty();
	
	/**What are the overall bounds of the items in this glyphset?**/
	public Rectangle2D bounds();

	/**Get the axis descriptor.**/
	public DescriptorPair<?,?> axisDescriptors();
	
	/**Set the axis descriptor (optional operation)**/
	public void axisDescriptors(DescriptorPair<?,?> descriptor);
	
	/**How many items in this glyphset?
	 * */
	public long size();
	
	/**Create a number of subsets of the data.
	 * 
	 * Notes:
	 *  * Each items in the source must be in exactly one return segment.
	 *  * Segments may have size zero (discouraged, but allowed).
	 *  * Segments do not need to be the same size (though it is helpful) 
	 * 
	 * @param count Maximum number of segments to return. May return fewer.
	 **/
	public List<Glyphset<G,I>> segment(int count) throws IllegalArgumentException;

	/**Glyphsets that support random access.
	 * 
	 * Random-access glyphsets should return 'segments' equal to size and
	 * return contiguous chunks from segment.  This is not a requirement,
	 * but a suggestion.
	 */
	public static interface RandomAccess<G,I> extends Glyphset<G,I> {
		/**Return the item at the specified index.**/
		public Glyph<G,I> get(long l);
	}
}
