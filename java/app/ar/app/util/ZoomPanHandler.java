package ar.app.util;

import java.awt.Graphics2D;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.*;
import java.awt.geom.*;

import javax.swing.JComponent;

import ar.util.HasViewTransform;

/**Utility for working with zoom/pan on a display component.
 * 
 * This class should only be registered with objects that are both JComponents 
 * and implement the HasViewTransform interface.
 * **/
public class ZoomPanHandler implements MouseListener, MouseMotionListener, KeyListener {
    private static final int ZOOM_BUTTON = InputEvent.BUTTON2_MASK;
    private static final int PAN_BUTTON = InputEvent.BUTTON1_MASK;
	
    private Point2D down = new Point2D.Float();
    private int yLast;
    
    
    /**Install a new ZoomPanHandler instance on the passed item.**/
    public static void installOn(JComponent target) {
    	ZoomPanHandler h = new ZoomPanHandler();
    	if (target instanceof HasViewTransform) {
    		target.setFocusable(true);
    		target.requestFocus();
    		
    		target.addMouseListener(h);
    		target.addMouseMotionListener(h);
    		target.addKeyListener(h);
    	} else {
    		throw new IllegalArgumentException("Target must be *both* a JComponent and implement HasViewTransform");
    	}
    }
    
    
	/**
     * @see java.awt.event.MouseListener#mousePressed(java.awt.event.MouseEvent)
     */
    @Override
	public void mousePressed(MouseEvent e) { 
		e.getComponent().requestFocus();

        if (buttonEquals(e, ZOOM_BUTTON) ) {
        	JComponent canvas = (JComponent) e.getComponent();

            canvas.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            
            AffineTransform af = ((Graphics2D) canvas.getGraphics()).getTransform();
            try {af.inverseTransform(e.getPoint(), down);}
			catch (NoninvertibleTransformException e1) {throw new RuntimeException(e1);}
            yLast = e.getY();
        } else if (buttonEquals(e, PAN_BUTTON)) {
            e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            down = e.getPoint();
        }
    }
    
    /**
     * @see java.awt.event.MouseMotionListener#mouseDragged(java.awt.event.MouseEvent)
     */
    @Override
	public void mouseDragged(MouseEvent e) {
    	HasViewTransform canvas = (HasViewTransform) e.getComponent();
    	JComponent component = (JComponent) e.getComponent();
        if (buttonEquals(e, ZOOM_BUTTON) ) {
            int y = e.getY();
            int dy = y-yLast;
            double zoom = 1 + ((double)dy) / 100;

            component.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            
            zoom(canvas, zoom, true);
            
            yLast = y;
        } else if (buttonEquals(e, PAN_BUTTON)) {
            double x = e.getX(),   y = e.getY();
            double dx = x-down.getX(), dy = y-down.getY();

            pan(canvas, dx,dy);
            down = e.getPoint();
        }
    }

    protected static void pan(HasViewTransform canvas, double dx, double dy) {
    	AffineTransform vt = canvas.viewTransform();
    	double tx = vt.getTranslateX()+dx;
    	double ty = vt.getTranslateY()+dy;
    	AffineTransform t = AffineTransform.getTranslateInstance(tx,ty);
    	t.scale(vt.getScaleX(), vt.getScaleY());
    	canvas.viewTransform(t, false);
    }
    
    /**
     * Zoom the given display at the given point by the zoom factor,
     * in either absolute (item-space) or screen co-ordinates.
     * @param canvas the canvas to zoom
     * @param zoom the scale factor by which to zoom
     * coordinates, otherwise it will be treated as screen (pixel) coordinates
     */
    protected static void zoom(HasViewTransform canvas, double zoom, boolean provisional) {
    	AffineTransform vt = canvas.viewTransform();
        vt.scale(zoom, zoom);
        canvas.viewTransform(vt, provisional);
    }
    
    /**
     * @see java.awt.event.MouseListener#mouseReleased(java.awt.event.MouseEvent)
     */
    @Override
	public void mouseReleased(MouseEvent e) {
        if (buttonEquals(e, ZOOM_BUTTON) || buttonEquals(e, PAN_BUTTON)) {
            e.getComponent().setCursor(Cursor.getDefaultCursor());
        } 
        
        if (buttonEquals(e, ZOOM_BUTTON)) {
        	HasViewTransform canvas = (HasViewTransform) e.getComponent();
        	canvas.viewTransform(canvas.viewTransform(), false);
        }
    }
    
    private static final boolean buttonEquals(MouseEvent e, int button) {
    	return (e.getModifiers() & button) == button;
    }

	@Override
	public void mouseClicked(MouseEvent e) { 
		if (e.getClickCount() == 2) {
	    	HasViewTransform canvas = (HasViewTransform) e.getComponent();
			canvas.zoomFit();
		}
	}
	
	@Override
	/**When the 'c' key is pressed, pan so the center of the data is at the center of the screen.
	 * When the '+' key is pressed, doubles the current zoom.
	 * When the '-' key is pressed, halves the current zoom.
	 * When the 'z' key is pressed, does a zoom fit.
	 */
	public void keyTyped(KeyEvent e) {
		char c = e.getKeyChar();
		HasViewTransform canvas = (HasViewTransform) e.getComponent();
		JComponent comp = (JComponent) e.getComponent();
		AffineTransform vt = canvas.viewTransform();

		if (c == 'c' || c == 'C') {
			Rectangle vb = comp.getBounds();
			Rectangle2D db = canvas.dataBounds();

			double xmargin = vb.width/vt.getScaleX()-db.getWidth();
			double ymargin = vb.height/vt.getScaleY()-db.getHeight();
			double tx = db.getMinX()-(xmargin/2);
			double ty = db.getMinY()-(ymargin/2);
						
			AffineTransform t = AffineTransform.getScaleInstance(vt.getScaleX(), vt.getScaleY());
			t.translate(-tx,-ty);
			canvas.viewTransform(t, false);
		} else if (c == '+' || c == '=') {
			vt.scale(2, 2);
			canvas.viewTransform(vt, false);
		} else if (c == '-' || c == '_') {
			vt.scale(.5,.5);
			canvas.viewTransform(vt, false);
		} else if (c == 'z' || c == 'Z') {
			canvas.zoomFit();
		}
	}

	
	public void mouseEntered(MouseEvent e) {/*Ignored.*/}
	public void mouseExited(MouseEvent e) {/*Ignored.*/}
	public void mouseMoved(MouseEvent e) {/*Ignored.*/}
	public void keyPressed(KeyEvent e) {/*Ignored*/}
	public void keyReleased(KeyEvent e) {/*Ignored*/}
}
