// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/** Remote handle to a {@link Connection} managed by another {@link ConnectionService}. */
public final class RemoteConnection {
    public abstract static class Callback {
        public Callback() {
        }

        public void onStateChanged(RemoteConnection connection, int state) {
        }

        public void onDisconnected(
                RemoteConnection connection, DisconnectCause disconnectCause) {
        }

        public void onRingbackRequested(RemoteConnection connection, boolean ringback) {
        }

        public void onConnectionCapabilitiesChanged(
                RemoteConnection connection, int connectionCapabilities) {
        }

        public void onConnectionPropertiesChanged(
                RemoteConnection connection, int connectionProperties) {
        }

        public void onPostDialWait(
                RemoteConnection connection, String remainingPostDialSequence) {
        }

        public void onPostDialChar(RemoteConnection connection, char nextChar) {
        }

        public void onVoipAudioChanged(RemoteConnection connection, boolean isVoip) {
        }

        public void onStatusHintsChanged(
                RemoteConnection connection, StatusHints statusHints) {
        }

        public void onAddressChanged(
                RemoteConnection connection, Uri address, int presentation) {
        }

        public void onCallerDisplayNameChanged(
                RemoteConnection connection, String callerDisplayName, int presentation) {
        }

        public void onVideoStateChanged(RemoteConnection connection, int videoState) {
        }

        public void onDestroyed(RemoteConnection connection) {
        }

        public void onConferenceableConnectionsChanged(
                RemoteConnection connection,
                List<RemoteConnection> conferenceableConnections) {
        }

        public void onVideoProviderChanged(
                RemoteConnection connection, VideoProvider videoProvider) {
        }

        public void onConferenceChanged(
                RemoteConnection connection, RemoteConference conference) {
        }

        public void onExtrasChanged(RemoteConnection connection, Bundle extras) {
        }

        public void onConnectionEvent(
                RemoteConnection connection, String event, Bundle extras) {
        }
    }

    public static class VideoProvider {
        public abstract static class Callback {
            public Callback() {
            }

            public void onSessionModifyRequestReceived(
                    VideoProvider videoProvider, VideoProfile videoProfile) {
            }

            public void onSessionModifyResponseReceived(
                    VideoProvider videoProvider,
                    int status,
                    VideoProfile requestedProfile,
                    VideoProfile responseProfile) {
            }

            public void onCallSessionEvent(VideoProvider videoProvider, int event) {
            }

            public void onPeerDimensionsChanged(
                    VideoProvider videoProvider, int width, int height) {
            }

            public void onCallDataUsageChanged(VideoProvider videoProvider, long dataUsage) {
            }

            public void onCameraCapabilitiesChanged(
                    VideoProvider videoProvider,
                    VideoProfile.CameraCapabilities cameraCapabilities) {
            }

            public void onVideoQualityChanged(VideoProvider videoProvider, int videoQuality) {
            }
        }

        private final Connection.VideoProvider mDelegate;
        private final Set<Callback> mCallbacks = new CopyOnWriteArraySet<Callback>();

