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

package util.data;

import java.io.*;

import nom.tam.fits.*;
import nom.tam.util.*;

import crush.CRUSH;
import crush.sourcemodel.GaussianSource;
import util.Complex;
import util.ConfidenceCalculator;
import util.CoordinatePair;
import util.Projection2D;
import util.Range;
import util.Unit;
import util.Util;
import util.Vector2D;

public abstract class GridImage<CoordinateType extends CoordinatePair> extends Data2D {
	Grid2D<CoordinateType> grid;
	
	// TODO make private...
	public double smoothFWHM;  
	public double extFilterFWHM = Double.NaN;
	public double correctingFWHM = Double.NaN;	

	public String fileName;
	public String creator = "CRUSH", creatorVersion = CRUSH.getFullVersion();
	
	public Header header;
	
	public GridImage() {
	}
	
	public GridImage(int sizeX, int sizeY) {
		super(sizeX, sizeY);
	}
	
	public GridImage(double[][] data) {
		super(data);
	}
	
	public GridImage(double[][] data, int[][] flag) {
		super(data, flag);
	}
	
	public Grid2D<CoordinateType> getGrid() { return grid; }
	
	public void setGrid(Grid2D<CoordinateType> grid) { 
		this.grid = grid; 
		double fwhm = Math.sqrt(grid.getPixelArea()) / fwhm2size;
		if(smoothFWHM < fwhm) smoothFWHM = fwhm;	
	}

	public void setResolution(double value) { 
		getGrid().setResolution(value);
		smoothFWHM = Math.max(smoothFWHM, value / fwhm2size);
	}
	
	public Vector2D getResolution() {
		return getGrid().getResolution();
	}
	
	
	public Projection2D<CoordinateType> getProjection() { return getGrid().getProjection(); }
	
	public void setProjection(Projection2D<CoordinateType> projection) { getGrid().projection = projection; }
	
	public CoordinateType getReference() { return getGrid().getReference(); }
	
	public void setReference(CoordinateType reference) { getGrid().setReference(reference); }
		
	protected GridImage<CoordinateType> getImage(double[][] data, String contentType, Unit unit) {
		@SuppressWarnings("unchecked")
		GridImage<CoordinateType> image = (GridImage<CoordinateType>) clone();
		image.setData(data);
		image.contentType = contentType;
		image.unit = unit;
		return image;		
	}

	public GridImage<CoordinateType> getFluxImage() {
		return getImage(getData(), "Flux", unit);
	}
	
	public double getPixelArea() {
		return getGrid().getPixelArea();
	}
	
	
	public double getPointsPerSmoothingBeam() {
		return Math.max(1.0, fwhm2size * smoothFWHM / getGrid().pixelSizeX()) * Math.max(1.0, fwhm2size * smoothFWHM / getGrid().pixelSizeY());
	}
	
	public double getImageFWHM() {
		return smoothFWHM;
	}
	
	public double getImageBeamArea() {
		final double A = fwhm2size * getImageFWHM();
		return A*A;		
	}
	
	public double getExtFilterCorrectionFactor(double FWHM) {
		if(Double.isNaN(extFilterFWHM)) return 1.0;
		double effectiveFilterFWHM2 = FWHM*FWHM + extFilterFWHM*extFilterFWHM;
		double effectiveFWHM2 = FWHM*FWHM + smoothFWHM*smoothFWHM;
		return 1.0 / (1.0 - effectiveFWHM2/effectiveFilterFWHM2);
	}
	
	public void reset() {
		clear();
		smoothFWHM = Math.sqrt(getPixelArea()) / fwhm2size;
		extFilterFWHM = Double.NaN;
		correctingFWHM = Double.NaN;
	}

