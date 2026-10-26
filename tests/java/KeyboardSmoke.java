// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.ContextWrapper;
import android.view.*;

public final class KeyboardSmoke {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class Probe extends Activity {
        @Override public android.content.res.Resources.Theme getTheme(){return android.content.res.Resources.getSystem().newTheme();}
        int fallback,interactions;
        @Override public void onUserInteraction(){interactions++;}
        @Override public boolean onKeyDown(int code, KeyEvent event) { if (code == KeyEvent.KEYCODE_A) { ++fallback; return true; } return super.onKeyDown(code,event); }
    }
    private static final class TouchContext extends ContextWrapper {
        private final android.content.pm.PackageManager manager;
        TouchContext(){super(null);java.util.Properties p=new java.util.Properties();p.setProperty("package","org.shiraz.fixture");manager=new shiraz.runtime.HostPackageManager(p,this);}
        public android.content.pm.PackageManager getPackageManager(){return manager;}
    }
    private static void touchFocus(){
        check(!shiraz.runtime.NativeSurface.hasTouchscreen(),"dummy SDL backend has no direct touchscreen");
        TouchContext context=new TouchContext();android.widget.FrameLayout root=new android.widget.FrameLayout(context),group=new android.widget.FrameLayout(context);View child=new View(context),outside=new View(context);child.setFocusable(true);outside.setFocusable(true);group.addView(child);root.addView(group);root.addView(outside);
        try{
            shiraz.runtime.HostInput.updateTouchscreen(false);group.setTouchscreenBlocksFocus(true);check(group.getTouchscreenBlocksFocus()&&child.requestFocus(),"keyboard-only host does not block focus");
            shiraz.runtime.HostInput.updateTouchscreen(true);check(context.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)&&!context.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN,1),"unversioned touchscreen feature");
            group.setTouchscreenBlocksFocus(true);check(outside.hasFocus()&&!child.requestFocus(),"enable blocking moves focus outside and rejects reentry");
            child.setFocusableInTouchMode(true);check(child.requestFocus(),"touch-focusable child permitted");child.setFocusableInTouchMode(false);outside.requestFocus();
            group.setKeyboardNavigationCluster(true);java.util.ArrayList<View> clusters=new java.util.ArrayList<View>();root.addKeyboardNavigationClusters(clusters,View.FOCUS_FORWARD);check(clusters.size()==1&&clusters.get(0)==group&&group.getTouchscreenBlocksFocus(),"blocked cluster discovery preserves flag");
            check(group.restoreFocusInCluster(View.FOCUS_DOWN)&&child.hasFocus()&&group.getTouchscreenBlocksFocus(),"cluster navigation can enter blocked group");
            View second=new View(context);second.setFocusable(true);group.addView(second);check(second.requestFocus(),"focus can move within entered cluster");outside.requestFocus();check(!child.requestFocus(),"ordinary reentry blocked after leaving cluster");
            group.setTouchscreenBlocksFocus(false);check(child.requestFocus(),"disable blocking restores ordinary focus");
        }finally{shiraz.runtime.HostInput.updateTouchscreen(false);}
    }
    private static void clusters(ContextWrapper context){
        Activity activity=new Activity(){public android.content.res.Resources.Theme getTheme(){return android.content.res.Resources.getSystem().newTheme();}};android.widget.FrameLayout root=new android.widget.FrameLayout(context),a=new android.widget.FrameLayout(context),b=new android.widget.FrameLayout(context),nested=new android.widget.FrameLayout(context);
        a.setKeyboardNavigationCluster(true);b.setKeyboardNavigationCluster(true);nested.setKeyboardNavigationCluster(true);a.setId(11);b.setId(12);a.layout(0,0,100,100);b.layout(100,0,200,100);
        View first=new View(context),last=new View(context),other=new View(context),outside=new View(context);for(View v:new View[]{first,last,other,outside})v.setFocusable(true);
        a.addView(first);nested.addView(last);a.addView(nested);b.addView(other);root.addView(a);root.addView(b);root.addView(outside);activity.setContentView(root);
        java.util.ArrayList<View> values=new java.util.ArrayList<View>();root.addKeyboardNavigationClusters(values,View.FOCUS_FORWARD);check(values.size()==2&&values.get(0)==a&&values.get(1)==b,"nested clusters coalesce");
        check(last.requestFocus(),"initial nested focus");check(last.keyboardNavigationClusterSearch(null,View.FOCUS_FORWARD)==b,"topmost containing cluster");
        KeyEvent forward=new KeyEvent(1,2,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,KeyEvent.META_META_ON);KeyEvent backward=new KeyEvent(1,2,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,KeyEvent.META_META_ON|KeyEvent.META_SHIFT_ON);
        check(activity.getWindow().shirazDispatchKeyEvent(forward)&&other.hasFocus(),"Meta Tab moves to next cluster");check(activity.getWindow().shirazDispatchKeyEvent(backward)&&last.hasFocus(),"return restores nested focus");
        other.requestFocus();check(activity.getWindow().shirazDispatchKeyEvent(forward)&&outside.hasFocus(),"last cluster moves to default group");check(activity.getWindow().shirazDispatchKeyEvent(forward)&&last.hasFocus(),"default group wraps to first cluster");
        other.requestFocus();nested.removeView(last);first.setFocusedByDefault(true);check(a.restoreFocusInCluster(View.FOCUS_DOWN)&&first.hasFocus(),"detached remembered target falls back to default");
        b.setVisibility(View.GONE);values.clear();root.addKeyboardNavigationClusters(values,View.FOCUS_FORWARD);check(values.size()==1&&values.get(0)==a,"hidden cluster omitted");b.setVisibility(View.VISIBLE);
        a.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);values.clear();root.addKeyboardNavigationClusters(values,View.FOCUS_FORWARD);check(values.size()==1&&values.get(0)==b,"blocked descendants excluded");a.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        b.setNextClusterForwardId(a.getId());check(b.keyboardNavigationClusterSearch(null,View.FOCUS_FORWARD)==a,"explicit next cluster");
        other.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View view,int key,KeyEvent event){return true;}});other.requestFocus();check(activity.getWindow().shirazDispatchKeyEvent(forward)&&other.hasFocus(),"app handles key before cluster navigation");
        try{root.keyboardNavigationClusterSearch(null,999);throw new AssertionError("invalid cluster direction");}catch(IllegalArgumentException expected){}
        forward.recycle();backward.recycle();
    }
    private static void hardwareInputMethod(ContextWrapper context) {
        android.view.inputmethod.InputMethodManager imm=android.view.inputmethod.InputMethodManager.shirazHardwareOnly();
        final int[] results={0};
        android.os.ResultReceiver receiver=new android.os.ResultReceiver(null){protected void onReceiveResult(int code,android.os.Bundle data){results[0]++;}};
        android.widget.EditText editor=new android.widget.EditText(context);
        android.view.ViewTreeObserver observer=android.view.ViewTreeObserver.shirazCreateWindowObserver();
        editor.shirazAttachTree(observer);check(editor.requestFocus(),"hardware editor focus");
        check(!imm.showSoftInput(editor,imm.SHOW_FORCED,receiver),"no soft IME can be shown");
        check(!imm.hideSoftInputFromWindow(editor.getWindowToken(),0,receiver),"no soft IME hide request dispatched");
        check(!imm.hideSoftInputFromWindow(null,imm.HIDE_NOT_ALWAYS,receiver),"null token rejected");
        imm.toggleSoftInput(imm.SHOW_FORCED,0);imm.toggleSoftInputFromWindow(editor.getWindowToken(),0,imm.HIDE_NOT_ALWAYS);imm.toggleSoftInputFromWindow(null,0,0);
        check(results[0]==0,"undispatched IME requests must not invent result notifications");
        check(editor.isFocused()&&!imm.isActive(editor)&&!imm.isActive()&&!imm.isAcceptingText()&&!imm.isFullscreenMode(),"hardware focus independent of absent IME connection");
        editor.dispatchKeyEvent(new KeyEvent(0,"Vienna",0,0));
        check("Vienna".contentEquals(editor.getText()),"committed hardware text still reaches focused editor after hide");
        editor.shirazDetachTree();observer.shirazKill();
        check(!imm.hideSoftInputFromWindow(editor.getWindowToken(),0,receiver)&&results[0]==0,"detached editor hide has no callback");
        receiver.send(7,null);check(results[0]==1,"local receiver delivery");
        if(android.os.Looper.myLooper()==null)android.os.Looper.prepare();
        android.os.ResultReceiver async=new android.os.ResultReceiver(new android.os.Handler()){protected void onReceiveResult(int code,android.os.Bundle data){check(code==9,"result code preserved");results[0]++;}};
        async.send(9,null);check(results[0]==1,"handler delivery remains asynchronous");
        android.os.Looper.myLooper().shirazDispatchDue(10);check(results[0]==2,"handler delivers result once");
        try{receiver.writeToParcel(null,0);throw new AssertionError("remote receiver transport accepted");}catch(UnsupportedOperationException expected){}
    }
    private static void hostInputMethod(ContextWrapper context){
        final boolean[] visible={false};final int[] requests={0};
        android.view.inputmethod.InputMethodManager imm=android.view.inputmethod.InputMethodManager.shirazHost(new android.view.inputmethod.InputMethodManager.KeyboardController(){public boolean setVisible(boolean value){visible[0]=value;requests[0]++;return true;}});
        android.widget.EditText editor=new android.widget.EditText(context);
        check(!imm.showSoftInput(editor,0)&&requests[0]==0,"detached editor cannot open host keyboard");
        android.view.ViewTreeObserver observer=android.view.ViewTreeObserver.shirazCreateWindowObserver();editor.shirazAttachTree(observer);editor.requestFocus();
        check(imm.showSoftInput(editor,imm.SHOW_IMPLICIT)&&visible[0],"Android show reaches host keyboard");
        check(!imm.hideSoftInputFromWindow(new android.os.Binder(),0)&&visible[0],"foreign window cannot hide keyboard");
        check(imm.hideSoftInputFromWindow(editor.getWindowToken(),0)&&!visible[0]&&editor.hasFocus(),"hide preserves editor focus");
        check(imm.showSoftInput(editor,imm.SHOW_FORCED),"forced show");
        check(!imm.hideSoftInputFromWindow(editor.getWindowToken(),imm.HIDE_IMPLICIT_ONLY)&&visible[0],"implicit hide preserves forced keyboard");
        imm.hideSoftInputFromWindow(editor.getWindowToken(),0);
        imm.toggleSoftInput(0,0);check(visible[0],"toggle shows keyboard");
        editor.shirazDetachTree();imm.shirazCheckEditor();check(!visible[0],"detaching editor hides host keyboard");observer.shirazKill();
        final android.view.inputmethod.InputMethodManager host=imm;
        ContextWrapper dialogContext=new ContextWrapper(context){
            public Object getSystemService(String name){return INPUT_METHOD_SERVICE.equals(name)?host:null;}
            public android.content.res.Resources.Theme getTheme(){return getResources().newTheme();}
        };
        android.app.Dialog dialog=new android.app.Dialog(dialogContext);
        android.widget.EditText field=new android.widget.EditText(dialogContext);dialog.setContentView(field);dialog.show();field.requestFocus();shiraz.runtime.DialogOverlay.windowFocus(null,true);
        check(visible[0],"new focused editor dialog requests keyboard without explicit show");
        for(int i=0;i<3;i++){
            dialog.hide();imm.shirazCheckEditor();check(!visible[0],"hidden dialog releases keyboard");
            dialog.show();shiraz.runtime.DialogOverlay.windowFocus(null,true);check(field.hasFocus()&&visible[0],"reshown dialog restores editor and keyboard");
        }
        dialog.hide();imm.shirazCheckEditor();dialog.getWindow().setSoftInputMode(3);dialog.show();shiraz.runtime.DialogOverlay.windowFocus(null,true);
        check(field.hasFocus()&&!visible[0],"always-hidden soft input policy respected on reshow");
        dialog.dismiss();imm.shirazCheckEditor();
        android.app.Dialog replacement=new android.app.Dialog(dialogContext);android.widget.EditText replacementField=new android.widget.EditText(dialogContext);
        replacement.setContentView(replacementField);replacement.show();replacementField.requestFocus();shiraz.runtime.DialogOverlay.windowFocus(null,true);
        check(visible[0],"fresh dialog after dismissal requests keyboard");replacement.dismiss();imm.shirazCheckEditor();


    }
    private static void characterMap(){
        KeyCharacterMap map=KeyCharacterMap.load(-1);check(map.getKeyboardType()==KeyCharacterMap.FULL&&KeyCharacterMap.load(999).getKeyboardType()==KeyCharacterMap.FULL,"virtual keyboard fallback");
        check(map.get(KeyEvent.KEYCODE_A,0)=='a'&&map.get(KeyEvent.KEYCODE_A,KeyEvent.META_SHIFT_LEFT_ON)=='A',"logical letter/shift");
        check(map.get(KeyEvent.KEYCODE_A,KeyEvent.META_CAPS_LOCK_ON)=='A'&&map.get(KeyEvent.KEYCODE_A,KeyEvent.META_CAPS_LOCK_ON|KeyEvent.META_SHIFT_ON)=='A',"virtual caps and shift");
        check(map.get(KeyEvent.KEYCODE_2,KeyEvent.META_SHIFT_ON)=='@'&&map.get(KeyEvent.KEYCODE_NUMPAD_2,KeyEvent.META_SHIFT_ON|KeyEvent.META_NUM_LOCK_ON)=='2'&&map.get(KeyEvent.KEYCODE_NUMPAD_2,0)==0,"digit and keypad mapping");
        check(map.get(KeyEvent.KEYCODE_A,KeyEvent.META_CTRL_ON)==0&&map.get(KeyEvent.KEYCODE_DPAD_UP,0)==0,"shortcut/noncharacter keys");
        check(map.getMatch(KeyEvent.KEYCODE_A,new char[]{'a','A'},KeyEvent.META_SHIFT_ON)=='A'&&map.getMatch(KeyEvent.KEYCODE_A,new char[]{'z'})==0,"candidate matching");
        KeyCharacterMap.KeyData data=new KeyCharacterMap.KeyData();check(map.getKeyData(KeyEvent.KEYCODE_A,data)&&data.displayLabel=='A'&&data.meta[0]=='a'&&data.meta[1]=='A',"key data");
        KeyEvent event=new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A,0,KeyEvent.META_SHIFT_ON);check(event.getUnicodeChar()=='A'&&event.getDisplayLabel()=='A'&&event.isPrintingKey(),"KeyEvent character-map delegation");
    }
    public static void main(String[] args) {android.os.Looper.prepareMainLooper();characterMap();
        ContextWrapper context = new ContextWrapper(null); KeyboardFixture.stateChecks(context);clusters(context);touchFocus();
        Probe activity = new Probe(); View content = new View(context); content.setFocusable(true); activity.setContentView(content);
        final int[] intercepted = {0};
        content.setOnKeyListener(new View.OnKeyListener() { public boolean onKey(View view, int code, KeyEvent event) { ++intercepted[0]; return true; } });
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A)); check(activity.fallback == 1 && intercepted[0] == 0, "unfocused content must not steal keys");
        check(content.requestFocus(), "content focus"); activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_A));
        check(activity.fallback == 1 && intercepted[0] == 1, "focused View precedes Activity callback");
        check(activity.interactions==2,"Key interaction callback precedes focused View dispatch");
        MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,1,1,0),up=MotionEvent.obtain(0,1,MotionEvent.ACTION_UP,1,1,0);
        activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(up);down.recycle();up.recycle();
        check(activity.interactions==3,"Touch interaction callback only on down");
        activity.setContentView(new View(context)); check(!content.hasFocus(), "replacing content clears old focus");
        Activity canceled = new Activity(); canceled.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));
        canceled.dispatchKeyEvent(KeyEvent.changeFlags(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),KeyEvent.FLAG_CANCELED)); check(!canceled.isFinishing(), "canceled back cannot finish Activity");
        canceled.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)); canceled.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)); check(canceled.isFinishing(), "tracked back finishes Activity on release");
        KeyEvent multiple = new KeyEvent(1,2,KeyEvent.ACTION_MULTIPLE,KeyEvent.KEYCODE_A,3);
        final AssertionError marker = new AssertionError("key callback failed");
        KeyEvent.Callback broken = new KeyEvent.Callback() {
            public boolean onKeyDown(int code, KeyEvent event) { throw marker; }
            public boolean onKeyUp(int code, KeyEvent event) { return false; }
            public boolean onKeyLongPress(int code, KeyEvent event) { return false; }
            public boolean onKeyMultiple(int code, int count, KeyEvent event) { return false; }
        };
        try { multiple.dispatch(broken); throw new AssertionError("key callback failure swallowed"); } catch (AssertionError expected) { check(expected == marker, "original callback exception"); }
        check(multiple.getAction() == KeyEvent.ACTION_MULTIPLE && multiple.getRepeatCount() == 3, "multiple fallback restores event after failure");
        KeyEvent copy = new KeyEvent(multiple); copy.recycle(); try { copy.getAction(); throw new AssertionError("recycled key accepted"); } catch (IllegalStateException expected) { }
        check(multiple.getAction() == KeyEvent.ACTION_MULTIPLE, "recycling copy does not alter original");
        hostInputMethod(new ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}});
        hardwareInputMethod(new ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}});
        System.out.println("KEYBOARD_DISPATCH_TRACKING_AND_FOCUS_OK");
    }
}
