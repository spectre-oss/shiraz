// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

/** Explicit host navigation mode: pointer selection followed by ordinary keyboard activation. */
final class KeyboardPointer {
    private View pressed;
    private float startX,startY;
    void dispatch(Window window,View root,MotionEvent event){
        int action=event.getActionMasked();
        float x=event.getX(),y=event.getY();
        if(action==MotionEvent.ACTION_DOWN){
            pressed=root==null?null:root.shirazKeyboardPointerTarget(x,y);
            startX=x;startY=y;
            return;
        }
        if(action==MotionEvent.ACTION_CANCEL||event.getPointerCount()!=1||Math.abs(x-startX)>8||Math.abs(y-startY)>8){pressed=null;return;}
        if(action!=MotionEvent.ACTION_UP)return;
        View target=pressed;pressed=null;
        if(target==null||root==null||target!=root.shirazKeyboardPointerTarget(x,y)||!target.isAttachedToWindow())return;
        root.shirazSetTouchMode(false);
        if(!target.requestFocus()||!target.isFocused()||target.onCheckIsTextEditor())return;
        long time=event.getEventTime();
        window.shirazDispatchKeyEvent(new KeyEvent(time,time,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER,0,0,0,0,0,0x101));
        window.shirazDispatchKeyEvent(new KeyEvent(time,time,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER,0,0,0,0,0,0x101));
    }
}
