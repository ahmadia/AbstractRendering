package ar.app.display;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ExecutorService;

import ar.*;
import ar.app.util.MostRecentOnlyExecutor;
import ar.app.util.ZoomPanHandler;
import ar.util.Util;

public class FullDisplay extends ARComponent.Aggregating implements ZoomPanHandler.HasViewTransform {
	protected static final long serialVersionUID = 1L;

	protected final SimpleDisplay display;
	
	protected Aggregator<?,?> aggregator;
	protected Glyphset<?> dataset;
	protected Renderer renderer;
	
	protected AffineTransform viewTransformRef = new AffineTransform();
	protected AffineTransform inverseViewTransformRef = new AffineTransform();

	protected volatile boolean renderAgain = false;
	protected volatile boolean renderError = false;
	protected volatile Aggregates<?> aggregates;
	protected ExecutorService renderPool = new MostRecentOnlyExecutor(1,"ARPanel Render Thread");//TODO: Redoing painting to use futures...
		
	public FullDisplay(Aggregator<?,?> aggregator, Transfer<?,?> transfer, Glyphset<?> glyphs, Renderer renderer) {
		super();
		display = new SimpleDisplay(null, transfer, renderer);
		this.setLayout(new BorderLayout());
		this.add(display, BorderLayout.CENTER);
		this.invalidate();
		this.aggregator = aggregator;
		this.dataset = glyphs;
		this.renderer = renderer;
		
		ZoomPanHandler h = new ZoomPanHandler();
		super.addMouseListener(h);
		super.addMouseMotionListener(h);
	}
	
	protected void finalize() {renderPool.shutdown();}
	
	protected FullDisplay build(Aggregator<?,?> aggregator, Transfer<?,?> transfer, Glyphset<?> glyphs, Renderer renderer) {
		return new FullDisplay(aggregator, transfer, glyphs, renderer);
	}

	public Aggregates<?> refAggregates() {return display.refAggregates();}
	public void refAggregates(Aggregates<?> aggregates) {display.refAggregates(aggregates);}
	
	public Renderer renderer() {return renderer;}
	
	public Glyphset<?> dataset() {return dataset;}
	public void dataset(Glyphset<?> data) {
		this.dataset = data;
		this.aggregates = null;
		this.repaint();
	}
	
	public Transfer<?,?> transfer() {return display.transfer();}
	public void transfer(Transfer<?,?> t) {display.transfer(t);}
	
	public Aggregator<?,?> aggregator() {return aggregator;}
	public void aggregator(Aggregator<?,?> aggregator) {
		this.aggregator = aggregator;
		this.aggregates = null;
		this.repaint();
	}
	
	public Aggregates<?> aggregates() {return aggregates;}
	public void aggregates(Aggregates<?> aggregates) {
		this.display.aggregates(aggregates);
		this.aggregates = aggregates;
		this.repaint();
	}
	
	
	@Override
	public void paint(Graphics g) {
		panelPaint(g);
		super.paint(g);
	}
	
	//Override this method in subclasses to make custom painting
	protected void panelPaint(Graphics g) {
		Runnable action = null;
		if (renderer == null 
				|| dataset == null ||  dataset.isEmpty() 
				|| aggregator == null
				|| renderError == true) {
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, this.getWidth(), this.getHeight());
		} else if (renderAgain || aggregates == null) {
			action = new RenderAggregates();
		} 

		if (action != null) {
			renderPool.execute(action);
			renderAgain =false; 
		} 
	}
	
	/**Calculate aggregates for a given region.**/
	protected final class RenderAggregates implements Runnable {
		@SuppressWarnings({"unchecked","rawtypes"})
		public void run() {
			int width = FullDisplay.this.getWidth();
			int height = FullDisplay.this.getHeight();
			long start = System.currentTimeMillis();
			AffineTransform ivt = inverseViewTransform();
			try {
				aggregates = renderer.aggregate(dataset, (Aggregator) aggregator, ivt, width, height);
				display.aggregates(aggregates);
				long end = System.currentTimeMillis();
				if (PERF_REP) {
					System.out.printf("%d ms (Aggregates render on %d x %d grid)\n",
							(end-start), aggregates.highX()-aggregates.lowX(), aggregates.highY()-aggregates.lowY());
				}
			} catch (ClassCastException e) {
				renderError = true;
			}
			
			FullDisplay.this.repaint();
		}
	}
	
	
	public String toString() {return String.format("ARPanel[Dataset: %1$s, Ruleset: %2$s]", dataset, display.transfer(), aggregator);}
	
	
	
    /**Use this transform to convert values from the absolute system
     * to the screen system.
     */
	public AffineTransform viewTransform() {return new AffineTransform(viewTransformRef);}
	protected void innerSetViewTransform(AffineTransform vt) throws NoninvertibleTransformException {
		renderAgain = true;
		viewTransform(vt);
	}
	
	public void viewTransform(AffineTransform vt) throws NoninvertibleTransformException {		
		this.viewTransformRef = vt;
		inverseViewTransformRef  = new AffineTransform(vt);
		inverseViewTransformRef.invert();
		this.aggregates(null);
		this.repaint();
	}
	
	/**Use this transform to convert screen values to the absolute/canvas
	 * values.
	 */
	public AffineTransform inverseViewTransform() {return new AffineTransform(inverseViewTransformRef);}

	
	public void zoomFit() {
		try {
			if (dataset() == null || dataset().bounds() ==null) {return;}
			Rectangle2D content = dataset().bounds();
			
			AffineTransform vt = Util.zoomFit(content, getWidth(), getHeight());
			viewTransform(vt);
		} catch (Exception e) {} //Ignore all zoom-fit errors...they are usually caused by under-specified state
	}
}
