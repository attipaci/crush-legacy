/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila_kovacs[AT]post.harvard.edu>.
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

package crush.hawcplus;

import java.io.File;
import java.util.List;

import crush.CRUSH;
import crush.Channel;
import crush.fits.HDUReader;
import crush.fits.HDURowReader;
import crush.sofia.SofiaChopperData;
import crush.sofia.SofiaIntegration;
import jnum.Unit;
import jnum.Util;
import jnum.astro.*;
import jnum.math.Vector2D;
import nom.tam.fits.*;
import nom.tam.util.ArrayDataInput;

public class HawcPlusIntegration extends SofiaIntegration<HawcPlus, HawcPlusFrame> {	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3894220792729801094L;

	public boolean flagJumps = true;
	
	public HawcPlusIntegration(HawcPlusScan parent) {
		super(parent);
	}	
	
	@Override
	public void setTau() throws Exception {
		super.setTau();
		// TODO printEquivalentTaus();
	}
	
	// TODO
	/*
	public void printEquivalentTaus() {
		System.err.println("   --->"
				+ " tau(225GHz):" + Util.f3.format(getTau("225ghz"))
				+ ", tau(LOS):" + Util.f3.format(zenithTau / scan.horizontal.sinLat())
				+ ", PWV:" + Util.f2.format(getTau("pwv")) + "mm"
		);		
	}
	*/
	
	@Override
	public HawcPlusFrame getFrameInstance() {
		return new HawcPlusFrame((HawcPlusScan) scan);
	}
	
	protected void read(List<BinaryTableHDU> dataHDUs) throws Exception {	
	    
		int records = 0;
		for(BinaryTableHDU hdu : dataHDUs) records += hdu.getAxes()[0];
		
		System.err.println(" Processing scan data:");
		System.err.println("   Reading " + records + " frames from " + dataHDUs.size() + " HDU(s).");
		System.err.println("   Sampling at " + Util.f2.format(1.0 / instrument.integrationTime) + " Hz ---> " 
				+ Util.f1.format(instrument.samplingInterval * records / Unit.min) + " minutes.");
			
		clear();
		ensureCapacity(records);
		for(int t=records; --t>=0; ) add(null);
		
		int startIndex = 0;
		for(int i=0; i<dataHDUs.size(); i++) {
			BinaryTableHDU hdu = dataHDUs.get(i);
			//new HawcPlusReader(hdu, startIndex).read();
			new HawcPlusRowReader(hdu, ((HawcPlusScan) scan).fits.getStream()).read(1);
			
			startIndex += hdu.getAxes()[0];
		}	
		
	}
		
