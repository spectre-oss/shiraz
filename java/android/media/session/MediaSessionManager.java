// SPDX-License-Identifier: GPL-3.0-only
package android.media.session;

import android.content.ComponentName;
import android.content.Context;
import android.media.Session2Token;
import android.os.Handler;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Provides support for interacting with {@link MediaSession}s that applications have published.
 */
public final class MediaSessionManager {
    public interface OnActiveSessionsChangedListener {
        void onActiveSessionsChanged(List<MediaController> controllers);
    }

    public interface OnSession2TokensChangedListener {
        void onSession2TokensChanged(List<Session2Token> tokens);
    }

    public interface OnMediaKeyEventSessionChangedListener {
        void onMediaKeyEventSessionChanged(String packageName, MediaSession.Token sessionToken);
    }

    public static final class RemoteUserInfo {
        private final String mPackageName;
        private final int mPid;
        private final int mUid;

        public RemoteUserInfo(String packageName, int pid, int uid) {
            if (packageName == null) {
                throw new NullPointerException("packageName shouldn't be null");
            }
            if (TextUtils.isEmpty(packageName)) {
                throw new IllegalArgumentException("packageName shouldn't be empty");
            }
            mPackageName = packageName;
            mPid = pid;
            mUid = uid;
        }

        public String getPackageName() {
            return mPackageName;
        }

        public int getPid() {
            return mPid;
        }

        public int getUid() {
            return mUid;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof RemoteUserInfo)) return false;
            RemoteUserInfo other = (RemoteUserInfo) obj;
            return Objects.equals(mPackageName, other.mPackageName)
                    && mPid == other.mPid
                    && mUid == other.mUid;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mPid, mUid);
        }
    }

    private final Context mContext;
    private final Set<Session2Token> mSession2Tokens = new LinkedHashSet<Session2Token>();
    private final Map<OnActiveSessionsChangedListener, Handler> mActiveListeners =
            new LinkedHashMap<OnActiveSessionsChangedListener, Handler>();
    private final Map<OnSession2TokensChangedListener, Handler> mSession2Listeners =
            new LinkedHashMap<OnSession2TokensChangedListener, Handler>();
    private final Map<OnMediaKeyEventSessionChangedListener, Executor> mKeyListeners =
            new LinkedHashMap<OnMediaKeyEventSessionChangedListener, Executor>();

    public MediaSessionManager() {
        mContext = null;
    }

    public MediaSessionManager(Context context) {
        mContext = context;
    }

    public void notifySession2Created(Session2Token token) {
        Objects.requireNonNull(token, "token shouldn't be null");
        final List<Session2Token> snapshot;
        final Map<OnSession2TokensChangedListener, Handler> listeners;
        synchronized (this) {
            mSession2Tokens.add(token);
            snapshot = Collections.unmodifiableList(new ArrayList<Session2Token>(mSession2Tokens));
            listeners = new LinkedHashMap<OnSession2TokensChangedListener, Handler>(mSession2Listeners);
        }
        for (final Map.Entry<OnSession2TokensChangedListener, Handler> entry : listeners.entrySet()) {
            Handler h = entry.getValue();
            final OnSession2TokensChangedListener l = entry.getKey();
            if (h != null) {
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        l.onSession2TokensChanged(snapshot);
                    }
                });
            } else {
                l.onSession2TokensChanged(snapshot);
            }
        }
    }

    public List<MediaController> getActiveSessions(ComponentName notificationListener) {
        return Collections.emptyList();
    }

    public MediaSession.Token getMediaKeyEventSession() {
        return null;
    }

    public String getMediaKeyEventSessionPackageName() {
        return "";
    }

    public List<Session2Token> getSession2Tokens() {
        synchronized (this) {
            return new ArrayList<Session2Token>(mSession2Tokens);
        }
    }

    public void addOnActiveSessionsChangedListener(
            OnActiveSessionsChangedListener sessionListener,
            ComponentName notificationListener) {
        addOnActiveSessionsChangedListener(sessionListener, notificationListener, null);
    }

    public void addOnActiveSessionsChangedListener(
            OnActiveSessionsChangedListener sessionListener,
            ComponentName notificationListener,
            Handler handler) {
        Objects.requireNonNull(sessionListener, "sessionListener may not be null");
        synchronized (this) {
            mActiveListeners.put(sessionListener, handler);
        }
    }

    public void removeOnActiveSessionsChangedListener(
            OnActiveSessionsChangedListener sessionListener) {
        Objects.requireNonNull(sessionListener, "sessionListener may not be null");
        synchronized (this) {
            mActiveListeners.remove(sessionListener);
        }
    }

    public void addOnSession2TokensChangedListener(OnSession2TokensChangedListener listener) {
        addOnSession2TokensChangedListener(listener, null);
    }

    public void addOnSession2TokensChangedListener(
            OnSession2TokensChangedListener listener, Handler handler) {
        Objects.requireNonNull(listener, "listener shouldn't be null");
        synchronized (this) {
            mSession2Listeners.put(listener, handler);
        }
    }

    public void removeOnSession2TokensChangedListener(OnSession2TokensChangedListener listener) {
        Objects.requireNonNull(listener, "listener shouldn't be null");
        synchronized (this) {
            mSession2Listeners.remove(listener);
        }
    }

    public boolean isTrustedForMediaControl(RemoteUserInfo userInfo) {
        Objects.requireNonNull(userInfo, "userInfo may not be null");
        return userInfo.getUid() == android.os.Process.myUid();
    }

    public void addOnMediaKeyEventSessionChangedListener(
            Executor executor, OnMediaKeyEventSessionChangedListener listener) {
        Objects.requireNonNull(executor, "executor shouldn't be null");
        Objects.requireNonNull(listener, "listener shouldn't be null");
        synchronized (this) {
            mKeyListeners.put(listener, executor);
        }
    }

    public void removeOnMediaKeyEventSessionChangedListener(
            OnMediaKeyEventSessionChangedListener listener) {
        Objects.requireNonNull(listener, "listener shouldn't be null");
        synchronized (this) {
            mKeyListeners.remove(listener);
        }
    }
}
