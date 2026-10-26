// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Alternating on/off distances in path coordinates, copied independently of caller storage. */
public class DashPathEffect extends PathEffect {
    private final float[] intervals;
    private final float phase;
    public DashPathEffect(float[] values,float phase) {
        if(values==null)throw new NullPointerException("dash intervals");
        if(values.length<2 || values.length>256 || (values.length&1)!=0 || !Float.isFinite(phase) || Math.abs(phase)>10000000)throw new IllegalArgumentException("dash count/phase");
        double total=0;for(float value:values){if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("dash interval");total+=value;}
        if(total==0)throw new IllegalArgumentException("empty dash period");
        intervals=values.clone();this.phase=phase;
    }
    public final float[] shirazIntervals(){return intervals.clone();}
    public final float shirazPhase(){return phase;}
}
