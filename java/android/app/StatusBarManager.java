// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.ComponentName;
import android.graphics.drawable.Icon;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class StatusBarManager {
    public static final int TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED = 0;
    public static final int TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED = 1;
    public static final int TILE_ADD_REQUEST_RESULT_TILE_ADDED = 2;

    public static final int TILE_ADD_REQUEST_ERROR_MISMATCHED_PACKAGE = 1000;
    public static final int TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS = 1001;
    public static final int TILE_ADD_REQUEST_ERROR_BAD_COMPONENT = 1002;
    public static final int TILE_ADD_REQUEST_ERROR_NOT_CURRENT_USER = 1003;
    public static final int TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND = 1004;
    public static final int TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE = 1005;

    StatusBarManager() {
    }

    public void requestAddTileService(ComponentName tileServiceComponentName,
            CharSequence tileLabel, Icon icon, Executor resultExecutor,
            final Consumer<Integer> resultCallback) {
        Objects.requireNonNull(tileServiceComponentName, "tileServiceComponentName");
        Objects.requireNonNull(tileLabel, "tileLabel");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(resultExecutor, "resultExecutor");
        Objects.requireNonNull(resultCallback, "resultCallback");
        resultExecutor.execute(new Runnable() {
            @Override
            public void run() {
                resultCallback.accept(Integer.valueOf(TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE));
            }
        });
    }

    public boolean canLaunchCaptureContentActivityForNote(Activity activity) {
        Objects.requireNonNull(activity, "activity");
        return false;
    }
}
