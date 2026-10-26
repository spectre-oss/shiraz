// SPDX-License-Identifier: GPL-3.0-only
import android.hardware.display.DisplayManager;
public final class DisplaySmoke {
    public static void main(String[] args) {
        if (!android.content.Context.DISPLAY_SERVICE.equals("display")) throw new AssertionError("display service name");
        DisplayManager manager = new DisplayManager();
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            public void onDisplayAdded(int displayId) {}
            public void onDisplayRemoved(int displayId) {}
            public void onDisplayChanged(int displayId) {}
        };
        manager.registerDisplayListener(listener, null);
        manager.registerDisplayListener(listener, null);
        if (manager.shirazListenerCount() != 1) throw new AssertionError("duplicate display listener");
        manager.unregisterDisplayListener(listener);
        if (manager.shirazListenerCount() != 0) throw new AssertionError("display listener remained");
        try { manager.registerDisplayListener(null, null); throw new AssertionError("null display listener accepted"); }
        catch (IllegalArgumentException expected) {}
        System.out.println("DISPLAY_SERVICE_OK");
    }
}
