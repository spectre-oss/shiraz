// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.display;
import android.os.Handler;
import java.util.ArrayList;
/** One application display. Listeners are recorded; the host display does not rotate. */
public final class DisplayManager {
    public interface DisplayListener {
        void onDisplayAdded(int displayId);
        void onDisplayRemoved(int displayId);
        void onDisplayChanged(int displayId);
    }
    private android.content.res.Resources resources;
    public DisplayManager(){}
    public DisplayManager(android.content.res.Resources resources){this.resources=resources;}
    public android.view.Display getDisplay(int id){return id==0&&resources!=null?android.view.Display.shirazForResources(resources):null;}
    public android.view.Display[] getDisplays(){android.view.Display display=getDisplay(0);return display==null?new android.view.Display[0]:new android.view.Display[]{display};}
    private final ArrayList<DisplayListener> listeners = new ArrayList<DisplayListener>();
    public void registerDisplayListener(DisplayListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener");
        if (!listeners.contains(listener)) listeners.add(listener);
    }
    public void unregisterDisplayListener(DisplayListener listener) { listeners.remove(listener); }
    public int shirazListenerCount() { return listeners.size(); }
}
