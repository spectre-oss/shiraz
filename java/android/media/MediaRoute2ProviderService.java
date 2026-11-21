// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class MediaRoute2ProviderService extends Service {
    public static final String SERVICE_INTERFACE = "android.media.MediaRoute2ProviderService";

    public static final long REQUEST_ID_NONE = 0L;

    public static final int REASON_UNKNOWN_ERROR = 0;
    public static final int REASON_REJECTED = 1;
    public static final int REASON_NETWORK_ERROR = 2;
    public static final int REASON_ROUTE_NOT_AVAILABLE = 3;
    public static final int REASON_INVALID_COMMAND = 4;

    private final IBinder mBinder = new Binder();
    private final Map<String, RoutingSessionInfo> mSessions =
            new LinkedHashMap<String, RoutingSessionInfo>();
    private final List<MediaRoute2Info> mRoutes = new ArrayList<MediaRoute2Info>();

    public MediaRoute2ProviderService() {}

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    public abstract void onSetRouteVolume(long requestId, String routeId, int volume);

    public abstract void onSetSessionVolume(long requestId, String sessionId, int volume);

    public final RoutingSessionInfo getSessionInfo(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        synchronized (mSessions) {
            return mSessions.get(sessionId);
        }
    }

    public final List<RoutingSessionInfo> getAllSessionInfo() {
        synchronized (mSessions) {
            return new ArrayList<RoutingSessionInfo>(mSessions.values());
        }
    }

    public final void notifySessionCreated(long requestId, RoutingSessionInfo sessionInfo) {
        Objects.requireNonNull(sessionInfo, "sessionInfo");
        synchronized (mSessions) {
            mSessions.put(sessionInfo.getId(), sessionInfo);
        }
    }

    public final void notifySessionUpdated(RoutingSessionInfo sessionInfo) {
        Objects.requireNonNull(sessionInfo, "sessionInfo");
        synchronized (mSessions) {
            if (mSessions.containsKey(sessionInfo.getId())) {
                mSessions.put(sessionInfo.getId(), sessionInfo);
            }
        }
    }

    public final void notifySessionReleased(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        synchronized (mSessions) {
            mSessions.remove(sessionId);
        }
    }

    public final void notifyRequestFailed(long requestId, int reason) {}

    public abstract void onCreateSession(
            long requestId, String packageName, String routeId, Bundle sessionHints);

    public abstract void onReleaseSession(long requestId, String sessionId);

    public abstract void onSelectRoute(long requestId, String sessionId, String routeId);

    public abstract void onDeselectRoute(long requestId, String sessionId, String routeId);

    public abstract void onTransferToRoute(long requestId, String sessionId, String routeId);

    public void onDiscoveryPreferenceChanged(RouteDiscoveryPreference preference) {}

    public final void notifyRoutes(Collection<MediaRoute2Info> routes) {
        Objects.requireNonNull(routes, "routes");
        synchronized (mRoutes) {
            mRoutes.clear();
            mRoutes.addAll(routes);
        }
    }
}
