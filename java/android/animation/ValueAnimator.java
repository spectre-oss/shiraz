// SPDX-License-Identifier: GPL-3.0-only
package android.animation;
import android.os.Looper;
import android.view.Choreographer;
import java.util.ArrayList;
/** Numeric animation driven by the host Choreographer. Object/keyframe animation is separate. */
public class ValueAnimator extends Animator {
 public static final int INFINITE=-1,RESTART=1,REVERSE=2;
 public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator animation);}
 private ArrayList<AnimatorUpdateListener> updates=new ArrayList<AnimatorUpdateListener>();
 private static volatile float durationScale=1;
 private float runScale=1;
 /** Host setting applies to subsequently started animations; no desktop settings daemon required. */
 public static float getDurationScale(){return durationScale;}
 public static void setDurationScale(float scale){if(!Float.isFinite(scale)||scale<0||scale>1000)throw new IllegalArgumentException("Animator duration scale");durationScale=scale;}
 public static boolean areAnimatorsEnabled(){return durationScale!=0;}
 private double[] values={0,1};private boolean integers;
 private long duration=300,delay,requestTime,anchor=-1,lastPlayTime;
 private int repeatCount,repeatMode=RESTART,generation,lastIteration;
 private float seek,fraction;private Object value=Float.valueOf(0);
 private boolean started,running,startNotified,anchored,terminating;
 private Looper owner;private Choreographer scheduler;
 private TimeInterpolator interpolator=new android.view.animation.AccelerateDecelerateInterpolator();
 private Choreographer.FrameCallback frame;
 public static ValueAnimator ofFloat(float... values){ValueAnimator animator=new ValueAnimator();animator.setFloatValues(values);return animator;}
 public static ValueAnimator ofInt(int... values){ValueAnimator animator=new ValueAnimator();animator.setIntValues(values);return animator;}
 private void values(int count){if(count<1||count>4096)throw new IllegalArgumentException("Animation value count");thread();}
 public void setFloatValues(float... source){if(source==null)throw new NullPointerException("values");values(source.length);double[] next=new double[Math.max(2,source.length)];for(int i=0;i<source.length;i++){if(!Float.isFinite(source[i]))throw new IllegalArgumentException("Animation value must be finite");next[i+(source.length==1?1:0)]=source[i];}values=next;integers=false;}
 public void setIntValues(int... source){if(source==null)throw new NullPointerException("values");values(source.length);double[] next=new double[Math.max(2,source.length)];for(int i=0;i<source.length;i++)next[i+(source.length==1?1:0)]=source[i];values=next;integers=true;}
 public ValueAnimator setDuration(long ms){if(ms<0)throw new IllegalArgumentException("Duration");duration=ms;return this;}public long getDuration(){return duration;}
 public void setStartDelay(long ms){delay=Math.max(0,ms);}public long getStartDelay(){return delay;}
 public void setRepeatCount(int count){if(count< -1||count>1000000)throw new IllegalArgumentException("Repeat count");repeatCount=count;}public int getRepeatCount(){return repeatCount;}
 @Override public long getTotalDuration(){if(repeatCount<0)return DURATION_INFINITE;long count=repeatCount+1L,total=duration>Long.MAX_VALUE/count?Long.MAX_VALUE:duration*count;return total>Long.MAX_VALUE-delay?Long.MAX_VALUE:total+delay;}
 public void setRepeatMode(int mode){if(mode!=RESTART&&mode!=REVERSE)throw new IllegalArgumentException("Repeat mode");repeatMode=mode;}public int getRepeatMode(){return repeatMode;}
 public void setInterpolator(TimeInterpolator value){interpolator=value;}public TimeInterpolator getInterpolator(){return interpolator;}
 public void addUpdateListener(AnimatorUpdateListener listener){if(listener==null)throw new NullPointerException("listener");if(updates.size()>=1024)throw new IllegalStateException("Update listener limit");updates.add(listener);}public void removeUpdateListener(AnimatorUpdateListener listener){updates.remove(listener);}public void removeAllUpdateListeners(){updates.clear();}
 public Object getAnimatedValue(){return value;}public Object getAnimatedValue(String name){return name==null||name.isEmpty()?value:null;}public float getAnimatedFraction(){return fraction;}
 public boolean isStarted(){return started;}public boolean isRunning(){return running;}
 private void thread(){if(owner!=null&&Looper.myLooper()!=owner)throw new IllegalStateException("Animator must run on its owning Looper");}
 private boolean notifyEvent(int kind,int token){for(AnimatorListener listener:new ArrayList<AnimatorListener>(listeners)){switch(kind){case 0:listener.onAnimationStart(this);break;case 1:listener.onAnimationEnd(this);break;case 2:listener.onAnimationCancel(this);break;case 3:listener.onAnimationRepeat(this);break;}if(generation!=token)return false;}return true;}
 private void clear(){if(scheduler!=null&&frame!=null)scheduler.removeFrameCallback(frame);started=running=false;anchored=false;shirazAnimationCleared();}
 protected final void shirazCheckThread(){thread();}
 protected void shirazAnimationCleared(){}
 protected void shirazInitializeValues(){}
 protected void shirazApplyValue(Object value){}
 protected final void shirazSetStartValue(Number value){if(value==null||!Double.isFinite(value.doubleValue()))throw new IllegalArgumentException("Nonfinite property value");values[0]=integers?value.intValue():value.floatValue();}
 private boolean begin(int token){if(startNotified)return generation==token;shirazInitializeValues();if(generation!=token)return false;startNotified=true;running=true;return notifyEvent(0,token);}
 private boolean animate(float input,int token){shirazInitializeValues();if(generation!=token)return false;fraction=interpolator==null?input:interpolator.getInterpolation(input);if(!Float.isFinite(fraction))throw new IllegalArgumentException("Nonfinite interpolated fraction");double position=fraction*(values.length-1);int index=Math.max(0,Math.min(values.length-2,(int)Math.floor(position)));double result=values[index]+(values[index+1]-values[index])*(position-index);if(integers)value=Integer.valueOf((int)result);else value=Float.valueOf((float)result);shirazApplyValue(value);if(generation!=token)return false;for(AnimatorUpdateListener listener:new ArrayList<AnimatorUpdateListener>(updates)){listener.onAnimationUpdate(this);if(generation!=token)return false;}return true;}
 public void start(){thread();if(Looper.myLooper()==null)throw new IllegalStateException("Animator requires a Looper");if(started)cancel();owner=Looper.myLooper();scheduler=Choreographer.getInstance();final int token=++generation;frame=new Choreographer.FrameCallback(){public void doFrame(long nanos){if(generation==token)tick(nanos/1000000);}};started=true;runScale=durationScale;terminating=false;running=startNotified=false;lastIteration=0;anchored=false;requestTime=Choreographer.shirazAnimationTimeMillis();lastPlayTime=0;
  try{if(delay==0){if(!begin(token)||!animate(seek,token))return;}if(generation==token&&started)scheduler.postFrameCallback(frame);}catch(RuntimeException|Error failure){if(generation==token)clear();throw failure;}
 }
 private void tick(long now){if(!started)return;thread();int token=generation;try{
  if(!running&&now-requestTime<delay*(double)runScale){scheduler.postFrameCallback(frame);return;}if(!begin(token)||!started)return;
  if(!anchored){anchor=now-(long)(seek*duration*runScale);anchored=true;}long elapsed=Math.max(0,now-anchor);lastPlayTime=runScale==0?duration:(long)(elapsed/(double)runScale);
  double total=duration==0?(repeatCount<0?1:(double)repeatCount+1):(double)elapsed/(duration*(double)runScale);boolean done=runScale==0||(repeatCount>=0&&total>=repeatCount+1d);
  int iteration=done?Math.max(0,repeatCount):(int)Math.min(Integer.MAX_VALUE,Math.floor(total));float progress=done?1:(float)(total-Math.floor(total));
  if(iteration>lastIteration&&!done){lastIteration=iteration;if(!notifyEvent(3,token)||!started)return;}
  if(repeatMode==REVERSE&&(iteration&1)!=0)progress=1-progress;
  if(!animate(progress,token)||!started)return;
  if(done){clear();seek=0;terminating=true;try{notifyEvent(1,token);}finally{if(generation==token)terminating=false;}}else scheduler.postFrameCallback(frame);
 }catch(RuntimeException|Error failure){if(generation==token)clear();throw failure;}}
 public void cancel(){thread();if(!started||terminating)return;terminating=true;int token=generation;try{if(!begin(token))return;if(!notifyEvent(2,token))return;clear();seek=0;token=++generation;notifyEvent(1,token);}catch(RuntimeException|Error failure){if(generation==token)clear();throw failure;}finally{if(generation==token)terminating=false;}}
 public void end(){thread();if(terminating)return;terminating=true;if(!started){owner=Looper.myLooper();if(owner==null){terminating=false;throw new IllegalStateException("Animator requires a Looper");}scheduler=Choreographer.getInstance();started=true;startNotified=false;++generation;}int token=generation;try{if(!begin(token))return;float end=repeatMode==REVERSE&&repeatCount>=0&&(repeatCount&1)!=0?0:1;if(!animate(end,token))return;clear();seek=0;token=++generation;notifyEvent(1,token);}catch(RuntimeException|Error failure){if(generation==token)clear();throw failure;}finally{if(generation==token)terminating=false;}}
 public void setCurrentFraction(float value){thread();if(!Float.isFinite(value)||value<0||value>1)throw new UnsupportedOperationException("Only single-iteration seek fractions [0,1] are implemented");seek=value;anchored=false;lastPlayTime=(long)(value*duration);int token=generation;try{shirazInitializeValues();animate(value,token);}catch(RuntimeException|Error failure){if(generation==token&&started)clear();throw failure;}}
 public void setCurrentPlayTime(long time){if(time<0)throw new IllegalArgumentException("Play time");setCurrentFraction(duration==0?1:(float)((double)time/duration));}public long getCurrentPlayTime(){return lastPlayTime;}
 public void reverse(){throw new UnsupportedOperationException("Live animation reversal is not implemented");}
 protected final void shirazCopyTo(ValueAnimator copy){copy.values=values.clone();copy.integers=integers;copy.duration=duration;copy.delay=delay;copy.repeatCount=repeatCount;copy.repeatMode=repeatMode;copy.interpolator=interpolator;copy.listeners=new ArrayList<AnimatorListener>(listeners);copy.updates=new ArrayList<AnimatorUpdateListener>(updates);}
 public ValueAnimator clone(){ValueAnimator copy=new ValueAnimator();shirazCopyTo(copy);return copy;}
}
