// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;
import android.graphics.Canvas;
import android.graphics.PixelFormat;

/** Two-layer fade with frame-driven invalidation and no permanent child alpha changes. */
public class TransitionDrawable extends LayerDrawable {
    private boolean crossFade,running,drawing;
    private int from,to,amount,duration;
    private long started=-1;
    public TransitionDrawable(Drawable[] layers){super(validate(layers));}
    private static Drawable[] validate(Drawable[] layers){if(layers==null)throw new NullPointerException("layers");if(layers.length<2||layers[0]==null||layers[1]==null)throw new IllegalArgumentException("Transition requires two drawable layers");return layers;}
    protected long shirazTransitionTime(){return android.os.SystemClock.uptimeMillis();}
    public void setCrossFadeEnabled(boolean enabled){if(crossFade!=enabled){crossFade=enabled;invalidateSelf();}}
    public boolean isCrossFadeEnabled(){return crossFade;}
    public void startTransition(int milliseconds){if(milliseconds<0)throw new IllegalArgumentException("Transition duration");amount=from=0;to=255;duration=milliseconds;started=-1;running=true;invalidateSelf();}
    public void resetTransition(){running=false;amount=from=to=0;started=-1;invalidateSelf();}
    private void advance(long now){if(!running)return;if(started<0)started=now;long elapsed=Math.max(0,now-started);if(duration==0||elapsed>=duration){amount=to;running=false;}else amount=from+(int)((to-from)*elapsed/duration);}
    public void reverseTransition(int milliseconds){
        if(milliseconds<0)throw new IllegalArgumentException("Transition duration");
        long now=shirazTransitionTime();advance(now);boolean active=running;int target=to==255?0:255;
        int remaining=active?(int)((long)duration*Math.abs(target-amount)/Math.max(1,Math.abs(to-from))):milliseconds;
        from=amount;to=target;duration=remaining;started=now;running=true;invalidateSelf();
    }
    private void layer(Canvas canvas,int index,int fraction){Drawable child=getDrawable(index);int original=child.getAlpha();try{child.setAlpha(original*fraction/255);child.draw(canvas);}finally{child.setAlpha(original);}}
    @Override public void draw(Canvas canvas){advance(shirazTransitionTime());drawing=true;try{if(!crossFade||amount<255)layer(canvas,0,crossFade?255-amount:255);if(amount>0)layer(canvas,1,amount);}finally{drawing=false;}if(running)invalidateSelf();}
    @Override public void invalidateDrawable(Drawable who){if(!drawing)super.invalidateDrawable(who);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    @Override public void jumpToCurrentState(){super.jumpToCurrentState();if(running){amount=to;running=false;invalidateSelf();}}
}
