/*******************************************************************************
 * Copyright (c) 2016 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.hawcplus;


import crush.Channel;
import crush.Frame;
import crush.Instrument;
import crush.telescope.sofia.SofiaFrame;
import jnum.astro.GeodeticCoordinates;
import jnum.math.Vector2D;


public class HawcPlusFrame extends SofiaFrame {
    /**
     * 
     */
    private static final long serialVersionUID = 6511202510198331668L;

    long mceSerial;
    float hwpAngle;
    byte[] jumpCounter;
    
    public float LOS, roll;
    
    int status;

    boolean isComplete = false;
    
  
    
    public HawcPlusFrame(HawcPlusScan hawcScan) {
        super(hawcScan);
        create(hawcScan.instrument.size());
    }
    
    @Override
    public HawcPlusFrame copy(boolean withContents) {
        HawcPlusFrame copy = (HawcPlusFrame) super.copy(withContents);
        
        if(jumpCounter != null) {
            copy.jumpCounter = new byte[jumpCounter.length];
            if(withContents) System.arraycopy(jumpCounter, 0, copy.jumpCounter, 0, jumpCounter.length);
        }
        
        return copy;
    }

    public void parseData(int frameIndex, int[] DAC, short[] jump) {   
        parseData(DAC, jump, frameIndex * FITS_CHANNELS);
    }

    // Parses data for valid pixels only...
    private void parseData(int[] DAC, short[] jump, int from) {  
        HawcPlus hawc = (HawcPlus) scan.instrument;
        
        if(jump != null) jumpCounter = new byte[data.length];
        
        for(final HawcPlusPixel pixel : hawc) {
            data[pixel.index] = DAC[from + pixel.fitsIndex] / hawc.subarrayGainRenorm[pixel.sub];
            if(jump != null) jumpCounter[pixel.index] = (byte) jump[from + pixel.fitsIndex];
        }
    }
    
    public void parseData(int[][] DAC, short[][] jump) {  
        HawcPlus hawc = (HawcPlus) scan.instrument;
        
        if(jump != null) jumpCounter = new byte[data.length];
        
        for(final HawcPlusPixel pixel : hawc) {
            data[pixel.index] = DAC[pixel.fitsRow][pixel.fitsCol] / hawc.subarrayGainRenorm[pixel.sub];
            if(jump != null) jumpCounter[pixel.index] = (byte) jump[pixel.fitsRow][pixel.fitsCol];
        }
    }

    @Override
    public void cloneReadout(Frame from) {
        super.cloneReadout(from);
        
        HawcPlusFrame frame = (HawcPlusFrame) from;
        jumpCounter = frame.jumpCounter;
        chopperPosition = frame.chopperPosition;
        hwpAngle = frame.hwpAngle;
        MJD = frame.MJD;
        mceSerial = frame.mceSerial;
    }
    
    @Override
    public void addDataFrom(Frame other, double scaling) {
        super.addDataFrom(other, scaling);
        if(scaling == 0.0) return;
        
        final HawcPlusFrame frame = (HawcPlusFrame) other;
        final float fScale = (float) scaling;
        
        hwpAngle += fScale * frame.hwpAngle;
    }
    
    
    @Override
    public void slimTo(Instrument<?> instrument) {
        super.slimTo(instrument);

        if(jumpCounter == null) return;

        final byte[] newJumpCounter = new byte[instrument.size()];
        for(int k=instrument.size(); --k >= 0; ) newJumpCounter[k] = jumpCounter[instrument.get(k).index];      

        jumpCounter = newJumpCounter;
    }
  
    @Override
    public GeodeticCoordinates getSite() { return site; }
    
    @Override
    public boolean validate() {
        HawcPlusScan hawcScan = (HawcPlusScan) scan;
        HawcPlus hawc = hawcScan.instrument;
        
        if(!isComplete) return false;
         
        // Skip data that is not normal observing
        if(status != FITS_FLAG_NORMAL_OBSERVING || (hawcScan.useBetweenScans && status == FITS_FLAG_BETWEEN_SCANS)) 
            return false;
        
        if(!Double.isNaN(hawcScan.transitTolerance)) if(hawcScan.chopper != null) {
            double dev = Math.abs(chopperPosition.length()) - hawcScan.chopper.amplitude;
            if(Math.abs(dev) > hawcScan.transitTolerance) return false;
        }
        
        
        if(hasTelescopeInfo) {
            if(equatorial == null) return false;
            if(equatorial.isNull()) return false;
            if(scan.isNonSidereal) if(objectEq.isNull()) return false;
            
            if(equatorial.isNaN()) return false;
            if(horizontal.isNaN()) return false;
         
            if(Double.isNaN(LST)) return false;
            if(Double.isNaN(site.longitude())) return false;
            if(Double.isNaN(site.latitude())) return false;
            if(Double.isNaN(telescopeVPA)) return false;
            if(Double.isNaN(instrumentVPA)) return false;
            
            if(chopperPosition != null) {
                if(Double.isNaN(chopVPA)) return false;
                
                horizontalOffset.add(chopperPosition);
                horizontal.addOffset(chopperPosition);

                Vector2D offset = chopperPosition.copy();
                
                horizontalToNativeEquatorial(offset);
                
                equatorial.addNativeOffset(offset);
            }

            // TODO HWP angle in equatorial... (check sign)
            hwpAngle += telescopeVPA;
        }
           
        if(hawc.darkSquidCorrection) darkCorrect();

        return super.validate();
       
    }
    

    public void darkCorrect() {
        HawcPlus hawc = (HawcPlus) scan.instrument;

        for(HawcPlusPixel pixel : hawc) if(!pixel.isFlagged(Channel.FLAG_BLIND))
            data[pixel.index] -= data[hawc.darkSquidLookup[pixel.sub][pixel.col]];
    }
  
    public void instrumentToEquatorial(Vector2D offset) {
        offset.rotate(-instrumentVPA);
    }

    public void equatorialToInstrument(Vector2D offset) {
        offset.rotate(instrumentVPA);
    }
   
    public final static int JUMP_RANGE = 1<<7;  // Jumps are 7-bit signed values...
    
    public final static int FITS_FLAG_NORMAL_OBSERVING = 0;
    public final static int FITS_FLAG_LOS_REWIND = 1;
    public final static int FITS_FLAG_IVCURVES = 2;
    public final static int FITS_FLAG_BETWEEN_SCANS = 3;

    public final static int FITS_ROWS = 41;
    public final static int FITS_COLS = 128;
    public final static int FITS_CHANNELS = FITS_ROWS * FITS_COLS;

    public static byte SAMPLE_PHI0_JUMP = sampleFlags.next('j', "phi0 jump").value();
    public static byte SAMPLE_TRANSIENT_NOISE = sampleFlags.next('T', "transient noise").value();
   
}
