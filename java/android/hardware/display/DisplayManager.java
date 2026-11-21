// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.display;

import android.content.res.Resources;
import android.graphics.Point;
import android.os.Handler;
import android.view.Display;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Objects;

/** One application display. Listeners are recorded; the host display does not rotate. */
public final class DisplayManager {
    public static final String DISPLAY_CATEGORY_PRESENTATION =
            "android.hardware.display.category.PRESENTATION";

    public static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = 1 << 0;
    public static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = 1 << 1;
    public static final int VIRTUAL_DISPLAY_FLAG_SECURE = 1 << 2;
    public static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = 1 << 3;
    public static final int VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR = 1 << 4;

    public static final int MATCH_CONTENT_FRAMERATE_UNKNOWN = -1;
    public static final int MATCH_CONTENT_FRAMERATE_NEVER = 0;
    public static final int MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY = 1;
    public static final int MATCH_CONTENT_FRAMERATE_ALWAYS = 2;

    public interface DisplayListener {
        void onDisplayAdded(int displayId);

        void onDisplayRemoved(int displayId);

        void onDisplayChanged(int displayId);
    }

    private Resources resources;
    private final ArrayList<DisplayListener> listeners = new ArrayList<DisplayListener>();

    public DisplayManager() {
    }

    public DisplayManager(Resources resources) {
        this.resources = resources;
    }

    /**
     * Stable desktop virtual-device baseline, independent of the app window.
     * Matches the system resource display's initial 720 x 1280 configuration.
     * This is not a measurement of the host monitor or a phone panel.
     */
    public Point getStableDisplaySize() {
        return new Point(720, 1280);
    }

    public Display getDisplay(int id) {
        return id == 0 && resources != null ? Display.shirazForResources(resources) : null;
    }

    public Display[] getDisplays() {
        Display display = getDisplay(0);
        return display == null ? new Display[0] : new Display[] { display };
    }

    public Display[] getDisplays(String category) {
        if (category == null) {
            return getDisplays();
        }
        return new Display[0];
    }

    public void registerDisplayListener(DisplayListener listener, Handler handler) {
        if (listener == null) throw new IllegalArgumentException("listener");
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void unregisterDisplayListener(DisplayListener listener) {
        listeners.remove(listener);
    }

    public int shirazListenerCount() {
        return listeners.size();
    }

    public VirtualDisplay createVirtualDisplay(
            String name,
            int width,
            int height,
            int densityDpi,
            Surface surface,
            int flags) {
        return createVirtualDisplay(name, width, height, densityDpi, surface, flags, null, null);
    }

    public VirtualDisplay createVirtualDisplay(
            String name,
            int width,
            int height,
            int densityDpi,
            Surface surface,
            int flags,
            VirtualDisplay.Callback callback,
            Handler handler) {
        VirtualDisplayConfig.Builder builder =
                new VirtualDisplayConfig.Builder(name, width, height, densityDpi);
        builder.setFlags(flags);
        if (surface != null) {
            builder.setSurface(surface);
        }
        return createVirtualDisplay(builder.build(), handler, callback);
    }

    public VirtualDisplay createVirtualDisplay(VirtualDisplayConfig config) {
        return createVirtualDisplay(config, null, null);
    }

    public VirtualDisplay createVirtualDisplay(
            VirtualDisplayConfig config,
            Handler handler,
            VirtualDisplay.Callback callback) {
        Objects.requireNonNull(config, "virtualDisplayConfig must not be null");
        Display backing = resources != null ? Display.shirazForResources(resources) : null;
        return new VirtualDisplay(
                config.getName(),
                backing,
                config.getSurface(),
                config.getWidth(),
                config.getHeight(),
                config.getDensityDpi(),
                callback,
                handler);
    }

    public HdrConversionMode getHdrConversionMode() {
        return new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_UNSUPPORTED);
    }

    public int getMatchContentFrameRateUserPreference() {
        return MATCH_CONTENT_FRAMERATE_NEVER;
    }
}
