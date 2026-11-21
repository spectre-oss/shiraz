// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.app.PendingIntent;
import android.content.Context;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Process;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MediaSession2 implements AutoCloseable {
    private static final String SESSION_ID_EXTRA = "shiraz.media.session2.id";
    private static final Map<Session2Token, MediaSession2> ACTIVE_SESSIONS =
            new LinkedHashMap<Session2Token, MediaSession2>();

    private final Context mContext;
    private final String mId;
    private final PendingIntent mSessionActivity;
    private final Executor mCallbackExecutor;
    private final SessionCallback mCallback;
    private final Session2Token mToken;
    private final Map<ControllerInfo, MediaController2> mConnectedControllers =
            new LinkedHashMap<ControllerInfo, MediaController2>();
    private volatile boolean mPlaybackActive;
    private volatile boolean mClosed;

    MediaSession2(
            Context context,
            String id,
            PendingIntent sessionActivity,
            Bundle tokenExtras,
            Executor callbackExecutor,
            SessionCallback callback) {
        mContext = context;
        mId = id;
        mSessionActivity = sessionActivity;
        mCallbackExecutor = callbackExecutor;
        mCallback = callback;
        Bundle extras = tokenExtras != null ? new Bundle(tokenExtras) : new Bundle();
        extras.putString(SESSION_ID_EXTRA, id);
        String pkg = context != null && context.getPackageName() != null
                ? context.getPackageName() : "org.shiraz.app";
        mToken = new Session2Token(
                Process.myUid(), Session2Token.TYPE_SESSION, pkg, null, extras);
        synchronized (ACTIVE_SESSIONS) {
            for (MediaSession2 existing : ACTIVE_SESSIONS.values()) {
                if (Objects.equals(existing.mId, id)
                        && Objects.equals(existing.mToken.getPackageName(), pkg)) {
                    throw new IllegalStateException("Session ID must be unique per package: " + id);
                }
            }
            ACTIVE_SESSIONS.put(mToken, this);
        }
    }

    static List<Session2Token> shirazGetActiveTokens() {
        synchronized (ACTIVE_SESSIONS) {
            return Collections.unmodifiableList(new ArrayList<Session2Token>(ACTIVE_SESSIONS.keySet()));
        }
    }

    static MediaSession2 shirazFindSession(Session2Token token) {
        synchronized (ACTIVE_SESSIONS) {
            return ACTIVE_SESSIONS.get(token);
        }
    }

    Session2CommandGroup shirazConnectController(
            MediaController2 controller, ControllerInfo info) {
        if (mClosed) return null;
        Session2CommandGroup allowed = mCallback.onConnect(this, info);
        if (allowed == null) {
            return null;
        }
        synchronized (mConnectedControllers) {
            mConnectedControllers.put(info, controller);
        }
        mCallback.onPostConnect(this, info);
        return allowed;
    }

    void shirazDisconnectController(final ControllerInfo info) {
        MediaController2 removed;
        synchronized (mConnectedControllers) {
            removed = mConnectedControllers.remove(info);
        }
        if (removed != null && !mClosed) {
            mCallbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mCallback.onDisconnected(MediaSession2.this, info);
                }
            });
        }
    }

    Session2Command.Result shirazOnControllerCommand(
            ControllerInfo info, Session2Command command, Bundle args) {
        if (mClosed) {
            return new Session2Command.Result(
                    Session2Command.Result.RESULT_ERROR_UNKNOWN_ERROR, null);
        }
        return mCallback.onSessionCommand(this, info, command, args);
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        synchronized (ACTIVE_SESSIONS) {
            ACTIVE_SESSIONS.remove(mToken);
        }
        List<Map.Entry<ControllerInfo, MediaController2>> entries;
        synchronized (mConnectedControllers) {
            entries = new ArrayList<Map.Entry<ControllerInfo, MediaController2>>(
                    mConnectedControllers.entrySet());
            mConnectedControllers.clear();
        }
        for (int i = 0; i < entries.size(); i++) {
            final ControllerInfo info = entries.get(i).getKey();
            final MediaController2 controller = entries.get(i).getValue();
            controller.shirazNotifyDisconnected();
            mCallbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mCallback.onDisconnected(MediaSession2.this, info);
                }
            });
        }
    }

    public String getId() {
        return mId;
    }

    public Session2Token getToken() {
        return mToken;
    }

    public void broadcastSessionCommand(Session2Command command, Bundle args) {
        Objects.requireNonNull(command, "command");
        List<MediaController2> controllers;
        synchronized (mConnectedControllers) {
            controllers = new ArrayList<MediaController2>(mConnectedControllers.values());
        }
        for (int i = 0; i < controllers.size(); i++) {
            controllers.get(i).shirazDeliverCommand(command, args);
        }
    }

    public Object sendSessionCommand(
            final ControllerInfo controller,
            final Session2Command command,
            final Bundle args) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(command, "command");
        final Object token = new Object();
        final MediaController2 target;
        synchronized (mConnectedControllers) {
            target = mConnectedControllers.get(controller);
        }
        if (target != null) {
            final Session2Command.Result result = target.shirazDeliverCommand(command, args);
            mCallbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mCallback.onCommandResult(MediaSession2.this, controller, token, command, result);
                }
            });
        }
        return token;
    }

    public void cancelSessionCommand(ControllerInfo controller, Object token) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(token, "token");
    }

    public void setPlaybackActive(boolean playbackActive) {
        if (mPlaybackActive == playbackActive) return;
        mPlaybackActive = playbackActive;
        List<MediaController2> controllers;
        synchronized (mConnectedControllers) {
            controllers = new ArrayList<MediaController2>(mConnectedControllers.values());
        }
        for (int i = 0; i < controllers.size(); i++) {
            controllers.get(i).shirazNotifyPlaybackActiveChanged(playbackActive);
        }
    }

    public boolean isPlaybackActive() {
        return mPlaybackActive;
    }

    public List<ControllerInfo> getConnectedControllers() {
        synchronized (mConnectedControllers) {
            return new ArrayList<ControllerInfo>(mConnectedControllers.keySet());
        }
    }

    public PendingIntent shirazSessionActivity() {
        return mSessionActivity;
    }

    public Context shirazContext() {
        return mContext;
    }

    public static final class Builder {
        private final Context mContext;
        private String mId = "";
        private PendingIntent mSessionActivity;
        private Executor mCallbackExecutor;
        private SessionCallback mCallback;
        private Bundle mExtras;

        public Builder(Context context) {
            mContext = Objects.requireNonNull(context, "context");
        }

        public Builder setSessionActivity(PendingIntent pi) {
            mSessionActivity = pi;
            return this;
        }

        public Builder setId(String id) {
            mId = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder setSessionCallback(Executor executor, SessionCallback callback) {
            mCallbackExecutor = Objects.requireNonNull(executor, "executor");
            mCallback = Objects.requireNonNull(callback, "callback");
            return this;
        }

        public Builder setExtras(Bundle extras) {
            mExtras = extras != null ? new Bundle(extras) : null;
            return this;
        }

        public MediaSession2 build() {
            if (mCallbackExecutor == null) {
                mCallbackExecutor = new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                };
            }
            if (mCallback == null) {
                mCallback = new SessionCallback() {};
            }
            return new MediaSession2(
                    mContext, mId, mSessionActivity, mExtras, mCallbackExecutor, mCallback);
        }
    }

    public static final class ControllerInfo {
        private final MediaSessionManager.RemoteUserInfo mRemoteUserInfo;
        private final Bundle mConnectionHints;
        private final Object mControllerKey;

        ControllerInfo(
                MediaSessionManager.RemoteUserInfo remoteUserInfo,
                Bundle connectionHints,
                Object controllerKey) {
            mRemoteUserInfo = Objects.requireNonNull(remoteUserInfo, "remoteUserInfo");
            mConnectionHints = connectionHints != null ? new Bundle(connectionHints) : Bundle.EMPTY;
            mControllerKey = controllerKey;
        }

        public MediaSessionManager.RemoteUserInfo getRemoteUserInfo() {
            return mRemoteUserInfo;
        }

        public String getPackageName() {
            return mRemoteUserInfo.getPackageName();
        }

        public int getUid() {
            return mRemoteUserInfo.getUid();
        }

        public Bundle getConnectionHints() {
            return new Bundle(mConnectionHints);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mControllerKey, mRemoteUserInfo);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ControllerInfo)) return false;
            ControllerInfo other = (ControllerInfo) obj;
            if (mControllerKey != null || other.mControllerKey != null) {
                return Objects.equals(mControllerKey, other.mControllerKey);
            }
            return Objects.equals(mRemoteUserInfo, other.mRemoteUserInfo);
        }

        @Override
        public String toString() {
            return "ControllerInfo{pkg=" + getPackageName() + ", uid=" + getUid() + "}";
        }
    }

    public abstract static class SessionCallback {
        public SessionCallback() {}

        public Session2CommandGroup onConnect(
                MediaSession2 session, ControllerInfo controller) {
            return null;
        }

        public void onPostConnect(MediaSession2 session, ControllerInfo controller) {}

        public void onDisconnected(MediaSession2 session, ControllerInfo controller) {}

        public Session2Command.Result onSessionCommand(
                MediaSession2 session,
                ControllerInfo controller,
                Session2Command command,
                Bundle args) {
            return new Session2Command.Result(Session2Command.Result.RESULT_INFO_SKIPPED, null);
        }

        public void onCommandResult(
                MediaSession2 session,
                ControllerInfo controller,
                Object token,
                Session2Command command,
                Session2Command.Result result) {}
    }
}