        VideoProvider(Connection.VideoProvider delegate) {
            this.mDelegate = delegate;
            if (delegate != null) {
                delegate.addListener(
                        new Connection.VideoProvider.Listener() {
                            @Override
                            public void onReceiveSessionModifyRequest(
                                    VideoProfile videoProfile) {
                                for (Callback cb : mCallbacks) {
                                    cb.onSessionModifyRequestReceived(
                                            VideoProvider.this, videoProfile);
                                }
                            }

                            @Override
                            public void onReceiveSessionModifyResponse(
                                    int status,
                                    VideoProfile requestedProfile,
                                    VideoProfile responseProfile) {
                                for (Callback cb : mCallbacks) {
                                    cb.onSessionModifyResponseReceived(
                                            VideoProvider.this,
                                            status,
                                            requestedProfile,
                                            responseProfile);
                                }
                            }

                            @Override
                            public void onHandleCallSessionEvent(int event) {
                                for (Callback cb : mCallbacks) {
                                    cb.onCallSessionEvent(VideoProvider.this, event);
                                }
                            }

                            @Override
                            public void onChangePeerDimensions(int width, int height) {
                                for (Callback cb : mCallbacks) {
                                    cb.onPeerDimensionsChanged(
                                            VideoProvider.this, width, height);
                                }
                            }

                            @Override
                            public void onSetCallDataUsage(long dataUsage) {
                                for (Callback cb : mCallbacks) {
                                    cb.onCallDataUsageChanged(VideoProvider.this, dataUsage);
                                }
                            }

                            @Override
                            public void onChangeCameraCapabilities(
                                    VideoProfile.CameraCapabilities cameraCapabilities) {
                                for (Callback cb : mCallbacks) {
                                    cb.onCameraCapabilitiesChanged(
                                            VideoProvider.this, cameraCapabilities);
                                }
                            }

                            @Override
                            public void onChangeVideoQuality(int videoQuality) {
                                for (Callback cb : mCallbacks) {
                                    cb.onVideoQualityChanged(
                                            VideoProvider.this, videoQuality);
                                }
                            }
                        });
            }
        }

        public void registerCallback(Callback l) {
            if (l != null) {
                mCallbacks.add(l);
            }
        }

        public void unregisterCallback(Callback l) {
            if (l != null) {
                mCallbacks.remove(l);
            }
        }

        public void setCamera(String cameraId) {
            if (mDelegate != null) {
                mDelegate.onSetCamera(cameraId);
            }
        }

        public void setPreviewSurface(Surface surface) {
            if (mDelegate != null) {
                mDelegate.onSetPreviewSurface(surface);
            }
        }

        public void setDisplaySurface(Surface surface) {
            if (mDelegate != null) {
                mDelegate.onSetDisplaySurface(surface);
            }
        }

        public void setDeviceOrientation(int rotation) {
            if (mDelegate != null) {
                mDelegate.onSetDeviceOrientation(rotation);
            }
        }

        public void setZoom(float value) {
            if (mDelegate != null) {
                mDelegate.onSetZoom(value);
            }
        }

        public void sendSessionModifyRequest(
                VideoProfile fromProfile, VideoProfile toProfile) {
            if (mDelegate != null) {
                mDelegate.onSendSessionModifyRequest(fromProfile, toProfile);
            }
        }

        public void sendSessionModifyResponse(VideoProfile responseProfile) {
            if (mDelegate != null) {
                mDelegate.onSendSessionModifyResponse(responseProfile);
            }
        }

        public void requestCameraCapabilities() {
            if (mDelegate != null) {
                mDelegate.onRequestCameraCapabilities();
            }
        }

        public void requestCallDataUsage() {
            if (mDelegate != null) {
                mDelegate.onRequestConnectionDataUsage();
            }
        }

