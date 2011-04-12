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

package crush;

import util.data.WeightedPoint;

public class PhaseSignal {
	PhaseSet phases;
	CorrelatedMode mode;
	double[] value, weight;
	float[] syncGains;
	int generation = 0;
	
	public PhaseSignal(PhaseSet phases, CorrelatedMode mode) {
		this.phases = phases;
		this.mode = mode;
		
		value = new double[phases.size()];
		weight = new double[phases.size()];
		
		syncGains = new float[mode.channels.size()];
		
		phases.signals.put(mode, this);
	}
	
	
	public double getValue(int i) { return value[i]; }
	
	public double getWeight(int i) { return weight[i]; }
	
	public void scale(double factor) {
		double f2 = factor * factor;
		for(int i=value.length; --i >= 0; ) {
			value[i] *= factor;
			weight[i] /= f2;
		}
		// Rescale the corresponding synching gains also to keep the product intact
		for(int k=syncGains.length; --k >= 0; ) syncGains[k] /= factor;
		
		// TODO What about signals of gain dependent modes? 
	}
	
	
	protected synchronized void update(boolean isRobust) throws IllegalAccessException {
		final float[] G = mode.getGains();
		final float[] dG = syncGains;
	
		// Make syncGains carry the gain increment since last sync...
		for(int k=G.length; --k >= 0; ) dG[k] = G[k] - dG[k];
			
		final ChannelGroup<?> channels = mode.channels;
		final WeightedPoint dC = new WeightedPoint(); 
		
		// Always use maximum-likelihood gains...
		if(phases.integration.hasOption("phases.estimator")) 
			isRobust = phases.integration.option("phases.estimator").equals("median");
		
		WeightedPoint[] temp = null;
		if(isRobust) {
			temp = new WeightedPoint[G.length];
			for(int i=temp.length; --i >= 0; ) temp[i] = new WeightedPoint();
		}
		
		for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
				
			if(isRobust) offsets.getRobustCorrelated(mode, G, temp, dC);
			else offsets.getMLCorrelated(mode, G, dC);
			
			if(dC.weight <= 0.0) continue;
			
			for(int k=G.length; --k >= 0; )
				offsets.value[channels.get(k).index] -= dG[k] * value[i] + G[k] * dC.value;
			
			value[i] += dC.value;
			weight[i] = dC.weight;	
		}		
		
		generation++;
		setSyncGains(G);
	}
	
	public synchronized WeightedPoint[] getGainIncrement() {
		final ChannelGroup<?> channels = mode.channels;
		final WeightedPoint[] dG = new WeightedPoint[channels.size()];
		
		for(int k=channels.size(); --k >= 0; ) dG[k] = getGainIncrement(channels.get(k));
			
		return dG;
	}
	
	protected WeightedPoint getGainIncrement(Channel channel) {
		double sum = 0.0, sumw = 0.0;

		for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			if(offsets.flag != 0) continue;
			
			final double C = value[i];
			final double wC = offsets.weight[channel.index] * C;
			sum += (wC * offsets.value[channel.index]);
			sumw += (wC * C);
		}
		return sumw > 0.0 ? new WeightedPoint(sum / sumw, sumw) : new WeightedPoint();
	}
	
	// TODO robust gains?...
	
	protected synchronized void setSyncGains(float[] G) {
		System.arraycopy(G, 0, syncGains, 0, G.length);
	}
	
	protected void syncGains() throws IllegalAccessException {
		final ChannelGroup<?> channels = mode.channels;
		final float[] G = mode.getGains();		
		final float[] dG = syncGains;
		
		for(int k=G.length; --k >= 0; ) dG[k] = G[k] - dG[k];
			
		for(int i=phases.size(); --i >= 0; ) {
			final PhaseOffsets offsets = phases.get(i);
			for(int k=G.length; --k >= 0; ) if(weight[i] > 0.0)
				offsets.value[channels.get(k).index] -= dG[k] * value[i];
		}
		
		setSyncGains(G);
	}
	

}