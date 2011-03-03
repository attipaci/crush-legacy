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
// Copyright (c) 2008 Attila Kovacs 

package util;

import java.text.NumberFormat;
import java.util.*;

public class Range {
	public double min, max;
	
	public Range() { empty(); }
	
	public Range(double minValue, double maxValue) {
		setRange(minValue, maxValue);
	}
	
	@Override
	public boolean equals(Object o) {
		if(o instanceof Range) {
			Range range = (Range) o;
			if(Double.compare(range.min, min) != 0) return false;
			if(Double.compare(range.max, max) != 0) return false;
			return true;
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		return HashCode.get(min) ^ HashCode.get(max);
	}
	
	public void empty() {
		min=Double.POSITIVE_INFINITY; max=Double.NEGATIVE_INFINITY;		
	}
	
	public void full() {
		min=Double.NEGATIVE_INFINITY; max=Double.POSITIVE_INFINITY;	
	}
	
	public void setRange(double minValue, double maxValue) {
		min = minValue;
		max = maxValue;
	}
	
	public void scale(double value) {
		min *= value;
		max *= value;
	}
	
	public boolean contains(double value) {
		if(Double.isNaN(value)) return min == Double.NEGATIVE_INFINITY && max == Double.POSITIVE_INFINITY;
		return value >= min && value < max;
	}
	
	public boolean contains(Range range) {
		return contains(range.min) && contains(range.max);
	}
	
	public boolean intersects(Range range) {
		return contains(range.min) || contains(range.max) || range.contains(this);
	}
	
	public static Range parse(String text) {
		return parse(text, false);
	}
	
	public void include(double value) {
		if(value < min) min = value;
		if(value > max) max = value;		
	}
	
	public static Range parse(String text, boolean isPositive) {
		Range range = new Range();
		StringTokenizer tokens = new StringTokenizer(text, " \t:" + (isPositive ? "-" : ""));
		
		if(tokens.hasMoreTokens()) {
			String spec = tokens.nextToken();
			if(spec.equals("*")) range.min = Double.NEGATIVE_INFINITY;
			else range.min = Double.parseDouble(spec);
		}
		if(tokens.hasMoreTokens()) {
			String spec = tokens.nextToken();
			if(spec.equals("*")) range.max = Double.POSITIVE_INFINITY;
			else range.max = Double.parseDouble(spec);
		}
		return range;	
	}
	
	public double span() {
		return max - min;
	}
	
	@Override
	public String toString() {
		return min + ":" + max;
	}

	public String toString(NumberFormat nf) {
		return nf.format(min) + ":" + nf.format(max);
	}
	
	public void fullRange() {
		min = Double.NEGATIVE_INFINITY;
		max = Double.POSITIVE_INFINITY;
	}
	
}
