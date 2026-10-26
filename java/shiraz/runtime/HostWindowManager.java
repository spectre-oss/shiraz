// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.res.Resources;
import android.view.Display;
import android.view.WindowManager;
/** The same configured logical display used by the application's resources and surface. */
public final class HostWindowManager implements WindowManager {
    private final Display display;
    private final WindowManager owner;
    private android.view.View root;
    private final java.util.IdentityHashMap<android.view.View,android.widget.PopupWindow> windows=new java.util.IdentityHashMap<>();
    private final java.util.WeakHashMap<android.view.View,Boolean> hostDismissed=new java.util.WeakHashMap<>();
    public void shirazSetRoot(android.view.View view){root=view;}
    private WindowManager.LayoutParams params(android.view.ViewGroup.LayoutParams params){if(!(params instanceof WindowManager.LayoutParams))throw new IllegalArgumentException("Window attributes required");return (WindowManager.LayoutParams)params;}
    public void addView(android.view.View view,android.view.ViewGroup.LayoutParams params){
        if(owner!=null){owner.addView(view,params);return;}
        DialogOverlay.requireMain();WindowManager.LayoutParams p=params(params);
        if(root==null||!root.isAttachedToWindow())throw new IllegalStateException("No attached host window");
        if(p.token!=null&&p.token!=root.getWindowToken())throw new IllegalArgumentException("Window token belongs to another window");
        if(windows.containsKey(view))throw new IllegalStateException("Window already added");
        android.view.ViewGroup holder=new android.view.ViewGroup(view.getContext()){
            protected void onMeasure(int widthSpec,int heightSpec){view.measure(widthSpec,heightSpec);setMeasuredDimension(view.getMeasuredWidth(),view.getMeasuredHeight());}
            protected void onLayout(boolean changed,int left,int top,int right,int bottom){view.layout(0,0,right-left,bottom-top);}
        };
        hostDismissed.remove(view);
        holder.addView(view,p);
        android.widget.PopupWindow popup=new android.widget.PopupWindow(holder,p.width,p.height,(p.flags&8)==0);
        popup.setTouchable((p.flags&16)==0);popup.setTouchModal((p.flags&(8|32))==0);popup.setOutsideTouchable(false);popup.setClippingEnabled(false);
        view.setLayoutParams(p);popup.setOnDismissListener(new android.widget.PopupWindow.OnDismissListener(){public void onDismiss(){if(windows.remove(view)!=null)hostDismissed.put(view,Boolean.TRUE);holder.removeView(view);}});popup.showAtLocation(root,p.gravity,p.x,p.y);windows.put(view,popup);
    }
    public void updateViewLayout(android.view.View view,android.view.ViewGroup.LayoutParams params){if(owner!=null){owner.updateViewLayout(view,params);return;}DialogOverlay.requireMain();WindowManager.LayoutParams p=params(params);android.widget.PopupWindow popup=windows.get(view);if(popup==null)throw new IllegalArgumentException("Window is not added");view.setLayoutParams(p);popup.setFocusable((p.flags&8)==0);popup.setTouchable((p.flags&16)==0);popup.setTouchModal((p.flags&(8|32))==0);popup.setWidth(p.width);popup.setHeight(p.height);popup.update(p.x,p.y,p.width,p.height);}
    public void removeView(android.view.View view){if(owner!=null){owner.removeView(view);return;}DialogOverlay.requireMain();android.widget.PopupWindow popup=windows.remove(view);if(popup==null){if(hostDismissed.remove(view)!=null)return;throw new IllegalArgumentException("Window is not added");}popup.dismiss();}
    public void removeViewImmediate(android.view.View view){if(owner!=null){owner.removeViewImmediate(view);return;}removeView(view);}
    public HostWindowManager(Resources resources) { this(resources,null); }
    /** Scoped display metrics, with window ownership retained by the base context. */
    public HostWindowManager(Resources resources,WindowManager owner) { display = Display.shirazForResources(resources); this.owner=owner; }
    public Display getDefaultDisplay() { return display; }
}
