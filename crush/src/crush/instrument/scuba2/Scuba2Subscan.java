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

package crush.instrument.scuba2;

import crush.*;
import crush.telescope.GroundBased;
import crush.telescope.jcmt.JCMTTauTable;
import jnum.Unit;
import jnum.Util;
import jnum.astro.AstroTime;
import jnum.astro.HorizontalCoordinates;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class Scuba2Subscan extends Integration<Scuba2, Scuba2Frame> implements GroundBased {
	/**
	 * 
	 */
	private static final long serialVersionUID = -6513008414302600380L;
	
	ArrayList<Scuba2Fits> files = new ArrayList<Scuba2Fits>(4);
	
	public double totalIntegrationTime;
	public int rawFrames;

	int[] readoutLevel;
	
	public Scuba2Subscan(Scuba2Scan parent) {
		super(parent);
	}	
	
	
	@Override
	public void setTau() throws Exception {
		String source = option("tau").getValue().toLowerCase();
		if(source.equals("jctables") && hasOption("tau.jctables")) setJCMTTableTau();
		else super.setTau();
		
		printEquivalentTaus();	
	}
	
	public void setJCMTTableTau() throws Exception {
		String source = hasOption("tau.jctables") ? option("tau.jctables").getPath() : ".";
		String spec = scan.getShortDateString();
		String fileName = source + File.separator + spec + ".jcmt-183-ghz.dat";
		
		try {
			JCMTTauTable table = JCMTTauTable.get((int) scan.getMJD(), fileName);
			table.setOptions(option("tau"));
			setTau("183ghz", table.getTau(getMJD()));	
		}
		catch(IOException e) { fallbackTau("jctables", e); }
	}
	
	private void fallbackTau(String from, Exception e) throws Exception {
		if(hasOption(from + ".fallback")) {
			warning("Tau lookup failed: " + e.getMessage());
			String source = option(from + ".fallback").getValue().toLowerCase();
			if(source.equals(from)) {
				warning("Deadlocked fallback option!");
				throw e;
			}	
			info("... Falling back to '" + source + "'.");
			instrument.setOption("tau=" + source);
			setTau();
			return;
		}
		throw e;	
		
	}
	
	public void printEquivalentTaus() {	
		CRUSH.values(this, "--->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", tau(LOS):" + Util.f3.format(getTau("scuba2") / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);		
	}
	
	
	@Override
	public Scuba2Frame getFrameInstance() {
		return new Scuba2Frame((Scuba2Scan) scan);
	}
	
	
	public void read() throws FitsException, UnsupportedIntegrationException, IOException {
		clear();
		
		Scuba2Scan scuba2Scan = (Scuba2Scan) scan;
			
		readoutLevel = new int[scuba2Scan.subarrays * Scuba2Subarray.PIXELS];
		Arrays.fill(readoutLevel, scuba2Scan.blankingValue);
		
		// Read the subsequent subarray data (if any).
		for(int i=0; i<files.size(); i++) {
			readFile(files.get(i), i == 0);	
			System.gc();
		}
	}
	
	private void readFile(Scuba2Fits file, boolean isFirstFile) throws FitsException, UnsupportedIntegrationException, IOException {
		if(CRUSH.debug) CRUSH.detail(this, "<FILE> " + file.getFile().getName());
		
		Fits fits = new Fits(file.getFile());		
		BasicHDU<?>[] HDU = fits.read();
		
		if(isFirstFile) {
			parsePrimaryHeader(HDU[0].getHeader());
			
			// TODO WCS-TAB HDU has data timestamps...
			// TODO could match when reading coordinates, or check if same size...
			// Read the coordinate info etc. from the first subscan file.
			readCoordinateData(getJcmtHDU(HDU));
		}
		
		Scuba2Subarray subarray = instrument.subarray[file.getSubarrayIndex()];
		subarray.scaling = hasOption(subarray.id + ".scale") ? option(subarray.id + ".scale").getDouble() : 1.0;
	
		readArrayData((ImageHDU) HDU[0], fits.getStream(), subarray.channelOffset, (float) subarray.scaling);
		
		if(hasOption("darkcorrect")) readDarkSquidData(file.getSubarrayIndex(), getDarkSquidHDU(HDU));
		
		subarray.parseFlatcalHDU(getFlatcalHDU(HDU));
		
		fits.close();
	}

	private void setReadoutLevels(final int[][] DAC, final int channelOffset) {
		for(int bol=Scuba2Subarray.PIXELS; --bol >= 0; ) readoutLevel[channelOffset + bol] = DAC[bol%Scuba2.SUBARRAY_COLS][bol/Scuba2.SUBARRAY_COLS];
	}

	
	private void readArrayData(ImageHDU dataHDU, final ArrayDataInput in, final int channelOffset, final float scaling) throws IOException, FitsException {
	    final int[] sizes = dataHDU.getAxes();    
	    final int nt = sizes[0];
	    final int[][] data = new int[sizes[1]][sizes[2]];
        
	    // Trim the coordinates to match the data size...
        if(nt < size()) for(int t=size(); --t >= nt; ) remove(t);
        
        dataHDU.getData().reset();
        
	    for(int i=0; i<nt; i++) {
	        if(in.readLArray(data) <= 0) break;
	        
	        if(i == 0) setReadoutLevels(data, channelOffset);
	        Scuba2Frame frame = get(i);
	        if(frame != null) frame.parseData(data, channelOffset, scaling, readoutLevel);
	    }
    }
	
	public void readDarkSquidData(int subarrayIndex, BinaryTableHDU hdu) throws FitsException {
	    int[][] data = (int[][]) hdu.getRow(0)[hdu.findColumn("DATA")];
	    for(int t=0; t<size(); t++) get(t).setDarkSquid(subarrayIndex, data[t]);
	}
	
	
	public BinaryTableHDU getJcmtHDU(BasicHDU<?>[] HDU) {
		for(int i=1; i<HDU.length; i++) {
			String extName = HDU[i].getHeader().getStringValue("EXTNAME");
			if(extName != null) if(extName.endsWith("JCMTSTATE")) return (BinaryTableHDU) HDU[i];
		}
		return null;		
	}

	public BinaryTableHDU getDarkSquidHDU(BasicHDU<?>[] HDU) {
        for(int i=1; i<HDU.length; i++) {
            String extName = HDU[i].getHeader().getStringValue("EXTNAME");
            if(extName != null) if(extName.endsWith("DKSQUID.DATA_ARRAY")) return (BinaryTableHDU) HDU[i];
        }
        return null;        
    }
	
	public BinaryTableHDU getFlatcalHDU(BasicHDU<?>[] HDU) {
		for(int i=1; i<HDU.length; i++) {
			String extName = HDU[i].getHeader().getStringValue("EXTNAME");
			if(extName != null) if(extName.endsWith("FLATCAL.DATA_ARRAY")) return (BinaryTableHDU) HDU[i];
		}
		return null;		
	}
	
	public void darkCorrect() {
        info("Applying dark SQUID correction.");
        
        new Fork<Void>() {
            @Override
            protected void process(Scuba2Frame frame) {
                for(Scuba2Pixel pixel : instrument) 
                    frame.data[pixel.index] -= frame.darkSquid[pixel.subarrayNo][pixel.row % Scuba2.SUBARRAY_ROWS];
            }
            
        }.process();
    
    }
    
	@Override
    public void validate() {
	    if(hasOption("darkcorrect")) darkCorrect();
	    super.validate();
	}
	
	@Override
	public String getID() { return Integer.toString(integrationNo+1); }
	
	public void parsePrimaryHeader(Header header) throws HeaderCardException, UnsupportedIntegrationException {
		integrationNo = header.getIntValue("NSUBSCAN") - 1;
		
		boolean isDark = header.getDoubleValue("SHUTTER", 1.0) == 0.0;
		if(isDark) throw new DarkSubscanException();
		
		String sequenceType = header.getStringValue("SEQ_TYPE").toLowerCase();
		if(sequenceType.equals("fastflat")) throw new FastFlatSubscanException();
		if(sequenceType.equals("noise")) throw new NoiseSubscanException();
				
		totalIntegrationTime = header.getDoubleValue("INT_TIME") * Unit.s;
		rawFrames = header.getIntValue("NAXIS3"); 
	
		// Get the tracking coordinates for the scan, if not already set...
		Scuba2Scan scubaScan = (Scuba2Scan) scan;
		if(scubaScan.trackingClass == null) scubaScan.parseCoordinateInfo(header);
		
		info("Subscan " + getID() + ": " + Util.f2.format(totalIntegrationTime / Unit.s) + " seconds with " + rawFrames + " frames --> @ "
				+ Util.f2.format(rawFrames / totalIntegrationTime) + " Hz.");
		
		
		
		if(hasOption("subscan.minlength")) if(totalIntegrationTime < option("subscan.minlength").getDouble() * Unit.s)
			throw new IllegalStateException("Subscan " + getID() + " is less than " + option("subscan.minlength").getDouble() + "s long. Skipping.");

		
		instrument.integrationTime = instrument.samplingInterval = totalIntegrationTime / rawFrames;
	}
	
	public void readCoordinateData(BinaryTableHDU hdu) throws FitsException {
		
		// TODO chop phase and beam (L/R/M?)...	
		final Scuba2Scan scuba2Scan = (Scuba2Scan) scan;			
		
		Object[] table = null;
		
		try { table = (Object[]) ((ColumnTable<?>) hdu.getData().getData()).getRow(0); }
		catch(NullPointerException e) {
		    error("FITS input is missing essential binary tables.");
		    CRUSH.suggest(this,
		            "        Use the 'proexts' option when converting from SDF. E.g.:\n\n" +
		            "         > ndf2fits <input.sdf> <output.fits> proexts");
		    throw new IllegalArgumentException("FITS contains no table data.");
		}
			
		//final boolean isEquatorial = scuba2Scan.trackingClass == EquatorialCoordinates.class;
			
		final double[] MJDTAI = (double[]) table[hdu.findColumn("TCS_TAI")];
		final double TAI2TT = AstroTime.TAI2TT / Unit.day;
		final int samples = MJDTAI.length;
			
		if(samples < 2) {
			scan.warning("Subscan " + getID() + " has no coordinate data. Dropping from set.");
			return;
		}
				
	
		final double[] AZ = (double[]) table[hdu.findColumn("TCS_AZ_AC1")];
		final double[] EL = (double[]) table[hdu.findColumn("TCS_AZ_AC2")];		
		final double[] tAZ = (double[]) table[hdu.findColumn("TCS_AZ_BC1")];
		final double[] tEL = (double[]) table[hdu.findColumn("TCS_AZ_BC2")];

	
		final int[] SN = (int[]) table[hdu.findColumn("RTS_NUM")];
		int iDT = hdu.findColumn("SC2_MIXTEMP");
		final float[] DT = iDT < 0 ? null : (float[]) table[iDT];

		
		final double[] CX = (double[]) table[hdu.findColumn("SMU_AZ_CHOP_X")];
		final double[] CY = (double[]) table[hdu.findColumn("SMU_AZ_CHOP_Y")];
		final boolean isChopped = (CX.length == AZ.length);
	
		final double[] JX = (double[]) table[hdu.findColumn("SMU_AZ_JIG_X")];
		final double[] JY = (double[]) table[hdu.findColumn("SMU_AZ_JIG_Y")];
		final boolean isJiggled = (CX.length == AZ.length);
	
		clear();
		ensureCapacity(samples);
		for(int i=samples; --i >=0; ) add(null);
		
		new CRUSH.Fork<Void>(samples, getThreadCount()) {
			private AstroTime time;
			
			@Override
			public void init() {
				time = new AstroTime();
			}
			
			@Override
			protected void processIndex(int i) {
				// Check to see if the frame has valid astrometry...
				if(Double.isNaN(AZ[i])) return;
				
				final Scuba2Frame frame = new Scuba2Frame(scuba2Scan);

				//final double UT = (((double[]) row[iUT])[0] * Unit.sec) % Unit.day;
				frame.MJD = MJDTAI[i] + TAI2TT;
				time.setMJD(frame.MJD);
						
				frame.LST = time.getLMST(scan.site.longitude(), scuba2Scan.dUT1);
					
				frame.horizontal = new HorizontalCoordinates(AZ[i], EL[i]);
				frame.horizontalOffset = new Vector2D((AZ[i] - tAZ[i]) * frame.horizontal.cosLat(), EL[i] - tEL[i]);		
				
				if(isChopped || isJiggled) {
					frame.chopperPosition = new Vector2D();
					
					if(isChopped) { frame.chopperPosition.addX(CX[i]); frame.chopperPosition.addY(CY[i]); }
					if(isJiggled) { frame.chopperPosition.addX(JX[i]); frame.chopperPosition.addY(JY[i]); }
		
					frame.horizontalOffset.add(frame.chopperPosition);
					frame.horizontal.addOffset(frame.chopperPosition);
				}
				
				frame.calcParallacticAngle();

				frame.frameNumber = SN[i];
				if(DT != null) frame.detectorT = DT[i];
				
				set(i, frame);

			}
			
		}.process();
		
	}

	
	/*
	public void writeTemperatureGains() throws IOException {
		// Now write to a file
		String fileName = CRUSH.workPath + File.separator + "temperature-gains-" + getFileID + ".dat";
		instrument.writeTemperatureGains(fileName, getASCIIHeader());
	}
	
	

	@Override
	public void writeProducts() {
		super.writeProducts();
		if(hasOption("write.tgains")) {
			try { writeTemperatureGains(); }
			catch(IOException e) { warning("Problem writing temperature gains."); }
		}
	}
	*/
	
	
	@Override
	public String getFullID(String separator) {
		return super.getFullID(separator) + separator + instrument.filter;
	}
	
}
