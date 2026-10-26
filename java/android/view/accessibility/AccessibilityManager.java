// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;
import android.os.Handler;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Local capability registry. No Android accessibility service or host AT-SPI bridge is connected. */
public final class AccessibilityManager {
    public interface AccessibilityStateChangeListener {void onAccessibilityStateChanged(boolean enabled);}
    public interface TouchExplorationStateChangeListener {void onTouchExplorationStateChanged(boolean enabled);}
    public interface HighTextContrastChangeListener {void onHighTextContrastStateChanged(boolean enabled);}
    private final IdentityHashMap<AccessibilityStateChangeListener,Handler> state=new IdentityHashMap<AccessibilityStateChangeListener,Handler>();
    private final IdentityHashMap<TouchExplorationStateChangeListener,Handler> touch=new IdentityHashMap<TouchExplorationStateChangeListener,Handler>();
    private final IdentityHashMap<HighTextContrastChangeListener,Handler> contrast=new IdentityHashMap<HighTextContrastChangeListener,Handler>();
    private boolean closed;
    private AccessibilityManager(){}
    public static AccessibilityManager shirazCreate(){return new AccessibilityManager();}
    private void live(){if(closed)throw new IllegalStateException("Accessibility manager is closed");}
    public synchronized boolean isEnabled(){live();return false;}
    public synchronized boolean isTouchExplorationEnabled(){live();return false;}
    public synchronized boolean isHighTextContrastEnabled(){live();return false;}
    @SuppressWarnings("rawtypes") public synchronized List getInstalledAccessibilityServiceList(){live();return Collections.emptyList();}
    @SuppressWarnings("rawtypes") public synchronized List getEnabledAccessibilityServiceList(int feedbackTypeFlags){live();return Collections.emptyList();}
    private <T> void add(IdentityHashMap<T,Handler> map,T listener,Handler handler){live();if(listener==null)throw new NullPointerException("listener");if(!map.containsKey(listener)&&map.size()>=1024)throw new IllegalStateException("Accessibility listener limit");map.put(listener,handler);}
    private <T> boolean remove(IdentityHashMap<T,Handler> map,T listener){live();boolean present=map.containsKey(listener);map.remove(listener);return present;}
    public synchronized boolean addAccessibilityStateChangeListener(AccessibilityStateChangeListener listener){add(state,listener,null);return true;}
    public synchronized void addAccessibilityStateChangeListener(AccessibilityStateChangeListener listener,Handler handler){add(state,listener,handler);}
    public synchronized boolean removeAccessibilityStateChangeListener(AccessibilityStateChangeListener listener){return remove(state,listener);}
    public synchronized boolean addTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener){add(touch,listener,null);return true;}
    public synchronized void addTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener,Handler handler){add(touch,listener,handler);}
    public synchronized boolean removeTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener){return remove(touch,listener);}
    public synchronized void addHighTextContrastStateChangeListener(HighTextContrastChangeListener listener,Handler handler){add(contrast,listener,handler);}
    public synchronized void removeHighTextContrastStateChangeListener(HighTextContrastChangeListener listener){remove(contrast,listener);}
    public synchronized void sendAccessibilityEvent(AccessibilityEvent event){live();if(event==null)throw new NullPointerException("event");throw new UnsupportedOperationException("Accessibility event delivery requires a host accessibility bridge");}
    public synchronized void interrupt(){live();throw new UnsupportedOperationException("Accessibility feedback requires a host accessibility bridge");}
    public synchronized void shirazClose(){if(closed)return;closed=true;state.clear();touch.clear();contrast.clear();}
}
