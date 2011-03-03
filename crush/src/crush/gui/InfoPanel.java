/*******************************************************************************
 * Copyright (c) 2010 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2007 Attila Kovacs 

package crush.gui;

import java.awt.*;
import javax.swing.*;

public class InfoPanel extends JPanel {
    /**
	 * 
	 */
	private static final long serialVersionUID = 2660465593795584392L;
	int borderwidth = 4, spacing = 4;
    Font font = new Font("SansSerif", Font.PLAIN, 13); 
    String[] lines;
    
    
    public InfoPanel(int size) {
    	//setBorder(BorderFactory.createRaisedBevelBorder());
    	setBorder(BorderFactory.createLineBorder(Color.black));
    	setBackground(Color.WHITE);
    	setFont(font);
    	lines = new String[Math.max(1, size)];
    	for(int i=0; i<lines.length; i++) lines[i] = "";
    }
     
    @Override
	public void paintComponent(Graphics g) {
    	FontMetrics fm = g.getFontMetrics(font);
    	int fontheight=fm.getHeight();
    	int height = 2*borderwidth + lines.length * fontheight + (lines.length-1) * spacing;
 
    	setPreferredSize(new Dimension(1, height));
    	
    	super.paintComponent(g);
    	
    	int offset = fontheight + borderwidth - 2;

    	for(int i=0; i<lines.length; i++) {
    		if(lines[0] != null) g.drawString(lines[i], borderwidth + 5, offset);
    		offset += spacing + fontheight;
    	}
    }
    
    public void clear() {
    	for(int i=0; i<lines.length; i++) lines[i] = "";
    	repaint();    	
    }
    
}
