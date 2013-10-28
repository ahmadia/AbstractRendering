package ar.renderers.tasks;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RecursiveTask;

import ar.Aggregates;
import ar.Aggregator;
import ar.Glyph;
import ar.Glyphset;
import ar.aggregates.AggregateUtils;
import ar.aggregates.ConstantAggregates;
import ar.aggregates.TouchedBoundsWrapper;
import ar.renderers.AggregationStrategies;
import ar.renderers.RenderUtils;
import ar.util.Util;

public abstract class GlyphParallelAggregation<I,G,A> extends RecursiveTask<Aggregates<A>> {
	private static final long serialVersionUID = 705015978061576950L;
	protected final int taskSize;
	protected final long low;
	protected final long high;
	protected final Glyphset<? extends G, ? extends I> glyphs;
	protected final AffineTransform view;
	protected final Rectangle viewport;
	protected final Aggregator<I,A> op;
	protected final RenderUtils.Progress recorder;

	protected GlyphParallelAggregation(
			Glyphset<? extends G, ? extends I> glyphs, 
			AffineTransform view,
			Aggregator<I,A> op, 
			Rectangle viewport,
			int taskSize,
			RenderUtils.Progress recorder,
			long low, long high) {
		this.glyphs = glyphs;
		this.view = view;
		this.op = op;
		this.viewport =viewport;
		this.taskSize = taskSize;
		this.recorder = recorder;
		this.low = low;
		this.high = high;
	}


	protected Aggregates<A> compute() {
		if ((high-low) > taskSize) {return split();}
		else {return local();}
	}

	protected final Aggregates<A> split() {
		long mid = Util.mean(low, high);

		GlyphParallelAggregation<I,G,A> top = this.subTask(low, mid);
		GlyphParallelAggregation<I,G,A> bottom = this.subTask(mid, high);
		invokeAll(top, bottom);
		Aggregates<A> aggs;
		try {aggs = AggregationStrategies.horizontalRollup(top.get(), bottom.get(), op);}
		catch (InterruptedException | ExecutionException e) {throw new RuntimeException(e);}
		return aggs;
	}
	
	
	/**DESTRUCTIVELY updates the target at x/y with the value passed and the target operation.**/
	protected final void update(Aggregates<A> target, I v, int x, int y) {
		A existing = target.get(x,y);
		A update = op.combine(x,y,existing, v);
		target.set(x, y, update);
	}
	
	/**Discretize each glyph into an aggregates set.**/
	protected abstract Aggregates<A> local();
	
	protected abstract GlyphParallelAggregation<I,G,A> subTask(long low, long high);
	
	protected Aggregates<A> allocateAggregates(Rectangle2D bounds) {
		Rectangle fullBounds = view.createTransformedShape(bounds).getBounds();
		Aggregates<A> aggs = AggregateUtils.make(fullBounds.x, fullBounds.y,
				fullBounds.x+fullBounds.width, fullBounds.y+fullBounds.height, 
				op.identity());
		return new TouchedBoundsWrapper<>(aggs, false);
	}

	private static final class Points<I, A> extends GlyphParallelAggregation<I,Point2D,A> {
		public Points(
				Glyphset<? extends Point2D, ? extends I> glyphs, 
				AffineTransform view,
				Aggregator<I,A> op, 
				Rectangle viewport,
				int taskSize,
				RenderUtils.Progress recorder,
				long low, long high) {
			super(glyphs, view, op, viewport, taskSize, recorder, low,high);
		}

		protected final Aggregates<A> local() {
			Glyphset<? extends Point2D, ? extends I> subset = glyphs.segment(low,  high);
			Aggregates<A> aggregates = allocateAggregates(glyphs.bounds());
			if (aggregates instanceof ConstantAggregates) {return aggregates;}

			Point2D scratch = new Point2D.Double();
			for (Glyph<? extends Point2D, ? extends I> g: subset) {
				Point2D p = g.shape();	//A point has no bounding box...so life is easy
				view.transform(p, scratch);
				int x = (int) scratch.getX();
				int y = (int) scratch.getY();
				I v = g.info();
				
				update(aggregates, v, x,y);
			}

			recorder.update(subset.size());
			return aggregates;
		}
		
