/*******************************************************************************
 * Copyright (c) 2013 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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
// Copyright (c) 2010 Attila Kovacs 

package crush.array;


import java.util.*;

import kovacs.util.Configurator;
import crush.*;


public abstract class MonoArray<ChannelType extends SimplePixel> extends
		Array<ChannelType, ChannelType> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 3231662319547162814L;

	public MonoArray(String name, int size) {
		super(name, size);
	}

	public MonoArray(String name) {
		super(name);
	}

	@Override
	public void setOptions(Configurator options) {
		super.setOptions(options);	
		if(hasOption("beam")) resolution = option("beam").getDouble() * getSizeUnitValue();
	}
	
	@Override
	public int getPixelCount() {
		return size();
	}
	
	@Override
	public Collection<? extends Pixel> getPixels() {
		return copyGroup();
	}
	
	@Override
	public Collection<? extends Pixel> getMappingPixels() {
		return getObservingChannels().copyGroup().discard(~0);
	}
	
	

}
