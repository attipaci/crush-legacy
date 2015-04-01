/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.hawcplus;

import java.util.StringTokenizer;

import kovacs.math.Vector2D;
import kovacs.util.*;

import crush.Channel;
import crush.array.SingleColorPixel;

public class HawcPlusPixel extends SingleColorPixel {
	public int row, col, mux, pin;
	public Vector2D size = defaultSize;
	public double muxGain = 1.0, pinGain = 1.0, colGain = 1.0, rowGain = 1.0, saeGain = 0.0;
	
	// 16 x 8 (rows x cols)
	long readoutOffset = 0L;
	
	
	public HawcPlusPixel(HawcPlus array, int zeroIndex) {
		super(array, zeroIndex+1);
		row = zeroIndex / 8;
		col = zeroIndex % 8;
		
		// mux & pin filled when reading 'wiring.dat'
		
		calcPosition();
		
		// TODO This is just a workaround...
		variance = 1.0;
	}
	
	
	@Override
	public Channel copy() {
		HawcPlusPixel copy = (HawcPlusPixel) super.copy();
		if(size != null) copy.size = (Vector2D) size.clone();
		return copy;		
	}

	public void calcPosition() {
		// ALt/Az maps show this to be correct...
		position = getPosition(size, row, col);
	}
	
	public static Vector2D getPosition(Vector2D size, double row, double col) {
		return new Vector2D(size.x() * col, -size.y() * row);
	}
	
	@Override
	public int getCriticalFlags() {
		return FLAG_DEAD | FLAG_BLIND;
	}
	
	@Override
	public void uniformGains() {
		super.uniformGains();
		muxGain = 1.0;
		pinGain = 1.0;
	}
	
	@Override
	public String toString() {
		return super.toString() + "\t" + Util.f3.format(muxGain);
	}
	
	@Override
	public void parseValues(StringTokenizer tokens) {	
		super.parseValues(tokens);
		if(tokens.hasMoreTokens()) muxGain = Double.parseDouble(tokens.nextToken());
	}
	
	
	public static Vector2D defaultSize = new Vector2D(5.0 * Unit.arcsec, 5.0 * Unit.arcsec);
	
	public final static int FLAG_MUX = 1 << nextSoftwareFlag++;
	public final static int FLAG_PIN = 1 << nextSoftwareFlag++;
	public final static int FLAG_ROW = 1 << nextSoftwareFlag++;
	public final static int FLAG_COL = 1 << nextSoftwareFlag++;
	public final static int FLAG_SAE = 1 << nextSoftwareFlag++;

	
}
