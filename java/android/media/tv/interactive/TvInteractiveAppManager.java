// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv.interactive;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class TvInteractiveAppManager {
    public static final int SERVICE_STATE_UNREALIZED = 1;
    public static final int SERVICE_STATE_PREPARING = 2;
    public static final int SERVICE_STATE_READY = 3;
    public static final int SERVICE_STATE_ERROR = 4;

    public static final int INTERACTIVE_APP_STATE_STOPPED = 1;
    public static final int INTERACTIVE_APP_STATE_RUNNING = 2;
    public static final int INTERACTIVE_APP_STATE_ERROR = 3;

    public static final int TELETEXT_APP_STATE_SHOW = 1;
    public static final int TELETEXT_APP_STATE_HIDE = 2;
    public static final int TELETEXT_APP_STATE_ERROR = 3;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_UNKNOWN = 1;
    public static final int ERROR_NOT_SUPPORTED = 2;
    public static final int ERROR_WEAK_SIGNAL = 3;
    public static final int ERROR_RESOURCE_UNAVAILABLE = 4;
    public static final int ERROR_BLOCKED = 5;
    public static final int ERROR_ENCRYPTED = 6;
    public static final int ERROR_UNKNOWN_CHANNEL = 7;

    public static final String INTENT_KEY_INTERACTIVE_APP_SERVICE_ID = "interactive_app_id";
    public static final String INTENT_KEY_BI_INTERACTIVE_APP_TYPE = "bi_interactive_app_type";
    public static final String INTENT_KEY_BI_INTERACTIVE_APP_URI = "bi_interactive_app_uri";
    public static final String INTENT_KEY_COMMAND_TYPE = "command_type";
    public static final String INTENT_KEY_TV_INPUT_ID = "tv_input_id";
    public static final String INTENT_KEY_CHANNEL_URI = "channel_uri";

    public static final String ACTION_APP_LINK_COMMAND =
            "android.media.tv.interactive.action.APP_LINK_COMMAND";
    public static final String APP_LINK_KEY_PACKAGE_NAME = "package_name";
    public static final String APP_LINK_KEY_CLASS_NAME = "class_name";
    public static final String APP_LINK_KEY_COMMAND_TYPE = "command_type";
    public static final String APP_LINK_KEY_SERVICE_ID = "service_id";
    public static final String APP_LINK_KEY_BACK_URI = "back_uri";

    public abstract static class TvInteractiveAppCallback {
        public TvInteractiveAppCallback() {
        }

        public void onInteractiveAppServiceAdded(String iAppServiceId) {
        }

        public void onInteractiveAppServiceRemoved(String iAppServiceId) {
        }

        public void onInteractiveAppServiceUpdated(String iAppServiceId) {
        }

        public void onTvInteractiveAppServiceStateChanged(
                String iAppServiceId, int type, int state, int err) {
        }
    }

    private final List<TvInteractiveAppServiceInfo> mServices =
            new ArrayList<TvInteractiveAppServiceInfo>();
    private final List<AppLinkInfo> mAppLinks = new ArrayList<AppLinkInfo>();
    private final Map<TvInteractiveAppCallback, Executor> mCallbacks =
            new LinkedHashMap<TvInteractiveAppCallback, Executor>();

    public TvInteractiveAppManager() {
    }

    public List<TvInteractiveAppServiceInfo> getTvInteractiveAppServiceList() {
        synchronized (mServices) {
            return new ArrayList<TvInteractiveAppServiceInfo>(mServices);
        }
    }

    public List<AppLinkInfo> getAppLinkInfoList() {
        synchronized (mAppLinks) {
            return new ArrayList<AppLinkInfo>(mAppLinks);
        }
    }

    public void registerAppLinkInfo(String tvIAppServiceId, AppLinkInfo appLinkInfo) {
        Objects.requireNonNull(tvIAppServiceId, "tvIAppServiceId cannot be null");
        Objects.requireNonNull(appLinkInfo, "appLinkInfo cannot be null");
        synchronized (mAppLinks) {
            mAppLinks.add(appLinkInfo);
        }
    }

    public void unregisterAppLinkInfo(String tvIAppServiceId, AppLinkInfo appLinkInfo) {
        Objects.requireNonNull(tvIAppServiceId, "tvIAppServiceId cannot be null");
        Objects.requireNonNull(appLinkInfo, "appLinkInfo cannot be null");
        synchronized (mAppLinks) {
            mAppLinks.remove(appLinkInfo);
        }
    }

    public void sendAppLinkCommand(String tvIAppServiceId, Bundle command) {
        Objects.requireNonNull(tvIAppServiceId, "tvIAppServiceId cannot be null");
        Objects.requireNonNull(command, "command cannot be null");
    }

    public void registerCallback(Executor executor, TvInteractiveAppCallback callback) {
        Objects.requireNonNull(executor, "executor cannot be null");
        Objects.requireNonNull(callback, "callback cannot be null");
        synchronized (mCallbacks) {
            mCallbacks.put(callback, executor);
        }
    }

    public void unregisterCallback(TvInteractiveAppCallback callback) {
        Objects.requireNonNull(callback, "callback cannot be null");
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }
}
