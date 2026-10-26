// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import java.util.ArrayList;

/** Batched software View properties driven by the UI Choreographer. */
public final class ViewPropertyAnimator {
    private static final int ALPHA=0, TX=1, TY=2, TZ=3, X=4, Y=5, Z=6, SX=7, SY=8;
    private final View view;
    private final ArrayList<Property> pending=new ArrayList<Property>();
    private final ArrayList<Run> runs=new ArrayList<Run>();
    private long duration=300,delay;
    private TimeInterpolator interpolator=new android.view.animation.AccelerateDecelerateInterpolator();
    private Animator.AnimatorListener listener;
    private ValueAnimator.AnimatorUpdateListener update;
    private Runnable startAction,endAction;
    private final Runnable starter=new Runnable(){public void run(){start();}};
    ViewPropertyAnimator(View view){this.view=view;}
    private static final class Property {
        final int id;final float from,delta;
        Property(int id,float from,float delta){this.id=id;this.from=from;this.delta=delta;}
    }
    private final class Run extends AnimatorListenerAdapter implements ValueAnimator.AnimatorUpdateListener {
        final ValueAnimator animator=ValueAnimator.ofFloat(0,1);
        final ArrayList<Property> properties;
        final Runnable onStart,onEnd;
        boolean canceled,finished;
        Run(ArrayList<Property> properties,Runnable onStart,Runnable onEnd){this.properties=properties;this.onStart=onStart;this.onEnd=onEnd;}
        public void onAnimationStart(Animator animation){try{if(onStart!=null)onStart.run();if(listener!=null)listener.onAnimationStart(animation);}catch(RuntimeException|Error failure){release();throw failure;}}
        public void onAnimationCancel(Animator animation){canceled=true;try{if(listener!=null)listener.onAnimationCancel(animation);}catch(RuntimeException|Error failure){release();throw failure;}}
        public void onAnimationRepeat(Animator animation){if(listener!=null)listener.onAnimationRepeat(animation);}
        void release(){if(!finished){finished=true;runs.remove(this);view.setHasTransientState(false);}}
        public void onAnimationEnd(Animator animation){release();if(listener!=null)listener.onAnimationEnd(animation);if(!canceled&&onEnd!=null)onEnd.run();}
        public void onAnimationUpdate(ValueAnimator animation){
            try {
            float fraction=animation.getAnimatedFraction();
            for(Property property:new ArrayList<Property>(properties)){
                if(finished)return;
                if(properties.contains(property))set(property.id,property.from+fraction*property.delta);
            }
            if(!finished&&update!=null)update.onAnimationUpdate(animation);
            }catch(RuntimeException|Error failure){release();throw failure;}
        }
    }
    public ViewPropertyAnimator setDuration(long value){if(value<0)throw new IllegalArgumentException("Duration");duration=value;return this;}
    public long getDuration(){return duration;}
    public ViewPropertyAnimator setStartDelay(long value){if(value<0)throw new IllegalArgumentException("Start delay");delay=value;return this;}
    public long getStartDelay(){return delay;}
    public ViewPropertyAnimator setInterpolator(TimeInterpolator value){interpolator=value;return this;}
    public TimeInterpolator getInterpolator(){return interpolator;}
    public ViewPropertyAnimator setListener(Animator.AnimatorListener value){listener=value;return this;}
    public ViewPropertyAnimator setUpdateListener(ValueAnimator.AnimatorUpdateListener value){update=value;return this;}
    public ViewPropertyAnimator withStartAction(Runnable value){startAction=value;return this;}
    public ViewPropertyAnimator withEndAction(Runnable value){endAction=value;return this;}
    public ViewPropertyAnimator withLayer(){throw new UnsupportedOperationException("Property animation layer management is not implemented");}
    public void start(){
        view.removeCallbacks(starter);
        if(runs.size()>=64)throw new IllegalStateException("View property animation limit");
        Run run=new Run(new ArrayList<Property>(pending),startAction,endAction);
        pending.clear();startAction=endAction=null;
        run.animator.setDuration(duration);run.animator.setStartDelay(delay);run.animator.setInterpolator(interpolator);
        run.animator.addListener(run);run.animator.addUpdateListener(run);
        runs.add(run);view.setHasTransientState(true);
        try{run.animator.start();}catch(RuntimeException|Error failure){run.release();throw failure;}
    }
    public void cancel(){
        for(Run run:new ArrayList<Run>(runs)){try{run.animator.cancel();}finally{if(!run.animator.isStarted())run.release();}}
        pending.clear();startAction=endAction=null;view.removeCallbacks(starter);
    }
    private ViewPropertyAnimator property(int id,float value,boolean relative){
        if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite property animation");
        float from=get(id),delta=relative?value:value-from;
        if(!Float.isFinite(delta)||!Float.isFinite(from+delta))throw new IllegalArgumentException("Property animation overflow");
        for(Run run:new ArrayList<Run>(runs)){
            for(int i=run.properties.size()-1;i>=0;i--)if(run.properties.get(i).id==id)run.properties.remove(i);
            if(run.properties.isEmpty())run.animator.cancel();
        }
        if(pending.size()>=1024)throw new IllegalStateException("Pending property animation limit");
        pending.add(new Property(id,from,delta));view.removeCallbacks(starter);view.postOnAnimation(starter);return this;
    }
    private float get(int id){switch(id){case SX:return view.getScaleX();case SY:return view.getScaleY();case ALPHA:return view.getAlpha();case TX:return view.getTranslationX();case TY:return view.getTranslationY();case TZ:return view.getTranslationZ();case X:return view.getX();case Y:return view.getY();default:return view.getZ();}}
    private void set(int id,float value){switch(id){case SX:view.setScaleX(value);break;case SY:view.setScaleY(value);break;case ALPHA:view.setAlpha(value);break;case TX:view.setTranslationX(value);break;case TY:view.setTranslationY(value);break;case TZ:view.setTranslationZ(value);break;case X:view.setX(value);break;case Y:view.setY(value);break;default:view.setZ(value);}}
    public ViewPropertyAnimator scaleX(float value){return property(SX,value,false);}public ViewPropertyAnimator scaleXBy(float value){return property(SX,value,true);}
    public ViewPropertyAnimator scaleY(float value){return property(SY,value,false);}public ViewPropertyAnimator scaleYBy(float value){return property(SY,value,true);}
    public ViewPropertyAnimator alpha(float value){return property(ALPHA,value,false);}public ViewPropertyAnimator alphaBy(float value){return property(ALPHA,value,true);}
    public ViewPropertyAnimator translationX(float value){return property(TX,value,false);}public ViewPropertyAnimator translationXBy(float value){return property(TX,value,true);}
    public ViewPropertyAnimator translationY(float value){return property(TY,value,false);}public ViewPropertyAnimator translationYBy(float value){return property(TY,value,true);}
    public ViewPropertyAnimator translationZ(float value){return property(TZ,value,false);}public ViewPropertyAnimator translationZBy(float value){return property(TZ,value,true);}
    public ViewPropertyAnimator x(float value){return property(X,value,false);}public ViewPropertyAnimator xBy(float value){return property(X,value,true);}
    public ViewPropertyAnimator y(float value){return property(Y,value,false);}public ViewPropertyAnimator yBy(float value){return property(Y,value,true);}
    public ViewPropertyAnimator z(float value){return property(Z,value,false);}public ViewPropertyAnimator zBy(float value){return property(Z,value,true);}
}
