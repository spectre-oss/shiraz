// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.os.Bundle;
import android.os.Handler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Remote handle to a {@link Conference} managed by another {@link ConnectionService}. */
public final class RemoteConference {
    public abstract static class Callback {
        public Callback() {
        }

        public void onStateChanged(
                RemoteConference conference, int oldState, int newState) {
        }

        public void onDisconnected(
                RemoteConference conference, DisconnectCause disconnectCause) {
        }

        public void onConnectionAdded(
                RemoteConference conference, RemoteConnection connection) {
        }

        public void onConnectionRemoved(
                RemoteConference conference, RemoteConnection connection) {
        }

        public void onConnectionCapabilitiesChanged(
                RemoteConference conference, int connectionCapabilities) {
        }

        public void onConnectionPropertiesChanged(
                RemoteConference conference, int connectionProperties) {
        }

        public void onConferenceableConnectionsChanged(
                RemoteConference conference,
                List<RemoteConnection> conferenceableConnections) {
        }

        public void onDestroyed(RemoteConference conference) {
        }

        public void onExtrasChanged(RemoteConference conference, Bundle extras) {
        }
    }

    private static final class CallbackRecord {
        final Callback callback;
        final Handler handler;

        CallbackRecord(Callback callback, Handler handler) {
            this.callback = callback;
            this.handler = handler;
        }
    }

    private interface CallbackAction {
        void run(Callback cb);
    }

    private final Conference mConference;
    private final List<CallbackRecord> mCallbackRecords =
            new CopyOnWriteArrayList<CallbackRecord>();
    private final List<RemoteConnection> mChildConnections =
            new CopyOnWriteArrayList<RemoteConnection>();
    private final List<RemoteConnection> mConferenceableConnections =
            new ArrayList<RemoteConnection>();
    private final Map<Connection, RemoteConnection> mConnectionMap =
            new IdentityHashMap<Connection, RemoteConnection>();

    RemoteConference(Conference conference) {
        this.mConference = conference;
        if (conference != null) {
            for (Connection c : conference.getConnections()) {
                RemoteConnection rc = new RemoteConnection(c);
                rc.setConferenceInternal(this);
                mConnectionMap.put(c, rc);
                mChildConnections.add(rc);
            }
            conference.addListener(
                    new Conference.Listener() {
                        @Override
                        public void onStateChanged(
                                Conference conf, final int oldState, final int newState) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onStateChanged(
                                                    RemoteConference.this, oldState, newState);
                                        }
                                    });
                        }

