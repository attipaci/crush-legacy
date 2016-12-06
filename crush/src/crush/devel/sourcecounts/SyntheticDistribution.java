package crush.devel.sourcecounts;

import java.util.List;
import java.util.Vector;

import jnum.data.Grid1D;
import jnum.data.Histogram;

public class SyntheticDistribution extends ProbabilityDistribution {
    /**
     * 
     */
    private static final long serialVersionUID = 7231487167532940294L;
    
    private Vector<ComponentSpectrum> components;
    private CharSpectrum composite;
    
    public SyntheticDistribution(Histogram histogram, Grid1D grid, int overSampling) {
        super(histogram, grid, overSampling);
        components = new Vector<ComponentSpectrum>();
        composite = new CharSpectrum(size() >>> 1);
    }
  
    public void add(ComponentSpectrum spec) { 
        if(spec.size() != composite.size()) throw new IllegalArgumentException("Component size mismatch: found " 
                + spec.size() + ", expected " + composite.size() + ".");
        components.add(spec);    
    }
   
    public void clear() { components.clear(); }
    
    public List<ComponentSpectrum> getComponents() { return components; }
    
    public int components() { return components.size(); }
    
    public void recalc() {
        composite.clear();
        for(ComponentSpectrum c : components) composite.multiplyBy(c);
        composite.toProbabilities(this);
    }
    
}