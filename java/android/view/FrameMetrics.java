// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Frame timings in nanoseconds. Unmeasured stages return -1. */
public final class FrameMetrics {
 public static final int UNKNOWN_DELAY_DURATION=0,INPUT_HANDLING_DURATION=1,ANIMATION_DURATION=2,
  LAYOUT_MEASURE_DURATION=3,DRAW_DURATION=4,SYNC_DURATION=5,COMMAND_ISSUE_DURATION=6,
  SWAP_BUFFERS_DURATION=7,TOTAL_DURATION=8,FIRST_DRAW_FRAME=9,INTENDED_VSYNC_TIMESTAMP=10,VSYNC_TIMESTAMP=11;
 private final long[] values;
 public FrameMetrics(FrameMetrics other){values=other.values.clone();}
 FrameMetrics(long layout,long draw,boolean first){
  values=new long[12];java.util.Arrays.fill(values,-1);
  values[LAYOUT_MEASURE_DURATION]=layout;values[DRAW_DURATION]=draw;values[FIRST_DRAW_FRAME]=first?1:0;
 }
 public long getMetric(int id){return id>=0&&id<values.length?values[id]:-1;}
}
