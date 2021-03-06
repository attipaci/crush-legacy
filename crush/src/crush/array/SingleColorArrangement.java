/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.array;

import java.util.List;

import crush.Pixel;
import crush.instrument.ColorArrangement;

public class SingleColorArrangement<ChannelType extends SingleColorPixel> extends ColorArrangement<ChannelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3711770466718770949L;

	@Override
	public int getPixelCount() {
		return getInstrument().size();
	}

	@Override
	public List<? extends Pixel> getPixels() {
		return getInstrument();
	}

	@Override
	public List<? extends Pixel> getMappingPixels(int keepFlags) {
		return getInstrument().getObservingChannels().createGroup().discard(~keepFlags);
	}

}
