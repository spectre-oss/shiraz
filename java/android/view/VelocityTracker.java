// SPDX-License-Identifier: GPL-3.0-only
package android.view;
/** Bounded, unweighted quadratic fit of recent explicit event samples. No native history ingestion. */
public final class VelocityTracker {
 private static final int LIMIT=20;
 private static final class Track {
  final long[] time=new long[LIMIT];final float[] x=new float[LIMIT],y=new float[LIMIT];int count;float vx,vy;
  void add(long t,float px,float py){
   if(count>0&&(t<time[count-1]||t-time[count-1]>40))count=0;
   if(count>0&&t==time[count-1])count--;
   while(count>0&&(count==LIMIT||t-time[0]>100)){System.arraycopy(time,1,time,0,count-1);System.arraycopy(x,1,x,0,count-1);System.arraycopy(y,1,y,0,count-1);count--;}
   time[count]=t;x[count]=px;y[count]=py;count++;
  }
  double velocity(float[] values){
   if(count<2)return 0;
   // Times relative to the newest sample, in seconds, keep the fit origin local.
   int degree=count>=3?2:1;
   for(;degree>=1;degree--){int n=degree+1;double[][] matrix=new double[n][n+1];
    for(int i=0;i<count;i++){double t=(time[i]-time[count-1])/1000.0;double[] powers={1,t,t*t,t*t*t,t*t*t*t};for(int row=0;row<n;row++){for(int col=0;col<n;col++)matrix[row][col]+=powers[row+col];matrix[row][n]+=powers[row]*((double)values[i]-values[count-1]);}}
    boolean singular=false;
    for(int col=0;col<n;col++){int pivot=col;for(int row=col+1;row<n;row++)if(Math.abs(matrix[row][col])>Math.abs(matrix[pivot][col]))pivot=row;
     if(Math.abs(matrix[pivot][col])<1e-12){singular=true;break;}double[] swap=matrix[col];matrix[col]=matrix[pivot];matrix[pivot]=swap;double divisor=matrix[col][col];for(int j=col;j<=n;j++)matrix[col][j]/=divisor;
     for(int row=0;row<n;row++)if(row!=col){double factor=matrix[row][col];for(int j=col;j<=n;j++)matrix[row][j]-=factor*matrix[col][j];}
    }
    if(!singular&&Double.isFinite(matrix[1][n]))return matrix[1][n];
   }return 0;
  }
 }
 private final Track[] tracks=new Track[32];private boolean recycled;private int active=-1;
 private VelocityTracker(){}
 public static VelocityTracker obtain(){return new VelocityTracker();}
 private void live(){if(recycled)throw new IllegalStateException("VelocityTracker is recycled");}
 public void clear(){live();java.util.Arrays.fill(tracks,null);active=-1;}
 public void recycle(){clear();recycled=true;}
 public void addMovement(MotionEvent event){
  live();if(event==null)throw new NullPointerException("event");int action=event.getActionMasked();
  if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_CANCEL)clear();
  if(action==MotionEvent.ACTION_POINTER_UP){if(active==event.getPointerId(event.getActionIndex()))active=event.getPointerCount()>1?event.getPointerId(event.getActionIndex()==0?1:0):-1;return;}
  if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)return;
  if(action!=MotionEvent.ACTION_DOWN&&action!=MotionEvent.ACTION_MOVE&&action!=MotionEvent.ACTION_POINTER_DOWN)return;
  if(action==MotionEvent.ACTION_POINTER_DOWN)tracks[event.getPointerId(event.getActionIndex())]=null;
  int bits=event.getPointerIdBits();for(int id=0;id<32;id++)if((bits&(1<<id))==0)tracks[id]=null;
  if(active<0||tracks[active]==null)active=event.getPointerId(0);
  for(int i=0;i<event.getPointerCount();i++){int id=event.getPointerId(i);if(tracks[id]==null)tracks[id]=new Track();tracks[id].add(event.getEventTime(),event.getX(i),event.getY(i));}
 }
 public void computeCurrentVelocity(int units){computeCurrentVelocity(units,Float.MAX_VALUE);}
 public void computeCurrentVelocity(int units,float maxVelocity){live();if(units<=0||!Float.isFinite(maxVelocity)||maxVelocity<0)throw new IllegalArgumentException("Velocity units/limit");for(Track track:tracks)if(track!=null){track.vx=(float)Math.max(-maxVelocity,Math.min(maxVelocity,track.velocity(track.x)*units/1000.0));track.vy=(float)Math.max(-maxVelocity,Math.min(maxVelocity,track.velocity(track.y)*units/1000.0));}}
 private Track track(int id){live();if(id==-1)id=active;return id<0||id>31?null:tracks[id];}
 public float getXVelocity(){return getXVelocity(-1);}public float getYVelocity(){return getYVelocity(-1);}
 public float getXVelocity(int id){Track track=track(id);return track==null?0:track.vx;}public float getYVelocity(int id){Track track=track(id);return track==null?0:track.vy;}
}