	/*
	class HawcPlusReader extends HDUReader {	
		private int startIndex;
		private long[] SN;
		private int[] DAC;
		private short[] jump;
		private double[] TS; 
		private double[] RA, DEC, iVPA, tVPA, cVPA, LON, LAT, LST, PWV;
		private double[] objectRA, objectDEC;
		private float[] chopR, chopS;
		private int[] HWP, statusFlag;
		
		private boolean isLab;
			
		private final HawcPlusScan hawcPlusScan = (HawcPlusScan) scan;
		
		public HawcPlusReader(BinaryTableHDU hdu, int startIndex) throws FitsException {
			super(hdu);
			
			this.startIndex = startIndex;
		
			isLab = hasOption("lab");
				
			Object[] row = (Object[]) hdu.getRow(0);
			
			// The Sofia timestamp (decimal seconds since 0 UTC 1 Jan 1970...
			TS = (double[]) table.getColumn(hdu.findColumn("Timestamp"));	
			SN = (long[]) table.getColumn(hdu.findColumn("FrameCounter"));
			
			try { jump = (short[]) table.getColumn(hdu.findColumn("FluxJumps")); }
			catch(ClassCastException e) { System.err.println("   WARNING! unexpected FluxJumps storage format"); }
			
			// The R/T array data
			int iData = hdu.findColumn("SQ1Feedback");
			DAC = (int[]) table.getColumn(iData);
			
			int storeRows = ((int[][]) row[iData]).length;
			int storeCols = ((int[][]) row[iData])[0].length;
			System.err.println("   FITS has " + storeRows + "x" + storeCols + " arrays.");
			
			// HWP may be used in the future if support is extended for
            // scan-mode polarimetry (or polarimetry, in general...
			HWP = (int[]) table.getColumn(hdu.findColumn("hwpCounts"));
            
			// Ignore coordinate info for 'lab' data...
			if(isLab) {
			    System.err.println("   Lab mode data reduction. Ignoring telescope data...");
			    return;
			}
			
			statusFlag = (int[]) table.getColumn(hdu.findColumn("Flag"));
			
			//AZ = (double[]) table.getColumn(hdu.findColumn("AZ"));
            //EL = (double[]) table.getColumn(hdu.findColumn("EL"));
			
			// The tracking center in the basis coordinates of the scan (usually RA/DEC)
			RA = (double[]) table.getColumn(hdu.findColumn("RA"));
			DEC = (double[]) table.getColumn(hdu.findColumn("DEC"));

			if(scan.isNonSidereal) {
			    objectRA = (double[]) table.getColumn(hdu.findColumn("NonSiderealRA"));
	            objectDEC = (double[]) table.getColumn(hdu.findColumn("NonSiderealDec"));
			}
			
			// TODO should not be needed if data is proper
			// Check that:
			//   * OBJRA and OBJDEC set correctly
			//   * NonSiderealRA and NonSiderealDEC columns are filled.
			for(int i=0; i<RA.length; i++) if(!Double.isNaN(RA[i])) {
			    if(scan.equatorial == null) {
			        warning("Missing OBJRA/OBJDEC header keys. Using initial position instead.");
			        scan.equatorial = new EquatorialCoordinates(RA[i] * Unit.hourAngle, DEC[i] * Unit.deg, CoordinateEpoch.J2000);
			    }
			        
			    if(objectRA != null) if(Double.isNaN(objectRA[i])) {
			        objectRA = objectDEC = null;
			        if(scan.isNonSidereal) warning("Missing NonSiderealRA/NonSiderealDEC columns. Forcing sidereal mapping.");
			        scan.isNonSidereal = false;
			    }
			    
			    break;
			}
				
			LST = (double[]) table.getColumn(hdu.findColumn("LST"));

			iVPA = (double[]) table.getColumn(hdu.findColumn("SIBS_VPA"));
			tVPA = (double[]) table.getColumn(hdu.findColumn("TABS_VPA"));
			cVPA = (double[]) table.getColumn(hdu.findColumn("Chop_VPA"));

			LON = (double[]) table.getColumn(hdu.findColumn("LON"));
			LAT = (double[]) table.getColumn(hdu.findColumn("LAT"));
			
			// Interpolate between NaN values...
			int gaps = fillGaps(LON);
			fillGaps(LAT);
			System.err.println("   Gaps in LAT/LON data: " + gaps);
			
			
			chopR = (float[]) table.getColumn(hdu.findColumn("sofiaChopR"));
			chopS = (float[]) table.getColumn(hdu.findColumn("sofiaChopS"));

			PWV = (double[]) table.getColumn(hdu.findColumn("PWV"));
			
			// TODO a better way to clean up as we go...
			hawcPlusScan.closeFits();
			
		}
		
		@Override
		public Reader getReader() {
			return new Reader() {	
				private AstroTime timeStamp;
				private EquatorialCoordinates objectEq, apparent;
				
				@Override
				public void init() {
				    super.init();
				    
					timeStamp = new AstroTime();
					apparent = new EquatorialCoordinates();
					if(scan.equatorial != null) objectEq = (EquatorialCoordinates) scan.equatorial.copy();		
				}
				
 				@Override
				public void processRow(int i) {	
				    HawcPlus hawc = (HawcPlus) scan.instrument;
										
					// Create the frame object only if it cleared the above hurdles...
					final HawcPlusFrame frame = new HawcPlusFrame(hawcPlusScan);
					frame.index = i;
					frame.hasTelescopeInfo = !isLab;
						
					// Read the pixel data (DAC and MCE jump counter)
					frame.parseData(i, DAC, jump);
					frame.mceSerial = SN[i];
					
                    timeStamp.setUTCMillis(Math.round(1000.0 * TS[i]));
                    frame.MJD = timeStamp.getMJD();
                       
                    frame.hwpAngle = (float) (HWP[i] * HawcPlus.hwpStep - hawc.hwpTelescopeVertical);
                    
                    set(startIndex + i, frame);
					
					if(!frame.hasTelescopeInfo) return;
					
					// ======================================================================================
					// Below here is telescope data only, which will be ignored for 'lab' mode reductions...
					// Add the astrometry...
					// ======================================================================================
					
					frame.status = statusFlag[i];       
					
					// If there is no valid astrometry, then skip...
                    if(Double.isNaN(RA[i])) return;
                       
                    frame.PWV = PWV[i] * (float) Unit.um; 
                    
                    frame.site = new GeodeticCoordinates(LON[i] * Unit.deg, LAT[i] * Unit.deg);  
                    frame.LST = LST[i] * (float) Unit.hour;
                    
                    frame.equatorial = new EquatorialCoordinates(RA[i] * Unit.hourAngle, DEC[i] * Unit.deg, objectEq.epoch);							
					if(scan.isNonSidereal) objectEq.set(objectRA[i] * Unit.hourAngle, objectDEC[i] * Unit.deg);
					
					// TODO if MCCS fixes alt/az inconsistency then we can just realy on their data...
					//frame.horizontal = new HorizontalCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
                    //frame.telescopeCoords = new TelescopeCoordinates(AZ[i] * Unit.deg, EL[i] * Unit.deg);
                    
					// Calculate AZ/EL -- the values in the table are noisy aircraft values...
                    apparent.copy(frame.equatorial);
                    scan.toApparent.precess(apparent);
                    frame.horizontal = apparent.toHorizontal(frame.site, frame.LST);
                 
                    // TODO use actual telescope XEL, EL...
                    frame.telescopeCoords = new TelescopeCoordinates();
                    frame.telescopeCoords.copy(frame.horizontal);
					
					// I  -> T      rot by phi (instrument rotation)
                    // T' -> E      rot by -theta_ta
                    // T  -> H      rot by ROF
                    // H  -> E'     rot by PA
                    // I' -> E      rot by -theta_si
                    //
                    // T -> H -> E': theta_ta = ROF + PA
                    //
                    //    PA = theta_ta - ROF
                    //
                    // I -> T -> E': theta_si = phi - theta_ta
                    //
                    //    phi = theta_si - theta_ta
					//
					frame.instrumentVPA = iVPA[i] * (float) Unit.deg;
					frame.telescopeVPA = tVPA[i] * (float) Unit.deg;
					frame.chopVPA = cVPA[i] * (float) Unit.deg;
					
					// rotation from pixel coordinates to telescope coordinates...	
					frame.setRotation(frame.instrumentVPA - frame.telescopeVPA);
					
					// rotation from telescope coordinates to equatorial.
                    frame.setParallacticAngle(frame.telescopeVPA);
				
					// Calculate the scanning offsets...
					frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(objectEq);
					frame.equatorialNativeToHorizontal(frame.horizontalOffset);
					
					// In telescope XEL (phiS), EL (phiR)
					frame.chopperPosition = new Vector2D(chopS[i] * Unit.V, chopR[i] * Unit.V);
					
					// TODO empirical scaling...
					frame.chopperPosition.scale(SofiaChopperData.volts2Angle);
				
					// Rotate the chopper offset into the TA frame...
					// C -> E' rot by theta_cp
					// T -> E' rot by theta_ta
					// C -> T rot by theta_cp - theta_ta
					frame.chopperPosition.rotate(frame.chopVPA - frame.telescopeVPA);

				}
				
			};
		}
	}	
	*/
	
	
	class HawcPlusRowReader extends HDURowReader { 
	    private int iSN=-1, iDAC=-1, iJump=-1, iTS=-1;
	    private int iRA=-1, iDEC=-1, iAVPA=-1, iTVPA=-1, iCVPA=-1;
	    private int iLON=-1, iLAT=-1, iLST=-1, iPWV=-1, iORA=-1, iODEC=-1;
	    private int iChopR=-1, iChopS=-1, iHWP=-1, iStat=-1;
     