	// In 1-D at least 3 points per beam are needed to separate a positioned point
	// source from an extended source...
	// Similarly 9 points per beam are necessary for 2-D...
	public double countIndependentPoints(double area) {
		double smoothArea = fwhm2size * fwhm2size * smoothFWHM * smoothFWHM;
		double filterArea = fwhm2size * fwhm2size * extFilterFWHM * extFilterFWHM;
		double beamArea = this.getImageBeamArea();
		
		// Account for the filtering correction.
		double eta = 1.0;
		if(Double.isNaN(extFilterFWHM) && extFilterFWHM > 0.0) eta -= smoothArea / filterArea;
		double iPointsPerBeam = eta * Math.min(9.0, smoothArea / getPixelArea());
		
		return Math.ceil((1.0 + area/beamArea) * iPointsPerBeam);
	}
	
	public void crop(double dXmin, double dYmin, double dXmax, double dYmax) {
		if(dXmin > dXmax) { double temp = dXmin; dXmin = dXmax; dXmax=temp; }
		if(dYmin > dYmax) { double temp = dYmin; dYmin = dYmax; dYmax=temp; }
	
		if(verbose) System.err.println("Will crop to " + ((dXmax - dXmin)/Unit.arcsec) + "x" + ((dYmax - dYmin)/Unit.arcsec) + " arcsec.");
			
		Index2D c1 = getIndex(new Vector2D(dXmin, dYmin));
		Index2D c2 = getIndex(new Vector2D(dXmax, dYmax));
		
		crop(c1.i, c1.j, c2.i, c2.j);
	}

	@Override
	protected void crop(int imin, int jmin, int imax, int jmax) {
		super.crop(imin, jmin, imax, jmax);
		Vector2D refIndex = getGrid().getReferenceIndex();
		
		refIndex.x -= imin;
		refIndex.y -= jmin;
	}
	
