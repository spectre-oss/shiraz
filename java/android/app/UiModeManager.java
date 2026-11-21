// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class UiModeManager {
    public static String ACTION_ENTER_CAR_MODE = "android.app.action.ENTER_CAR_MODE";
    public static String ACTION_ENTER_DESK_MODE = "android.app.action.ENTER_DESK_MODE";
    public static String ACTION_EXIT_CAR_MODE = "android.app.action.EXIT_CAR_MODE";
    public static String ACTION_EXIT_DESK_MODE = "android.app.action.EXIT_DESK_MODE";

    public static final int DISABLE_CAR_MODE_GO_HOME = 1;
    public static final int ENABLE_CAR_MODE_GO_CAR_HOME = 1;
    public static final int ENABLE_CAR_MODE_ALLOW_SLEEP = 2;

    public static final int MODE_NIGHT_AUTO = 0;
    public static final int MODE_NIGHT_NO = 1;
    public static final int MODE_NIGHT_YES = 2;
    public static final int MODE_NIGHT_CUSTOM = 3;

    public interface ContrastChangeListener {
        void onContrastChanged(float contrast);
    }

    private final android.content.Context context;
    private int nightModeOverride = -1;
    private LocalTime customNightStart = LocalTime.of(22, 0);
    private LocalTime customNightEnd = LocalTime.of(6, 0);
    private final CopyOnWriteArrayList<ContrastChangeListener> contrastListeners =
            new CopyOnWriteArrayList<ContrastChangeListener>();

    public UiModeManager(android.content.Context context) {
        this.context = context;
    }

    public void enableCarMode(int flags) {
    }

    public void disableCarMode(int flags) {
    }

    public int getCurrentModeType() {
        return context.getResources().getConfiguration().uiMode & 15;
    }

    public void setNightMode(int mode) {
        nightModeOverride = mode;
    }

    public void setApplicationNightMode(int mode) {
        nightModeOverride = mode;
    }

    public int getNightMode() {
        if (nightModeOverride >= 0) {
            return nightModeOverride;
        }
        return (context.getResources().getConfiguration().uiMode & 48) == 32 ? MODE_NIGHT_YES : MODE_NIGHT_NO;
    }

    public LocalTime getCustomNightModeStart() {
        return customNightStart;
    }

    public void setCustomNightModeStart(LocalTime time) {
        customNightStart = Objects.requireNonNull(time, "time");
    }

    public LocalTime getCustomNightModeEnd() {
        return customNightEnd;
    }

    public void setCustomNightModeEnd(LocalTime time) {
        customNightEnd = Objects.requireNonNull(time, "time");
    }

    public float getContrast() {
        return 0.0f;
    }

    public void addContrastChangeListener(Executor executor, ContrastChangeListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        contrastListeners.addIfAbsent(listener);
    }

    public void removeContrastChangeListener(ContrastChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        contrastListeners.remove(listener);
    }
}