		@Override
		protected GlyphParallelAggregation<I, Point2D, A> subTask(long low, long high) {
			return new Points<>(glyphs, view, op, viewport, taskSize, recorder, low, high);
		}
	}

	private static final class Lines<I, A> extends GlyphParallelAggregation<I,Line2D,A> {
		public Lines(
				Glyphset<? extends Line2D, ? extends I> glyphs, 
				AffineTransform view,
				Aggregator<I,A> op, 
				Rectangle viewport,
				int taskSize,
				RenderUtils.Progress recorder,
				long low, long high) {
			super(glyphs, view, op, viewport, taskSize, recorder, low,high);
		}

		
		//based on 'optimized' version at http://en.wikipedia.org/wiki/Bresenham's_line_algorithm
		private void bressenham(Aggregates<A> canvas, Line2D line, I val) {
			int x0 = (int) line.getX1(); //TODO: Not sure if the rounding should happen here or later....
			int y0 = (int) line.getY1();
			int x1 = (int) line.getX2();
			int y1 = (int) line.getY2();
			boolean steep = Math.abs(y1 - y0) > Math.abs(x1 - x0);
			  if (steep) {
				  int temp = x0;
				  x0 = y0;
				  y0 = temp;

				  temp = x1;
				  x1 = y1;
				  y1 = temp;
			  }
			  
			  if (x0 > x1) {
				  int temp = x0;
				  x0 = x1;
				  x1 = temp;
				  
				  temp = y0;
				  y0 = y1;
				  y1 = temp;
			  }

			  double deltax = x1 - x0;
			  double deltay = Math.abs(y1 - y0);
			  double error = 0;
			  int ystep = y0 < y1 ? 1 : -1;
			  
			  int y = y0;
			  for (int x=x0; x> x1; x++) { //line only includes first endpoint
			    if (steep) {
			      if (x >= canvas.highY()) {break;}
			      if (y >= canvas.highX()) {break;}
				  update(canvas, val, x,y);
			    } else {
			      if (x >= canvas.highX()) {break;}
			      if (y >= canvas.highY()) {break;}
				  update(canvas, val, x,y);
			    }

			    error = error - deltay;
			    if (error < 0) {
			      y = y + ystep;
			      error = error + deltax;
			    }
			  }
		}
		
		
		protected final Aggregates<A> local() {
			Glyphset<? extends Line2D, ? extends I> subset = glyphs.segment(low,  high);
			Aggregates<A> aggregates = allocateAggregates(glyphs.bounds());
			if (aggregates instanceof ConstantAggregates) {return aggregates;}

			for (Glyph<? extends Line2D, ? extends I> g: subset) {
				bressenham(aggregates, g.shape(), g.info());
			}

			recorder.update(subset.size());
			return aggregates;
		}
		
		@Override
		protected GlyphParallelAggregation<I, Line2D, A> subTask(long low, long high) {
			return new Lines<>(glyphs, view, op, viewport, taskSize, recorder, low, high);
		}

	}
	
	private static final class Rectangles<I,A> extends GlyphParallelAggregation<I,Rectangle2D,A> {
		public Rectangles(
				Glyphset<? extends Rectangle2D, ? extends I> glyphs, 
				AffineTransform view,
				Aggregator<I,A> op, 
				Rectangle viewport,
				int taskSize,
				RenderUtils.Progress recorder,
				long low, long high) {
			super(glyphs, view, op, viewport, taskSize, recorder, low,high);
		}

		protected final Aggregates<A> local() {
			Glyphset<? extends Rectangle2D, ? extends I> subset = glyphs.segment(low,  high);
			Aggregates<A> aggregates = allocateAggregates(glyphs.bounds());
			if (aggregates instanceof ConstantAggregates) {return aggregates;}


			Point2D lowP = new Point2D.Double();
			Point2D highP = new Point2D.Double();

			final int width = viewport.width;
			final int height =viewport.height;
			for (Glyph<? extends Rectangle2D, ? extends I> g: subset) {
				Rectangle2D b = g.shape();	//A rectangle is its own bounding box!
				lowP.setLocation(b.getMinX(), b.getMinY());
				highP.setLocation(b.getMaxX(), b.getMaxY());

				view.transform(lowP, lowP);
				view.transform(highP, highP);

				int lowx = (int) Math.floor(lowP.getX());
				int lowy = (int) Math.floor(lowP.getY());
				int highx = (int) Math.ceil(highP.getX());
				int highy = (int) Math.ceil(highP.getY());

				I v = g.info();

				for (int x=Math.max(0,lowx); x<highx && x< width; x++){
					for (int y=Math.max(0, lowy); y<highy && y< height; y++) {
						update(aggregates, v, x,y);
					}
				}
			}

			recorder.update(subset.size());
			return aggregates;
		}
		
