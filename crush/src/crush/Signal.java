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

package crush;


import java.io.*;
import java.util.Arrays;

import jnum.Copiable;
import jnum.ExtraMath;
import jnum.Util;
import jnum.data.DataPoint;
import jnum.data.Statistics;
import jnum.data.WeightedPoint;
import jnum.data.samples.Gaussian1D;
import jnum.data.samples.Offset1D;
import jnum.data.samples.Samples1D;
import jnum.data.samples.overlay.Referenced1D;
import jnum.parallel.ParallelTask;

public class Signal implements Serializable, Cloneable, Copiable<Signal> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1918817167111994832L;
	private Mode mode;
	Integration<?, ?> integration;
	public float[] value, drifts;
	public float[] syncGains;
	int resolution;
	int driftN;
	boolean isFloating = false;
	
	
	public Signal(Mode mode, Integration<?, ?> integration) {
		this.mode = mode;
		this.integration = integration;
		
		if(mode != null) {
			syncGains = new float[mode.size()];
			integration.addSignal(this);
		}
	}
	
	public Signal(Mode mode, Integration<?, ?> integration, float[] values, boolean isFloating) {
		this(mode, integration);
		resolution = ExtraMath.roundupRatio(integration.size(), values.length);
		this.value = values;
		driftN = values.length;
	}
	
	public Mode getMode() {
		return mode;
	}
	
	public int length() { return value.length; }
	
	public int getResolution() {
		return resolution;
	}
	
	@Override
	public Signal clone() {
		try { return (Signal) super.clone(); }
		catch(CloneNotSupportedException e) { return null; }
	}
	
	@Override
    public Signal copy() {
	    Signal copy = clone();
	    if(drifts != null) copy.drifts = Arrays.copyOf(drifts, drifts.length);
	    if(syncGains != null) copy.syncGains = Arrays.copyOf(syncGains, syncGains.length);
	    if(value != null) copy.value = Arrays.copyOf(value, value.length);
	    return copy;
	}
	
	public final float valueAt(final Frame frame) {
		return value[frame.index / resolution]; 
	}
	
	public float weightAt(final Frame exposure) {
		return 1.0F;
	}
		
    public boolean isValidAt(final Frame frame) {
        return !Float.isNaN(valueAt(frame));
    }
	
	public void scale(double factor) {
		float fValue = (float) factor;
		for(int t=value.length; --t >= 0; ) value[t] *= fValue;
		if(drifts != null) for(int T=drifts.length; --T >= 0; ) drifts[T] *= fValue;
		for(int k=syncGains.length; --k >= 0; ) syncGains[k] /= fValue;
	}
	
	public void add(double x) {
		float fValue = (float) x;
		for(int t=value.length; --t >= 0; ) value[t] += fValue;
		if(drifts != null) for(int T=drifts.length; --T >= 0; ) drifts[T] += fValue;
	}
	
	public void subtract(double x) {
		float fValue = (float) x;
		for(int t=value.length; --t >= 0; ) value[t] -= fValue;
		if(drifts != null) for(int T=drifts.length; --T >= 0; ) drifts[T] -= fValue;
	}
	
	public void addDrifts() {
		if(drifts == null) return;
		
		for(int T=0, fromt=0; fromt<value.length; T++) {
			final int tot = Math.min(fromt + driftN, value.length);
			for(int t=tot; --t >= fromt; ) value[t] += drifts[T];
			fromt = tot;
		}
		
		drifts = null;
	}
	
	public double getRMS() { return Math.sqrt(getVariance()); }
	
	public double getUnderlyingRMS() { return Math.sqrt(getUnderlyingVariance()); }
	
	public double getVariance() {
		double sum = 0.0;
		int n = 0;
		for(int t=value.length; --t >= 0; ) if(!Float.isNaN(value[t])) {
			sum += value[t] * value[t];
			n++;
		}	
		return sum / n;
	}

	public double getUnderlyingVariance() {
		double sum = 0.0;
		int n = 0;
		for(int t=value.length; --t >= 0; ) if(!Float.isNaN(value[t])) {
			sum += value[t] * value[t] - 1.0;
			n++;
		}	
		return sum / n;
	}
	
	public final void removeDrifts() {
		removeDrifts(integration.framesFor(integration.filterTimeScale), true);
	}
	
	
	public final void removeDrifts(int nFrames, boolean isReconstructible) {		
		int N = ExtraMath.roundupRatio(nFrames, resolution);
			
		if(drifts == null || N != driftN) {
			addDrifts();
			if(isReconstructible) drifts = new float[ExtraMath.roundupRatio(value.length, N)];
			driftN = N;
		}
		
		for(int T=0, fromt=0; fromt<value.length; T++) {
			final int tot = Math.min(fromt + nFrames, integration.size());
			final float value = (float) level(fromt, tot);
			if(isReconstructible) drifts[T] += value;
			fromt = tot;
		}
	}
	
	
	public double level(int from, int to) {
		from = from / resolution;
		to = ExtraMath.roundupRatio(to, resolution);
		
		double sum = 0.0;
		int n=0;
		
		for(int t=from; t<to; t++) if(!Float.isNaN(value[t])) {
			sum += value[t];
			n++;
		}
		if(n == 0) return 0.0;
			
		float ave = (float) (sum / n);
		for(int t=from; t<to; t++) value[t] -= ave;
		return ave;
	}
		
	public WeightedPoint getMedian() {
		return new WeightedPoint(Statistics.median(value), Double.POSITIVE_INFINITY);
	}
	
	public WeightedPoint getMean() {
	    return new WeightedPoint(Statistics.mean(value), Double.POSITIVE_INFINITY);		
	}
	
	
	public void square() {
	    for(int t=value.length; --t >=0; ) value[t] *= value[t];
	}

	public void sqrt() {
	    for(int t=value.length; --t >=0; ) value[t] = (float) Math.sqrt(value[t]);
	}

	public void abs() {
	    for(int t=value.length; --t >=0; ) value[t] = Math.abs(value[t]);
	}


    /**
     * Calculates the numerical derivative as a chord (f'[n] -> {f[n+1] + f[n-1}/2). It is a sequential algorithm
     * that is in-place. As such it is efficient for sinlge-threaded processing of moderate sized data. For large
     * number of points it may be more efficient to create temporary storage for parallel processing, if the overheads
     * of the necessary storage and thread creations are worth it.
     * 
     * 
     */
    public void differentiate() {
        final float idt = 1.0F / (float) (resolution * integration.instrument.samplingInterval);
        final int nm1 = value.length - 1;
            
        // v[n] -> f'[n+0.5]
        for(int t=0; t < nm1; t++) value[t] = (value[t+1] - value[t]) * idt;

        // Extrapolate the last value
        value[nm1] = value[nm1-1];

        // otherwise, it's:
        // v[n] -> (f'[n+0.5] + f'[n-0.5])/2 = v[n] + v[n-1]
        for(int t=nm1; --t > 0; ) value[t] = 0.5F * (value[t] + value[t-1]);

        isFloating = false;
    }
    
    /**
     * Calculates the numerical 2nd derivative, using an in-place sequential algorithm
     * As such it is efficient for sinlge-threaded processing of moderate sized data. For large
     * number of points it may be more efficient to create temporary storage for parallel processing, if the overheads
     * of the necessary storage and thread creations are worth it.
     * 
     * 
     */
    public void secondDerivative() {
        final float idt = 1.0F / (float) (resolution * integration.instrument.samplingInterval);
        final float idt2 = idt*idt;
        final int nm2 = value.length - 2;
            
        // v[n] -> f''[n+1]
        for(int t=0; t < nm2; t++) value[t] = (value[t+2] + value[t] - 2.0F*value[t+1]) * idt2;

        // shift donw n -> n-1
        for(int t=nm2+1; --t > 0; ) value[t] = value[t-1];

        // last value same as one before...
        value[nm2+1] = value[nm2];
        
        isFloating = false;
    }

    /** 
     * Intergates using trapesiod rule.
     * 
     * 
     */
    public void integrate() {
        double dt = (float) (resolution * integration.instrument.samplingInterval);        
        double I = 0.0;

        float halfLast = 0.0F;

        for(int t=0; t<value.length; t++) {
            // Calculate next half increment of h/2 * f[t]
            float halfNext = 0.5F * value[t];

            // Add half increments from below and above 
            I += halfLast;
            I += halfNext;
            value[t] = (float) (I * dt);

            halfLast = halfNext;
        }

        isFloating = true;
    }

    public void differentiate(int nTimes) {
        if(nTimes < 0) throw new IllegalArgumentException("Negative multiplicity: " + nTimes);
        
        while(nTimes > 1) {
            secondDerivative();
            nTimes -= 2;
        }
        
        if(nTimes == 1) differentiate();
    }

    public void integrate(int nTimes) {
        if(nTimes < 0) throw new IllegalArgumentException("Negative multiplicity: " + nTimes);
        for(int i=0; i<nTimes; i++) integrate();
    }

    public Signal getDifferential() {
        Signal d = clone();
        d.differentiate();
        return d;
    }

    public Signal getIntegral() {
        Signal d = clone();
        d.integrate();
        return d;
    }

	
	public void level(boolean isRobust) {
		WeightedPoint center = isRobust ? getMedian() : getMean();
		float fValue = (float) center.value();
		for(int t=value.length; --t >= 0; ) value[t] -= fValue;
	}
	
	
    public final void smooth(double FWHM) {
        Referenced1D beam = Gaussian1D.getBeam(FWHM, resolution * integration.instrument.samplingInterval);
        smooth((double[]) beam.getCore(), beam.getReferenceIndex().value());
    }
    
    public void smooth(double[] beam, double centerIndex) {
        Samples1D smoothed = (Samples1D) new Samples1D.Float1D(value).getSmoothed(new Samples1D.Double1D(beam), new Offset1D(centerIndex), null, null);
        value = (float[]) smoothed.getCore();
    }

	
	// TODO Use this in ArrayUtil...
	public void smooth(double[] w) {
		int ic = w.length / 2;
		float[] smoothed = new float[value.length];
			
		for(int t=value.length; --t >= 0; ) {		
			int t1 = Math.max(0, t-ic); // the beginning index for the convolution
			final int tot = Math.min(value.length, t + w.length - ic);
			int i = ic + t - t1; // the beginning index for the weighting fn.
			double sum = 0.0, sumw = 0.0;
			
			for( ; t1<tot; t1++, i++) if(!Float.isNaN(value[t1])) {
				sum += w[i] * value[t1];
				sumw += Math.abs(w[i]);
			}
			if(sumw > 0.0) smoothed[t] = (float) (sum / sumw);
		}
		value = smoothed;
	}
	
	protected void setSyncGains(float[] G) {
		System.arraycopy(G, 0, syncGains, 0, G.length);
	}
	

	public void print(PrintStream out) {
		out.println("# " + (1.0 / (resolution * integration.instrument.integrationTime)));
		
		for(int t=0; t<value.length; t++) out.println(Util.e3.format(value[t]));
	}
	
	
	protected WeightedPoint[] getGainIncrement(boolean isRobust) {	
		if(integration.hasOption("signal-response")) 
			integration.comments.append("{" + Util.f2.format(getCovariance()) + "}");

		// Precalculate the gain-weight products...
		prepareFrameTempFields();
				
		// Calculate gains here...
		return isRobust ? getRobustGainIncrement() : getMLGainIncrement();
	}

	

	protected final WeightedPoint[] getMLGainIncrement() {
		
		CRUSH.Fork<DataPoint[]> increments = integration.new Fork<DataPoint[]>() {
			private DataPoint[] dG;
			
			@Override
			protected void init() {
				super.init();
				dG = integration.instrument.getDataPoints();
				for(int k=mode.size(); --k >= 0; ) dG[k].noData();
			}
			
			@Override 
			protected void process(Frame exposure){
				if(exposure.isFlagged(Frame.MODELING_FLAGS)) return;		
				
				for(int k=mode.size(); --k >= 0; ) {
					final Channel channel = mode.getChannel(k);
					
					if(exposure.sampleFlag[channel.index] != 0) continue;
					
					DataPoint increment = dG[k];
					increment.add(exposure.tempWC * exposure.data[channel.index]);
					increment.addWeight(exposure.tempWC2);
				}
			}
			
			@Override
			public DataPoint[] getLocalResult() { return dG; }
			
			@Override
			public DataPoint[] getResult() {
				final DataPoint[] globalIncrement = DataPoint.createArray(mode.size());
				
				for(ParallelTask<DataPoint[]> task : getWorkers()) {
					final DataPoint[] localIncrement = task.getLocalResult();
					for(int k=mode.size(); --k >= 0; ) {
						DataPoint global = globalIncrement[k];
						DataPoint local = localIncrement[k];
						global.add(local.value());
						global.addWeight(local.weight());
					}
					Instrument.recycle(localIncrement);
				}
				
				for(int k=mode.size(); --k >= 0; ) {
					final WeightedPoint increment = globalIncrement[k];
					if(increment.weight() > 0.0) increment.scaleValue(1.0 / increment.weight());
				}
				
				return globalIncrement;
			}
			
		};
		increments.process();
		return increments.getResult();
	}
	
	// TODO smart timestream access
	protected final WeightedPoint[] getRobustGainIncrement() {
		
		final WeightedPoint[] dG = WeightedPoint.createArray(mode.size());
		
		new CRUSH.Fork<Void>(dG.length, integration.getThreadCount()) {
			// Allocate storage for sorting if estimating robustly...
			private WeightedPoint[] gainData;
			
			@Override
			public void init() {
				super.init();
				gainData = integration.getDataPoints();
			}
		
			@Override
			public void cleanup() {
				Integration.recycle(gainData);
				super.cleanup();
			}
			
			@Override
			protected void processIndex(int k) {
				int n=0;
				final Channel channel = mode.getChannel(k);
				
				final WeightedPoint increment = dG[k];
				//increment.noData();
				for(final Frame exposure : integration) if(exposure != null) 
					if(exposure.tempWC2 > 0.0) if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[channel.index] == 0)  {
						final WeightedPoint point = gainData[n++];
						point.setValue(exposure.data[channel.index] / exposure.tempC);
						point.setWeight(exposure.tempWC2);
						increment.addWeight(point.weight());
						
						assert !Double.isNaN(point.value());
						assert !Double.isInfinite(point.value());
					}
				if(n > 0) Statistics.Inplace.smartMedian(gainData, 0, n, 0.25, increment);
			}
			
		}.process();
		

		return dG;
	}
	
	

	protected void resyncGains() throws Exception {
		final ChannelGroup<?> channels = mode.getChannels();
		final int nc = channels.size();
		
		final float[] G = mode.getGains();
		final float[] dG = syncGains;	
		
		for(int k=nc; --k >=0; ) dG[k] = G[k] - syncGains[k];
		
		integration.new Fork<Void>() {
			@Override 
			protected void process(final Frame exposure) {
				for(int k=nc; --k >=0; ) {
				    final Channel channel = mode.getChannel(k);
				    exposure.data[channel.index] -= dG[k] * valueAt(exposure);
				}
			}
		}.process();
		
		// Register the gains as the ones used for the signal...
		setSyncGains(G);	
	}
	
	
	protected void syncGains(final float[] sumwC2, boolean isTempReady) throws Exception {
		if(mode.fixedGains) throw new IllegalStateException("Cannot change gains for fixed gain modes.");
		
		final ChannelGroup<?> channels = mode.getChannels();
		final int nc = mode.size();
		final Dependents parms = integration.getDependents("gains-" + mode.name);
		
		final float[] G = mode.getGains();
		final float[] dG = syncGains;
		
		boolean changed = false;
		for(int k=nc; --k >=0; ) {
			dG[k] = G[k] - dG[k];
			if(dG[k] != 0.0) changed = true;
		}
		if(!changed) return;
			
		if(sumwC2 != null) parms.clear(channels, 0, integration.size());

		// Precalculate the gain-weight products...
		if(!isTempReady) prepareFrameTempFields();
		
		// Sync to data and calculate dependences...
		integration.new Fork<Void>() {
			@SuppressWarnings("null")
            @Override 
			protected void process(Frame exposure){
				for(int k=nc; --k >=0; ) {
				    boolean calcDependents = sumwC2 == null ? false : sumwC2[k] > 0.0F;
				   
				    if(!calcDependents) continue;
				    
					final int c = mode.getChannel(k).index;
					exposure.data[c] -= dG[k] * exposure.tempC;
					if(exposure.isUnflagged(Frame.MODELING_FLAGS)) if(exposure.sampleFlag[c] == 0)
						parms.addAsync(exposure, exposure.tempWC2 / sumwC2[k]);
				}
			}
		}.process();
		
		if(sumwC2 != null) {
		    // Account for the one gain parameter per channel...
		    // minus the overall gain renormalization...
		    // TODO calculate more properly with weights...
		    final double channelDependence = 1.0 - 1.0 / nc;
		    for(int k=nc; --k >= 0; ) if(sumwC2[k] > 0.0) parms.addAsync(channels.get(k), channelDependence);

		    // Apply the mode dependeces...
		    parms.apply(channels, 0, integration.size());
		}
		 
		// Register the gains as the ones used for the signal...
		setSyncGains(G);
		
		
		if(CRUSH.debug) integration.checkForNaNs(channels, 0, integration.size());
	}
	
	private void prepareFrameTempFields() {
		for(final Frame exposure : integration) if(exposure != null) {
			exposure.tempC = valueAt(exposure);
			if(Float.isNaN(exposure.tempC)) exposure.tempC = 0.0F;
			exposure.tempWC = exposure.isUnflagged(Frame.MODELING_FLAGS) ? exposure.relativeWeight * exposure.tempC : 0.0F;
			exposure.tempWC2 = exposure.tempWC * exposure.tempC;
		}
	}
	
	public double getCovariance() {
		final ChannelGroup<?> channels = mode.getChannels().createGroup().discard(~0);
		final int nc = integration.instrument.size();
		
		final double[] sumXS = new double[nc];
		final double[] sumX2 = new double[nc];
		final double[] sumS2 = new double[nc];
		
		for(final Frame exposure : integration) if(exposure != null) {
			for(final Channel channel : channels) if(exposure.sampleFlag[channel.index] == 0) {
				final float S = valueAt(exposure);
				if(!Float.isNaN(S)) {
					final float x = exposure.data[channel.index];
					sumX2[channel.index] += channel.weight * x * x;
					sumXS[channel.index] += channel.weight * x * S;
					sumS2[channel.index] += channel.weight * S * S;
				}
			}	
		}
		
		double C2 = 0.0;
		for(final Channel channel : channels) {
			final int c = channel.index;
			if(sumS2[c] > 0.0) C2 += sumXS[c] * sumXS[c] / (sumX2[c] * sumS2[c]);
		}
		
		return Math.sqrt(C2);
	}
	
	@Override
	public String toString() { return "Signal " + integration.getFullID("|") + "." + mode.getName(); }

	
}
