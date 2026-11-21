// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/** Multi-party conference call grouping multiple {@link Connection}s. */
public abstract class Conference extends Conferenceable {
    public static final long CONNECT_TIME_NOT_SPECIFIED = 0L;

    interface Listener {
        void onStateChanged(Conference conference, int oldState, int newState);

        void onDisconnected(Conference conference, DisconnectCause disconnectCause);

        void onConnectionAdded(Conference conference, Connection connection);

        void onConnectionRemoved(Conference conference, Connection connection);

        void onConferenceableConnectionsChanged(
                Conference conference, List<Connection> conferenceableConnections);

        void onDestroyed(Conference conference);

        void onConnectionCapabilitiesChanged(
                Conference conference, int connectionCapabilities);

        void onConnectionPropertiesChanged(Conference conference, int connectionProperties);

        void onVideoStateChanged(Conference c, int videoState);

        void onVideoProviderChanged(Conference c, Connection.VideoProvider videoProvider);

        void onStatusHintsChanged(Conference conference, StatusHints statusHints);

        void onExtrasChanged(Conference c, Bundle extras);

        void onExtrasRemoved(Conference c, List<String> keys);

        void onConferenceEvent(Conference c, String event, Bundle extras);
    }

    private final Set<Listener> mListeners = new CopyOnWriteArraySet<Listener>();
    private final List<Connection> mChildConnections = new CopyOnWriteArrayList<Connection>();
    private final List<Connection> mConferenceableConnections = new ArrayList<Connection>();

    private final PhoneAccountHandle mPhoneAccount;
    private CallAudioState mCallAudioState;
    private CallEndpoint mCallEndpoint;
    private int mState = Connection.STATE_NEW;
    private DisconnectCause mDisconnectCause;
    private int mConnectionCapabilities;
    private int mConnectionProperties;
    private long mConnectTimeMillis = CONNECT_TIME_NOT_SPECIFIED;
    private long mConnectionStartElapsedRealTime = CONNECT_TIME_NOT_SPECIFIED;
    private StatusHints mStatusHints;
    private Bundle mExtras;
    private boolean mRingbackRequested = false;

    public Conference(PhoneAccountHandle phoneAccount) {
        this.mPhoneAccount = phoneAccount;
    }