        private boolean isLab;
        private boolean isConfigured = false;
       
        private final HawcPlusScan hawcPlusScan = (HawcPlusScan) scan;
        
        public HawcPlusRowReader(BinaryTableHDU hdu, ArrayDataInput in) throws FitsException {
            super(hdu, in);
            
            isLab = hasOption("lab");
              
            // The Sofia timestamp (decimal seconds since 0 UTC 1 Jan 1970...
            iTS = hdu.findColumn("Timestamp");   
            iSN = hdu.findColumn("FrameCounter");
            
            iJump = hdu.findColumn("FluxJumps");
            iDAC = hdu.findColumn("SQ1Feedback");
              
            // HWP may be used in the future if support is extended for
            // scan-mode polarimetry (or polarimetry, in general...
            iHWP = hdu.findColumn("hwpCounts");
            
            // Ignore coordinate info for 'lab' data...
            if(isLab) {
                System.err.println("   Lab mode data reduction. Ignoring telescope data...");
                return;
            }
            
            iStat = hdu.findColumn("Flag");
            
            //iAZ = hdu.findColumn("AZ");
            //iEL = hdu.findColumn("EL");
            
            // The tracking center in the basis coordinates of the scan (usually RA/DEC)
            iRA = hdu.findColumn("RA");
            iDEC = hdu.findColumn("DEC");

            if(scan.isNonSidereal) {
                iORA = hdu.findColumn("NonSiderealRA");
                iODEC = hdu.findColumn("NonSiderealDec");
            }
                
            iLST = hdu.findColumn("LST");

            iAVPA = hdu.findColumn("SIBS_VPA");
            iTVPA = hdu.findColumn("TABS_VPA");
            iCVPA = hdu.findColumn("Chop_VPA");

            iLON = hdu.findColumn("LON");
            iLAT = hdu.findColumn("LAT");  
            
            iChopR = hdu.findColumn("sofiaChopR");
            iChopS = hdu.findColumn("sofiaChopS");

            iPWV = hdu.findColumn("PWV");
            
        }
        
