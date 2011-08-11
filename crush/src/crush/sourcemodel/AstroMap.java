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

package crush.sourcemodel;

import nom.tam.fits.*;
import nom.tam.util.*;



import java.io.*;
import java.util.*;

import crush.CRUSH;
import crush.Instrument;
import crush.Scan;

import util.*;
import util.data.Data2D;
import util.data.DataPoint;
import util.data.GridImage;
import util.data.Index2D;
import util.data.Statistics;
import util.data.WeightedPoint;


public class AstroMap extends AstroImage {
	public double[][] weight, count;
	
	public Vector<Scan<?, ?>> scans = new Vector<Scan<?, ?>>();
	public String commandLine;
	
	public int generation = 0;
	public double integrationTime = 0.0;	
	public double weightFactor = 1.0;
 
	public double filterBlanking = Double.NaN;
	public double clippingS2N = Double.NaN;
		
	private Vector2D reuseOffset = new Vector2D();

	public AstroMap() { 
		contentType = "Signal";
	}
	
	public AstroMap(Instrument<?> instrument) { 
		this(); 
		this.instrument = instrument;
	}
	
	public AstroMap(String fileName, Instrument<?> instrument) throws Exception { 
		this(instrument);
		read(fileName);		
	}

	public AstroMap(int i, int j) { 
		this();
		setSize(i, j);
	}
	
	@Override
	public Object clone() {
		AstroMap clone = (AstroMap) super.clone();
		clone.reuseOffset = new Vector2D();
		return clone;
	}
	
	@Override
	public void copyImageOf(Data2D other) {
		super.copyImageOf(other);
		if(!(other instanceof AstroMap)) return;
		
		AstroMap image = (AstroMap) other;
		
		// Make a copy of the fundamental data
		weight = (double[][]) copyOf(image.weight);
		count = (double[][]) copyOf(image.count);
	}
	
	@Override
	public void setImage(Data2D other) {
		super.setImage(other);
		if(!(other instanceof AstroMap)) return;
		
		AstroMap image = (AstroMap) other;
		
		// Make a copy of the fundamental data
		weight = image.weight;
		count = image.count;
	}
	
	@Override
	public void setSize(int i, int j) {
		super.setSize(i, j);
		weight = new double[i][j];
		count = new double[i][j];
	}
	
	@Override
	public String getPixelInfo(int i, int j) {
		if(!isValid(i, j)) return "";
		String type = "";
		if(contentType != null) if(contentType.length() != 0) type = contentType + "> ";
		return type + Util.getDecimalFormat(getS2N(i, j)).format(data[i][j]) + " +- " + Util.s2.format(getRMS(i, j)) + " " + unit.name;
	}
	
	
	@Override
	protected void crop(int imin, int jmin, int imax, int jmax) {
		double[][] oldweight = weight;
		double[][] oldcount = count;
		
		final int fromi = Math.max(0, imin);
		final int fromj = Math.max(0, jmin);
		final int toi = Math.min(sizeX()-1, imax);
		final int toj = Math.min(sizeY()-1, jmax);
		
		super.crop(imin, jmin, imax, jmax);
			
		for(int i=fromi, i1=fromi-imin; i<=toi; i++, i1++) for(int j=fromj, j1=fromj-jmin; j<=toj; j++, j1++) {
			weight[i1][j1] = oldweight[i][j];
			count[i1][j1] = oldcount[i][j];
		}
	}


	@Override
	public void clear() {
		super.clear();
		for(int i=sizeX(); --i >= 0; ) {
			Arrays.fill(weight[i], 0.0);
			Arrays.fill(count[i], 0.0);
		}
	}

	@Override
	public void reset() {
		super.reset();
		integrationTime = 0.0;
	}
	
	@Override
	public double weightAt(int i, int j) { return weight[i][j]; }
	