		@Override
		protected GlyphParallelAggregation<I, Rectangle2D, A> subTask(long low, long high) {
			return new Rectangles<>(glyphs, view, op, viewport, taskSize, recorder, low, high);
		}
	}

	private static final class Shapes<I, A> extends GlyphParallelAggregation<I,Shape,A> {
		public Shapes(
				Glyphset<? extends Shape, ? extends I> glyphs, 
				AffineTransform view,
				Aggregator<I,A> op, 
				Rectangle viewport,
				int taskSize,
				RenderUtils.Progress recorder,
				long low, long high) {
			super(glyphs, view, op, viewport, taskSize, recorder, low,high);
		}

		protected final Aggregates<A> local() {
			Glyphset<? extends Shape, ? extends I> subset = glyphs.segment(low,  high);
			Aggregates<A> aggregates = allocateAggregates(glyphs.bounds());
			if (aggregates instanceof ConstantAggregates) {return aggregates;}


			Point2D lowP = new Point2D.Double();
			Point2D highP = new Point2D.Double();
			Point2D testP = new Point2D.Double();

			final int width = viewport.width;
			final int height =viewport.height;
			for (Glyph<? extends Shape, ? extends I> g: subset) {
				Rectangle2D b = g.shape().getBounds2D();
				lowP.setLocation(b.getMinX(), b.getMinY());
				highP.setLocation(b.getMaxX(), b.getMaxY());

				view.transform(lowP, lowP);
				view.transform(highP, highP);

				int lowx = (int) Math.floor(lowP.getX());
				int lowy = (int) Math.floor(lowP.getY());
				int highx = (int) Math.ceil(highP.getX());
				int highy = (int) Math.ceil(highP.getY());

				I v = g.info();
				for (int x=Math.max(0,lowx); x<highx && x< width; x++){
					for (int y=Math.max(0, lowy); y<highy && y< height; y++) {
						testP.setLocation(x, y);
						if (g.shape().contains(testP)) {
							update(aggregates, v, x,y);
						}
					}
				}
			}

			recorder.update(subset.size());
			return aggregates;
		}
		
		@Override
		protected GlyphParallelAggregation<I, Shape, A> subTask(long low, long high) {
			return new Shapes<>(glyphs, view, op, viewport, taskSize, recorder, low, high);
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <I,G,A> GlyphParallelAggregation<I,G,A> make(
			Class<?> geometryType,
			Glyphset<? extends G, ? extends I> glyphs, 
			AffineTransform view,
			Aggregator<I,A> op, 
			Rectangle viewport,
			int taskSize,
			RenderUtils.Progress recorder,
			long low, long high) {

		if (Point2D.class.isAssignableFrom(geometryType)) {
			return (GlyphParallelAggregation<I, G, A>) new Points<>((Glyphset<Point2D,I>) glyphs, view, op, viewport, taskSize, recorder, low, high);			
		} else if (Rectangle2D.class.isAssignableFrom(geometryType)) {
			return  (GlyphParallelAggregation<I, G, A>) new Rectangles<>((Glyphset<Rectangle2D,I>) glyphs, view, op, viewport, taskSize, recorder, low, high);
		} else if (Line2D.class.isAssignableFrom(geometryType)) {
			return (GlyphParallelAggregation<I, G, A>) new Lines<>((Glyphset<Line2D,I>)glyphs, view, op, viewport, taskSize, recorder, low, high);
		} else if (Shape.class.isAssignableFrom(geometryType)){
			return (GlyphParallelAggregation<I, G, A>) new Shapes<>((Glyphset<Shape,I>) glyphs, view, op, viewport, taskSize, recorder, low, high);
		} else {
			throw new IllegalArgumentException("Could not construct aggregator for geometry type: " + geometryType.getName());
		}

	}
}