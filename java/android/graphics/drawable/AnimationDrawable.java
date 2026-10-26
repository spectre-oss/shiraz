// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.os.SystemClock;
import java.util.ArrayList;

/** Bounded frame animation driven by its owner's drawable scheduling callbacks. */
public class AnimationDrawable extends DrawableContainer implements Runnable, Animatable {
    private final ArrayList<Integer> durations=new ArrayList<Integer>();
    private boolean oneShot,running,animating;
    private int frame;
    public AnimationDrawable() { }
    public int getNumberOfFrames(){return children.size();}
    public Drawable getFrame(int index){return children.get(index);}
    public int getDuration(int index){return durations.get(index);}
    public boolean isOneShot(){return oneShot;}
    public void setOneShot(boolean value){oneShot=value;}
    public void addFrame(Drawable drawable,int duration){
        if(drawable==null)throw new NullPointerException("frame");
        if(duration<0)throw new IllegalArgumentException("Frame duration");
        addChild(drawable);durations.add(duration);
        if(!running){cancel();frame=0;selectDrawable(0);}
    }
    private void cancel(){running=false;super.unscheduleSelf(this);}
    private void show(int index,boolean animate){
        cancel();if(children.isEmpty()){animating=false;return;}
        frame=index;selectDrawable(frame);animating=animate;
        if(animate&&isVisible()){
            running=true;
            // A zero-duration loop must yield to input and other main-loop work.
            scheduleSelf(this,SystemClock.uptimeMillis()+Math.max(1,durations.get(frame)));
        }
    }
    public void start(){if(!running)show(0,children.size()>1||!oneShot);}
    public void stop(){animating=false;cancel();}
    public boolean isRunning(){return running;}
    public void run(){
        if(!running||!isVisible()||children.isEmpty())return;
        int next=frame+1;
        if(next>=children.size()){if(oneShot){animating=false;cancel();return;}next=0;}
        show(next,!(oneShot&&next==children.size()-1));
    }
    public void unscheduleSelf(Runnable task){if(task==this)running=false;super.unscheduleSelf(task);}
    public boolean setVisible(boolean visible,boolean restart){
        boolean changed=super.setVisible(visible,restart);
        if(!visible)cancel();
        else if(changed||restart)show(restart||!oneShot?0:frame,animating);
        return changed;
    }
}