	@Override
	public void scale(int i, int j, double factor) {
		data[i][j] *= factor;
		weight[i][j] /= factor * factor;
	}

	public final void addPointAt(Vector2D mapOffset, final double value, final double g, final double w, final double time) {
		double x = mapOffset.x;
		double y = mapOffset.y;
		toIndex(mapOffset);
		addPointAt((int)Math.round(mapOffset.x), (int)Math.round(mapOffset.y), value, g, w, time);
		mapOffset.x = x;
		mapOffset.y = y;
	}
	
	public final void addPointAt(SphericalCoordinates coords, final double value, final double g, final double w, final double time) {
		getProjection().project(coords, reuseOffset);
		toIndex(reuseOffset);
		addPointAt((int)Math.round(reuseOffset.x), (int)Math.round(reuseOffset.y), value, g, w, time);
	}

	public final void addPointAt(final int i, final int j, final double value, final double g, double w, final double time) {
		w *= g;
		data[i][j] += w * value;
		weight[i][j] += w * g;
		count[i][j] += time;
	}
	
	public synchronized void addDirect(AstroMap map, double w) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(map.flag[i][j] == 0) {
			data[i][j] += w * map.weight[i][j] * map.data[i][j];
			weight[i][j] += w * map.weight[i][j];
			count[i][j] += map.count[i][j];
			flag[i][j] = 0;
		}
		integrationTime += map.integrationTime;
	}

	// Normalize assuming weighting was sigma weighting
	public void normalize() {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; )  {
			if(weight[i][j] <= 0.0) {
				data[i][j] = 0.0;
				weight[i][j] = 0.0;
				flag[i][j] = 1;
			} 
			else {
				data[i][j] /= weight[i][j];	
				flag[i][j] = 0;	    
			}	    
		}
	}

	@Override
	protected void sanitize(int i, int j) {
		flag[i][j] |= 1;
		data[i][j] = 0.0;
		weight[i][j] = 0.0;
	}
	
	public void applyCorrection(double filtering, double[][] significance) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(weight[i][j] > 0.0) if(significance[i][j] <= clippingS2N) {
			data[i][j] /= filtering;
			weight[i][j] *= filtering * filtering;
		}	
	}

	// It's important to completely reset clipped points, otherwise they can be used...
	// One possibility for future is to raise flag only, then call sanitize()
	public void clipAboveRelativeRMS(double maxRelativeRMS) {
		clipAboveRelativeRMS(maxRelativeRMS, 0.0);
	}
	
	public void clipAboveRelativeRMS(double maxRelativeRMS, double refPercentile) {
		double[][] rms = getRMSImage().data;
		double maxRMS = maxRelativeRMS * new AstroImage(rms).select(refPercentile);
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(rms[i][j] > maxRMS) flag[i][j] = 1;
	}
	
	public void clipAboveRMS(double value) {
		double[][] rms = getRMSImage().data;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(rms[i][j] > value) flag[i][j] = 1;
	}
	
	public void clipBelowRelativeExposure(double minRelativeExposure) {
		clipBelowRelativeExposure(minRelativeExposure, 1.0);
	}
	
	public void clipBelowRelativeExposure(double minRelativeExposure, double refPercentile) {
		double minIntTime = minRelativeExposure * new AstroImage(count).select(refPercentile);
		clipBelowExposure(minIntTime);
	}
	
	public void clipBelowExposure(double minIntTime) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(count[i][j] < minIntTime) flag[i][j] = 1;
	}

	public void s2nClipBelow(double level) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] / getRMS(i,j) < level) flag[i][j] = 1;
	}

	public void s2nClipAbove(double level) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			if(data[i][j] / getRMS(i,j) > level) flag[i][j] = 1;
	}
	
	public boolean[][] getMask(double minS2N, int minNeighbours) {
		boolean[][] mask = new boolean[sizeX()][sizeY()];
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) if(data[i][j] / getRMS(i,j) > minS2N) mask[i][j] = true;
		
		if(minNeighbours > 0) {
			boolean[][] cleaned = new boolean[sizeX()][sizeY()];
			
			for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(mask[i][j]) {
				int neighbours = -1;
				final int fromi = Math.max(0, i-1);
				final int toi = Math.min(sizeX(), i+1);
				final int fromj = Math.max(0, j-1);
				final int toj = Math.min(sizeY(), j+1);
				for(int i1=fromi; i1<toi; i1++) for(int j1=fromj; j1<toj; j1++) if(mask[i1][j1]) neighbours++;
				if(neighbours >= minNeighbours) cleaned[i][j] = true;
			}
			return cleaned;
		}
		
		return mask;
	}

	@Override
	public void smooth(double[][] beam) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getSmoothed(beam, beamw);
		count = getTimeImage().getSmoothed(beam, null);
		weight = beamw;
	}
	
	@Override
	public void fastSmooth(double[][] beam, int stepX, int stepY) {
		double[][] beamw = new double[sizeX()][sizeY()];
		data = getFastSmoothed(beam, beamw, stepX, stepY);
		count = getTimeImage().getFastSmoothed(beam, null, stepX, stepY);
		weight = beamw;
	}
	
	@Override
	public void resample(GridImage<?> from) {
			
		if(!(from instanceof AstroMap)) {
			super.resample(from);
			return;
		}
		
		if(verbose) System.err.println(" Resampling map to "+ sizeX() + "x" + sizeY() + ".");
		
		AstroMap fromMap = (AstroMap) from;
		AstroImage fromWeight = fromMap.getWeightImage();
		AstroImage fromCount = fromMap.getTimeImage();
		
		final Vector2D v = new Vector2D();
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			v.set(i, j);
			toOffset(v);
			from.toIndex(v);
			
			//System.err.println(i + "," + j + " <--" + Util.f1.format(v.x) + "," + Util.f1.format(v.y));
		
			data[i][j] = fromMap.valueAtIndex(v.x, v.y);
			weight[i][j] = fromWeight.valueAtIndex(v.x, v.y);
			count[i][j] = fromCount.valueAtIndex(v.x, v.y);
			
			if(Double.isNaN(data[i][j])) flag[i][j] = 1;
			else flag[i][j] = 0;
		}
	}
	
	public AstroImage getFluxImage() {
		return getImage(data, "Flux", unit);
	}
	
	public AstroImage getS2NImage() {
		AstroImage image = getRMSImage();
		image.contentType = "S/N";
		image.unit = Unit.unity;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) image.data[i][j] = data[i][j] / image.data[i][j]; 
		return image;
	}
	
	public AstroImage getRMSImage() {
		double[][] rms = new double[sizeX()][sizeY()];
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) rms[i][j] = getRMS(i,j);
		return getImage(rms, "Noise", unit);
	}
	
	public AstroImage getWeightImage() { 
		return getImage(weight, "Weight", new Unit("[" + unit.name + "]**(-2)", 1.0 / (unit.value * unit.value)));
	}
	
	public AstroImage getTimeImage() { 
		return getImage(count, "Exposure",new Unit("s/pixel", Unit.s));
	}
	
	private AstroImage getImage(double[][] data, String contentType, Unit unit) {
		AstroImage image = (AstroImage) clone();
		image.data = data;
		image.contentType = contentType;
		image.unit = unit;
		return image;		
	}

	public final double getRMS(final Index2D index) {
		return getRMS(index.i, index.j);
	}
	
	public final double getRMS(final int i, final int j) {
		return 1.0 / Math.sqrt(weight[i][j]);		
	}
	
	public final double getS2N(final Index2D index) {
		return getS2N(index.i, index.j);
	}
	
	public final double getS2N(final int i, final int j) {
		return data[i][j] / getRMS(i,j);		
	}
	
	public double getTypicalRMS() {
		double sumw = 0.0;
		int n = 0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			sumw += 1.0 / weight[i][j];
			n++;
		}
		return Math.sqrt(n/sumw);
	}
	
	public void filterCorrect() {
		filterCorrect(instrument.resolution, getSkip(filterBlanking));
	}
	
	public void undoFilterCorrect() {
		undoFilterCorrect(instrument.resolution, getSkip(filterBlanking));
	}
				
	public int[][] getSkip(double blankingValue) {
		int[][] skip = (int[][]) copyOf(flag);
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(data[i][j] / getRMS(i,j) > blankingValue) skip[i][j] = 1;
		return skip;
	}

	@Override
	public void filterAbove(double extendedFWHM, int[][] skip) {
		super.filterAbove(extendedFWHM, skip);
		double r = getImageFWHM() / extendedFWHM;
		weightScale(1.0 / (1.0 - r*r));
	}
	
	public void filterAbove(double extendedFWHM, double blankingValue) {
		filterAbove(extendedFWHM, getSkip(blankingValue));
		filterBlanking = blankingValue;
	}

	@Override
	public void fftFilterAbove(double extendedFWHM, int[][] skip) {
		super.filterAbove(extendedFWHM, skip);
		double r = getImageFWHM() / extendedFWHM;
		weightScale(1.0 / (1.0 - r*r));
	}
	
	public void fftFilterAbove(double extendedFWHM, double blankingValue) {
		fftFilterAbove(extendedFWHM, getSkip(blankingValue));
		filterBlanking = blankingValue;
	}


	public double getMeanIntegrationTime() {
		double sum = 0.0, sumw = 0.0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			sum += count[i][j] * weight[i][j];
			sumw += weight[i][j];
		}
		return sum > 0.0 ? sum/sumw : 0.0;
	}
	
	@Override
	public void scale(final double scalar) {
		if(scalar == 1.0) return;		
		final double scalar2 = scalar*scalar;
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			data[i][j] *= scalar;
			weight[i][j] /= scalar2;
		}
	}  
	
	@Override
	public double mean() {
		double sum = 0.0, sumw = 0.0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			final double w = weight[i][j];
			sum += w * data[i][j];
			sumw += w;
		}
		return sum / sumw;
	}
	
	@Override
	public double median() {
		WeightedPoint[] point = new WeightedPoint[countPoints()];

		if(point.length == 0) return 0.0;

		int k=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0)
			point[k++] = new WeightedPoint(data[i][j], weight[i][j]);
		
		return Statistics.median(point).value;
	}
	
	public void rmsScale(final double scalar) {
		final double scalar2 = scalar*scalar;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) weight[i][j] /= scalar2;
	}

	public void weightScale(double scalar) {
		if(scalar == 1.0) return;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) weight[i][j] *= scalar;
	}

	public void weight(boolean robust) {
		double weightCorrection = robust ? getRobustChi2() : getChi2();
		weightScale(weightCorrection);
		weightFactor *= weightCorrection;
	}

	// Return to calculated weights...
	public void dataWeight() {
		weightScale(1.0/weightFactor);
		weightFactor = 1.0;
	}

	public double getChi2(boolean robust) {
		return robust ? getRobustChi2() : getChi2();
	}
	
	protected double getRobustChi2() {
		float[] chi2 = new float[sizeX() * sizeY()];
		if(chi2.length == 0) return 0.0;
		
		int k=0;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			final float s2n = (float) (data[i][j] / getRMS(i,j));
			chi2[k++] = s2n * s2n;
		}
		
		
		// median(x^2) = 0.454937 * sigma^2 
		return k > 0 ? 0.454937 / Statistics.median(chi2, 0, k) : Double.NaN;	
	}

	protected double getChi2() {
		double chi2 = 0.0;
		int n=0;
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			double s2n = data[i][j] / getRMS(i,j);
			chi2 += s2n * s2n;
			n++;
		}

		return n / chi2;	
	}
		
	public boolean containsNaN() {
		boolean hasNaN = false;
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) {
			if(Double.isNaN(data[i][j])) hasNaN = true;
			if(Double.isNaN(weight[i][j])) hasNaN = true;
		}
		return hasNaN;
	}


	// derive the MEM correction if MEM modeling
	
	public void MEM(double lambda, boolean forceNonNegative) {
		//double[][] smoothed = getConvolvedTo(data, 5.0*instrument.resolution);
		double[][] smoothed = new double[sizeX()][sizeY()];
		//if(forceNonNegative) for(int x=0; x<sizeX(); x++) for(int y=0; y<sizeY(); y++) if(smoothed[x][y] < 0.0) smoothed[x][y] = 0.0;
		MEM(smoothed, lambda);
	}
		
	public void MEM(double[][] model, double lambda) {
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) if(flag[i][j] == 0) {
			double sigma = Math.sqrt(1.0/weight[i][j]);
			double memValue = Math.hypot(sigma, data[i][j]) / Math.hypot(sigma, model[i][j]) ;
			data[i][j] -= Math.signum(data[i][j]) * lambda * sigma * Math.log(memValue);
		}
	}
	
	
	public Fits getFits() throws HeaderCardException, FitsException, IOException {
		FitsFactory.setUseHierarch(true);
		Fits fits = new Fits();	

		fits.addHDU(createHDU());
		fits.addHDU(getTimeImage().createHDU());
		fits.addHDU(getRMSImage().createHDU());
		fits.addHDU(getS2NImage().createHDU());

		editHeader(fits);
		
		if(instrument != null) if(instrument.hasOption("write.scandata")) 
			for(Scan<?,?> scan : scans) fits.addHDU(scan.getSummaryHDU(instrument.options));

		return fits;
	}

	public Fits getFits(String name) throws FitsException, IOException {
		FitsFactory.setUseHierarch(true);
		Fits fits;
	
		try { 
			fits = new Fits(new File(name)); 
			fileName = name;
		}
		catch(Exception e) { 
			fileName = CRUSH.workPath + name;
			fits = new Fits(new File(fileName)); 
		}
		
		return fits;
	}

	@Override
	public void read(String name) throws Exception {
		// Get the coordinate system information
		BasicHDU[] HDU = getFits(name).read();
		parseHeader(HDU[0].getHeader());
		readCrushData(HDU);
	}

	@Override
	public void parseHeader(Header header) throws Exception {	
		weightFactor = 1.0;
		filterBlanking = Double.NaN;
		clippingS2N = Double.NaN;
		integrationTime = Double.NaN;
		
		super.parseHeader(header);
	}

	@Override
	protected void parseCrushHeader(Header header) throws HeaderCardException {
		super.parseCrushHeader(header);
		weightFactor =  header.getDoubleValue("XWEIGHT", 1.0);
		integrationTime = header.getDoubleValue("INTEGRTN", Double.NaN) * Unit.s;
		filterBlanking = header.getDoubleValue("FLTRBLNK", header.getDoubleValue("MAP_XBLK", Double.NaN));
		clippingS2N = header.getDoubleValue("CLIPS2N", header.getDoubleValue("MAP_CLIP", Double.NaN));		
	}

	private void readCrushData(BasicHDU[] HDU) throws FitsException {
		setImage(HDU[0]);
		getTimeImage().setImage(HDU[1]);
		
		AstroImage noise = getWeightImage();
		noise.unit = unit;
		noise.setImage(HDU[2]);
		
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0; ) weight[i][j] = 1.0 / (weight[i][j] * weight[i][j]); 
	}


	public void write() throws HeaderCardException, FitsException, IOException { write(getFits()); }

	public void write(Fits fits) throws HeaderCardException, FitsException, IOException {
		if(fileName == null) fileName = CRUSH.workPath + File.separator + sourceName + ".fits";  
		BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream(fileName));
		fits.write(file);
		System.err.println(" Written " + fileName);
	}
	
	@Override
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException, IOException {
		super.editHeader(cursor);
		
		// Add the command-line reduction options
		if(commandLine != null) {
			StringTokenizer args = new StringTokenizer(commandLine);
			cursor.add(new HeaderCard("ARGS", args.countTokens(), "The number of arguments passed from the command line."));
			int i=1;
			while(args.hasMoreTokens()) Util.addLongFitsKey(cursor, "ARG" + (i++), args.nextToken(), "Command-line argument.");
		}
		
		cursor.add(new HeaderCard("V2JY", instrument.janskyPerBeam(), "1 Jy/beam in instrument data units."));	
		cursor.add(new HeaderCard("INTEGRTN", integrationTime / Unit.s, "The total integration time in seconds."));
		
		if(!Double.isNaN(filterBlanking))
			cursor.add(new HeaderCard("FLTRBLNK", filterBlanking, "The S/N blanking of LSS filter."));
		if(!Double.isNaN(clippingS2N))
			cursor.add(new HeaderCard("CLIPS2N", clippingS2N, "The S/N clipping level used in reduction."));
	
		// The number of scans contributing to this image
		cursor.add(new HeaderCard("SCANS", scans.size(), "The number of scans in this composite image."));			
		
	}
	
	
	public void printHeader() {
		header.dumpHeader(System.out);
	}      

	
	public void despike(double significance) {
		double[][] neighbours = {{ 0, 1, 0 }, { 1, 0, 1 }, { 0, 1, 0 }};
		AstroMap diff = (AstroMap) copy();
		diff.smooth(neighbours);
		
		WeightedPoint point = new WeightedPoint();
		WeightedPoint surrounding = new WeightedPoint();
		
		for(int i=0; i<sizeX(); i++) for(int j=0; j<sizeY(); j++) if(flag[i][j] == 0) {
			point.value = data[i][j];
			point.weight = weight[i][j];
			surrounding.value = diff.data[i][j];
			surrounding.weight = diff.weight[i][j];
			point.subtract(surrounding);
			if(point.significance() > significance) flag[i][j] = 1;			
		}	
	}
	
	
	public DataPoint getFlux(Region region) {
		final Bounds bounds = region.getBounds(this);
		double flux = 0.0, var = 0.0;

		double A = 1.0 / getPointsPerSmoothingBeam();

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j))  {
				flux += data[i][j];
				var += 1.0 / weight[i][j];
			}
		
		return new DataPoint(A * flux, A * Math.sqrt(var));
	}
	

	@Override
	public double getLevel(Region region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0, sumw = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) {
				sum += weight[i][j] * data[i][j];
				sumw += weight[i][j];
			}
		return sum / sumw;			
	}

	public double getMeanNoise(Region region) {
		final Bounds bounds = region.getBounds(this);
		double var = 0.0;
		int n = 0;

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0)  if(region.isInside(grid, i, j)) {
				double rms = getRMS(i,j);
				var += rms * rms;
				n++;
			}
		var /= (n-1);
		
		return Math.sqrt(var);
	}
	
	public double getMeanExposure(Region region) {	
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		int n = 0;

		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(flag[i][j] == 0) if(region.isInside(grid, i, j)) {
				sum += count[i][j];
				n++;
			}
		
		return n > 0 ? sum / n : 0.0;
	}
	
	@Override
	public int clean(double[][] beam, double gain, double replacementFWHM) {
	    	return clean(getS2NImage(), beam, gain, replacementFWHM);
	}
	
	@Override
	public String toString() {	
		String info = super.toString() +
			"  Noise Estimate from: " + (weightFactor == 1.0 ? "data" : "image (" + Util.f2.format(1.0 / weightFactor) + "x data)") + "\n"; 

		return info;
	}
	
}





