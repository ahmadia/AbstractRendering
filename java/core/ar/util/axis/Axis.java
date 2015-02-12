package ar.util.axis;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import ar.Glyphset;


/**Utilities for creating and displaying axes in abstract rendering.
 * 
 * WARNING: This is an ongoing experiment.  
 * 
 * Axes are fun because they show the relationship between the input data 
 * and the layout.  However, they are tricky because many things can mediate 
 * that relationship.  Axes are currently attached to the glyphset because
 * it represents the root input to abstract rendering and the axes is
 * trying to show a relationship that stretches further back.
 * 
 * However, axes are a tricky because transfers can modify the spatial relationship.
 * Stretch is an easy examples because it multiplies the spatial distribution in one
 * direction. Smear and Spread are trickier because they do not change the distribution
 * uniformly.  Selectors can similarly modify spatial arrangements.
 * 
 * This package is NOT generalized to cover all cases.  It currently handles simple
 * cases well enough, but non-uniformity (either discontinuities in mapping or non-uniform
 * changes in transfer) are not fully covered.  Suggestions are VERY welcome.
 * 
 * IDEAS:
 *    * Augment Transfer and Selector to handle guide transforms as well (default is pass-through)
 *    * Have no general automatic system, but have an expressive descriptive system
 *    * Have a simple automatic system that defaults to errors and warnings and no rendering
 *      when it detects violation
 *    * AR is designed to work with other visualization systems.  Maybe guides are really 
 *      the host system's problem
 */
public class Axis {

	public static final <T> AxisDescriptor<T> empty() {return new AxisDescriptor<T>("", Collections.<T,Double>emptyMap(), new Interpolate.Discrete<T>());}
	
	/**Produce descriptors indicating literal positions.
	 * DOES NOT reflect backing data, just the bounding box of the projection.
	 * **/
	public static DescriptorPair coordinantDescriptors(Glyphset<?,?> glyphs) {
		Rectangle2D bounds = glyphs.bounds();
		
		return new DescriptorPair(linearDescriptor("", bounds.getMinX(), bounds.getMaxX(), bounds.getMinX(), bounds.getMaxX(), 10, true),
					    		linearDescriptor("", bounds.getMinY(), bounds.getMaxY(), bounds.getMinY(), bounds.getMaxY(), 10, true));
	}
	

	@SuppressWarnings("unchecked")
	/**Construct a linear descriptor.
	 * 
	 *  The generic type T is determined by continuous flag. 
	 *  If continuous, T is type Long.  Otherwise it is type Double 
	 * 
	 * @param label Axis label 
	 * @param lowIn Low value in the input space
	 * @param highIn High value in the input space
	 * @param lowOut Low value in the output space
	 * @param highOut High value in the output space
	 * @param samples How many samples to make
	 * @param continuous Should the samples be from a continuous or discrete number representation
	 * @return
	 */
	public static <T extends Number> AxisDescriptor<T> linearDescriptor(String label, double lowIn, double highIn, double lowOut, double highOut, int samples, boolean continuous) {
		Map<Number, Double> rslt = continuous ? new TreeMap<Number, Double>() : new HashMap<Number, Double>();
		Interpolate<?> interp = continuous ? new Interpolate.LinearSmooth() : new Interpolate.Discrete<Long>();
		
		for (int i=0; i<samples+1; i++) {
			Number in;
			Number out;
			
			if (continuous) {
				in = new Double(lowIn + ((highIn-lowIn)/samples)*i);
				out = new Double(lowOut + ((highOut-lowOut)/samples)*i);
			} else {
				in = Math.round(lowIn + ((highIn-lowIn)/samples)*i);
				out = Math.round(lowOut + ((highOut-lowOut)/samples)*i);
			}
			
			rslt.put(in, out.doubleValue());
		}
		
		return new AxisDescriptor<>(label, (Map<T, Double>) rslt, (Interpolate<T>) interp);
	}
	
	/**Create an evenly-spaced categorical axis.**/
	public static AxisDescriptor<String> categoricalDescriptor(String label, double low, double high, String... labels) {
		int positions = labels.length;
		Map<String, Double> rslt = new HashMap<String, Double>();
		
		double gap = low + ((high-low)/(positions*2));
		for (int i=0; i<positions; i++) {
			Double val = gap + gap*i*2;
			rslt.put(labels[i], val);
		}
		return new AxisDescriptor<>(label, rslt, new Interpolate.Discrete<String>());
	}
	
	
	/**Merge two descriptors.  
	 * 
	 * NOTE: Assumes the interpolate from 'first' is the one to carry forward.
	 */
	public <T> AxisDescriptor<T> merge(AxisDescriptor<T> first, AxisDescriptor<T> second) {
		Map<T, Double> combined = new HashMap<>();
		combined.putAll(first.seeds);
		combined.putAll(second.seeds);
		
		String label;
		if (first.label == second.label) {label = first.label;}
		else {label = first.label + "/" + second.label;}
		
		return new AxisDescriptor<>(label, combined, first.interpolate);
	}
	
	
    //---------------------------------------------------- Rendering ----------------------------------------------------
	