	public void growFlags(final double radius, final int pattern) {
		if(verbose) System.err.println("Growing flagged areas.");
		
		final double dx = getGrid().pixelSizeX();
		final double dy = getGrid().pixelSizeY();
		
		final int di = (int)Math.ceil(radius / dx);
		final int dj = (int)Math.ceil(radius / dy);
		
		final int sizeX = sizeX();
		final int sizeY = sizeY();

		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isFlagged(i, j, pattern)) {
					final int fromi1 = Math.max(0, i-di);
					final int fromj1 = Math.max(0, j-dj);
					final int toi1 = Math.max(sizeX, i+di+1);
					final int toj1 = Math.max(sizeY, j+dj+1);
					final int matchPattern = getFlag(i, j) & pattern;
					
					// TODO for sheared grids...
					for(int i1 = toi1; --i1 >= fromi1; ) for(int j1 = toj1; --j1 >= fromj1; ) 
						if(Math.hypot((i-i1) * dx, (j-j1) * dy) <= radius) flag(i1, j1, matchPattern);
				}
			}
		}.process();
	}
	
	
		
	public double countBeams() { return getArea() / getImageBeamArea(); }

	public double getArea() { return countPoints() * getPixelArea(); }

	
	// Convolves image to the specified beam resolution
	// by a properly chosen convolving beam...
	public void smoothTo(double FWHM) {
		if(smoothFWHM >= FWHM) return;
		smooth(Math.sqrt(FWHM * FWHM - smoothFWHM * smoothFWHM));
	}	
	
	
	public void smooth(double FWHM) {
		int stepX = (int)Math.ceil(FWHM/(5.0 * getGrid().pixelSizeX()));
		int stepY = (int)Math.ceil(FWHM/(5.0 * getGrid().pixelSizeY()));
		
		fastSmooth(GaussianSource.getBeam(FWHM, getGrid(), 2.0), stepX, stepY);
		smoothFWHM = Math.hypot(smoothFWHM, FWHM);
		
		// The correcting FWHM is underlying FWHM...
		//if(!Double.isNaN(correctingFWHM)) correctingFWHM = Math.hypot(correctingFWHM, FWHM);
	}

	public double[][] getSmoothedTo(double FWHM) {
		if(smoothFWHM >= FWHM) return getData(); 
		return getSmoothed(Math.sqrt((FWHM * FWHM - smoothFWHM * smoothFWHM)));
	}
	
	public double[][] getSmoothed(double FWHM) {
		int stepX = (int)Math.ceil(FWHM/(5.0 * getGrid().pixelSizeX()));
		int stepY = (int)Math.ceil(FWHM/(5.0 * getGrid().pixelSizeY()));
		return getFastSmoothed(GaussianSource.getBeam(FWHM, getGrid(), 2.0), null, stepX, stepY);
	}   

	
	public void filterAbove(double FWHM) { filterAbove(FWHM, getFlag()); }

	public void filterAbove(double FWHM, int[][] skip) {
		final GridImage<?> extended = (GridImage<?>) copy();
		extended.setFlag(skip);
		extended.smoothTo(FWHM);
	
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				decrement(i, j, extended.getValue(i, j));
			}
		}.process();
		
		if(Double.isNaN(extFilterFWHM)) extFilterFWHM = FWHM;
		else extFilterFWHM = 1.0/Math.sqrt(1.0/(extFilterFWHM * extFilterFWHM) + 1.0/(FWHM*FWHM));
	}
	
	//	 8/20/07 Changed to use blanking 
	//         Using Gaussian taper.
	//         Robust re-levelling at the end.
	public void fftFilterAbove(double FWHM, final int[][] skip) {
		// sigma_x sigma_w = 1
		// FWHM_x sigma_w = 2.35
		// FWHM_x * 2Pi sigma_f = 2.35
		// sigma_f = 2.35/2Pi * 1.0/FWHM_x
		// delta_f = 1.0/(Nx * delta_x);
		// sigma_nf = sigma_f / delta_x = 2.35 * Nx * delta_x / (2Pi * FWHM_x)
		
		// Try to get an honest estimate of the extended structures using FFT (while blaning bright sources).
		// Then remove it from the original image...
		
		final double[][] extended = new double[sizeX()][sizeY()];
		
		Task<WeightedPoint> ecalc = new AveragingTask() {
			private double sumw = 0.0;
			private int n = 0;
			@Override
			public void process(final int i, final int j) {
				if(getFlag(i, j) != 0) return;	
				if(skip[i][j] > 0) extended[i][j] = 0.0;
				else {
					final double w = getWeight(i, j);
					extended[i][j] = w * getValue(i, j);
					sumw += w;
					n++;
				}
			}
			@Override
			public WeightedPoint getPartialResult() { return new WeightedPoint(sumw, n); }
		};
		ecalc.process();
		
		Complex[][] cdata = FFT.load(extended);
		FFT.uncheckedForward(cdata, true);

		final int nx = cdata.length;
		final int ny = cdata[0].length;

		double sigmax = Util.sigmasInFWHM *  nx * getGrid().pixelSizeX() / (2.0*Math.PI * FWHM);
		double sigmay = Util.sigmasInFWHM *  ny * getGrid().pixelSizeY() / (2.0*Math.PI * FWHM);
		
		final double ax = -0.5/(sigmax*sigmax);
		final double ay = -0.5/(sigmay*sigmay);

		for(int fx=nx; --fx>0; ) {
			final double axfx2 = ax*fx*fx;
			final Complex[] r1 = cdata[fx];
			final Complex[] r2 = cdata[nx-fx];
			
			for(int fy=ny; --fy>0; ) {
				final double A = Math.exp(axfx2 + ay*fy*fy);
				final int fy1 = ny - fy;
				
				r1[fy].scale(A);
				r2[fy].scale(A);
				r1[fy1].scale(A);
				r2[fy1].scale(A);
			}
		}
		cdata[0][0].zero();
		cdata[1][0].scale(Math.exp(ax));
		cdata[0][1].scale(Math.exp(ay));
		
		FFT.uncheckedForward(cdata, false);
		FFT.unload(cdata, extended);
	
		double avew = ecalc.getResult().value;
		if(avew > 0.0) {
			final double norm = 1.0 / avew;
			new Task<Void>() {
				@Override
				public void process(int i, int j) {
					decrement(i, j, norm * extended[i][j]);
				}
			}.process();
		}
		
		if(Double.isNaN(extFilterFWHM)) extFilterFWHM = FWHM;
		else extFilterFWHM = 1.0/Math.sqrt(1.0/(extFilterFWHM * extFilterFWHM) + 1.0/(FWHM*FWHM));
	}
	
	
	public void filterCorrect(double FWHM, final int[][] skip) {
		if(!Double.isNaN(correctingFWHM)) return;
		
		final double filterC = getExtFilterCorrectionFactor(FWHM);
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(skip[i][j] == 0) scale(i, j, filterC);
			}
		}.process();
		
		correctingFWHM = FWHM;
	}
	
	public void undoFilterCorrect(double FWHM, final int[][] skip) {
		if(!Double.isNaN(correctingFWHM)) return;
		
		final double iFilterC = getExtFilterCorrectionFactor(FWHM);
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(skip[i][j] == 0) scale(i, j, iFilterC);
			}
		}.process();
		
		correctingFWHM = Double.NaN;
	}
	
	
	public double[][] getBeam() {
		return GaussianSource.getBeam(getImageFWHM(), getGrid(), 3.0);
	}
	
	
	public void copyValueOf(final GridImage<CoordinateType> from, final double fromi, final double fromj, final int toi, final int toj) {
		setValue(toi, toj, from.valueAtIndex(fromi, fromj));
		if(isNaN(toi, toj)) setFlag(toi, toj, 1);
	}

	@SuppressWarnings("unchecked")
	public void resample(GridImage<CoordinateType> from) {
		if(verbose) System.err.println(" Resampling image to "+ sizeX() + "x" + sizeY() + ".");
		final Vector2D v = new Vector2D();
		
		// Antialias filter first...
		if(from.smoothFWHM < smoothFWHM) {
			from = (GridImage<CoordinateType>) from.copy();
			from.smoothTo(smoothFWHM);
		}
		
		final GridImage<?> antialiased = from;
		
		new InterpolatingTask() {
			@Override
			public void process(int i, int j) { 
				if(isUnflagged(i, j)) {
					v.set(i, j);
					toOffset(v);
					antialiased.toIndex(v);
				
					setValue(i, j, antialiased.valueAtIndex(v.x, v.y, getInterpolatorData()));
					if(isNaN(i, j)) setFlag(i, j, 1);
				}				
			}
		}.process();
	}
	
	public GridImage<CoordinateType> getRegrid(final double resolution) throws IllegalStateException {
		return getRegrid(new Vector2D(resolution, resolution));
	}

	public GridImage<CoordinateType> getRegrid(final Vector2D resolution) throws IllegalStateException {	
		Vector2D dRes = new Vector2D(resolution.x / getGrid().pixelSizeX(), resolution.y / getGrid().pixelSizeY());
		Grid2D<CoordinateType> toGrid = (Grid2D<CoordinateType>) getGrid().copy();
		
		Vector2D refIndex = toGrid.getReferenceIndex();
		
		if(verbose) System.err.print(" Reference index: " + refIndex.toString(Util.f1));
		
		refIndex.x /= dRes.x;
		refIndex.y /= dRes.y;
		
		if(verbose) System.err.println(" --> " + refIndex.toString(Util.f1));
		
		double[][] M = getGrid().getTransform();
		M[0][0] *= dRes.x;
		M[0][1] *= dRes.y;
		M[1][0] *= dRes.x;
		M[1][1] *= dRes.y;
		toGrid.setTransform(M);
		
		//System.err.println(" M = {{" + M[0][0] + ", " + M[0][1] + "}, {" + M[1][0] + "," + M[1][1] + "}}");

		return getRegrid(toGrid);
	}

	
	public GridImage<CoordinateType> getRegrid(final Grid2D<CoordinateType> toGrid) throws IllegalStateException {		
		// Check if it is an identical grid...
		// Add directly if it is...

		if(toGrid.equals(getGrid(), 1e-10)) {
			if(verbose) System.err.println(" Matching grids.");
			return this;
		}

		final int nx = (int) Math.ceil(sizeX() * getGrid().pixelSizeX() / toGrid.pixelSizeX());
		final int ny = (int) Math.ceil(sizeY() * getGrid().pixelSizeY() / toGrid.pixelSizeY());
		if(verbose) {
			System.err.println(" Regrid size: " + nx + "x" + ny);
			System.err.println(" Resolution = " + Vector2D.toString(toGrid.getResolution(), Unit.get("arcsec"), 2));
		}
		
		return getRegrid(toGrid, nx, ny);
	}
	
	@SuppressWarnings("unchecked")
	protected GridImage<CoordinateType> getRegrid(Grid2D<CoordinateType> toGrid, int nx, int ny) {	
		GridImage<CoordinateType> regrid = (GridImage<CoordinateType>) clone();
		regrid.setGrid(toGrid);
		regrid.setSize(nx, ny);
		regrid.fillFlag(1);
		regrid.resample(this);
		regrid.sanitize();
		
		return regrid;
	}
	

	public void regrid(double resolution) {		
		GridImage<CoordinateType> regrid = getRegrid(resolution);
		setImage(regrid);
		setGrid(regrid.getGrid());
		smoothFWHM = regrid.smoothFWHM;
	}

	
	public void regridTo(final GridImage<CoordinateType> image) throws IllegalStateException {
		GridImage<CoordinateType> regrid = getRegrid(image.getGrid());

		Vector2D corner1 = new Vector2D();
		Vector2D corner2 = new Vector2D(image.sizeX() - 1.0, image.sizeY() - 1.0);
		image.toOffset(corner1);
		image.toOffset(corner2);

		regrid.crop(corner1.x, corner1.y, corner2.x, corner2.y); 

		image.setImage(regrid);
	}

	public void clean() {
		clean(getImageFWHM(), 0.1, 0.5 * getImageFWHM());
	}

	public void clean(double FWHM, double gain, double replacementFWHM) {
		clean(getBeam(), gain, replacementFWHM); 
	}

	public int clean(double[][] beam, double gain, double replacementFWHM) {
		return clean(this, beam, gain, replacementFWHM);
	}

	public int clean(GridImage<CoordinateType> search, double[][] beam, double gain, double replacementFWHM) {
		if(verbose) System.err.println("Deconvolving to " + Util.f1.format(replacementFWHM/Unit.arcsec) + " arcsec resolution.");

		int ic = beam.length / 2;
		int jc = beam[0].length / 2;

		double[][] clean = new double[sizeX()][sizeY()];

		// Normalize to beam center
		double norm = beam[ic][jc];
		double beamInt = 0.0;
		for(int ib=beam.length; --ib >= 0; ) for(int jb=beam[0].length; --jb >= 0; ) {
			beam[ib][jb] /= norm;
			beamInt += beam[ib][jb];
		}

		// Remove until there is an 70% chance that the peak is real
		double critical = ConfidenceCalculator.getSigma(1.0 - 0.3 / Math.pow(getImageFWHM()/smoothFWHM, 2.0));
		// Or until the number of components can cover the map...
		int maxComponents = (int) Math.ceil(countBeams() / gain);			

		int components = 0;

		// Find the peak
		Index2D index = search.indexOfMaxDev();
		double peakValue = search.getValue(index.i, index.j);
		double ave = Math.abs(peakValue);

		do {			    
			// Get the peak value	 
			final int i = index.i;
			final int j = index.j;
			final int i0 = i - ic;	// The map index where the patch would start on...
			final int j0 = j - jc;

			final int imin = Math.max(0, i0);
			final int jmin = Math.max(0, j0);
			final int imax = Math.min(i0 + beam.length, sizeX());
			final int jmax = Math.min(j0 + beam[0].length, sizeY());

			final double decrement = gain * getValue(i, j);
			final double searchDecrement = gain * peakValue;

			// Pole is the peak value times the beam integral to conserve flux	    
			clean[i][j] += decrement * beamInt; // est. intergal flux in beam


			for(int i1=imin; i1 < imax; i1++) for(int j1=jmin; j1 < jmax; j1++) if(isUnflagged(i1, j1)) {
				final double B = beam[i1 - i0][j1 - j0];
				decrement(i1, j1, B * decrement);
				if(search != this) search.decrement(i, j, B * searchDecrement);
			}

			components++;

			peakValue = search.getValue(i, j);         

			/*
			if(verbose) if(components % 100 == 0) {
				System.err.print("\r " + components + " components removed. (Last: " + Util.f2.format(peakValue) + "-sigma, Ave: " + Util.f2.format(ave) + "-sigma)   ");
				System.err.flush();
			}
			*/

			// The moving average value of the peak...
			ave *= Math.exp(-0.03);
			ave += 0.03 * Math.abs(peakValue);

			index = search.indexOfMaxDev();

		} while(Math.abs(peakValue) > critical && components < maxComponents);

		
		//if(verbose) System.err.println("\r " + components + " components removed. (Last: " + Util.f2.format(peakValue) + "-sigma, Ave: " + Util.f2.format(ave) + "-sigma)   ");

		if(verbose) System.err.println(" " + components + " components removed. (Last: " + Util.f2.format(peakValue) + "-sigma, Ave: " + Util.f2.format(ave) + "-sigma)   ");

		
		GridImage<?> cleanImage = (GridImage<?>) clone();
		cleanImage.setData(clean);
		
		clean = cleanImage.getSmoothed(replacementFWHM);

		// Add deconvolved components back to the residual noise...
		for(int i=sizeX(); --i >= 0; ) for(int j=sizeY(); --j >= 0 ; ) increment(i, j, clean[i][j]);

		smoothFWHM = Math.sqrt(getGrid().getPixelArea()) / fwhm2size;

		if(verbose) System.err.println();

		return components;
	}

	
	
	public void toIndex(Vector2D offset) { getGrid().toIndex(offset); }
	
	public void toOffset(Vector2D index) { getGrid().toOffset(index); }

	public void getIndex(Vector2D offset, Index2D index) {
		final double x = offset.x;
		final double y = offset.y;
		toIndex(offset);
		index.i = (int) Math.round(offset.x);
		index.j = (int) Math.round(offset.y);
		offset.x = x;
		offset.y = y;		
	}
	
	public void getOffset(Index2D index, Vector2D offset) {
		offset.x = index.i;
		offset.y = index.j;
		toOffset(offset);		
	}

	public Index2D getIndex(Vector2D offset) {
		Index2D index = new Index2D();
		getIndex(offset, index);
		return index;
	}
	
	public Vector2D getOffset(Index2D index) {
		Vector2D offset = new Vector2D();
		getOffset(index, offset);
		return offset;
	}
	
	public void read(String fileName) throws Exception {	
		read(fileName, 0);
	}
		
	public void read(String fileName, int hduIndex) throws Exception {	
		Fits fits = new Fits(fileName);
		setImage(fits.getHDU(hduIndex));
	}
		
	public void setImage(BasicHDU HDU) throws FitsException {		
		Object image = HDU.getData().getData();
		noFlag();
	
		try {
			final float[][] fdata = (float[][]) image;
			new Task<Void>() {
				@Override
				public void process(int i, int j) {
					if(Float.isNaN(fdata[j][i])) flag(i, j);
					else setValue(i, j, fdata[j][i] * unit.value);	    
				}
			}.process();
		}
		catch(ClassCastException e) {
			final double[][] ddata = (double[][]) image;
			new Task<Void>() {
				@Override
				public void process(int i, int j) {
					if(Double.isNaN(ddata[j][i])) flag(i, j);
					else setValue(i, j, ddata[j][i] * unit.value);	    
				}
			}.process();
		}
	}

	public ImageHDU createHDU() throws HeaderCardException, FitsException {
		final float[][] fitsImage = new float[sizeY()][sizeX()];
		
		new Task<Void>() {
			@Override
			public void process(int i, int j) {
				if(isUnflagged(i, j)) fitsImage[j][i] = (float) (getValue(i, j) / unit.value);
				else fitsImage[j][i] = Float.NaN;
			}
		}.process();
		
		ImageHDU hdu = (ImageHDU)Fits.makeHDU(fitsImage);

		hdu.addValue("EXTNAME", contentType, "The type of data contained in this HDU");
		getGrid().addCoordinateInfo(hdu);

		Range range = getRange();

		hdu.addValue("DATAMIN", range.min / unit.value, "");
		hdu.addValue("DATAMAX", range.max / unit.value, "");

		hdu.addValue("BZERO", 0.0, "Zeroing level of the image data");
		hdu.addValue("BSCALE", 1.0, "Scaling of the image data");
		hdu.addValue("BUNIT", unit.name, "The image data unit.");

		return hdu;
	}


	public void addLongHierarchKey(Cursor cursor, String key, String value) throws FitsException, HeaderCardException {
		addLongHierarchKey(cursor, key, 0, value);
	}

	public void addLongHierarchKey(Cursor cursor, String key, int part, String value) throws FitsException, HeaderCardException {
		if(value.length() == 0) value = "true";

		String alt = part > 0 ? "." + part : "";

		int available = 69 - (key.length() + alt.length() + 3);

		if(value.length() < available) cursor.add(new HeaderCard("HIERARCH." + key + alt, value, null));
		else { 
			if(alt.length() == 0) {
				part = 1;
				alt = "." + part;
				available -= 2;
			}

			cursor.add(new HeaderCard("HIERARCH." + key + alt, value.substring(0, available), null));
			addLongHierarchKey(cursor, key, (char)(part+1), value.substring(available)); 
		}
	}
	
	public void write() throws HeaderCardException, FitsException, IOException { write(getFits()); }

	public void write(Fits fits) throws HeaderCardException, FitsException, IOException {
		BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream(fileName));
		fits.write(file);
		System.err.println(" Written " + fileName);
	}
	
	public void write(String name) throws HeaderCardException, FitsException, IOException {
		Fits fits = new Fits();	

		fits.addHDU(createHDU());
		editHeader(fits);

		BufferedDataOutputStream file = new BufferedDataOutputStream(new FileOutputStream(name));

		fits.write(file);	
		System.err.println(" Written Image to " + name);
	}
	
	public final void editHeader(Fits fits) throws HeaderCardException, FitsException, IOException {

		nom.tam.util.Cursor cursor = fits.getHDU(0).getHeader().iterator();

		// Go to the end of the header cards...
		while(cursor.hasNext()) cursor.next();
		editHeader(cursor);
	}
		
	public void editHeader(Cursor cursor) throws HeaderCardException, FitsException, IOException {
		double imageFWHM = getImageFWHM();

		cursor.add(new HeaderCard("DATE", FitsDate.getFitsDateString(), "Time-stamp of creation."));
		cursor.add(new HeaderCard("CREATOR", creator, "The software that created the image."));
		//cursor.add(new HeaderCard("ORIGIN", "Caltech", "California Institute of Technology"));

		cursor.add(new HeaderCard("BMAJ", imageFWHM, "The beam major axis (radians)"));
		cursor.add(new HeaderCard("BMIN", imageFWHM, "The beam minor axis (radians)."));
		cursor.add(new HeaderCard("BPA", 0.0, "The beam position angle (radians)."));
		cursor.add(new HeaderCard("SMOOTH", smoothFWHM / Unit.arcsec, "The FWHM (arcsec) of the smoothing applied."));	
		cursor.add(new HeaderCard("SMTHRMS", true, "Is the Noise (RMS) image smoothed?"));
		cursor.add(new HeaderCard("RESOLUTN", imageFWHM / Unit.arcsec, "The effective image FWHM (arcsec)."));	

		if(!Double.isNaN(extFilterFWHM)) 
			cursor.add(new HeaderCard("EXTFLTR", extFilterFWHM / Unit.arcsec, "Large-scale structure filtering FWHM."));

		if(!Double.isNaN(correctingFWHM))
			cursor.add(new HeaderCard("CORRECTN", correctingFWHM / Unit.arcsec, "The FWHM (arcsec) for which fluxes are corrected."));
	}
	
	public void parseHeader(Header header) throws Exception {
		this.header = header;
		
		int sizeX = header.getIntValue("NAXIS1");
		int sizeY = header.getIntValue("NAXIS2");

		setSize(sizeX, sizeY);

		getGrid().parseCoordinateInfo(header);

		creator = header.getStringValue("CREATOR");
		if(creator == null) creator = "unknown";
		creatorVersion = "unknown";

		correctingFWHM = header.getDoubleValue("CORRECTN", Double.NaN) * Unit.arcsec;
		creatorVersion = header.getStringValue("CRUSHVER");

		// get the map resolution
		smoothFWHM = header.getDoubleValue("SMOOTH", 0.0) * Unit.arcsec;
		if(smoothFWHM < Math.sqrt(getGrid().getPixelArea()) / fwhm2size) smoothFWHM = Math.sqrt(getGrid().getPixelArea()) / fwhm2size;
		extFilterFWHM = header.getDoubleValue("EXTFLTR", Double.NaN) * Unit.arcsec;
	}
	
	
	public Fits getFits() throws HeaderCardException, FitsException, IOException {
		FitsFactory.setUseHierarch(true);
		Fits fits = new Fits();	

		fits.addHDU(createHDU());
		editHeader(fits);
	
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
	public String toString() {		
		Grid2D<?> grid = getGrid();
		String info =
			"  Map Size: " + sizeX() + " x " + sizeY() + " pixels. (" 
			+ Util.f1.format(sizeX() * grid.pixelSizeX() / Unit.arcmin) + " x " + Util.f1.format(sizeY() * grid.pixelSizeY() / Unit.arcmin) + " arcmin)." + "\n"
			+ grid.toString()
			+ 	"  Applied Smoothing: " + Util.f2.format(smoothFWHM / Unit.arcsec) + " arcsec." + " (includes pixelization)\n"
			+ "  Image Resolution (FWHM): " + Util.f2.format(getImageFWHM() / Unit.arcsec) + " arcsec. (includes smoothing)" + "\n";
			
		return info;
	}


	public void setKey(String key, String value) throws HeaderCardException {
		String comment = header.containsKey(key) ? header.findCard(key).getComment() : "Set bu user.";

		// Try add as boolean, int or double -- fall back to String...
		try{ header.addValue(key, Util.parseBoolean(value), comment); }
		catch(NumberFormatException e1) { 
			try{ header.addValue(key, Integer.parseInt(value), comment); }
			catch(NumberFormatException e2) {
				try{ header.addValue(key, Double.parseDouble(value), comment); }
				catch(NumberFormatException e3) { header.addValue(key, value, comment); }
			}
		}
	}

	public void printHeader() {
		header.dumpHeader(System.out);
	}  
	
	
	public void flag(Region<CoordinateType> region) { flag(region, 1); }

	public void flag(Region<CoordinateType> region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(getGrid(), i, j)) flag(i, j, pattern);
	}

	public void unflag(Region<CoordinateType> region, int pattern) {
		final Bounds bounds = region.getBounds(this);
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++) 
			if(region.isInside(getGrid(), i, j)) unflag(i, j, pattern);
	}

	

	public double getLevel(Region<CoordinateType> region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0, sumw = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j)) {
				final double w = getWeight(i, j);
				sum += w * getValue(i, j);
				sumw += w;
			}
		return sum / sumw;			
	}
	

	public double getIntegral(Region<CoordinateType> region) {
		final Bounds bounds = region.getBounds(this);
		double sum = 0.0;
		for(int i=bounds.fromi; i<=bounds.toi; i++) for(int j=bounds.fromj; j<=bounds.toj; j++)
			if(isUnflagged(i, j)) if(region.isInside(getGrid(), i, j)) sum += getValue(i, j);	
		return sum;			
	}

	
}