                        @Override
                        public void onDisconnected(
                                Conference conf, final DisconnectCause disconnectCause) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onDisconnected(
                                                    RemoteConference.this, disconnectCause);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionAdded(Conference conf, Connection connection) {
                            RemoteConnection rc = mConnectionMap.get(connection);
                            if (rc == null) {
                                rc = new RemoteConnection(connection);
                                mConnectionMap.put(connection, rc);
                            }
                            rc.setConferenceInternal(RemoteConference.this);
                            mChildConnections.add(rc);
                            final RemoteConnection added = rc;
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onConnectionAdded(RemoteConference.this, added);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionRemoved(
                                Conference conf, Connection connection) {
                            final RemoteConnection removed = mConnectionMap.remove(connection);
                            if (removed != null) {
                                removed.setConferenceInternal(null);
                                mChildConnections.remove(removed);
                                dispatch(
                                        new CallbackAction() {
                                            @Override
                                            public void run(Callback cb) {
                                                cb.onConnectionRemoved(
                                                        RemoteConference.this, removed);
                                            }
                                        });
                            }
                        }

                        @Override
                        public void onConferenceableConnectionsChanged(
                                Conference conf, List<Connection> conferenceableConnections) {
                        }

                        @Override
                        public void onDestroyed(Conference conf) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onDestroyed(RemoteConference.this);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionCapabilitiesChanged(
                                Conference conf, final int connectionCapabilities) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onConnectionCapabilitiesChanged(
                                                    RemoteConference.this,
                                                    connectionCapabilities);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionPropertiesChanged(
                                Conference conf, final int connectionProperties) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onConnectionPropertiesChanged(
                                                    RemoteConference.this,
                                                    connectionProperties);
                                        }
                                    });
                        }

                        @Override
                        public void onVideoStateChanged(Conference c, int videoState) {
                        }

                        @Override
                        public void onVideoProviderChanged(
                                Conference c, Connection.VideoProvider videoProvider) {
                        }

                        @Override
                        public void onStatusHintsChanged(
                                Conference conf, StatusHints statusHints) {
                        }

                        @Override
                        public void onExtrasChanged(Conference c, final Bundle extras) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onExtrasChanged(RemoteConference.this, extras);
                                        }
                                    });
                        }

                        @Override
                        public void onExtrasRemoved(Conference c, List<String> keys) {
                            final Bundle current = c.getExtras();
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onExtrasChanged(RemoteConference.this, current);
                                        }
                                    });
                        }

                        @Override
                        public void onConferenceEvent(
                                Conference c, String event, Bundle extras) {
                        }
                    });
        }
    }

    private void dispatch(final CallbackAction action) {
        for (final CallbackRecord rec : mCallbackRecords) {
            if (rec.handler != null) {
                rec.handler.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                action.run(rec.callback);
                            }
                        });
            } else {
                action.run(rec.callback);
            }
        }
    }

    public List<RemoteConnection> getConnections() {
        return Collections.unmodifiableList(mChildConnections);
    }

    public int getState() {
        return mConference == null ? Connection.STATE_DISCONNECTED : mConference.getState();
    }

    public int getConnectionCapabilities() {
        return mConference == null ? 0 : mConference.getConnectionCapabilities();
    }

    public int getConnectionProperties() {
        return mConference == null ? 0 : mConference.getConnectionProperties();
    }

    public Bundle getExtras() {
        return mConference == null ? null : mConference.getExtras();
    }

    public void disconnect() {
        if (mConference != null) {
            mConference.onDisconnect();
        }
    }

    public void separate(RemoteConnection connection) {
        if (mConference != null
                && connection != null
                && mChildConnections.contains(connection)) {
            mConference.onSeparate(connection.getConnectionInternal());
        }
    }

    public void merge() {
        if (mConference != null) {
            mConference.onMerge();
        }
    }

    public void swap() {
        if (mConference != null) {
            mConference.onSwap();
        }
    }

    public void hold() {
        if (mConference != null) {
            mConference.onHold();
        }
    }

    public void unhold() {
        if (mConference != null) {
            mConference.onUnhold();
        }
    }

    public DisconnectCause getDisconnectCause() {
        return mConference == null ? null : mConference.getDisconnectCause();
    }

    public void playDtmfTone(char digit) {
        if (mConference != null) {
            mConference.onPlayDtmfTone(digit);
        }
    }

    public void stopDtmfTone() {
        if (mConference != null) {
            mConference.onStopDtmfTone();
        }
    }

    public void setCallAudioState(CallAudioState state) {
        if (mConference != null) {
            mConference.setCallAudioStateInternal(state);
        }
    }

    public List<RemoteConnection> getConferenceableConnections() {
        return Collections.unmodifiableList(
                new ArrayList<RemoteConnection>(mConferenceableConnections));
    }

    public void registerCallback(Callback callback) {
        registerCallback(callback, null);
    }

    public void registerCallback(Callback callback, Handler handler) {
        if (callback != null) {
            unregisterCallback(callback);
            mCallbackRecords.add(new CallbackRecord(callback, handler));
        }
    }

    public void unregisterCallback(Callback callback) {
        if (callback != null) {
            for (CallbackRecord rec : mCallbackRecords) {
                if (rec.callback == callback) {
                    mCallbackRecords.remove(rec);
                }
            }
        }
    }
}