        public void setPauseImage(Uri uri) {
            if (mDelegate != null) {
                mDelegate.onSetPauseImage(uri);
            }
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

    private final Connection mConnection;
    private final List<CallbackRecord> mCallbackRecords =
            new CopyOnWriteArrayList<CallbackRecord>();
    private final List<RemoteConnection> mConferenceableConnections =
            new ArrayList<RemoteConnection>();
    private VideoProvider mVideoProvider;
    private RemoteConference mConference;

    RemoteConnection(Connection connection) {
        this.mConnection = connection;
        if (connection != null) {
            if (connection.getVideoProvider() != null) {
                mVideoProvider = new VideoProvider(connection.getVideoProvider());
            }
            connection.addConnectionListener(
                    new Connection.Listener() {
                        @Override
                        public void onStateChanged(Connection c, final int state) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onStateChanged(RemoteConnection.this, state);
                                        }
                                    });
                        }

                        @Override
                        public void onAddressChanged(
                                Connection c, final Uri newAddress, final int presentation) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onAddressChanged(
                                                    RemoteConnection.this,
                                                    newAddress,
                                                    presentation);
                                        }
                                    });
                        }

                        @Override
                        public void onCallerDisplayNameChanged(
                                Connection c,
                                final String callerDisplayName,
                                final int presentation) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onCallerDisplayNameChanged(
                                                    RemoteConnection.this,
                                                    callerDisplayName,
                                                    presentation);
                                        }
                                    });
                        }

                        @Override
                        public void onVideoStateChanged(Connection c, final int videoState) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onVideoStateChanged(
                                                    RemoteConnection.this, videoState);
                                        }
                                    });
                        }

                        @Override
                        public void onDisconnected(
                                Connection c, final DisconnectCause disconnectCause) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onDisconnected(
                                                    RemoteConnection.this, disconnectCause);
                                        }
                                    });
                        }

                        @Override
                        public void onPostDialWait(Connection c, final String remaining) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onPostDialWait(
                                                    RemoteConnection.this, remaining);
                                        }
                                    });
                        }

                        @Override
                        public void onPostDialChar(Connection c, final char nextChar) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onPostDialChar(
                                                    RemoteConnection.this, nextChar);
                                        }
                                    });
                        }

                        @Override
                        public void onRingbackRequested(
                                Connection c, final boolean ringback) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onRingbackRequested(
                                                    RemoteConnection.this, ringback);
                                        }
                                    });
                        }

                        @Override
                        public void onDestroyed(Connection c) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onDestroyed(RemoteConnection.this);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionCapabilitiesChanged(
                                Connection c, final int capabilities) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onConnectionCapabilitiesChanged(
                                                    RemoteConnection.this, capabilities);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionPropertiesChanged(
                                Connection c, final int properties) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onConnectionPropertiesChanged(
                                                    RemoteConnection.this, properties);
                                        }
                                    });
                        }

                        @Override
                        public void onVideoProviderChanged(
                                Connection c, Connection.VideoProvider vp) {
                            mVideoProvider = vp == null ? null : new VideoProvider(vp);
                            final VideoProvider wrapped = mVideoProvider;
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onVideoProviderChanged(
                                                    RemoteConnection.this, wrapped);
                                        }
                                    });
                        }

                        @Override
                        public void onAudioModeIsVoipChanged(
                                Connection c, final boolean isVoip) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onVoipAudioChanged(
                                                    RemoteConnection.this, isVoip);
                                        }
                                    });
                        }

                        @Override
                        public void onStatusHintsChanged(
                                Connection c, final StatusHints statusHints) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onStatusHintsChanged(
                                                    RemoteConnection.this, statusHints);
                                        }
                                    });
                        }

                        @Override
                        public void onConferenceablesChanged(
                                Connection c, List<Conferenceable> conferenceables) {
                        }

                        @Override
                        public void onConferenceChanged(Connection c, Conference conference) {
                        }

                        @Override
                        public void onExtrasChanged(Connection c, final Bundle extras) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onExtrasChanged(RemoteConnection.this, extras);
                                        }
                                    });
                        }

                        @Override
                        public void onExtrasRemoved(Connection c, List<String> keys) {
                            final Bundle current = c.getExtras();
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onExtrasChanged(
                                                    RemoteConnection.this, current);
                                        }
                                    });
                        }

                        @Override
                        public void onConnectionEvent(
                                Connection c, final String event, final Bundle extras) {
                            dispatch(
                                    new CallbackAction() {
                                        @Override
                                        public void run(Callback cb) {
                                            cb.onConnectionEvent(
                                                    RemoteConnection.this, event, extras);
                                        }
                                    });
                        }

                        @Override
                        public void onRttInitiationSuccess(Connection c) {
                        }

                        @Override
                        public void onRttInitiationFailure(Connection c, int reason) {
                        }

                        @Override
                        public void onRttSessionRemotelyTerminated(Connection c) {
                        }

                        @Override
                        public void onRemoteRttRequest(Connection c) {
                        }
                    });
        }
    }

    private interface CallbackAction {
        void run(Callback cb);
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

    Connection getConnectionInternal() {
        return mConnection;
    }

    void setConferenceInternal(final RemoteConference conference) {
        mConference = conference;
        dispatch(
                new CallbackAction() {
                    @Override
                    public void run(Callback cb) {
                        cb.onConferenceChanged(RemoteConnection.this, conference);
                    }
                });
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

    public int getState() {
        return mConnection == null ? Connection.STATE_DISCONNECTED : mConnection.getState();
    }

    public DisconnectCause getDisconnectCause() {
        return mConnection == null ? null : mConnection.getDisconnectCause();
    }

    public int getConnectionCapabilities() {
        return mConnection == null ? 0 : mConnection.getConnectionCapabilities();
    }

    public int getConnectionProperties() {
        return mConnection == null ? 0 : mConnection.getConnectionProperties();
    }

    public boolean isVoipAudioMode() {
        return mConnection != null && mConnection.getAudioModeIsVoip();
    }

    public StatusHints getStatusHints() {
        return mConnection == null ? null : mConnection.getStatusHints();
    }

    public Uri getAddress() {
        return mConnection == null ? null : mConnection.getAddress();
    }

    public int getAddressPresentation() {
        return mConnection == null
                ? TelecomManager.PRESENTATION_UNKNOWN
                : mConnection.getAddressPresentation();
    }

    public CharSequence getCallerDisplayName() {
        return mConnection == null ? null : mConnection.getCallerDisplayName();
    }

    public int getCallerDisplayNamePresentation() {
        return mConnection == null
                ? TelecomManager.PRESENTATION_UNKNOWN
                : mConnection.getCallerDisplayNamePresentation();
    }

    public int getVideoState() {
        return mConnection == null
                ? VideoProfile.STATE_AUDIO_ONLY
                : mConnection.getVideoState();
    }

    public VideoProvider getVideoProvider() {
        return mVideoProvider;
    }

    public Bundle getExtras() {
        return mConnection == null ? null : mConnection.getExtras();
    }

    public boolean isRingbackRequested() {
        return mConnection != null && mConnection.isRingbackRequested();
    }

    public void abort() {
        if (mConnection != null) {
            mConnection.onAbort();
        }
    }

    public void answer() {
        if (mConnection != null) {
            mConnection.onAnswer();
        }
    }

    public void reject() {
        if (mConnection != null) {
            mConnection.onReject();
        }
    }

    public void hold() {
        if (mConnection != null) {
            mConnection.onHold();
        }
    }

    public void unhold() {
        if (mConnection != null) {
            mConnection.onUnhold();
        }
    }

    public void disconnect() {
        if (mConnection != null) {
            mConnection.onDisconnect();
        }
    }

    public void playDtmfTone(char digit) {
        if (mConnection != null) {
            mConnection.onPlayDtmfTone(digit);
        }
    }

    public void stopDtmfTone() {
        if (mConnection != null) {
            mConnection.onStopDtmfTone();
        }
    }

    public void postDialContinue(boolean proceed) {
        if (mConnection != null) {
            mConnection.onPostDialContinue(proceed);
        }
    }

    public void pullExternalCall() {
        if (mConnection != null) {
            mConnection.onPullExternalCall();
        }
    }

    public void addConferenceParticipants(List<Uri> participants) {
        if (mConnection != null) {
            mConnection.onAddConferenceParticipants(participants);
        }
    }

    public void setCallAudioState(CallAudioState state) {
        if (mConnection != null) {
            mConnection.setCallAudioStateInternal(state);
        }
    }

    public List<RemoteConnection> getConferenceableConnections() {
        return Collections.unmodifiableList(
                new ArrayList<RemoteConnection>(mConferenceableConnections));
    }

    public RemoteConference getConference() {
        return mConference;
    }
}
