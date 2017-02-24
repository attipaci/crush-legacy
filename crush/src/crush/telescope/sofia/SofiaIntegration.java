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

package crush.telescope.sofia;

import crush.Channel;
import crush.Integration;
import crush.Scan;
import crush.telescope.GroundBased;
import jnum.LockedException;
import jnum.Unit;
import jnum.Util;

public abstract class SofiaIntegration<InstrumentType extends SofiaCamera<? extends Channel>, FrameType extends SofiaFrame> 
extends Integration<InstrumentType, FrameType> implements GroundBased {

    /**
     * 
     */
    private static final long serialVersionUID = -4771883165716694480L;


    public SofiaIntegration(Scan<InstrumentType, ?> parent) {
        super(parent);
    }

    @Override
    public double getModulationFrequency(int signalMode) {
        SofiaScan<?,?> sofiaScan = (SofiaScan<?,?>) scan;
        if(sofiaScan.isChopping) return sofiaScan.chopper.frequency;
        return super.getModulationFrequency(signalMode);
    }

    public double getMeanPWV() { return ((SofiaScan<?,?>) scan).environment.pwv.midPoint(); }
    
    public double getModelPWV() {
        info("Estimating PWV based on altitude...");
        double pwv41k = hasOption("pwv41k") ? option("pwv41k").getDouble() * Unit.um : 29.0 * Unit.um;
        double b = 1.0 / (hasOption("pwvscale") ? option("pwvscale").getDouble() : 5.0);
        double altkf = ((SofiaScan<?,?>) scan).aircraft.altitude.midPoint() / (1000.0 * Unit.ft);
        return pwv41k * Math.exp(-b * (altkf - 41.0));
    }
    
    @Override
    public void validate() {  
        double pwv = Double.NaN;
        if(hasOption("pwvmodel")) pwv = getModelPWV();
        else {
            pwv = getMeanPWV();
            if(pwv == 0.0 || Double.isNaN(pwv)) {
                info("--> FIX: Using default PWV model...");
                pwv = getModelPWV();
            } 
        }

        info("PWV: " + Util.f1.format(pwv / Unit.um) + " um");
     
        if(!hasOption("tau.pwv")) {
            try { instrument.getOptions().process("tau.pwv", Double.toString(pwv / Unit.um)); }
            catch(LockedException e) {}
        }
       
        super.validate();
    }

}
