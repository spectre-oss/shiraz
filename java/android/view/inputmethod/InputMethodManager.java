// SPDX-License-Identifier: GPL-3.0-only
package android.view.inputmethod;

import android.os.IBinder;
import android.os.ResultReceiver;
import android.view.View;

/** Android editor requests forwarded to the host text-input service. */
public final class InputMethodManager {
    public static final int SHOW_IMPLICIT=1, SHOW_FORCED=2;
    public static final int HIDE_IMPLICIT_ONLY=1, HIDE_NOT_ALWAYS=2;
    public static final int RESULT_UNCHANGED_SHOWN=0, RESULT_UNCHANGED_HIDDEN=1,
            RESULT_SHOWN=2, RESULT_HIDDEN=3;
    private View served;private InputConnection connection;
    private int traceRequests;
    private void trace(String action,View view){if("1".equals(System.getenv("SHIRAZ_TRACE_INPUT"))&&traceRequests++<256)System.err.println("SHIRAZ_IME_REQUEST action="+action+" attached="+(view!=null&&view.isAttachedToWindow())+" focused="+(view!=null&&view.hasFocus()));}
    public interface KeyboardController { boolean setVisible(boolean visible); }
    private KeyboardController keyboard;private boolean shown,forced,explicit;
    private InputMethodManager() {}
    public static InputMethodManager shirazHost(KeyboardController controller){InputMethodManager value=new InputMethodManager();value.keyboard=controller;return value;}
    public boolean shirazKeyboardRequested(){return shown;}
    public void shirazCheckEditor(){if(shown&&(served==null||!served.isAttachedToWindow()||!served.hasFocus())){keyboard.setVisible(false);shown=false;forced=false;explicit=false;}}
    public void restartInput(View view){if(connection!=null)connection.closeConnection();served=view;connection=view==null?null:view.onCreateInputConnection(new EditorInfo());}
    public void updateSelection(View view,int start,int end,int composingStart,int composingEnd){}
    public void updateCursorAnchorInfo(View view,CursorAnchorInfo info){}
    public void updateExtractedText(View view,int token,ExtractedText text){}
    public boolean shirazCommitText(String text){return served!=null&&served.isAttachedToWindow()&&served.hasFocus()&&connection!=null&&connection.commitText(text,1);}
    public static InputMethodManager shirazHardwareOnly() { return new InputMethodManager(); }
    public boolean showSoftInput(View view,int flags) { return showSoftInput(view,flags,null); }
    public boolean showSoftInput(View view,int flags,ResultReceiver receiver) {
        trace("show",view);
        if(keyboard==null||view==null||!view.isAttachedToWindow()||!view.hasFocus())return false;
        if(!keyboard.setVisible(true))return false;
        if(served!=view)restartInput(view);
        boolean previous=shown;shown=true;forced|=(flags&SHOW_FORCED)!=0;explicit|=forced||(flags&SHOW_IMPLICIT)==0;
        if(receiver!=null)receiver.send(previous?RESULT_UNCHANGED_SHOWN:RESULT_SHOWN,null);
        return true;
    }
    public boolean hideSoftInputFromWindow(IBinder token,int flags) { return hideSoftInputFromWindow(token,flags,null); }
    public boolean hideSoftInputFromWindow(IBinder token,int flags,ResultReceiver receiver) {
        trace("hide",served);
        if(keyboard==null||token==null||served==null||token!=served.getWindowToken())return false;
        if(explicit&&(flags&HIDE_IMPLICIT_ONLY)!=0||forced&&(flags&HIDE_NOT_ALWAYS)!=0)return false;
        if(!keyboard.setVisible(false))return false;
        boolean previous=shown;shown=false;forced=false;explicit=false;
        if(receiver!=null)receiver.send(previous?RESULT_HIDDEN:RESULT_UNCHANGED_HIDDEN,null);
        return true;
    }
    public void toggleSoftInput(int showFlags,int hideFlags) {if(served!=null){if(shown)hideSoftInputFromWindow(served.getWindowToken(),hideFlags);else showSoftInput(served,showFlags);}}
    public void toggleSoftInputFromWindow(IBinder token,int showFlags,int hideFlags) {if(served!=null&&token!=null&&token==served.getWindowToken())toggleSoftInput(showFlags,hideFlags);}
    public boolean isAcceptingText() { return served!=null&&served.isAttachedToWindow()&&served.hasFocus()&&(connection!=null||served.onCheckIsTextEditor()); }
    public boolean isActive() { return isAcceptingText(); }
    public boolean isActive(View view) { return view==served&&isAcceptingText(); }
    public boolean isFullscreenMode() { return false; }
}