        private synchronized void configure(Object[] row) {
            if(isConfigured) return;
            
            int storeRows = ((int[][]) row[iDAC]).length;
            int storeCols = ((int[][]) row[iDAC])[0].length;
            System.err.println("   FITS has " + storeRows + "x" + storeCols + " arrays.");
            
            if(scan.equatorial == null) {
                warning("Missing OBJRA/OBJDEC header keys. Using initial position instead.");
                scan.equatorial = new EquatorialCoordinates(((double[]) row[iRA])[0] * Unit.hourAngle, ((double[]) row[iDEC])[0] * Unit.deg, CoordinateEpoch.J2000);
            }
            
            if(iORA >= 0) if(Double.isNaN(((double[]) row[iORA])[0])) {
                iORA = iODEC = -1;
                if(scan.isNonSidereal) warning("Missing NonSiderealRA/NonSiderealDEC columns. Forcing sidereal mapping.");
                scan.isNonSidereal = false;
            } 
             
            isConfigured = true;
        }
        
      
        @Override
        public Reader getReader() {
            return new Reader() {   
                private AstroTime timeStamp;
                private EquatorialCoordinates objectEq, apparent;
                private boolean isConfigured = false;
                
                @Override
                public void init() {
                    super.init();
                    
                    timeStamp = new AstroTime();
                    apparent = new EquatorialCoordinates();
                    if(scan.equatorial != null) objectEq = (EquatorialCoordinates) scan.equatorial.copy();      
                }
                
              
                
                @Override
                public void processRow(int i, Object[] row) {                    
                    HawcPlus hawc = (HawcPlus) scan.instrument;
                                        
                    // Create the frame object only if it cleared the above hurdles...
                    final HawcPlusFrame frame = new HawcPlusFrame(hawcPlusScan);
                    frame.index = i;
                    frame.hasTelescopeInfo = !isLab;
                        
                    // Read the pixel data (DAC and MCE jump counter)
                    frame.parseData((int[][]) row[iDAC], (short[][]) row[iJump]);
                    frame.mceSerial = ((long[]) row[iSN])[0];
                    
                    timeStamp.setUTCMillis(Math.round(1000.0 * ((double[]) row[iTS])[0]));
                    frame.MJD = timeStamp.getMJD();
                       
                    frame.hwpAngle = (float) (((int[]) row[iHWP])[0] * HawcPlus.hwpStep - hawc.hwpTelescopeVertical);
                    
                    set(i, frame);
                    
                    if(!frame.hasTelescopeInfo) return;
                    
                    // ======================================================================================
                    // Below here is telescope data only, which will be ignored for 'lab' mode reductions...
                    // Add the astrometry...
                    // ======================================================================================
                    
                    frame.status = ((int[]) row[iStat])[0];       
                    
                    // If there is no valid astrometry, then skip...
                    if(Double.isNaN(((double[]) row[iRA])[0])) return;
                    if(Double.isNaN(((double[]) row[iLON])[0])) return;
                    
                    if(!isConfigured) configure(row);
                    if(objectEq == null) objectEq = (EquatorialCoordinates) scan.equatorial.copy();
                    
                    frame.PWV = ((double[]) row[iPWV])[0] * (float) Unit.um; 
                    
                    frame.site = new GeodeticCoordinates(((double[]) row[iLON])[0] * Unit.deg, ((double[]) row[iLAT])[0] * Unit.deg);  
                    frame.LST = ((double[]) row[iLST])[0] * (float) Unit.hour;
                    
                    frame.equatorial = new EquatorialCoordinates(((double[]) row[iRA])[0] * Unit.hourAngle, ((double[]) row[iDEC])[0] * Unit.deg, objectEq.epoch);                            
                    if(scan.isNonSidereal) objectEq.set(((double[]) row[iORA])[0] * Unit.hourAngle, ((double[]) row[iODEC])[0] * Unit.deg);
                
                     
                    // TODO if MCCS fixes alt/az inconsistency then we can just realy on their data...
                    //frame.horizontal = new HorizontalCoordinates(((double[]) row[iAZ])[0] * Unit.deg, ((double[]) row[iEL])[0] * Unit.deg);                
                    //frame.telescopeCoords = new TelescopeCoordinates(frame.horizontal);
                    
                    // Calculate AZ/EL -- the values in the table are noisy aircraft values...
                    apparent.copy(frame.equatorial);
                    scan.toApparent.precess(apparent);
                    frame.horizontal = apparent.toHorizontal(frame.site, frame.LST);
                 
                    // TODO use actual telescope XEL, EL...
                    frame.telescopeCoords = new TelescopeCoordinates();
                    frame.telescopeCoords.copy(frame.horizontal);
                    
                    // I  -> T      rot by phi (instrument rotation)
                    // T' -> E      rot by -theta_ta
                    // T  -> H      rot by ROF
                    // H  -> E'     rot by PA
                    // I' -> E      rot by -theta_si
                    //
                    // T -> H -> E': theta_ta = ROF + PA
                    //
                    //    PA = theta_ta - ROF
                    //
                    // I -> T -> E': theta_si = phi - theta_ta
                    //
                    //    phi = theta_si - theta_ta
                    //
                    frame.instrumentVPA = ((double[]) row[iAVPA])[0] * (float) Unit.deg;
                    frame.telescopeVPA = ((double[]) row[iTVPA])[0] * (float) Unit.deg;
                    frame.chopVPA = ((double[]) row[iCVPA])[0] * (float) Unit.deg;
                    
                    // rotation from pixel coordinates to telescope coordinates...  
                    frame.setRotation(frame.instrumentVPA - frame.telescopeVPA);
                    
                    // rotation from telescope coordinates to equatorial.
                    frame.setParallacticAngle(frame.telescopeVPA);
                
                    // Calculate the scanning offsets...
                    frame.horizontalOffset = frame.equatorial.getNativeOffsetFrom(objectEq);
                    frame.equatorialNativeToHorizontal(frame.horizontalOffset);
                    
                    // In telescope XEL (phiS), EL (phiR)
                    frame.chopperPosition = new Vector2D(((float[]) row[iChopS])[0] * Unit.V, ((float[]) row[iChopR])[0] * Unit.V);
                    
                    // TODO empirical scaling...
                    frame.chopperPosition.scale(SofiaChopperData.volts2Angle);
                
                    // Rotate the chopper offset into the TA frame...
                    // C -> E' rot by theta_cp
                    // T -> E' rot by theta_ta
                    // C -> T rot by theta_cp - theta_ta
                    frame.chopperPosition.rotate(frame.chopVPA - frame.telescopeVPA);
                }
                
            };
        }
    }   
	
	
	@Override
	public void writeProducts() {
		super.writeProducts();
		
		if(hasOption("write.flatfield")) {
			String fileName = option("write.flatfield").getValue();
			if(fileName.isEmpty()) fileName = CRUSH.workPath + File.separator + "flatfield-" + getDisplayID() + ".fits";
			try { instrument.writeFlatfield(fileName); }
			catch(Exception e) { e.printStackTrace(); }
		}
	}
	
