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

package crush.gismo;

import java.io.IOException;
import java.util.*;
import java.text.ParseException;

import util.LogFile;
import util.Range;
import util.Unit;
import util.Util;
import util.Vector2D;
import util.astro.AstroTime;
import util.astro.HorizontalCoordinates;
import util.data.WeightedPoint;

public class PointingTable extends ArrayList<PointingEntry> {
	/**
	 * 
	 */
	private static final long serialVersionUID = -416191292991301334L;

	public String fileName;
	
	public double minS2N = 10.0;
	public Range sizeRange = new Range(15.0 * Unit.arcsec, 25.0 * Unit.arcsec);
	
	public double searchRadius = 15.0 * Unit.deg;
	public double timeWindow = 0.5 * Unit.hour;
	
	PointingTable pointings;
	
	public void read(String fileName) throws IOException {
		
		System.err.print("Parsing pointing logs. ");
		
		for(LogFile.Row row : LogFile.read(fileName)) {
			PointingEntry pointing = new PointingEntry();
			try { 
				AstroTime time = AstroTime.forFitsTimeStamp(row.get("id").getValue().substring(0, 10));
				pointing.MJD = time.getMJD() + row.get("UTh").getDouble() * Unit.hour / Unit.day;
				
				pointing.horizontal = new HorizontalCoordinates(
						row.get("AZd").getDouble() * Unit.deg,
						row.get("ELd").getDouble() * Unit.deg
				);	
				
				try {
					pointing.offset = new Vector2D(
							row.get("pnt.X").getDouble() * Unit.arcsec,
							row.get("pnt.Y").getDouble() * Unit.arcsec
					);

					pointing.significance = row.get("src.peak").getDouble() / row.get("src.dpeak").getDouble();

					pointing.FWHM = row.get("src.FWHM").getDouble() * Unit.arcsec;

					if(pointing.significance > minS2N) if(sizeRange.contains(pointing.FWHM)) 
						add(pointing);
				}
				catch(NumberFormatException e) {}
				
			}
			catch(ParseException e) {
				System.err.println("WARNING! Cannot parse date " + row.get("date").getValue());
			}
		}
		
		this.fileName = fileName;
		
		Collections.sort(this);
		
		System.err.println(size() + " valid records found.");	
	}
	
	public int indexBefore(double MJD) {
		for(int i=size(); --i >= 0; ) if(get(i).MJD < MJD) return i;
		return -1;
	}
	
	public Vector2D getIncrement(double MJD, HorizontalCoordinates horizontal, IRAMPointingModel pointingModel) {
		int i0 = indexBefore(MJD);
		if(i0 < 0) throw new IllegalStateException("No pointing data available for the specified time.");
		
		WeightedPoint dX = new WeightedPoint();
		WeightedPoint dY = new WeightedPoint();
		
		double dMJD = 2.0 * timeWindow / Unit.day;
		
		for(int from = i0; --from >= 0; ) {
			if(MJD - get(from).MJD > dMJD) break;

			PointingEntry pointing = get(from);
			Vector2D increment = getIncrementalPointing(pointing, pointingModel);
			double weight = getWeight(MJD - pointing.MJD, horizontal.distanceTo(pointing.horizontal));
			
			dX.average(new WeightedPoint(increment.x, weight));
			dY.average(new WeightedPoint(increment.y, weight));
			
		}
	
		for(int to=i0; ++to<size(); ) {
			if(get(to).MJD - MJD > dMJD) break;
	
			PointingEntry pointing = get(to);
			Vector2D increment = getIncrementalPointing(pointing, pointingModel);
			double weight = getWeight(MJD - pointing.MJD, horizontal.distanceTo(pointing.horizontal));
			
			dX.average(new WeightedPoint(increment.x, weight));
			dY.average(new WeightedPoint(increment.y, weight));
		}
		
		System.err.println("   Incremental Pointing is " + 
				Util.f1.format(dX.value / Unit.arcsec) + "," +
				Util.f1.format(dY.value / Unit.arcsec) + 
				" (quality:" + Util.f2.format(dX.weight) + ")."
				);
		
		return new Vector2D(dX.value, dY.value);
		
	}
	
	public Vector2D getIncrementalPointing(PointingEntry pointing, IRAMPointingModel pointingModel) {		
		Vector2D model = pointingModel.getCorrection(pointing.horizontal);
		model.x = pointing.offset.x - model.x;
		model.y = pointing.offset.y - model.y;
		return model;
	}
	
	public double getWeight(double dMJD, double distance) {
		double devX = dMJD * Unit.day / timeWindow;
		double devT = distance / searchRadius;
		
		return Math.exp(-0.5 * (devX * devX + devT * devT));
	}
	
}



class PointingEntry implements Comparable<PointingEntry> {
	double MJD;
	HorizontalCoordinates horizontal;
	Vector2D offset;
	double significance;
	double FWHM;
	
	public int compareTo(PointingEntry arg0) {
		return Double.compare(MJD, arg0.MJD);
	}
	
	
	
}