    void addListener(Listener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    void removeListener(Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    public final PhoneAccountHandle getPhoneAccountHandle() {
        return mPhoneAccount;
    }

    public final List<Connection> getConnections() {
        return Collections.unmodifiableList(mChildConnections);
    }

    public final int getState() {
        return mState;
    }

    public final boolean isRingbackRequested() {
        return mRingbackRequested;
    }

    public final int getConnectionCapabilities() {
        return mConnectionCapabilities;
    }

    public final int getConnectionProperties() {
        return mConnectionProperties;
    }

    public final CallAudioState getCallAudioState() {
        return mCallAudioState;
    }

    public final CallEndpoint getCurrentCallEndpoint() {
        return mCallEndpoint;
    }

    public Connection.VideoProvider getVideoProvider() {
        return null;
    }

    public int getVideoState() {
        return VideoProfile.STATE_AUDIO_ONLY;
    }

    public void onDisconnect() {
    }

    public void onSeparate(Connection connection) {
    }

    public void onMerge(Connection connection) {
    }

    public void onHold() {
    }

    public void onUnhold() {
    }

    public void onMerge() {
    }

    public void onSwap() {
    }

    public void onPlayDtmfTone(char c) {
    }

    public void onStopDtmfTone() {
    }

    public void onCallAudioStateChanged(CallAudioState state) {
    }

    public void onCallEndpointChanged(CallEndpoint callEndpoint) {
        mCallEndpoint = callEndpoint;
    }

    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {
    }

    public void onMuteStateChanged(boolean isMuted) {
    }

    public void onConnectionAdded(Connection connection) {
    }

    public void onAddConferenceParticipants(List<Uri> participants) {
    }

    public void onAnswer(int videoState) {
    }

    public void onReject() {
    }

    public final void setOnHold() {
        setState(Connection.STATE_HOLDING);
    }

    public final void setDialing() {
        setState(Connection.STATE_DIALING);
    }

    public final void setRinging() {
        setState(Connection.STATE_RINGING);
    }

    public final void setActive() {
        setRingbackRequested(false);
        setState(Connection.STATE_ACTIVE);
    }

    public final void setDisconnected(DisconnectCause disconnectCause) {
        mDisconnectCause = disconnectCause;
        setState(Connection.STATE_DISCONNECTED);
        for (Listener l : mListeners) {
            l.onDisconnected(this, mDisconnectCause);
        }
    }

    public final DisconnectCause getDisconnectCause() {
        return mDisconnectCause;
    }

    public final void setConnectionCapabilities(int connectionCapabilities) {
        if (connectionCapabilities != mConnectionCapabilities) {
            mConnectionCapabilities = connectionCapabilities;
            for (Listener l : mListeners) {
                l.onConnectionCapabilitiesChanged(this, mConnectionCapabilities);
            }
        }
    }

    public final void setConnectionProperties(int connectionProperties) {
        if (connectionProperties != mConnectionProperties) {
            mConnectionProperties = connectionProperties;
            for (Listener l : mListeners) {
                l.onConnectionPropertiesChanged(this, mConnectionProperties);
            }
        }
    }

    public final boolean addConnection(Connection connection) {
        if (connection != null
                && !mChildConnections.contains(connection)
                && connection.setConference(this)) {
            mChildConnections.add(connection);
            onConnectionAdded(connection);
            for (Listener l : mListeners) {
                l.onConnectionAdded(this, connection);
            }
            return true;
        }
        return false;
    }

    public final void removeConnection(Connection connection) {
        if (connection != null && mChildConnections.remove(connection)) {
            connection.resetConference();
            for (Listener l : mListeners) {
                l.onConnectionRemoved(this, connection);
            }
        }
    }

    public final void setConferenceableConnections(List<Connection> conferenceableConnections) {
        mConferenceableConnections.clear();
        if (conferenceableConnections != null) {
            for (Connection c : conferenceableConnections) {
                if (c != null && !mConferenceableConnections.contains(c)) {
                    mConferenceableConnections.add(c);
                }
            }
        }
        List<Connection> snap = getConferenceableConnections();
        for (Listener l : mListeners) {
            l.onConferenceableConnectionsChanged(this, snap);
        }
    }

    public final void setRingbackRequested(boolean ringback) {
        mRingbackRequested = ringback;
    }

    public final void setVideoState(Connection c, int videoState) {
        for (Listener l : mListeners) {
            l.onVideoStateChanged(this, videoState);
        }
    }

    public final void setVideoProvider(Connection c, Connection.VideoProvider videoProvider) {
        for (Listener l : mListeners) {
            l.onVideoProviderChanged(this, videoProvider);
        }
    }

    public final List<Connection> getConferenceableConnections() {
        return Collections.unmodifiableList(
                new ArrayList<Connection>(mConferenceableConnections));
    }

    public final void destroy() {
        for (Connection connection : mChildConnections) {
            removeConnection(connection);
        }
        if (mState != Connection.STATE_DISCONNECTED) {
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        }
        for (Listener l : mListeners) {
            l.onDestroyed(this);
        }
    }

    public final void setConnectionTime(long connectionTimeMillis) {
        mConnectTimeMillis = connectionTimeMillis;
    }

    @Deprecated
    public final void setConnectionStartElapsedRealTime(long connectionStartElapsedRealTime) {
        setConnectionStartElapsedRealtimeMillis(connectionStartElapsedRealTime);
    }

    public final void setConnectionStartElapsedRealtimeMillis(
            long connectionStartElapsedRealTime) {
        mConnectionStartElapsedRealTime = connectionStartElapsedRealTime;
    }

    public final long getConnectionTime() {
        return mConnectTimeMillis;
    }

    public final long getConnectionStartElapsedRealtimeMillis() {
        return mConnectionStartElapsedRealTime;
    }

    final void setCallAudioStateInternal(CallAudioState state) {
        mCallAudioState = state;
        onCallAudioStateChanged(state);
    }

    public static Conference createFailedConference(
            DisconnectCause disconnectCause, PhoneAccountHandle phoneAccount) {
        Conference failed =
                new Conference(phoneAccount) {};
        failed.setDisconnected(
                disconnectCause != null
                        ? disconnectCause
                        : new DisconnectCause(DisconnectCause.ERROR));
        return failed;
    }

    @Override
    public String toString() {
        return "[State: "
                + Connection.stateToString(mState)
                + ",Capabilites: "
                + Connection.capabilitiesToString(mConnectionCapabilities)
                + ", VideoState: "
                + getVideoState()
                + ", children: "
                + mChildConnections
                + ", conferenceable: "
                + mConferenceableConnections
                + "]";
    }

    public final void setStatusHints(StatusHints statusHints) {
        mStatusHints = statusHints;
        for (Listener l : mListeners) {
            l.onStatusHintsChanged(this, statusHints);
        }
    }

    public final StatusHints getStatusHints() {
        return mStatusHints;
    }

    public final void setExtras(Bundle extras) {
        mExtras = extras == null ? null : new Bundle(extras);
        Bundle copy = getExtras();
        for (Listener l : mListeners) {
            l.onExtrasChanged(this, copy);
        }
    }

    public final void putExtras(Bundle extras) {
        Objects.requireNonNull(extras, "extras");
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putAll(extras);
        Bundle copy = getExtras();
        for (Listener l : mListeners) {
            l.onExtrasChanged(this, copy);
        }
    }

    public final void removeExtras(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        if (mExtras != null) {
            for (String k : keys) {
                mExtras.remove(k);
            }
        }
        List<String> copy = Collections.unmodifiableList(new ArrayList<String>(keys));
        for (Listener l : mListeners) {
            l.onExtrasRemoved(this, copy);
        }
    }

    public final void removeExtras(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        ArrayList<String> list = new ArrayList<String>(keys.length);
        Collections.addAll(list, keys);
        removeExtras(list);
    }

    public final Bundle getExtras() {
        return mExtras == null ? null : new Bundle(mExtras);
    }

    public void onExtrasChanged(Bundle extras) {
    }

    public void sendConferenceEvent(String event, Bundle extras) {
        for (Listener l : mListeners) {
            l.onConferenceEvent(this, event, extras);
        }
    }

    private void setState(int newState) {
        if (mState != newState) {
            int oldState = mState;
            mState = newState;
            for (Listener l : mListeners) {
                l.onStateChanged(this, oldState, newState);
            }
        }
    }
}