	@Override
	public String getFullID(String separator) {
		return scan.getID();
	}

	@Override
    public void validate() {
	      
	    
	    checkJumps();
	        
	    super.validate();
	    
	    flagJumps = hasOption("flagjumps");
	}
	
	private void checkJumps() {
	    System.err.print("   Checking for flux jumps... ");

	    final byte[] startCounter = getFirstFrame().jumpCounter;   

	    new Fork<Void>() {        
	        @Override
	        protected void process(HawcPlusFrame frame) {
	            for(int k=startCounter.length; --k >= 0; ) 
	                if(frame.jumpCounter[k] != startCounter[k]) instrument.get(k).hasJumps = true;
	        }

	    }.process();

	    int jumpPixels = 0;
	    for(HawcPlusPixel pixel : instrument) if(pixel.hasJumps) jumpPixels++;

	    System.err.println(jumpPixels > 0 ? "found jump(s) in " + jumpPixels + " pixels." : "All good!");
	}
	
	
	@Override
    public boolean checkConsistency(final Channel channel, final int from, final int to) {
	    boolean result = super.checkConsistency(channel, from, to);
	    
	    if(!((HawcPlusPixel) channel).hasJumps) return result;
	    
	    if(!flagJumps) return result;
	    
	    boolean isInitialized = false;
	    byte jumpStart = (byte) 0;
	    
	    final int clearFlag = ~HawcPlusFrame.SAMPLE_PHI0_JUMP;
	    
	    for(int t=to; --t > from; ) {
	        final HawcPlusFrame exposure = get(t);
	        if(exposure == null) continue;
	     
	        exposure.sampleFlag[channel.index] &= clearFlag;
	        
	        if(!isInitialized) {
	            jumpStart = exposure.jumpCounter[channel.index];
	            isInitialized = true;
	        }
	        
	        else if(exposure.jumpCounter[channel.index] != jumpStart) {      
	            flagJump(channel, from, to);
	            return false;
	        }
	     
	    }
	        
	    return result;
	}
	
	private void flagJump(final Channel channel, final int from, int to) {
	    while(--to >= from) get(to).sampleFlag[channel.index] |= HawcPlusFrame.SAMPLE_PHI0_JUMP;
	}
	
	
	// TODO fill gaps in position data, if any...
	private int fillGaps(double[] x) {   
	    int from = 0;
	    int gaps = 0;
	    
	    while(from < x.length-1) {
	        // Skip over valid points until a gap is found...
	        while(!Double.isNaN(x[from]) && from < x.length-1) from++;
	        
	        // Find the next valid point after the gap...
	        int to = from;
	        while(Double.isNaN(x[to]) && to < x.length-1) to++;

	        if(to >= x.length) return gaps;

	        gaps++;
	        
	        double last = x[from];
	        double delta = (x[to] - x[from]) / (to - from);

	        for(int i=from+1; i<to; i++) {
	            last += delta;
	            x[i] = last;
	        }

	        from = to;
	    }
	        
	    return gaps;
	
	}
	
}
