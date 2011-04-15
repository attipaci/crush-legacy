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
// Copyright (c) 2010 Attila Kovacs 

package crush.polka;

import util.Unit;
import util.Util;
import crush.*;
import crush.laboca.*;
import crush.polarization.*;

import java.io.*;

import nom.tam.fits.BinaryTableHDU;
import nom.tam.fits.FitsException;
import nom.tam.fits.HeaderCardException;

public class PolKa extends Laboca {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5407116500180513583L;

	float Q0 = 0.0F, U0 = 0.0F; // The intrinsic polarization of the instrument...
	double wavePlateFrequency = 1.56 * Unit.Hz; // The default rotation frequency
	double jitter = 0.003;
	double phi0 = 0.0;
	boolean isOrthogonal = false;
	Channel wavePlateChannel, frequencyChannel;
	PolKaTimeStamps timeStamps;
	
	public PolKa() {
		super();
		name = "polka";
	}
	
	@Override
	public void initialize() {
		super.initialize();
		
		if(hasOption("waveplate.data")) {
			timeStamps = new PolKaTimeStamps();
			String waveFile = option("waveplate.data").getValue();
			try { timeStamps.read(waveFile); }
			catch(IOException e) { 
				System.err.println("ERROR! Cannot read waveplate data from " + waveFile); 
				System.exit(1);
			}
		}
	}
	
	@Override
	public void validate(double MJD) {
		
		if(hasOption("waveplate.frequency")) 
			wavePlateFrequency = option("waveplate.frequency").getDouble() * Unit.Hz;
		
		if(hasOption("waveplate.jitter")) 
			jitter = option("waveplate.jitter").getDouble();
		
		
		if(hasOption("waveplate.refangle")) {
			phi0 = option("waveplate.refangle").getDouble() * Unit.deg;
			System.err.println(" Waveplate reference angle " + Util.f1.format(phi0/Unit.deg));
		}
		
		if(hasOption("waveplate.channel")) {
			int beIndex = option("waveplate.channel").getInt();
			wavePlateChannel = get(beIndex-1);
			if(wavePlateChannel != null) 
				System.err.println(" Waveplate angles from channel " + wavePlateChannel.dataIndex);
		}
		
		if(hasOption("waveplate.fchannel")) {
			int beIndex = option("waveplate.fchannel").getInt();
			frequencyChannel = get(beIndex-1);
			if(frequencyChannel != null) 
				System.err.println(" Waveplate frequencies from channel " + frequencyChannel.dataIndex);
		}
			
		super.validate(MJD);
	}
	
	@Override
	public void readPar(BinaryTableHDU hdu) throws IOException, FitsException, HeaderCardException {
		super.readPar(hdu);
	
		int iAnalyzer = hdu.findColumn("POLTY");
		
		if(hasOption("analyzer")) {
			String value = option("analyzer").getValue().toUpperCase();
			setAnalyzer(value.charAt(0));
		}
		else if(iAnalyzer > 0) setAnalyzer(((String) hdu.getRow(0)[iAnalyzer]).charAt(0));
		else {
			System.err.println("ERROR! Analyzer position is not defined. Use 'analyzer' option to");
			System.err.println("       to manually set it to 'H' or 'V'.");
			System.exit(1);
		}
		
		System.err.println(" Analyzer grid orientation is " + (isOrthogonal ? "V" : "H"));
	}
	
	public void setAnalyzer(char c) {
		switch(c) {
		case 'N' : 
			System.err.println();
			System.err.println("WARNING! It appears you are trying to reduce total-power data in");
			System.err.println("         polarization mode. You should use 'laboca' as your instrument");
			System.err.println("         instead of 'polka'. Otherwise, if you are reducing 2010 data");
			System.err.println("         use the 'pol' key to set 'H' or 'V' analyzer positions");
			System.err.println("         manually. Exiting.");
			System.err.println();
			System.exit(1);
			break;
		case 'V' : 
			isOrthogonal = true; break;
		case 'H' :
			isOrthogonal = false; break;
		default :
			System.err.println();
			System.err.println("WARNING! Polarization analyzer position is undefined. Set the 'pol'");
			System.err.println("         option to 'H' or 'V' to specify. Exiting.");
			System.err.println();
			System.exit(1);
			break;
		}
	}
	
	@Override
	public LabocaScan getScanInstance() {
		return new PolKaScan(this);
	}
	
	@Override
	public SourceModel<?, ?> getSourceModelInstance() {
		if(hasOption("source.synchronized")) return new SyncPolarMap<Laboca, PolKaScan>(this);
		else return new PolarMap<Laboca, PolKaScan>(this);
	}  
	
	public double getWavePlateAngle(double dMJD) {
		return Math.IEEEremainder(phi0 + 2.0 * Math.PI * dMJD * Unit.day * wavePlateFrequency, 2.0 * Math.PI);
	}
	
	public void calcWavePlate(double fromMJD, double toMJD) {
		if(timeStamps == null) return;
		
		int from = timeStamps.higherIndex(fromMJD);
		int to = timeStamps.lowerIndex(toMJD);
		
		if(from < 0 || to >= timeStamps.size()) 
			throw new IllegalStateException("No PolKa time stamps for specified time range.");
		
		int rotations = to - from;
		wavePlateFrequency = rotations / (toMJD - fromMJD) / Unit.day;
		
		System.err.println(" Half-wave plate frequency is " + Util.f4.format(wavePlateFrequency / Unit.Hz) + " Hz.");
		
		double rms = 0.0, max = 0.0;
		for(int i=from+1; i<to; i++) {
			double dev = timeStamps.get(i) * Unit.day - (i-from) / wavePlateFrequency;
			max = Math.max(dev, max);
			rms += dev*dev;			
		}
		if(rotations > 1) rms = Math.sqrt(rms / (rotations-1));

		System.err.println(" Half-wave plate jitter is " + Util.f3.format(1000.0*rms) + " ms rms, " + Util.f3.format(1000.0*max) + " ms max.");
		
	} 
}
