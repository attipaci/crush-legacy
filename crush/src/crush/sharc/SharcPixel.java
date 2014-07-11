/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.sharc;

import java.util.StringTokenizer;

import kovacs.math.Vector2D;
import kovacs.util.Unit;
import crush.Channel;
import crush.array.SimplePixel;

public class SharcPixel extends SimplePixel {
	boolean isBad = false;
		
	public SharcPixel(Sharc instrument, int backendIndex) {
		super(instrument, backendIndex);
		position = getPosition(backendIndex);
	}
	
	static Vector2D getPosition(double backendIndex) {
		return new Vector2D(0.0, (backendIndex - 0.5 * (Sharc.pixels+1)) * spacing); 
	}

	@Override
	public void parseValues(StringTokenizer tokens, int criticalFlags) {
		super.parseValues(tokens, criticalFlags);
		if(isBad) flag(Channel.FLAG_BLIND);
	}
	
	public final static double spacing = 5.0 * Unit.arcsec;
}