// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;

/** Drawable overlay in host coordinates, above content and below foreground decorations. */
public class ViewOverlay implements Drawable.Callback {
    private final View host;
    private final ArrayList<Drawable> drawables=new ArrayList<Drawable>();
    ViewOverlay(View host){this.host=host;}
    public void add(Drawable drawable){
        if(drawable==null)throw new IllegalArgumentException("Overlay drawable");
        if(drawables.contains(drawable))return;
        if(drawables.size()>=256)throw new IllegalStateException("Overlay drawable limit");
        Drawable.Callback previous=drawable.getCallback();
        if(previous instanceof ViewOverlay)((ViewOverlay)previous).remove(drawable);
        drawables.add(drawable);drawable.setCallback(this);host.invalidate();
    }
    public void remove(Drawable drawable){
        if(drawable==null)throw new IllegalArgumentException("Overlay drawable");
        if(!drawables.remove(drawable))return;
        host.unscheduleDrawable(drawable);if(drawable.getCallback()==this)drawable.setCallback(null);host.invalidate();
    }
    public void clear(){for(Drawable d:new ArrayList<Drawable>(drawables))remove(d);}
    public final int shirazDrawableCount(){return drawables.size();}
    final boolean contains(Drawable drawable){return drawables.contains(drawable);}
    final void draw(Canvas canvas){
        if(drawables.isEmpty())return;int saved=canvas.save();
        try{canvas.clipRect(0,0,host.getWidth(),host.getHeight());for(Drawable d:new ArrayList<Drawable>(drawables))if(contains(d)){int childSave=canvas.save();try{d.draw(canvas);}finally{canvas.restoreToCount(childSave);}}}
        finally{canvas.restoreToCount(saved);}
    }
    public void invalidateDrawable(Drawable who){if(contains(who))host.invalidate();}
    public void scheduleDrawable(Drawable who,Runnable task,long when){if(contains(who))host.scheduleDrawable(who,task,when);}
    public void unscheduleDrawable(Drawable who,Runnable task){host.unscheduleDrawable(who,task);}
}
