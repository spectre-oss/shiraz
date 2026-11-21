// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.Context;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.Process;
import java.util.Objects;
import java.util.concurrent.Executor;

public class MediaController2 implements AutoCloseable {
    private final Executor mCallbackExecutor;
    private final ControllerCallback mCallback;
    private final MediaSession2.ControllerInfo mControllerInfo;
    private MediaSession2 mSession;
    private Session2Token mConnectedToken;
    private volatile boolean mPlaybackActive;
    private volatile boolean mClosed;

    MediaController2(
            Context context,
            Session2Token token,
            Bundle connectionHints,
            Executor executor,
            ControllerCallback callback) {
        mCallbackExecutor = executor;
        mCallback = callback;
        String pkg = context != null && context.getPackageName() != null
                ? context.getPackageName() : "org.shiraz.app";
        MediaSessionManager.RemoteUserInfo userInfo =
                new MediaSessionManager.RemoteUserInfo(pkg, Process.myPid(), Process.myUid());
        mControllerInfo = new MediaSession2.ControllerInfo(userInfo, connectionHints, this);

        MediaSession2 session = MediaSession2.shirazFindSession(token);
        if (session != null) {
            final Session2CommandGroup commands =
                    session.shirazConnectController(this, mControllerInfo);
            if (commands != null) {
                mSession = session;
                mConnectedToken = session.getToken();
                mPlaybackActive = session.isPlaybackActive();
                mCallbackExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        mCallback.onConnected(MediaController2.this, commands);
                    }
                });
                return;
            }
        }
        mCallbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mCallback.onDisconnected(MediaController2.this);
            }
        });
    }

    void shirazNotifyDisconnected() {
        if (mClosed) return;
        mClosed = true;
        mConnectedToken = null;
        mSession = null;
        mCallbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mCallback.onDisconnected(MediaController2.this);
            }
        });
    }

    void shirazNotifyPlaybackActiveChanged(final boolean playbackActive) {
        if (mClosed) return;
        mPlaybackActive = playbackActive;
        mCallbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mCallback.onPlaybackActiveChanged(MediaController2.this, playbackActive);
            }
        });
    }

    Session2Command.Result shirazDeliverCommand(
            final Session2Command command, final Bundle args) {
        if (mClosed) {
            return new Session2Command.Result(
                    Session2Command.Result.RESULT_ERROR_UNKNOWN_ERROR, null);
        }
        return mCallback.onSessionCommand(
                this, command, args != null ? new Bundle(args) : null);
    }

    @Override
    public void close() {
        if (mClosed) return;
        mClosed = true;
        MediaSession2 session = mSession;
        mSession = null;
        mConnectedToken = null;
        if (session != null) {
            session.shirazDisconnectController(mControllerInfo);
        }
        mCallbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mCallback.onDisconnected(MediaController2.this);
            }
        });
    }

    public Session2Token getConnectedToken() {
        return mConnectedToken;
    }

    public boolean isPlaybackActive() {
        return mPlaybackActive;
    }

    public Object sendSessionCommand(final Session2Command command, final Bundle args) {
        Objects.requireNonNull(command, "command");
        final Object token = new Object();
        final MediaSession2 session = mSession;
        if (session != null && !mClosed) {
            final Session2Command.Result result = session.shirazOnControllerCommand(
                    mControllerInfo, command, args != null ? new Bundle(args) : null);
            final Session2Command.Result safeResult = result != null
                    ? result
                    : new Session2Command.Result(Session2Command.Result.RESULT_INFO_SKIPPED, null);
            mCallbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    mCallback.onCommandResult(MediaController2.this, token, command, safeResult);
                }
            });
        }
        return token;
    }

    public void cancelSessionCommand(Object token) {
        Objects.requireNonNull(token, "token");
    }

    public static final class Builder {
        private final Context mContext;
        private final Session2Token mToken;
        private Bundle mConnectionHints;
        private Executor mCallbackExecutor;
        private ControllerCallback mCallback;

        public Builder(Context context, Session2Token token) {
            mContext = Objects.requireNonNull(context, "context");
            mToken = Objects.requireNonNull(token, "token");
        }

        public Builder setConnectionHints(Bundle connectionHints) {
            mConnectionHints = connectionHints != null ? new Bundle(connectionHints) : null;
            return this;
        }

        public Builder setControllerCallback(Executor executor, ControllerCallback callback) {
            mCallbackExecutor = Objects.requireNonNull(executor, "executor");
            mCallback = Objects.requireNonNull(callback, "callback");
            return this;
        }

        public MediaController2 build() {
            Executor exec = mCallbackExecutor;
            if (exec == null) {
                exec = new Executor() {
                    @Override
                    public void execute(Runnable command) {
                        command.run();
                    }
                };
            }
            ControllerCallback cb = mCallback != null ? mCallback : new ControllerCallback() {};
            return new MediaController2(mContext, mToken, mConnectionHints, exec, cb);
        }
    }

    public abstract static class ControllerCallback {
        public ControllerCallback() {}

        public void onConnected(
                MediaController2 controller, Session2CommandGroup allowedCommands) {}

        public void onDisconnected(MediaController2 controller) {}

        public void onPlaybackActiveChanged(
                MediaController2 controller, boolean playbackActive) {}

        public Session2Command.Result onSessionCommand(
                MediaController2 controller, Session2Command command, Bundle args) {
            return new Session2Command.Result(Session2Command.Result.RESULT_INFO_SKIPPED, null);
        }

        public void onCommandResult(
                MediaController2 controller,
                Object token,
                Session2Command command,
                Session2Command.Result result) {}
    }
}
