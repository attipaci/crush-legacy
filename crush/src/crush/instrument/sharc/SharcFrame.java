/*******************************************************************************
 * Copyright (c) 2014 Attila Kovacs <attila[AT]sigmyne.com>.
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
 *     Attila Kovacs <attila[AT]sigmyne.com> - initial API and implementation
 ******************************************************************************/

package crush.instrument.sharc;

import java.io.DataInput;
import java.io.IOException;

import crush.telescope.HorizontalFrame;
import jnum.Unit;
import jnum.math.Vector2D;

public class SharcFrame extends HorizontalFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6241292032442297935L;
	
	public SharcFrame(SharcScan parent) {
		super(parent);
		data = new float[Sharc.pixels];
	}

	public void readFrom(DataInput in, int index, float norm) throws IOException {
		this.index = index;
		
		for(int i=0; i<Sharc.pixels; i++) data[i] = in.readInt() * norm;
		
		SharcScan sharcScan = (SharcScan) scan;
		
		final double UT = sharcScan.ut_time + index * scan.instrument.samplingInterval;
		MJD = sharcScan.iMJD + UT / Unit.day;
		LST = sharcScan.LST + index * scan.instrument.samplingInterval;
			
		// Enforce the calculation of the equatorial coordinates
		equatorial = null;

		horizontal = sharcScan.equatorial.toHorizontal(sharcScan.site, LST);
		
		double PA = sharcScan.equatorial.getParallacticAngle(sharcScan.site, LST);
		
		sinPA = Math.sin(PA);
		cosPA = Math.cos(PA);
	
		// TODO check the index 2 offset...
		// (index + 2) or (index+4) if quadrature sampling?...
		horizontalOffset = new Vector2D(sharcScan.otf_longitude_step * (index + scanIndexOffset), 0.0);
	
		chopperPosition.zero();
		
		// Add in the scanning offsets...
		horizontalOffset.add(sharcScan.horizontalOffset);	
		
	}
	

	public static int scanIndexOffset = 0;
}
 