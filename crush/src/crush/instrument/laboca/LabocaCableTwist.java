/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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
package crush.instrument.laboca;

import crush.Channel;
import crush.ZeroMeanGains;

/**
 * @author pumukli
 *
 */
public class LabocaCableTwist extends ZeroMeanGains {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1813119272323673961L;
	
	@Override
	public double getRelativeGain(Channel c) throws Exception {
	    final int pin = ((LabocaPixel) c).pin;
		return pin < 0 ? Double.NaN : pin - 13.5;
	}

	@Override
	public void setRawGain(Channel c, double value) throws Exception {
		throw new UnsupportedOperationException("Cannot set cable twist gains.");
	}
	
	
}
