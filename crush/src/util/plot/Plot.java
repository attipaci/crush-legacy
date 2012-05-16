/*******************************************************************************
 * Copyright (c) 2011 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
 * All rights reserved. 
 * 
 * This file is part of crush.
 * 
 *     crush is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     crush is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with crush.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Attila Kovacs <attila_kovacs[AT]post.harvard.edu> - initial API and implementation
 ******************************************************************************/

package util.plot;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.OverlayLayout;

// TODO dragging boundaries to adjust component sizes?

public class Plot<ContentType extends ContentLayer> extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1434685464605442072L;
	
	private ContentArea<? extends ContentType> contentArea;

	public PlotSidePane left, right, top, bottom;
	public PlotPane center,topLeft, topRight, bottomLeft, bottomRight;
	
	private Stroke stroke;
	
	// containers for each sub-panel...
	// getComponentAt/findComponentAt --> the top-most visible component at the position.
	// validate()? (does layout, but how is it different from paintComponent?...)
	
	// TODO
	// constructors:
	//   Plot(float[][])
	//   Plot(double[][])
	//   Plot(Data2D)
	//   Plot(GridImage<?>)
	//   ...
	
	// Top:
	//  * Title
	//  * AxisLabel
	//  * Ruler (adjustable)
	
	// Bottom:
	//	* Ruler (adjustable)
	//	* AxisLabel
	//  * (ColorBar.Vertical, ScaleBar, AxisLabel)
	//  * (...)
	
	// Left:
	//	* AxisLabel
	//  * Ruler (adjustable)
	
	// Right:
	//	* Ruler (adjustable)
	//  * AxisLabel
	//  * (ColorBar.Horizontal, ...)
	//  * (...)
	
	GridBagLayout layout = new GridBagLayout();
	
	public Plot() {
		setLayout(layout);

		// The central plot area
		center = new PlotPane(this);
		center.setLayout(new OverlayLayout(center));
		add(center, 1, 1, GridBagConstraints.BOTH, 1.0, 1.0);
	
		// The sides...
		left = new PlotSidePane(this, Plot.LEFT_SIDE);
		add(left, 0, 1, GridBagConstraints.VERTICAL, 0.0, 0.0);
					
		right = new PlotSidePane(this, Plot.RIGHT_SIDE);
		add(right, 2, 1, GridBagConstraints.VERTICAL, 0.0, 0.0);
		
		top = new PlotSidePane(this, Plot.TOP_SIDE);
		add(top, 1, 0, GridBagConstraints.HORIZONTAL, 0.0, 0.0);
		
		bottom = new PlotSidePane(this, Plot.BOTTOM_SIDE);
		add(bottom, 1, 2, GridBagConstraints.HORIZONTAL, 0.0, 0.0);
	
		// The corners...
		topLeft = new PlotPane(this);
		add(topLeft, 0, 0, GridBagConstraints.BOTH, 0.0, 0.0);	
		
		topRight = new PlotPane(this);
		add(topRight, 2, 0, GridBagConstraints.BOTH, 0.0, 0.0);
		
		bottomLeft = new PlotPane(this);
		add(bottomLeft, 0, 2, GridBagConstraints.BOTH, 0.0, 0.0);
	
		bottomRight = new PlotPane(this);
		add(bottomRight, 2, 2, GridBagConstraints.BOTH, 0.0, 0.0);
		
		defaults();
	}

	@Override
	public void setBackground(Color color) {
		super.setBackground(color);
		for(Component c : getComponents()) c.setBackground(color);
	}
	
	public PlotPane getCenterPane() { return center; }
	
	public PlotPane getLeftane() { return left; }
	
	public PlotPane getRightPane() { return right; }
	
	public PlotPane getTopPane() { return top; }
	
	public PlotPane getBottomPane() { return bottom; }
	
	public PlotPane getTopLeftPane() { return topLeft; }
	
	public PlotPane getTopRightPane() { return topRight; }
	
	public PlotPane getBottomLeftPane() { return bottomLeft; }
	
	public PlotPane getBottomRightPane() { return bottomRight; }
	
	public void defaults() {
		setFont(defaultFont);
	}
	
	public void setTransparent(boolean value) {
		for(Component c : getComponents()) if(c instanceof PlotPane) 
			((PlotPane) c).setTransparent(value);
	}
	
	@Override
	public void paintComponent(Graphics g) {
		contentArea.setSize(center.getSize());
		super.paintComponent(g);
	}
	
	public Rectangle2D getCoordinateBounds(int side) {
		
		Point2D c1 = null, c2 = null; 
		
		switch(side) {
		case(Plot.LEFT_SIDE):
			c1 = new Point2D.Double(0.0, getHeight());
			c2 = new Point2D.Double(0.0, 0.0);
			break;
		case(Plot.RIGHT_SIDE):
			c1 = new Point2D.Double(getWidth(), getHeight());
			c2 = new Point2D.Double(getWidth(), 0.0);
			break;
		case(Plot.TOP_SIDE):
			c1 = new Point2D.Double(0, 0.0);
			c2 = new Point2D.Double(getWidth(), 0.0);
			break;
		case(Plot.BOTTOM_SIDE):
			c1 = new Point2D.Double(0, 0.0);
			c2 = new Point2D.Double(getWidth(), 0.0);
			break;
		default: return null;
		}
		
		contentArea.toCoordinates(c1);
		contentArea.toCoordinates(c2);
		
		return new Rectangle2D.Double(
			Math.min(c1.getX(), c2.getX()),
			Math.min(c1.getY(), c2.getY()),
			Math.abs(c1.getX() - c2.getX()),
			Math.abs(c1.getY() - c2.getY())
		);
	}
	
	
	public ContentArea<? extends ContentType> getContent() { return contentArea; }
	
	public void setContent(ContentArea<? extends ContentType> area) { 
		if(this.contentArea != null) center.remove(this.contentArea);
		this.contentArea = area; 
		center.add(area);
	}
	
	private void add(JComponent component, int x, int y, int fill, double weightx, double weighty) {	
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = weightx;
        c.weighty = weighty;
        c.gridx = x;
        c.gridy = y;
        c.fill = fill;
        layout.setConstraints(component, c);
        add(component);
	}
	
	public Stroke getStroke() { return stroke; }

	public void setStroke(Stroke s) { this.stroke = s; }
	
	public float getFontSize() { return getFont().getSize2D(); }
	
	
	public void setFontSize(float size) {
		setFont(getFont().deriveFont(size));
	}
	
	public void setFontBold(boolean value) {
		int style = getFont().getStyle();
		if(value) style |= Font.BOLD;
		else style &= ~Font.BOLD;
		setFont(getFont().deriveFont(style));
	}
	
	public void setFontItalic(boolean value) {
		int style = getFont().getStyle();
		if(value) style |= Font.ITALIC;
		else style &= ~Font.ITALIC;
		setFont(getFont().deriveFont(style));
	}
	
	public boolean isFontItalic() {
		return (getFont().getStyle() & Font.ITALIC) != 0;
	}
	
	public boolean isFontBold() {
		return (getFont().getStyle() & Font.BOLD) != 0;
	}
	
	public static Font defaultFont = new Font("SansSerif", Font.BOLD, 15);
	
	public final static int SIDE_UNDEFINED = -1;
	public final static int TOP_SIDE = 0;
	public final static int BOTTOM_SIDE = 1;
	public final static int LEFT_SIDE = 2;
	public final static int RIGHT_SIDE = 3;
	
	
}
