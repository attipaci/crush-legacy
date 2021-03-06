/*******************************************************************************
 * Copyright (c) 2015 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.mustang2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.Header;
import nom.tam.fits.HeaderCardException;
import crush.Channel;
import crush.CorrelatedModality;
import crush.Scan;
import crush.array.Camera;
import crush.array.SingleColorArrangement;
import crush.telescope.GroundBased;
import crush.telescope.Mount;
import jnum.Constant;
import jnum.Unit;
import jnum.Util;
import jnum.data.Statistics;
import jnum.io.LineParser;
import jnum.math.Vector2D;

public class Mustang2 extends Camera<Mustang2Pixel> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = 8027132292040380342L;

	Mustang2Readout[] readout;
	double focus;
	double temperatureGain;
	
	// TODO ? Vector2D arrayPointingCenter
	
	public Mustang2() {
		super("mustang2", new SingleColorArrangement<Mustang2Pixel>(), maxPixels);	
		setResolution(6.2 * Unit.arcsec);
		mount = Mount.CASSEGRAIN;
		samplingInterval = integrationTime = 1.0 * Unit.ms;
	}	
	
	@Override
	public Mustang2 copy() {
		Mustang2 copy = (Mustang2) super.copy();
		if(readout != null) {
			copy.readout = new Mustang2Readout[readout.length];
			for(int i=readout.length; --i >= 0; ) copy.readout[i] = readout[i].copy();
		}
		return copy;
	}
	
	
	@Override
    protected void initDivisions() {
		super.initDivisions();
			
		try { addDivision(getDivision("polarizations", Mustang2Pixel.class.getField("polarizationIndex"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
		try { addDivision(getDivision("mux", Mustang2Pixel.class.getField("readoutIndex"), Channel.FLAG_DEAD)); }
		catch(Exception e) { error(e); }
		
	}
	
	@Override
    protected void initModalities() {
		super.initModalities();
				
		try {
			CorrelatedModality muxMode = new CorrelatedModality("polarizations", "P", divisions.get("polarizations"), Mustang2Pixel.class.getField("polarizationGain"));		
			muxMode.setGainFlag(Mustang2Pixel.FLAG_POL);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }
		
		try {
			CorrelatedModality muxMode = new CorrelatedModality("mux", "m", divisions.get("mux"), Mustang2Pixel.class.getField("readoutGain"));		
			muxMode.setGainFlag(Mustang2Pixel.FLAG_MUX);
			addModality(muxMode);
		}
		catch(NoSuchFieldException e) { error(e); }	
		
	}
	

	@Override
	public int maxPixels() {
		return maxReadouts * maxReadoutChannels;
	}

	@Override
	public String getTelescopeName() {
		return "GBT";
	}

	@Override
	public Mustang2Pixel getChannelInstance(int backendIndex) {
		return new Mustang2Pixel(this, backendIndex);
	}

	@Override
	public Scan<?, ?> getScanInstance() {
		return new Mustang2Scan(this);
	}
	
	
	public void parseScanPrimaryHDU(BasicHDU<?> hdu) throws HeaderCardException {
		Header header = hdu.getHeader();
				
		// nReadouts = header.getIntValue("NROACHES", 1);
		focus = header.getDoubleValue("LFCY", Double.NaN) * Unit.mm;
		
	}

	
	public void parseHardwareHDU(BinaryTableHDU hdu) throws FitsException {
		Header header = hdu.getHeader();
		
		samplingInterval = integrationTime = Unit.s / header.getDoubleValue("SMPLFREQ", 1000.0);
		
		Object[] data = hdu.getRow(0);
		
		byte[] row = (byte[]) data[hdu.findColumn("ROW")];
		byte[] col = (byte[]) data[hdu.findColumn("COL")];
		float[] f0 = (float[]) data[hdu.findColumn("RESFREQ")];
		float[] atten = (float[]) data[hdu.findColumn("ATTEN")];
		float[] bias = (float[]) data[hdu.findColumn("DETBIAS")];
		float[] heater = (float[]) data[hdu.findColumn("DETHEATERS")];
		float[] gain = (float[]) data[hdu.findColumn("GAINS")];
		
		readout = new Mustang2Readout[bias.length];
		
		for(int i=0; i<readout.length; i++) {
			Mustang2Readout r = new Mustang2Readout(i);
			r.bias = bias[i];
			r.heater = heater[i];
			readout[i] = r;
		}
			
		int pixels = f0.length;
		info("Reading out " + pixels + " channels.");
		clear();
		ensureCapacity(pixels);
		
		int n = 0;
		double[] G = new double[pixels];
		final double maxG = 0.1;			// A maximum reasonable temperature gain rad/K...
											// Allows for +/- ~15 K dynamic range
		
		for(int i=0; i<pixels; i++) {
			Mustang2Pixel pixel = new Mustang2Pixel(this, i);
			
			pixel.gain = gain[i];
			pixel.frequency = f0[i] * Unit.GHz;
			pixel.attenuation = atten[i];
			pixel.readoutIndex = col[i]; 
			pixel.muxIndex = row[i];
			
			if(pixel.gain == 0.0) pixel.flag(Channel.FLAG_BLIND); 
			else if(pixel.gain > maxG) pixel.flag(Channel.FLAG_GAIN); 
			
			if(pixel.frequency < 0.0) pixel.flag(Channel.FLAG_DEAD); 
		
			if(pixel.isUnflagged()) G[n++] = Math.abs(pixel.gain);
			
			add(pixel);
		}
		
		// Normalize the gains
		if(n > 0) {
			temperatureGain = Statistics.Inplace.median(G, 0, n);		
			info("Average gain is " + Util.s2.format(temperatureGain) + " / K for " + n + " pixels.");
			for(Channel pixel : this) pixel.gain /= temperatureGain;
		}
	}
	
	@Override
	public float normalizeArrayGains() throws Exception {
		float aveG = super.normalizeArrayGains();
		temperatureGain *= aveG;
		return aveG;
	}
	
	@Override
	public void validate(Scan<?,?> scan) {
		super.validate(scan);
		gain *= temperatureGain;
	}
	
	@Override
    protected void loadChannelData() {
		for(int i=0; i<readout.length; i++) if(hasOption("frequencies." + (i+1))) {
			try { readout[i].parseFrequencies(option("frequencies." + (i+1)).getPath()); }
			catch(IOException e) { error(e); }
		}
			
		if(hasOption("positions")) {
			try { parsePositions(option("positions").getPath()); }
			catch(IOException e) { error(e); }
		}
		
		assignPositions();
			
		if(hasOption("pol")) restrictPolarization(option("pol").getDouble() * Unit.deg);
		
		super.loadChannelData();
	}
		
	public void parsePositions(String fileName) throws IOException {
		
		for(Mustang2Readout r : readout) if(r != null) for(Mustang2PixelID id : r.tones) id.flag(Mustang2PixelID.FLAG_UNUSED);
		
		final double pol0 = hasOption("positions.pol0") ? option("positions.pol0").getDouble() * Unit.deg : 0.0;
		
		LineParser parser = new LineParser() {
            @Override
            protected boolean parse(String line) throws Exception {
                StringTokenizer tokens = new StringTokenizer(line);
                if(tokens.countTokens() < 5) return false;
                Mustang2Readout r = readout[Integer.parseInt(tokens.nextToken())];

                int channel = Integer.parseInt(tokens.nextToken());

                if(channel >= r.tones.size()) return false;
                Mustang2PixelID id = r.tones.get(channel);
                
                id.position = new Vector2D(Double.parseDouble(tokens.nextToken()), Double.parseDouble(tokens.nextToken()));         
                id.position.scale(Unit.arcsec);
                
                id.unflag(Mustang2PixelID.FLAG_UNUSED); 
                
                int flag = Integer.parseInt(tokens.nextToken());
                if(flag == 0) id.flag(Mustang2PixelID.FLAG_BLIND);
                else id.unflag(Mustang2PixelID.FLAG_BLIND);
                
                if(tokens.hasMoreTokens()) {
                    id.polarizationAngle = Constant.rightAngle + Math.IEEEremainder(Double.parseDouble(tokens.nextToken()) * Unit.deg - pol0, Math.PI);
                    if(Util.fixedPrecisionEquals(id.polarizationAngle, Math.PI, 1e-6)) id.polarizationAngle = 0.0;
                }
                
                return true;
            }
		};
		
		parser.read(fileName);
		
		info("Parsed " + parser.getLinesProcessed() + " pixel positions from " + fileName);

	}
	
	private void assignPositions() {	
		info("Assigning known pixels to resonators.");
		
		for(Mustang2Pixel pixel : this) pixel.flagID();
		
		if(hasOption("readout")) {
			int i = option("readout").getInt() - 1;
			assignPositions(i);	
		}
		else for(int i=0; i<readout.length; i++) if(readout[i] != null) if(!readout[i].tones.isEmpty()) assignPositions(i);

		// Mark to discard any channels that have no IDs or are blind channels...
		for(Channel channel : this) if(channel.isFlagged(Mustang2Pixel.FLAG_NOTONEID | Mustang2Pixel.FLAG_BLIND))
			channel.flag(Channel.FLAG_DISCARD);
	}
	
	private void assignPositions(int readoutIndex) {
		ArrayList<Mustang2Pixel> pixels = getReadoutPixels(readoutIndex);
		Mustang2Readout r = readout[readoutIndex];
		for(Mustang2Pixel pixel : pixels) pixel.setFrequencyID(r.getNearestID(pixel.frequency));
	}
	
	/*
	public void assignPositions(int readoutIndex) {
		ArrayList<Mustang2Pixel> pixels = getReadoutPixels(readoutIndex);
		Mustang2PixelMatch identifier = new Mustang2PixelMatch(getOptions().get("pixelid"));
		identifier.addAll(readout[readoutIndex].tones);
		identifier.match(new ResonatorList<Mustang2Pixel>(pixels));
	}
	*/
	
	
	public ArrayList<Mustang2Pixel> getReadoutPixels(int readoutIndex) {
		ArrayList<Mustang2Pixel> pixels = new ArrayList<Mustang2Pixel>(maxReadoutChannels);
		for(Mustang2Pixel pixel : this) if(pixel.readoutIndex == readoutIndex) pixels.add(pixel);
		return pixels;
	}
	
	public ArrayList<Mustang2Pixel> getPolarizationPixels(double polarizationAngle) {
		if(Double.isNaN(polarizationAngle)) return this;
		ArrayList<Mustang2Pixel> pixels = new ArrayList<Mustang2Pixel>(maxReadoutChannels);
		for(Mustang2Pixel pixel : this) if(pixel.polarizationAngle == polarizationAngle) pixels.add(pixel);
		return pixels;
	}
	
	private void restrictPolarization(double polarizationAngle) {
		if(Double.isNaN(polarizationAngle)) return;
		polarizationAngle = Constant.rightAngle + Math.IEEEremainder(polarizationAngle, Math.PI);
		if(Util.fixedPrecisionEquals(polarizationAngle, Math.PI, 1e-6)) polarizationAngle = 0.0;
		info("Restricting polarization to " + Util.s3.format(polarizationAngle / Unit.deg) + "degrees.") ;
		for(Mustang2Pixel pixel : this) if(!Util.fixedPrecisionEquals(pixel.polarizationAngle, polarizationAngle, 1e-3)) pixel.flag(Channel.FLAG_DEAD);
	}
	
	
	@Override
	public String getScanOptionsHelp() {
		return super.getScanOptionsHelp() + 
				"     -sparse        Reduce sparsely sampled data on a coarser grid.\n";
	}
	
	/*
	@Override
	public String getDataLocationHelp() {
		return super.getDataLocationHelp() +
				"     -date=         YYYYMMDD when the data was collected.\n" +
				"     -ndf2fits=     The path to the ndf2fits executable. Required for\n" +
				"                    reading native SDF data.\n";
	}
	*/
	
	public static int maxReadouts = 7;
	public static int maxReadoutChannels = 36;
	public static int maxPixels = 338;
}
