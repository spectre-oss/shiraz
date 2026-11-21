// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class MediaRouter2 {
    private static final String SYSTEM_ROUTE_ID = "DEFAULT_ROUTE";
    private static final String SYSTEM_SESSION_ID = "SYSTEM_SESSION";
    private static MediaRouter2 sInstance;

    private final Context mContext;
    private final String mClientPackageName;
    private final Map<String, MediaRoute2Info> mRoutes =
            new LinkedHashMap<String, MediaRoute2Info>();
    private final RoutingController mSystemController;
    private final Map<RouteCallback, Executor> mRouteCallbacks =
            new ConcurrentHashMap<RouteCallback, Executor>();
    private final Map<TransferCallback, Executor> mTransferCallbacks =
            new ConcurrentHashMap<TransferCallback, Executor>();
    private final Map<ControllerCallback, Executor> mControllerCallbacks =
            new ConcurrentHashMap<ControllerCallback, Executor>();
    private final Map<Consumer<RouteListingPreference>, Executor> mListingCallbacks =
            new ConcurrentHashMap<Consumer<RouteListingPreference>, Executor>();
    private volatile RouteListingPreference mRouteListingPreference;
    private volatile OnGetControllerHintsListener mOnGetControllerHintsListener;

    private MediaRouter2(Context context, String clientPackageName) {
        mContext = context;
        mClientPackageName = clientPackageName != null ? clientPackageName
                : (context != null && context.getPackageName() != null
                        ? context.getPackageName() : "org.shiraz.app");
        AudioManager am = AudioManager.shirazForProcess();
        int maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        MediaRoute2Info systemRoute = new MediaRoute2Info.Builder(SYSTEM_ROUTE_ID, "Device Speaker")
                .addFeature(MediaRoute2Info.FEATURE_LIVE_AUDIO)
                .addFeature(MediaRoute2Info.FEATURE_LIVE_VIDEO)
                .setType(MediaRoute2Info.TYPE_BUILTIN_SPEAKER)
                .setConnectionState(MediaRoute2Info.CONNECTION_STATE_CONNECTED)
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(maxVol)
                .setVolume(curVol)
                .setSystemRoute(true)
                .build();
        mRoutes.put(SYSTEM_ROUTE_ID, systemRoute);
        RoutingSessionInfo sessionInfo = new RoutingSessionInfo.Builder(
                SYSTEM_SESSION_ID, mClientPackageName)
                .setName("System Audio")
                .addSelectedRoute(SYSTEM_ROUTE_ID)
                .setVolumeHandling(MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE)
                .setVolumeMax(maxVol)
                .setVolume(curVol)
                .build();
        mSystemController = new RoutingController(sessionInfo);
    }

    public static synchronized MediaRouter2 getInstance(Context context) {
        Objects.requireNonNull(context, "context");
        if (sInstance == null) {
            sInstance = new MediaRouter2(context.getApplicationContext() != null
                    ? context.getApplicationContext() : context, context.getPackageName());
        }
        return sInstance;
    }

    public static MediaRouter2 getInstance(
            Context context,
            String clientPackageName,
            Executor executor,
            Runnable onInstanceInvalidatedListener) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(clientPackageName, "clientPackageName");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(onInstanceInvalidatedListener, "onInstanceInvalidatedListener");
        return new MediaRouter2(context, clientPackageName);
    }

    public ScanToken requestScan(ScanRequest scanRequest) {
        Objects.requireNonNull(scanRequest, "scanRequest");
        return new ScanToken();
    }

    public void cancelScanRequest(ScanToken token) {
        Objects.requireNonNull(token, "token");
    }

    public void registerRouteCallback(
            Executor executor, RouteCallback routeCallback, RouteDiscoveryPreference preference) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(routeCallback, "routeCallback");
        Objects.requireNonNull(preference, "preference");
        mRouteCallbacks.put(routeCallback, executor);
    }

    public void unregisterRouteCallback(RouteCallback routeCallback) {
        Objects.requireNonNull(routeCallback, "routeCallback");
        mRouteCallbacks.remove(routeCallback);
    }

    public void registerRouteListingPreferenceUpdatedCallback(
            Executor executor,
            Consumer<RouteListingPreference> routeListingPreferenceUpdatedCallback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(
                routeListingPreferenceUpdatedCallback, "routeListingPreferenceUpdatedCallback");
        mListingCallbacks.put(routeListingPreferenceUpdatedCallback, executor);
    }

    public void unregisterRouteListingPreferenceUpdatedCallback(
            Consumer<RouteListingPreference> callback) {
        Objects.requireNonNull(callback, "callback");
        mListingCallbacks.remove(callback);
    }

    public boolean showSystemOutputSwitcher() {
        return false;
    }

    public void setRouteListingPreference(final RouteListingPreference routeListingPreference) {
        mRouteListingPreference = routeListingPreference;
        for (Map.Entry<Consumer<RouteListingPreference>, Executor> entry
                : mListingCallbacks.entrySet()) {
            final Consumer<RouteListingPreference> cb = entry.getKey();
            entry.getValue().execute(new Runnable() {
                @Override
                public void run() {
                    cb.accept(routeListingPreference);
                }
            });
        }
    }

    public RouteListingPreference getRouteListingPreference() {
        return mRouteListingPreference;
    }

    public List<MediaRoute2Info> getRoutes() {
        synchronized (mRoutes) {
            return Collections.unmodifiableList(
                    new ArrayList<MediaRoute2Info>(mRoutes.values()));
        }
    }

    public void registerTransferCallback(Executor executor, TransferCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        mTransferCallbacks.put(callback, executor);
    }

    public void unregisterTransferCallback(TransferCallback callback) {
        Objects.requireNonNull(callback, "callback");
        mTransferCallbacks.remove(callback);
    }

    public void registerControllerCallback(Executor executor, ControllerCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        mControllerCallbacks.put(callback, executor);
    }

    public void unregisterControllerCallback(ControllerCallback callback) {
        Objects.requireNonNull(callback, "callback");
        mControllerCallbacks.remove(callback);
    }

    public void setOnGetControllerHintsListener(OnGetControllerHintsListener listener) {
        mOnGetControllerHintsListener = listener;
    }

    public void transferTo(MediaRoute2Info route) {
        Objects.requireNonNull(route, "route");
        OnGetControllerHintsListener hintsListener = mOnGetControllerHintsListener;
        if (hintsListener != null) {
            hintsListener.onGetControllerHints(route);
        }
    }

    public void stop() {
        for (Map.Entry<TransferCallback, Executor> entry : mTransferCallbacks.entrySet()) {
            final TransferCallback cb = entry.getKey();
            entry.getValue().execute(new Runnable() {
                @Override
                public void run() {
                    cb.onStop(mSystemController);
                }
            });
        }
    }

    public RoutingController getSystemController() {
        return mSystemController;
    }

    public RoutingController getController(String id) {
        Objects.requireNonNull(id, "id");
        if (SYSTEM_SESSION_ID.equals(id) || mSystemController.getId().equals(id)) {
            return mSystemController;
        }
        return null;
    }

    public List<RoutingController> getControllers() {
        return Collections.singletonList(mSystemController);
    }

    public void setRouteVolume(MediaRoute2Info route, int volume) {
        Objects.requireNonNull(route, "route");
        if (SYSTEM_ROUTE_ID.equals(route.getId())) {
            mSystemController.setVolume(volume);
        }
    }

    public Context shirazContext() {
        return mContext;
    }

    public abstract static class RouteCallback {
        public RouteCallback() {}

        @Deprecated
        public void onRoutesAdded(List<MediaRoute2Info> routes) {}

        @Deprecated
        public void onRoutesRemoved(List<MediaRoute2Info> routes) {}

        @Deprecated
        public void onRoutesChanged(List<MediaRoute2Info> routes) {}

        public void onRoutesUpdated(List<MediaRoute2Info> routes) {}
    }

    public abstract static class TransferCallback {
        public TransferCallback() {}

        public void onTransfer(
                RoutingController oldController, RoutingController newController) {}

        public void onTransferFailure(MediaRoute2Info requestedRoute) {}

        public void onStop(RoutingController controller) {}
    }

    public abstract static class ControllerCallback {
        public ControllerCallback() {}

        public void onControllerUpdated(RoutingController controller) {}
    }

    public interface OnGetControllerHintsListener {
        Bundle onGetControllerHints(MediaRoute2Info route);
    }

    public static final class ScanToken {
        ScanToken() {}
    }

    public static final class ScanRequest {
        private final boolean mIsScreenOffScan;

        ScanRequest(boolean isScreenOffScan) {
            mIsScreenOffScan = isScreenOffScan;
        }

        public boolean isScreenOffScan() {
            return mIsScreenOffScan;
        }

        public static final class Builder {
            private boolean mIsScreenOffScan;

            public Builder() {}

            public Builder setScreenOffScan(boolean isScreenOffScan) {
                mIsScreenOffScan = isScreenOffScan;
                return this;
            }

            public ScanRequest build() {
                return new ScanRequest(mIsScreenOffScan);
            }
        }
    }

    public class RoutingController {
        private RoutingSessionInfo mSessionInfo;
        private boolean mReleased;

        RoutingController(RoutingSessionInfo sessionInfo) {
            mSessionInfo = Objects.requireNonNull(sessionInfo, "sessionInfo");
        }

        public String getId() {
            return mSessionInfo.getId();
        }

        public Bundle getControlHints() {
            return mSessionInfo.getControlHints();
        }

        public List<MediaRoute2Info> getSelectedRoutes() {
            return resolveRoutes(mSessionInfo.getSelectedRoutes());
        }

        public List<MediaRoute2Info> getSelectableRoutes() {
            return resolveRoutes(mSessionInfo.getSelectableRoutes());
        }

        public List<MediaRoute2Info> getDeselectableRoutes() {
            return resolveRoutes(mSessionInfo.getDeselectableRoutes());
        }

        public List<MediaRoute2Info> getTransferableRoutes() {
            return resolveRoutes(mSessionInfo.getTransferableRoutes());
        }

        public boolean wasTransferInitiatedBySelf() {
            return Objects.equals(
                    mClientPackageName, mSessionInfo.getTransferInitiatorPackageName());
        }

        public RoutingSessionInfo getRoutingSessionInfo() {
            return mSessionInfo;
        }

        public int getVolumeHandling() {
            return mSessionInfo.getVolumeHandling();
        }

        public int getVolumeMax() {
            return mSessionInfo.getVolumeMax();
        }

        public int getVolume() {
            return mSessionInfo.getVolume();
        }

        public boolean isReleased() {
            return mReleased;
        }

        public void selectRoute(MediaRoute2Info route) {
            Objects.requireNonNull(route, "route");
        }

        public void deselectRoute(MediaRoute2Info route) {
            Objects.requireNonNull(route, "route");
        }

        public void setVolume(int volume) {
            int clamped = Math.max(0, Math.min(getVolumeMax(), volume));
            mSessionInfo = new RoutingSessionInfo.Builder(mSessionInfo)
                    .setVolume(clamped)
                    .build();
            AudioManager.shirazForProcess().setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0);
        }

        public void release() {
            if (!SYSTEM_SESSION_ID.equals(mSessionInfo.getId())) {
                mReleased = true;
            }
        }

        @Override
        public String toString() {
            return "RoutingController{id=" + getId() + ", released=" + mReleased + "}";
        }

        private List<MediaRoute2Info> resolveRoutes(List<String> routeIds) {
            List<MediaRoute2Info> list = new ArrayList<MediaRoute2Info>();
            synchronized (mRoutes) {
                for (int i = 0; i < routeIds.size(); i++) {
                    MediaRoute2Info info = mRoutes.get(routeIds.get(i));
                    if (info != null) {
                        list.add(info);
                    }
                }
            }
            return Collections.unmodifiableList(list);
        }
    }
}
