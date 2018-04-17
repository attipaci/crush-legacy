/*******************************************************************************
 * Copyright (c) 2018 Attila Kovacs <attila[AT]sigmyne.com>.
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

package crush.instrument.hirmes;


import crush.Channel;
import crush.array.SingleColorPixel;
import jnum.Constant;
import jnum.Util;
import jnum.text.SmartTokenizer;

public class HirmesPixel extends SingleColorPixel {
    /**
     * 
     */
    private static final long serialVersionUID = 293691569452930105L;
    
    public int sub, subrow, subcol, row, col, mux, pin, seriesArray, biasLine;
    public float jump = 0.0F;
    public boolean hasJumps = false;
    
    public double subGain = 1.0, rowGain = 1.0, colGain = 1.0, muxGain = 1.0, pinGain = 1.0, seriesGain = 1.0, biasGain = 1.0;
    
    int jumpCounter = 0;
    
    double frequency;
    
    public HirmesPixel(Hirmes array, int zeroIndex) {
        super(array, zeroIndex);
           
        mux = zeroIndex / Hirmes.muxRows;
        pin = zeroIndex % Hirmes.muxRows;
        
        if(pin == Hirmes.DARK_SQUID_PIN) flag(FLAG_BLIND);
        
        sub = zeroIndex >= Hirmes.imagingPixels ? Hirmes.HIRES_ARRAY : Hirmes.IMAGING_ARRAY;
        
        row = subrow = pin % Hirmes.rows;   // Dark squid: row = 0;
        if(sub > 0) row += Hirmes.rows;     // Hires: rows 16-31
        
        col = 2*mux + (pin >= Hirmes.rows ? 1 : 0); // each mux reads out 2 geometric columns...
        subcol = col >= Hirmes.imagingCols ? col - Hirmes.imagingCols : col;
        
        frequency = Constant.c / array.instrumentData.wavelength;
        if(array.mode == Hirmes.MIDRES_MODE) frequency += subcol * array.frequencyStep;
        
        // TODO seriesArray, biasLine
    }
    
    @Override
    public double getFrequency() { return frequency; }

    public void calcSIBSPosition() {
        if(isFlagged(FLAG_BLIND)) position = null;
        position = ((Hirmes) instrument).getSIBSPosition(sub, subrow, subcol);
    }
        
    @Override
    public int getCriticalFlags() {
        return FLAG_DEAD;
    }
    
    @Override
    public void uniformGains() {
        super.uniformGains();
        muxGain = 1.0;
    }
    
    
    @Override
    public String toString() {
        return super.toString() 
                + "\t" + Util.f3.format(muxGain) 
                + "\t" + getFixedIndex() 
                + "\t" + sub 
                + "\t" + subrow 
                + "\t" + subcol;
    }
    
    @Override
    public void parseValues(SmartTokenizer tokens, int criticalFlags) { 
        super.parseValues(tokens, criticalFlags);
        if(tokens.hasMoreTokens()) muxGain = tokens.nextDouble();            
        if(isFlagged(Channel.FLAG_DEAD)) coupling = 0.0;
    }
    
    public final static int FLAG_SUB = softwareFlags.next('@', "Bad subarray gain").value();
    public final static int FLAG_BIAS = softwareFlags.next('b', "Bad TES bias gain").value();
    public final static int FLAG_MUX = softwareFlags.next('m', "Bad MUX gain").value();
    public final static int FLAG_PIN = softwareFlags.next('p', "Bad MUX pin gain").value();
    public final static int FLAG_ROW = softwareFlags.next('R', "Bad detector row gain").value();
    public final static int FLAG_COL = softwareFlags.next('c', "Bad detector col gain").value();
    public final static int FLAG_SERIES_ARRAY = softwareFlags.next('M', "Bad series array gain").value();
    public final static int FLAG_FLICKER = softwareFlags.next('T', "Flicker noise").value();
    public final static int FLAG_LOS_RESPONSE = softwareFlags.next('L', "LOS response").value();
    public final static int FLAG_ROLL_RESPONSE = softwareFlags.next('\\', "Roll response").value();

    

}