	public static void drawAxes(DescriptorPair axes, Graphics2D g2, AffineTransform viewTransform, Rectangle2D screenBounds) {
		
		Object restore_anti_alias = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

		drawAxis(axes.x, g2, viewTransform, screenBounds, true);
		drawAxis(axes.y, g2, viewTransform, screenBounds, false);

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, restore_anti_alias);

	}
	
	/**How far off of the line should labels be placed?**/
	public static float LABEL_OFFSET = 10;
	
	/**How far from the line should the tick go in the direction AWAY from the label?**/
	public static float TICK_AWAY = 3;
	
	/**How far from the line should the tick go in the direction TOWARDS the label?**/
	public static float TICK_TOWARD = 6;
	
	/**How many pixels high should the axes be given?**/
	public static int AXIS_SPACE = 100;
	
	public static final void drawAxis(AxisDescriptor<?> axis, Graphics2D g2, AffineTransform viewTransform, Rectangle2D screenBounds, boolean isX) {
		g2.setColor(Color.GRAY);
		double max=Double.NEGATIVE_INFINITY, min=Double.POSITIVE_INFINITY;		
		for (Map.Entry<?,Double> e:axis.seeds.entrySet()) {
			Double val = e.getValue();
			
			drawLine(val, val, TICK_TOWARD, TICK_AWAY, g2, viewTransform, screenBounds, isX);
			drawLabel(e.getKey(), val, val, LABEL_OFFSET, g2, viewTransform, screenBounds, isX, isX);

			max = Math.max(max, val);
			min = Math.min(min, val);
		}
		
		drawLine(min, max, 0,0, g2, viewTransform, screenBounds, isX);		
		drawLabel(axis.label, min, max, LABEL_OFFSET*4.5, g2, viewTransform, screenBounds, isX, !isX); //TODO: The '4.5' is a magic number...remove it by doing some whole-axis analysis
	}
	
	/**Draws text at the given position.
	 * Text is drawn in unscaled space, but positioning is done with respect to the view transform.
	 * This is an interpretation of Bertin-style 'point' implantation, applied to text.
	 */
	private static final void drawLabel(Object label, double val1, double val2, double offset, Graphics2D g2, AffineTransform vt, Rectangle2D screenBounds, boolean isX, boolean rotate) {
		AffineTransform restore = g2.getTransform();
		g2.setTransform(new AffineTransform());
		
		String labelText;
		if (label instanceof Integer || label instanceof Long) {
			labelText = String.format("%,d", label);
		} else if (label instanceof Number) {
			labelText = String.format("%.3f", label);
		} else {
			labelText = label.toString();
		}
		
		Rectangle2D stringBounds = g2.getFontMetrics().getStringBounds(labelText, g2); 

		Point2D p1, p2;
		AffineTransform t = new AffineTransform(vt);
		if (isX) {
			t.scale(1,1/vt.getScaleY());
			t.translate(0, -vt.getTranslateY()+screenBounds.getHeight()-AXIS_SPACE);

			p1 = new Point2D.Double(val1, offset);
			p2 = new Point2D.Double(val2, offset);
		} else {
			t.scale(1/vt.getScaleX(), 1);
			t.translate(-vt.getTranslateX()+AXIS_SPACE,0);

			p1 = new Point2D.Double(offset, val1);
			p2 = new Point2D.Double(offset, val2);			
		}
		
		t.transform(p1, p1);
		t.transform(p2, p2);
		
		double x,y;
		if (isX) {
			x = (p1.getX()+p2.getX())/2 - (stringBounds.getHeight()/4); //HACK: Divide by 4????  It just looks better...
			y = Math.min(p1.getY(), p2.getY()) + offset;
			t = AffineTransform.getTranslateInstance(x+screenBounds.getMinX(), y+screenBounds.getMinY());
		} else {
			x = Math.min(p1.getX(), p2.getX()) - (stringBounds.getWidth()+offset+offset); //HACK: TWICE!!! Not sure why...
			y = (p1.getY()+p2.getY())/2 + (stringBounds.getHeight()/4); //HACK: Divide by 4????  It just looks better...
			t = AffineTransform.getTranslateInstance(x+screenBounds.getMinX(), y+screenBounds.getMinY());
		}

		if (rotate) {t.rotate(Math.PI/2);}

		
		g2.setTransform(t);
		g2.drawString(labelText, 0,0);
		g2.setTransform(restore);
	}
	
	/**Draws a line between two points.  The line is always drawn in unscaled space, 
	 * so the line thickness is not affect by the view transform BUT the points are scaled to match 
	 * the view transform.  Otherwise said, this method achieves Bertin-style 'line' implantation.   
	 */
	private static final void drawLine(double val1, double val2, double toward, double away, Graphics2D g2, AffineTransform vt, Rectangle2D screenBounds, boolean isX) {
		AffineTransform t = new AffineTransform(vt);
		Point2D p1, p2;
		if (isX) {
			t.scale(1, 1/vt.getScaleY());
			t.translate(0, -vt.getTranslateY()+screenBounds.getHeight()-AXIS_SPACE);
			p1 = new Point2D.Double(val1, toward);
			p2 = new Point2D.Double(val2, -away);
		} else {
			t.scale(1/vt.getScaleX(), 1);
			t.translate(-vt.getTranslateX()+AXIS_SPACE,0);
			p1 = new Point2D.Double(away, val1);
			p2 = new Point2D.Double(-toward, val2);
		}
		
		t.transform(p1, p1);
		t.transform(p2, p2);
		
		AffineTransform restore = g2.getTransform();
		g2.setTransform(AffineTransform.getTranslateInstance(screenBounds.getMinX(), screenBounds.getMinY()));
		
		Line2D l = new Line2D.Double(p1, p2);
		g2.draw(l);
		g2.setTransform(restore);
	}	
}