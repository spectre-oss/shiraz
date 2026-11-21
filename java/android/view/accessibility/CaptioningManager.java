// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/** API type for optional caption settings; this runtime has no captioning service. */
public class CaptioningManager {
    private final ArrayList<CaptioningChangeListener> mListeners =
            new ArrayList<CaptioningChangeListener>();

    public CaptioningManager() {
    }

    public final boolean isEnabled() {
        return false;
    }

    public final Locale getLocale() {
        return null;
    }

    public final float getFontScale() {
        return 1f;
    }

    public CaptionStyle getUserStyle() {
        return CaptionStyle.DEFAULT;
    }

    public final boolean isSystemAudioCaptioningEnabled() {
        return false;
    }

    public final boolean isSystemAudioCaptioningUiEnabled() {
        return false;
    }

    public boolean isCallCaptioningEnabled() {
        return false;
    }

    public void addCaptioningChangeListener(CaptioningChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void removeCaptioningChangeListener(CaptioningChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    public static final class CaptionStyle {
        public static final int EDGE_TYPE_UNSPECIFIED = -1;
        public static final int EDGE_TYPE_NONE = 0;
        public static final int EDGE_TYPE_OUTLINE = 1;
        public static final int EDGE_TYPE_DROP_SHADOW = 2;
        public static final int EDGE_TYPE_RAISED = 3;
        public static final int EDGE_TYPE_DEPRESSED = 4;

        public static final CaptionStyle DEFAULT = new CaptionStyle();

        public final int foregroundColor = 0xffffffff;
        public final int backgroundColor = 0xff000000;
        public final int windowColor = 0;
        public final int edgeType = EDGE_TYPE_NONE;
        public final int edgeColor = 0xff000000;

        public Typeface getTypeface() {
            return null;
        }

        public boolean hasForegroundColor() {
            return true;
        }

        public boolean hasBackgroundColor() {
            return true;
        }

        public boolean hasWindowColor() {
            return false;
        }

        public boolean hasEdgeType() {
            return true;
        }

        public boolean hasEdgeColor() {
            return true;
        }
    }

    public abstract static class CaptioningChangeListener {
        public CaptioningChangeListener() {
        }

        public void onEnabledChanged(boolean enabled) {
        }

        public void onUserStyleChanged(CaptionStyle userStyle) {
        }

        public void onLocaleChanged(Locale locale) {
        }

        public void onFontScaleChanged(float fontScale) {
        }

        public void onSystemAudioCaptioningChanged(boolean enabled) {
        }

        public void onSystemAudioCaptioningUiChanged(boolean enabled) {
        }
    }
}
