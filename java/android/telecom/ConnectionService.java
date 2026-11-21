// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Base {@link Service} implemented by call providers to create and manage {@link Connection}s. */
public abstract class ConnectionService extends Service {
    public static final String SERVICE_INTERFACE = "android.telecom.ConnectionService";

    private final IBinder mBinder = new Binder();
    private final List<Connection> mConnections = new CopyOnWriteArrayList<Connection>();
    private final List<Conference> mConferences = new CopyOnWriteArrayList<Conference>();

    public ConnectionService() {
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        for (Connection c : new ArrayList<Connection>(mConnections)) {
            c.destroy();
        }
        mConnections.clear();
        for (Conference conf : new ArrayList<Conference>(mConferences)) {
            conf.destroy();
        }
        mConferences.clear();
        return super.onUnbind(intent);
    }

    public final RemoteConnection createRemoteIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Connection c = onCreateIncomingConnection(connectionManagerPhoneAccount, request);
        if (c == null) {
            c =
                    Connection.createFailedConnection(
                            new DisconnectCause(DisconnectCause.ERROR));
        } else {
            trackConnection(c);
        }
        return new RemoteConnection(c);
    }

    public final RemoteConnection createRemoteOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Connection c = onCreateOutgoingConnection(connectionManagerPhoneAccount, request);
        if (c == null) {
            c =
                    Connection.createFailedConnection(
                            new DisconnectCause(DisconnectCause.ERROR));
        } else {
            trackConnection(c);
        }
        return new RemoteConnection(c);
    }

    public final RemoteConference createRemoteIncomingConference(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Conference conf = onCreateIncomingConference(connectionManagerPhoneAccount, request);
        if (conf == null) {
            conf =
                    Conference.createFailedConference(
                            new DisconnectCause(DisconnectCause.ERROR),
                            connectionManagerPhoneAccount);
        } else {
            trackConference(conf);
        }
        return new RemoteConference(conf);
    }

    public final RemoteConference createRemoteOutgoingConference(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        Conference conf = onCreateOutgoingConference(connectionManagerPhoneAccount, request);
        if (conf == null) {
            conf =
                    Conference.createFailedConference(
                            new DisconnectCause(DisconnectCause.ERROR),
                            connectionManagerPhoneAccount);
        } else {
            trackConference(conf);
        }
        return new RemoteConference(conf);
    }

    public final void conferenceRemoteConnections(
            RemoteConnection remoteConnection1, RemoteConnection remoteConnection2) {
        if (remoteConnection1 != null && remoteConnection2 != null) {
            Connection c1 = remoteConnection1.getConnectionInternal();
            Connection c2 = remoteConnection2.getConnectionInternal();
            if (c1 != null && c2 != null) {
                onConference(c1, c2);
            }
        }
    }

    public final void addConference(Conference conference) {
        if (conference != null) {
            trackConference(conference);
        }
    }

    public final void addExistingConnection(
            PhoneAccountHandle phoneAccountHandle, Connection connection) {
        if (connection != null) {
            trackConnection(connection);
        }
    }

    public final void connectionServiceFocusReleased() {
    }

    public final Collection<Connection> getAllConnections() {
        return Collections.unmodifiableCollection(new ArrayList<Connection>(mConnections));
    }

    public final Collection<Conference> getAllConferences() {
        return Collections.unmodifiableCollection(new ArrayList<Conference>(mConferences));
    }

    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    public Conference onCreateIncomingConference(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    public void onCreateIncomingConnectionFailed(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
    }

    public void onCreateOutgoingConnectionFailed(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
    }

    public void onCreateIncomingConferenceFailed(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
    }

    public void onCreateOutgoingConferenceFailed(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
    }

    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    public Conference onCreateOutgoingConference(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    public Connection onCreateOutgoingHandoverConnection(
            PhoneAccountHandle fromPhoneAccountHandle, ConnectionRequest request) {
        return null;
    }

    public Connection onCreateIncomingHandoverConnection(
            PhoneAccountHandle fromPhoneAccountHandle, ConnectionRequest request) {
        return null;
    }

    public void onHandoverFailed(ConnectionRequest request, int error) {
    }

    public void onConference(Connection connection1, Connection connection2) {
    }

    public void onRemoteConferenceAdded(RemoteConference conference) {
    }

    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
    }

    public void onConnectionServiceFocusLost() {
    }

    public void onConnectionServiceFocusGained() {
    }

    private void trackConnection(Connection c) {
        if (!mConnections.contains(c)) {
            mConnections.add(c);
            c.addConnectionListener(
                    new Connection.Listener() {
                        @Override
                        public void onStateChanged(Connection conn, int state) {
                        }

                        @Override
                        public void onAddressChanged(
                                Connection conn, android.net.Uri newAddress, int presentation) {
                        }

                        @Override
                        public void onCallerDisplayNameChanged(
                                Connection conn, String callerDisplayName, int presentation) {
                        }

                        @Override
                        public void onVideoStateChanged(Connection conn, int videoState) {
                        }

                        @Override
                        public void onDisconnected(
                                Connection conn, DisconnectCause disconnectCause) {
                        }

                        @Override
                        public void onPostDialWait(Connection conn, String remaining) {
                        }

                        @Override
                        public void onPostDialChar(Connection conn, char nextChar) {
                        }

                        @Override
                        public void onRingbackRequested(Connection conn, boolean ringback) {
                        }

                        @Override
                        public void onDestroyed(Connection conn) {
                            mConnections.remove(conn);
                        }

                        @Override
                        public void onConnectionCapabilitiesChanged(
                                Connection conn, int capabilities) {
                        }

                        @Override
                        public void onConnectionPropertiesChanged(
                                Connection conn, int properties) {
                        }

                        @Override
                        public void onVideoProviderChanged(
                                Connection conn, Connection.VideoProvider videoProvider) {
                        }

                        @Override
                        public void onAudioModeIsVoipChanged(
                                Connection conn, boolean isVoip) {
                        }

                        @Override
                        public void onStatusHintsChanged(
                                Connection conn, StatusHints statusHints) {
                        }

                        @Override
                        public void onConferenceablesChanged(
                                Connection conn, List<Conferenceable> conferenceables) {
                        }

                        @Override
                        public void onConferenceChanged(
                                Connection conn, Conference conference) {
                        }

                        @Override
                        public void onExtrasChanged(
                                Connection conn, android.os.Bundle extras) {
                        }

                        @Override
                        public void onExtrasRemoved(Connection conn, List<String> keys) {
                        }

                        @Override
                        public void onConnectionEvent(
                                Connection conn, String event, android.os.Bundle extras) {
                        }

                        @Override
                        public void onRttInitiationSuccess(Connection conn) {
                        }

                        @Override
                        public void onRttInitiationFailure(Connection conn, int reason) {
                        }

                        @Override
                        public void onRttSessionRemotelyTerminated(Connection conn) {
                        }

                        @Override
                        public void onRemoteRttRequest(Connection conn) {
                        }
                    });
        }
    }

    private void trackConference(Conference conf) {
        if (!mConferences.contains(conf)) {
            mConferences.add(conf);
            conf.addListener(
                    new Conference.Listener() {
                        @Override
                        public void onStateChanged(
                                Conference c, int oldState, int newState) {
                        }

                        @Override
                        public void onDisconnected(
                                Conference c, DisconnectCause disconnectCause) {
                        }

                        @Override
                        public void onConnectionAdded(Conference c, Connection connection) {
                        }

                        @Override
                        public void onConnectionRemoved(
                                Conference c, Connection connection) {
                        }

                        @Override
                        public void onConferenceableConnectionsChanged(
                                Conference c, List<Connection> conferenceableConnections) {
                        }

                        @Override
                        public void onDestroyed(Conference c) {
                            mConferences.remove(c);
                        }

                        @Override
                        public void onConnectionCapabilitiesChanged(
                                Conference c, int connectionCapabilities) {
                        }

                        @Override
                        public void onConnectionPropertiesChanged(
                                Conference c, int connectionProperties) {
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
                                Conference c, StatusHints statusHints) {
                        }

                        @Override
                        public void onExtrasChanged(
                                Conference c, android.os.Bundle extras) {
                        }

                        @Override
                        public void onExtrasRemoved(Conference c, List<String> keys) {
                        }

                        @Override
                        public void onConferenceEvent(
                                Conference c, String event, android.os.Bundle extras) {
                        }
                    });
        }
    }
}
