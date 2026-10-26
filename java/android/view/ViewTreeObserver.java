// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import java.util.ArrayList;
/** Observer for the supported software view tree. Window attachment families are separate. */
public final class ViewTreeObserver {
 int windowX,windowY;
 int windowVisibility=View.VISIBLE;
 long drawingTime;
 public void shirazSetWindowOrigin(int x,int y){check();windowX=x;windowY=y;}
    public interface OnWindowFocusChangeListener {void onWindowFocusChanged(boolean hasFocus);}
    private final ArrayList<OnWindowFocusChangeListener> focus=new ArrayList<OnWindowFocusChangeListener>();
    public interface OnTouchModeChangeListener {void onTouchModeChanged(boolean inTouchMode);}
    private final ArrayList<OnTouchModeChangeListener> touch=new ArrayList<OnTouchModeChangeListener>();
    boolean touchMode;
    public void addOnTouchModeChangeListener(OnTouchModeChangeListener listener){add(touch,listener);}
    public void removeOnTouchModeChangeListener(OnTouchModeChangeListener listener){check();touch.remove(listener);}
    public void dispatchOnTouchModeChanged(boolean value){check();for(OnTouchModeChangeListener listener:new ArrayList<OnTouchModeChangeListener>(touch))listener.onTouchModeChanged(value);}
    boolean windowFocused;
    public void addOnWindowFocusChangeListener(OnWindowFocusChangeListener listener){add(focus,listener);}
    public void removeOnWindowFocusChangeListener(OnWindowFocusChangeListener listener){check();focus.remove(listener);}
    public void dispatchOnWindowFocusChange(boolean hasFocus){check();for(OnWindowFocusChangeListener listener:new ArrayList<OnWindowFocusChangeListener>(focus))listener.onWindowFocusChanged(hasFocus);}
    public interface OnGlobalLayoutListener {void onGlobalLayout();}
    public interface OnPreDrawListener {boolean onPreDraw();}
    public interface OnDrawListener {void onDraw();}
    private final ArrayList<OnGlobalLayoutListener> layout=new ArrayList<OnGlobalLayoutListener>();
    private final ArrayList<OnPreDrawListener> before=new ArrayList<OnPreDrawListener>();
    private final ArrayList<OnDrawListener> draw=new ArrayList<OnDrawListener>();
    public interface OnScrollChangedListener{void onScrollChanged();}
    private final ArrayList<OnScrollChangedListener> scroll=new ArrayList<OnScrollChangedListener>();
    boolean shirazScrollPending;
    public void addOnScrollChangedListener(OnScrollChangedListener listener){add(scroll,listener);}
    public void removeOnScrollChangedListener(OnScrollChangedListener listener){check();scroll.remove(listener);}
    public void dispatchOnScrollChanged(){check();for(OnScrollChangedListener listener:new ArrayList<OnScrollChangedListener>(scroll))listener.onScrollChanged();}
    private boolean alive=true,drawing;
    boolean insetsDirty=true;int insetsWidth=-1,insetsHeight=-1;WindowInsets rootInsets;
    int systemUiVisibility;boolean systemUiDirty=true;
    final Choreographer choreographer=android.os.Looper.myLooper()==null?null:Choreographer.getInstance();
    ViewTreeObserver(){}
    public boolean isAlive(){return alive;}
    private void check(){if(!alive)throw new IllegalStateException("View tree observer is no longer alive");}
    private <T> void add(ArrayList<T> list,T value){check();if(value==null)throw new NullPointerException("listener");if(list.size()>=1024)throw new IllegalStateException("Tree observer listener limit");list.add(value);}
    public void addOnGlobalLayoutListener(OnGlobalLayoutListener value){add(layout,value);}
    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener value){check();layout.remove(value);}
    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener value){removeOnGlobalLayoutListener(value);}
    public void addOnPreDrawListener(OnPreDrawListener value){add(before,value);}
    public void removeOnPreDrawListener(OnPreDrawListener value){check();before.remove(value);}
    private void drawChange(){check();if(drawing)throw new IllegalStateException("Draw listeners cannot change during onDraw");}
    public void addOnDrawListener(OnDrawListener value){drawChange();add(draw,value);}
    public void removeOnDrawListener(OnDrawListener value){drawChange();draw.remove(value);}
    public void dispatchOnGlobalLayout(){check();for(OnGlobalLayoutListener value:new ArrayList<OnGlobalLayoutListener>(layout))value.onGlobalLayout();}
    public boolean dispatchOnPreDraw(){check();if(shirazScrollPending){shirazScrollPending=false;dispatchOnScrollChanged();}boolean cancel=false;for(OnPreDrawListener value:new ArrayList<OnPreDrawListener>(before))cancel|=!value.onPreDraw();return cancel;}
    public void dispatchOnDraw(){check();if(drawing)throw new IllegalStateException("Reentrant draw observer dispatch");drawing=true;try{for(OnDrawListener value:new ArrayList<OnDrawListener>(draw))value.onDraw();}finally{drawing=false;}}
    final android.os.IBinder windowToken=new WindowToken();
    final WindowId windowId=new WindowId(this);
    private android.os.Handler handler;
    synchronized android.os.Handler windowHandler(){
        if(handler==null)handler=new android.os.Handler(android.os.Looper.getMainLooper());
        return handler;
    }
    int attachedViews;
    public static ViewTreeObserver shirazCreateWindowObserver(){return new ViewTreeObserver();}
    public void shirazKill(){drawChange();alive=false;layout.clear();before.clear();draw.clear();scroll.clear();focus.clear();touch.clear();shirazScrollPending=false;}
    void mergeAll(ArrayList<ViewTreeObserver> observers,int views){
        check();if(drawing)throw new IllegalStateException("Cannot attach during onDraw");
        if((long)attachedViews+views>65536)throw new IllegalStateException("Window view count limit");
        long lc=layout.size(),pc=before.size(),dc=draw.size(),sc=scroll.size(),fc=focus.size(),tc=touch.size();
        for(ViewTreeObserver old:observers){old.check();if(old==this)continue;if(old.drawing)throw new IllegalStateException("Cannot merge a drawing observer");lc+=old.layout.size();pc+=old.before.size();dc+=old.draw.size();sc+=old.scroll.size();fc+=old.focus.size();tc+=old.touch.size();}
        if(lc>1024||pc>1024||dc>1024||sc>1024||fc>1024||tc>1024)throw new IllegalStateException("Tree observer merge limit");
        for(ViewTreeObserver old:observers)if(old!=this){layout.addAll(old.layout);before.addAll(old.before);draw.addAll(old.draw);scroll.addAll(old.scroll);touch.addAll(old.touch);old.touch.clear();focus.addAll(old.focus);old.focus.clear();shirazScrollPending|=old.shirazScrollPending;old.scroll.clear();old.shirazScrollPending=false;old.layout.clear();old.before.clear();old.draw.clear();old.alive=false;}
        attachedViews+=views;
    }
}
