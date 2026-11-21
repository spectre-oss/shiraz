// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.bluetooth.BluetoothDevice;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.view.Surface;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;

/** Represents a phone call or connection to a remote endpoint managed by a {@link ConnectionService}. */
public abstract class Connection extends Conferenceable {
    public static final int STATE_INITIALIZING = 0;
    public static final int STATE_NEW = 1;
    public static final int STATE_RINGING = 2;
    public static final int STATE_DIALING = 3;
    public static final int STATE_ACTIVE = 4;
    public static final int STATE_HOLDING = 5;
    public static final int STATE_DISCONNECTED = 6;
    public static final int STATE_PULLING_CALL = 7;

    public static final int CAPABILITY_HOLD = 0x00000001;
    public static final int CAPABILITY_SUPPORT_HOLD = 0x00000002;
    public static final int CAPABILITY_MERGE_CONFERENCE = 0x00000004;
    public static final int CAPABILITY_SWAP_CONFERENCE = 0x00000008;
    public static final int CAPABILITY_RESPOND_VIA_TEXT = 0x00000020;
    public static final int CAPABILITY_MUTE = 0x00000040;
    public static final int CAPABILITY_MANAGE_CONFERENCE = 0x00000080;
    public static final int CAPABILITY_SUPPORTS_VT_LOCAL_RX = 0x00000100;
    public static final int CAPABILITY_SUPPORTS_VT_LOCAL_TX = 0x00000200;
    public static final int CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL =
            CAPABILITY_SUPPORTS_VT_LOCAL_RX | CAPABILITY_SUPPORTS_VT_LOCAL_TX;
    public static final int CAPABILITY_SUPPORTS_VT_REMOTE_RX = 0x00000400;
    public static final int CAPABILITY_SUPPORTS_VT_REMOTE_TX = 0x00000800;
    public static final int CAPABILITY_SUPPORTS_VT_REMOTE_BIDIRECTIONAL =
            CAPABILITY_SUPPORTS_VT_REMOTE_RX | CAPABILITY_SUPPORTS_VT_REMOTE_TX;
    public static final int CAPABILITY_SEPARATE_FROM_CONFERENCE = 0x00001000;
    public static final int CAPABILITY_DISCONNECT_FROM_CONFERENCE = 0x00002000;
    public static final int CAPABILITY_CAN_UPGRADE_TO_VIDEO = 0x00080000;
    public static final int CAPABILITY_CAN_PAUSE_VIDEO = 0x00100000;
    public static final int CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION = 0x00400000;
    public static final int CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO = 0x00800000;
    public static final int CAPABILITY_CAN_PULL_CALL = 0x01000000;
    public static final int CAPABILITY_SUPPORT_DEFLECT = 0x02000000;
    public static final int CAPABILITY_ADD_PARTICIPANT = 0x04000000;
    public static final int CAPABILITY_REMOTE_PARTY_SUPPORTS_RTT = 0x20000000;

    public static final int PROPERTY_HIGH_DEF_AUDIO = 1 << 2;
    public static final int PROPERTY_WIFI = 1 << 3;
    public static final int PROPERTY_IS_EXTERNAL_CALL = 1 << 4;
    public static final int PROPERTY_HAS_CDMA_VOICE_PRIVACY = 1 << 5;
    public static final int PROPERTY_SELF_MANAGED = 1 << 7;
    public static final int PROPERTY_IS_RTT = 1 << 8;
    public static final int PROPERTY_ASSISTED_DIALING = 1 << 9;
    public static final int PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL = 1 << 10;
    public static final int PROPERTY_IS_ADHOC_CONFERENCE = 1 << 12;
    public static final int PROPERTY_CROSS_SIM = 1 << 13;

    public static final int AUDIO_CODEC_NONE = 0;
    public static final int AUDIO_CODEC_AMR = 1;
    public static final int AUDIO_CODEC_AMR_WB = 2;
    public static final int AUDIO_CODEC_QCELP13K = 3;
    public static final int AUDIO_CODEC_EVRC = 4;
    public static final int AUDIO_CODEC_EVRC_B = 5;
    public static final int AUDIO_CODEC_EVRC_WB = 6;
    public static final int AUDIO_CODEC_EVRC_NW = 7;
    public static final int AUDIO_CODEC_GSM_EFR = 8;
    public static final int AUDIO_CODEC_GSM_FR = 9;
    public static final int AUDIO_CODEC_GSM_HR = 10;
    public static final int AUDIO_CODEC_G711U = 11;
    public static final int AUDIO_CODEC_G723 = 12;
    public static final int AUDIO_CODEC_G711A = 13;
    public static final int AUDIO_CODEC_G722 = 14;
    public static final int AUDIO_CODEC_G711AB = 15;
    public static final int AUDIO_CODEC_G729 = 16;
    public static final int AUDIO_CODEC_EVS_NB = 17;
    public static final int AUDIO_CODEC_EVS_WB = 18;
    public static final int AUDIO_CODEC_EVS_SWB = 19;
    public static final int AUDIO_CODEC_EVS_FB = 20;

    public static final int VERIFICATION_STATUS_NOT_VERIFIED = 0;
    public static final int VERIFICATION_STATUS_PASSED = 1;
    public static final int VERIFICATION_STATUS_FAILED = 2;

    public static final String EXTRA_LAST_FORWARDED_NUMBER =
            "android.telecom.extra.LAST_FORWARDED_NUMBER";
    public static final String EXTRA_CHILD_ADDRESS = "android.telecom.extra.CHILD_ADDRESS";
    public static final String EXTRA_CALL_SUBJECT = "android.telecom.extra.CALL_SUBJECT";
    public static final String EXTRA_ANSWERING_DROPS_FG_CALL =
            "android.telecom.extra.ANSWERING_DROPS_FG_CALL";
    public static final String EXTRA_ANSWERING_DROPS_FG_CALL_APP_NAME =
            "android.telecom.extra.ANSWERING_DROPS_FG_CALL_APP_NAME";
    public static final String EXTRA_SIP_INVITE = "android.telecom.extra.SIP_INVITE";
    public static final String EXTRA_IS_RTT_AUDIO_PRESENT =
            "android.telecom.extra.IS_RTT_AUDIO_PRESENT";
    public static final String EXTRA_AUDIO_CODEC = "android.telecom.extra.AUDIO_CODEC";
    public static final String EXTRA_AUDIO_CODEC_BITRATE_KBPS =
            "android.telecom.extra.AUDIO_CODEC_BITRATE_KBPS";
    public static final String EXTRA_AUDIO_CODEC_BANDWIDTH_KHZ =
            "android.telecom.extra.AUDIO_CODEC_BANDWIDTH_KHZ";
    public static final String EXTRA_LAST_KNOWN_CELL_IDENTITY =
            "android.telecom.extra.LAST_KNOWN_CELL_IDENTITY";

    public static final String EVENT_ON_HOLD_TONE_START =
            "android.telecom.event.ON_HOLD_TONE_START";
    public static final String EVENT_ON_HOLD_TONE_END = "android.telecom.event.ON_HOLD_TONE_END";
    public static final String EVENT_CALL_PULL_FAILED = "android.telecom.event.CALL_PULL_FAILED";
    public static final String EVENT_MERGE_START = "android.telecom.event.MERGE_START";
    public static final String EVENT_MERGE_COMPLETE = "android.telecom.event.MERGE_COMPLETE";
    public static final String EVENT_CALL_MERGE_FAILED =
            "android.telecom.event.CALL_MERGE_FAILED";
    public static final String EVENT_CALL_REMOTELY_HELD =
            "android.telecom.event.CALL_REMOTELY_HELD";
    public static final String EVENT_CALL_REMOTELY_UNHELD =
            "android.telecom.event.CALL_REMOTELY_UNHELD";
    public static final String EVENT_RTT_AUDIO_INDICATION_CHANGED =
            "android.telecom.event.RTT_AUDIO_INDICATION_CHANGED";
    public static final String EVENT_CALL_SWITCH_FAILED =
            "android.telecom.event.CALL_SWITCH_FAILED";
    public static final String EVENT_CALL_HOLD_FAILED = "android.telecom.event.CALL_HOLD_FAILED";

    public static final class RttModifyStatus {
        public static final int SESSION_MODIFY_REQUEST_SUCCESS = 1;
        public static final int SESSION_MODIFY_REQUEST_FAIL = 2;
        public static final int SESSION_MODIFY_REQUEST_INVALID = 3;
        public static final int SESSION_MODIFY_REQUEST_TIMED_OUT = 4;
        public static final int SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE = 5;

        private RttModifyStatus() {
        }
    }

    public static final class RttTextStream {
        private static final int READ_BUFFER_SIZE = 1000;
        private final Reader mPipeFromInCall;
        private final Writer mPipeToInCall;
        private final ParcelFileDescriptor mFdFromInCall;
        private final ParcelFileDescriptor mFdToInCall;
        private final char[] mReadBuffer = new char[READ_BUFFER_SIZE];

        RttTextStream(ParcelFileDescriptor toInCall, ParcelFileDescriptor fromInCall) {
            this.mFdFromInCall = Objects.requireNonNull(fromInCall, "fromInCall");
            this.mFdToInCall = Objects.requireNonNull(toInCall, "toInCall");
            this.mPipeFromInCall =
                    new InputStreamReader(
                            new ParcelFileDescriptor.AutoCloseInputStream(fromInCall),
                            StandardCharsets.UTF_8);
            this.mPipeToInCall =
                    new OutputStreamWriter(
                            new ParcelFileDescriptor.AutoCloseOutputStream(toInCall),
                            StandardCharsets.UTF_8);
        }

        RttTextStream(Reader fromInCall, Writer toInCall) {
            this.mFdFromInCall = null;
            this.mFdToInCall = null;
            this.mPipeFromInCall = Objects.requireNonNull(fromInCall, "fromInCall");
            this.mPipeToInCall = Objects.requireNonNull(toInCall, "toInCall");
        }

        public void write(String input) throws IOException {
            if (input == null) {
                return;
            }
            mPipeToInCall.write(input);
            mPipeToInCall.flush();
        }

        public String read() throws IOException {
            int numRead = mPipeFromInCall.read(mReadBuffer, 0, READ_BUFFER_SIZE);
            if (numRead < 0) {
                return null;
            }
            return new String(mReadBuffer, 0, numRead);
        }

        public String readImmediately() throws IOException {
            if (mPipeFromInCall.ready()) {
                return read();
            }
            return null;
        }

        ParcelFileDescriptor getFdFromInCall() {
            return mFdFromInCall;
        }

        ParcelFileDescriptor getFdToInCall() {
            return mFdToInCall;
        }

        static RttTextStream[] createPair() throws IOException {
            try {
                FileDescriptor[] aToB = Os.pipe();
                FileDescriptor[] bToA = Os.pipe();
                ParcelFileDescriptor aRead = dupAndCloseRaw(bToA[0]);
                ParcelFileDescriptor aWrite = dupAndCloseRaw(aToB[1]);
                ParcelFileDescriptor bRead = dupAndCloseRaw(aToB[0]);
                ParcelFileDescriptor bWrite = dupAndCloseRaw(bToA[1]);
                return new RttTextStream[] {
                    new RttTextStream(aWrite, aRead), new RttTextStream(bWrite, bRead)
                };
            } catch (UnsatisfiedLinkError
                    | NoClassDefFoundError
                    | ErrnoException
                    | RuntimeException fallback) {
                PipedWriter aWrite = new PipedWriter();
                PipedReader bRead = new PipedReader(aWrite, 4096);
                PipedWriter bWrite = new PipedWriter();
                PipedReader aRead = new PipedReader(bWrite, 4096);
                return new RttTextStream[] {
                    new RttTextStream(aRead, aWrite), new RttTextStream(bRead, bWrite)
                };
            }
        }

        private static ParcelFileDescriptor dupAndCloseRaw(FileDescriptor raw)
                throws IOException, ErrnoException {
            try {
                return ParcelFileDescriptor.dup(raw);
            } finally {
                try {
                    Os.close(raw);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public abstract static class VideoProvider {
        public static final int SESSION_EVENT_RX_PAUSE = 1;
        public static final int SESSION_EVENT_RX_RESUME = 2;
        public static final int SESSION_EVENT_TX_START = 3;
        public static final int SESSION_EVENT_TX_STOP = 4;
        public static final int SESSION_EVENT_CAMERA_FAILURE = 5;
        public static final int SESSION_EVENT_CAMERA_READY = 6;
        public static final int SESSION_EVENT_CAMERA_PERMISSION_ERROR = 7;

        public static final int SESSION_MODIFY_REQUEST_SUCCESS = 1;
        public static final int SESSION_MODIFY_REQUEST_FAIL = 2;
        public static final int SESSION_MODIFY_REQUEST_INVALID = 3;
        public static final int SESSION_MODIFY_REQUEST_TIMED_OUT = 4;
        public static final int SESSION_MODIFY_REQUEST_REJECTED_BY_REMOTE = 5;

        interface Listener {
            void onReceiveSessionModifyRequest(VideoProfile videoProfile);

            void onReceiveSessionModifyResponse(
                    int status, VideoProfile requestedProfile, VideoProfile responseProfile);

            void onHandleCallSessionEvent(int event);

            void onChangePeerDimensions(int width, int height);

            void onSetCallDataUsage(long dataUsage);

            void onChangeCameraCapabilities(VideoProfile.CameraCapabilities cameraCapabilities);

            void onChangeVideoQuality(int videoQuality);
        }

        private final Set<Listener> mListeners = new CopyOnWriteArraySet<Listener>();

        public VideoProvider() {
        }

        void addListener(Listener l) {
            if (l != null) {
                mListeners.add(l);
            }
        }

        void removeListener(Listener l) {
            if (l != null) {
                mListeners.remove(l);
            }
        }

        public abstract void onSetCamera(String cameraId);

        public abstract void onSetPreviewSurface(Surface surface);

        public abstract void onSetDisplaySurface(Surface surface);

        public abstract void onSetDeviceOrientation(int rotation);

        public abstract void onSetZoom(float value);

        public abstract void onSendSessionModifyRequest(
                VideoProfile fromProfile, VideoProfile toProfile);

        public abstract void onSendSessionModifyResponse(VideoProfile responseProfile);

        public abstract void onRequestCameraCapabilities();

        public abstract void onRequestConnectionDataUsage();

        public abstract void onSetPauseImage(Uri uri);

        public void receiveSessionModifyRequest(VideoProfile videoProfile) {
            for (Listener l : mListeners) {
                l.onReceiveSessionModifyRequest(videoProfile);
            }
        }

        public void receiveSessionModifyResponse(
                int status, VideoProfile requestedProfile, VideoProfile responseProfile) {
            for (Listener l : mListeners) {
                l.onReceiveSessionModifyResponse(status, requestedProfile, responseProfile);
            }
        }

        public void handleCallSessionEvent(int event) {
            for (Listener l : mListeners) {
                l.onHandleCallSessionEvent(event);
            }
        }

        public void changePeerDimensions(int width, int height) {
            for (Listener l : mListeners) {
                l.onChangePeerDimensions(width, height);
            }
        }

        public void setCallDataUsage(long dataUsage) {
            for (Listener l : mListeners) {
                l.onSetCallDataUsage(dataUsage);
            }
        }

        public void changeCameraCapabilities(
                VideoProfile.CameraCapabilities cameraCapabilities) {
            for (Listener l : mListeners) {
                l.onChangeCameraCapabilities(cameraCapabilities);
            }
        }

        public void changeVideoQuality(int videoQuality) {
            for (Listener l : mListeners) {
                l.onChangeVideoQuality(videoQuality);
            }
        }
    }

    interface Listener {
        void onStateChanged(Connection c, int state);

        void onAddressChanged(Connection c, Uri newAddress, int presentation);

        void onCallerDisplayNameChanged(
                Connection c, String callerDisplayName, int presentation);

        void onVideoStateChanged(Connection c, int videoState);

        void onDisconnected(Connection c, DisconnectCause disconnectCause);

        void onPostDialWait(Connection c, String remaining);

        void onPostDialChar(Connection c, char nextChar);

        void onRingbackRequested(Connection c, boolean ringback);

        void onDestroyed(Connection c);

        void onConnectionCapabilitiesChanged(Connection c, int capabilities);

        void onConnectionPropertiesChanged(Connection c, int properties);

        void onVideoProviderChanged(Connection c, VideoProvider videoProvider);

        void onAudioModeIsVoipChanged(Connection c, boolean isVoip);

        void onStatusHintsChanged(Connection c, StatusHints statusHints);

        void onConferenceablesChanged(Connection c, List<Conferenceable> conferenceables);

        void onConferenceChanged(Connection c, Conference conference);

        void onExtrasChanged(Connection c, Bundle extras);

        void onExtrasRemoved(Connection c, List<String> keys);

        void onConnectionEvent(Connection c, String event, Bundle extras);

        void onRttInitiationSuccess(Connection c);

        void onRttInitiationFailure(Connection c, int reason);

        void onRttSessionRemotelyTerminated(Connection c);

        void onRemoteRttRequest(Connection c);
    }

    private final Set<Listener> mListeners = new CopyOnWriteArraySet<Listener>();
    private final List<Conferenceable> mConferenceables = new ArrayList<Conferenceable>();

    private int mState = STATE_NEW;
    private CallAudioState mCallAudioState;
    private CallEndpoint mCurrentCallEndpoint;
    private Uri mAddress;
    private int mAddressPresentation = TelecomManager.PRESENTATION_ALLOWED;
    private String mCallerDisplayName;
    private int mCallerDisplayNamePresentation = TelecomManager.PRESENTATION_ALLOWED;
    private boolean mRingbackRequested = false;
    private int mConnectionCapabilities;
    private int mConnectionProperties;
    private VideoProvider mVideoProvider;
    private boolean mAudioModeIsVoip;
    private StatusHints mStatusHints;
    private int mVideoState = VideoProfile.STATE_AUDIO_ONLY;
    private DisconnectCause mDisconnectCause;
    private Conference mConference;
    private Bundle mExtras;
    private int mCallerNumberVerificationStatus = VERIFICATION_STATUS_NOT_VERIFIED;

    public Connection() {
    }

    void addConnectionListener(Listener l) {
        if (l != null) {
            mListeners.add(l);
        }
    }

    void removeConnectionListener(Listener l) {
        if (l != null) {
            mListeners.remove(l);
        }
    }

    public static String capabilitiesToString(int capabilities) {
        StringBuilder sb = new StringBuilder("[Capabilities:");
        if ((capabilities & CAPABILITY_HOLD) != 0) sb.append(" CAPABILITY_HOLD");
        if ((capabilities & CAPABILITY_SUPPORT_HOLD) != 0) sb.append(" CAPABILITY_SUPPORT_HOLD");
        if ((capabilities & CAPABILITY_MERGE_CONFERENCE) != 0)
            sb.append(" CAPABILITY_MERGE_CONFERENCE");
        if ((capabilities & CAPABILITY_SWAP_CONFERENCE) != 0)
            sb.append(" CAPABILITY_SWAP_CONFERENCE");
        if ((capabilities & CAPABILITY_RESPOND_VIA_TEXT) != 0)
            sb.append(" CAPABILITY_RESPOND_VIA_TEXT");
        if ((capabilities & CAPABILITY_MUTE) != 0) sb.append(" CAPABILITY_MUTE");
        if ((capabilities & CAPABILITY_MANAGE_CONFERENCE) != 0)
            sb.append(" CAPABILITY_MANAGE_CONFERENCE");
        if ((capabilities & CAPABILITY_SEPARATE_FROM_CONFERENCE) != 0)
            sb.append(" CAPABILITY_SEPARATE_FROM_CONFERENCE");
        if ((capabilities & CAPABILITY_DISCONNECT_FROM_CONFERENCE) != 0)
            sb.append(" CAPABILITY_DISCONNECT_FROM_CONFERENCE");
        if ((capabilities & CAPABILITY_CAN_UPGRADE_TO_VIDEO) != 0)
            sb.append(" CAPABILITY_CAN_UPGRADE_TO_VIDEO");
        if ((capabilities & CAPABILITY_CAN_PAUSE_VIDEO) != 0)
            sb.append(" CAPABILITY_CAN_PAUSE_VIDEO");
        if ((capabilities & CAPABILITY_CAN_PULL_CALL) != 0)
            sb.append(" CAPABILITY_CAN_PULL_CALL");
        if ((capabilities & CAPABILITY_SUPPORT_DEFLECT) != 0)
            sb.append(" CAPABILITY_SUPPORT_DEFLECT");
        if ((capabilities & CAPABILITY_ADD_PARTICIPANT) != 0)
            sb.append(" CAPABILITY_ADD_PARTICIPANT");
        if ((capabilities & CAPABILITY_REMOTE_PARTY_SUPPORTS_RTT) != 0)
            sb.append(" CAPABILITY_REMOTE_PARTY_SUPPORTS_RTT");
        sb.append("]");
        return sb.toString();
    }

    public static String propertiesToString(int properties) {
        StringBuilder sb = new StringBuilder("[Properties:");
        if ((properties & PROPERTY_HIGH_DEF_AUDIO) != 0) sb.append(" PROPERTY_HIGH_DEF_AUDIO");
        if ((properties & PROPERTY_WIFI) != 0) sb.append(" PROPERTY_WIFI");
        if ((properties & PROPERTY_IS_EXTERNAL_CALL) != 0)
            sb.append(" PROPERTY_IS_EXTERNAL_CALL");
        if ((properties & PROPERTY_HAS_CDMA_VOICE_PRIVACY) != 0)
            sb.append(" PROPERTY_HAS_CDMA_VOICE_PRIVACY");
        if ((properties & PROPERTY_SELF_MANAGED) != 0) sb.append(" PROPERTY_SELF_MANAGED");
        if ((properties & PROPERTY_IS_RTT) != 0) sb.append(" PROPERTY_IS_RTT");
        if ((properties & PROPERTY_ASSISTED_DIALING) != 0)
            sb.append(" PROPERTY_ASSISTED_DIALING");
        if ((properties & PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL) != 0)
            sb.append(" PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL");
        if ((properties & PROPERTY_IS_ADHOC_CONFERENCE) != 0)
            sb.append(" PROPERTY_IS_ADHOC_CONFERENCE");
        if ((properties & PROPERTY_CROSS_SIM) != 0) sb.append(" PROPERTY_CROSS_SIM");
        sb.append("]");
        return sb.toString();
    }

    public static String stateToString(int state) {
        switch (state) {
            case STATE_INITIALIZING:
                return "INITIALIZING";
            case STATE_NEW:
                return "NEW";
            case STATE_RINGING:
                return "RINGING";
            case STATE_DIALING:
                return "DIALING";
            case STATE_PULLING_CALL:
                return "PULLING_CALL";
            case STATE_ACTIVE:
                return "ACTIVE";
            case STATE_HOLDING:
                return "HOLDING";
            case STATE_DISCONNECTED:
                return "DISCONNECTED";
            default:
                return "UNKNOWN";
        }
    }

    public final Uri getAddress() {
        return mAddress;
    }

    public final int getAddressPresentation() {
        return mAddressPresentation;
    }

    public final String getCallerDisplayName() {
        return mCallerDisplayName;
    }

    public final int getCallerDisplayNamePresentation() {
        return mCallerDisplayNamePresentation;
    }

    public final int getState() {
        return mState;
    }

    public final int getVideoState() {
        return mVideoState;
    }

    public final CallAudioState getCallAudioState() {
        return mCallAudioState;
    }

    public final Conference getConference() {
        return mConference;
    }

    public final boolean isRingbackRequested() {
        return mRingbackRequested;
    }

    public final boolean getAudioModeIsVoip() {
        return mAudioModeIsVoip;
    }

    public final StatusHints getStatusHints() {
        return mStatusHints;
    }

    public final Bundle getExtras() {
        return mExtras == null ? null : new Bundle(mExtras);
    }

    public final DisconnectCause getDisconnectCause() {
        return mDisconnectCause;
    }

    public final int getConnectionCapabilities() {
        return mConnectionCapabilities;
    }

    public final int getConnectionProperties() {
        return mConnectionProperties;
    }

    public final void setAddress(Uri address, int presentation) {
        mAddress = address;
        mAddressPresentation = presentation;
        for (Listener l : mListeners) {
            l.onAddressChanged(this, address, presentation);
        }
    }

    public final void setCallerDisplayName(String callerDisplayName, int presentation) {
        mCallerDisplayName = callerDisplayName;
        mCallerDisplayNamePresentation = presentation;
        for (Listener l : mListeners) {
            l.onCallerDisplayNameChanged(this, callerDisplayName, presentation);
        }
    }

    public final void setVideoState(int videoState) {
        mVideoState = videoState;
        for (Listener l : mListeners) {
            l.onVideoStateChanged(this, mVideoState);
        }
    }

    public final void setActive() {
        setRingbackRequested(false);
        setState(STATE_ACTIVE);
    }

    public final void setRinging() {
        setState(STATE_RINGING);
    }

    public final void setInitializing() {
        setState(STATE_INITIALIZING);
    }

    public final void setInitialized() {
        setState(STATE_NEW);
    }

    public final void setDialing() {
        setState(STATE_DIALING);
    }

    public final void setPulling() {
        setState(STATE_PULLING_CALL);
    }

    public final void setOnHold() {
        setState(STATE_HOLDING);
    }

    public final void setVideoProvider(VideoProvider videoProvider) {
        mVideoProvider = videoProvider;
        for (Listener l : mListeners) {
            l.onVideoProviderChanged(this, videoProvider);
        }
    }

    public final VideoProvider getVideoProvider() {
        return mVideoProvider;
    }

    public final void setDisconnected(DisconnectCause disconnectCause) {
        mDisconnectCause = disconnectCause;
        setState(STATE_DISCONNECTED);
        for (Listener l : mListeners) {
            l.onDisconnected(this, disconnectCause);
        }
    }

    public final void setPostDialWait(String remaining) {
        for (Listener l : mListeners) {
            l.onPostDialWait(this, remaining);
        }
    }

    public final void setNextPostDialChar(char nextChar) {
        for (Listener l : mListeners) {
            l.onPostDialChar(this, nextChar);
        }
    }

    public final void setRingbackRequested(boolean ringback) {
        if (mRingbackRequested != ringback) {
            mRingbackRequested = ringback;
            for (Listener l : mListeners) {
                l.onRingbackRequested(this, ringback);
            }
        }
    }

    public final void setConnectionCapabilities(int connectionCapabilities) {
        if (mConnectionCapabilities != connectionCapabilities) {
            mConnectionCapabilities = connectionCapabilities;
            for (Listener l : mListeners) {
                l.onConnectionCapabilitiesChanged(this, mConnectionCapabilities);
            }
        }
    }

    public final void setConnectionProperties(int connectionProperties) {
        if (mConnectionProperties != connectionProperties) {
            mConnectionProperties = connectionProperties;
            for (Listener l : mListeners) {
                l.onConnectionPropertiesChanged(this, mConnectionProperties);
            }
        }
    }

    public final void destroy() {
        for (Listener l : mListeners) {
            l.onDestroyed(this);
        }
    }

    public final void setAudioModeIsVoip(boolean isVoip) {
        mAudioModeIsVoip = isVoip;
        for (Listener l : mListeners) {
            l.onAudioModeIsVoipChanged(this, isVoip);
        }
    }

    public final void setStatusHints(StatusHints statusHints) {
        mStatusHints = statusHints;
        for (Listener l : mListeners) {
            l.onStatusHintsChanged(this, statusHints);
        }
    }

    public final void setConferenceableConnections(List<Connection> conferenceableConnections) {
        mConferenceables.clear();
        if (conferenceableConnections != null) {
            for (Connection c : conferenceableConnections) {
                if (c != null && c != this) {
                    mConferenceables.add(c);
                }
            }
        }
        fireConferenceablesChanged();
    }

    public final void setConferenceables(List<Conferenceable> conferenceables) {
        mConferenceables.clear();
        if (conferenceables != null) {
            for (Conferenceable c : conferenceables) {
                if (c != null && c != this) {
                    mConferenceables.add(c);
                }
            }
        }
        fireConferenceablesChanged();
    }

    public final List<Conferenceable> getConferenceables() {
        return Collections.unmodifiableList(new ArrayList<Conferenceable>(mConferenceables));
    }

    private void fireConferenceablesChanged() {
        List<Conferenceable> snap = getConferenceables();
        for (Listener l : mListeners) {
            l.onConferenceablesChanged(this, snap);
        }
    }

    final boolean setConference(Conference conference) {
        if (mConference == null) {
            mConference = conference;
            for (Listener l : mListeners) {
                l.onConferenceChanged(this, conference);
            }
            return true;
        }
        return false;
    }

    final void resetConference() {
        if (mConference != null) {
            mConference = null;
            for (Listener l : mListeners) {
                l.onConferenceChanged(this, null);
            }
        }
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

    public final void setAudioRoute(int route) {
        boolean muted = mCallAudioState != null && mCallAudioState.isMuted();
        int mask =
                mCallAudioState != null
                        ? (mCallAudioState.getSupportedRouteMask() | route)
                        : (CallAudioState.ROUTE_WIRED_OR_EARPIECE
                                | CallAudioState.ROUTE_SPEAKER
                                | route);
        setCallAudioStateInternal(new CallAudioState(muted, route, mask));
    }

    public void requestBluetoothAudio(BluetoothDevice bluetoothDevice) {
        Objects.requireNonNull(bluetoothDevice, "bluetoothDevice");
        boolean muted = mCallAudioState != null && mCallAudioState.isMuted();
        int mask =
                (mCallAudioState != null ? mCallAudioState.getSupportedRouteMask() : 0)
                        | CallAudioState.ROUTE_BLUETOOTH;
        setCallAudioStateInternal(
                new CallAudioState(
                        muted,
                        CallAudioState.ROUTE_BLUETOOTH,
                        mask,
                        bluetoothDevice,
                        Collections.singletonList(bluetoothDevice)));
    }

    final void setCallAudioStateInternal(CallAudioState state) {
        mCallAudioState = state;
        onCallAudioStateChanged(state);
    }

    public final void requestCallEndpointChange(
            CallEndpoint endpoint,
            Executor executor,
            final OutcomeReceiver<Void, CallEndpointException> callback) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        mCurrentCallEndpoint = endpoint;
        onCallEndpointChanged(endpoint);
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(null);
                    }
                });
    }

    public final CallEndpoint getCurrentCallEndpoint() {
        return mCurrentCallEndpoint;
    }

    public final void sendRttInitiationSuccess() {
        for (Listener l : mListeners) {
            l.onRttInitiationSuccess(this);
        }
    }

    public final void sendRttInitiationFailure(int reason) {
        for (Listener l : mListeners) {
            l.onRttInitiationFailure(this, reason);
        }
    }

    public final void sendRttSessionRemotelyTerminated() {
        for (Listener l : mListeners) {
            l.onRttSessionRemotelyTerminated(this);
        }
    }

    public final void sendRemoteRttRequest() {
        for (Listener l : mListeners) {
            l.onRemoteRttRequest(this);
        }
    }

    public final void queryLocationForEmergency(
            long timeoutMillis,
            String provider,
            Executor executor,
            final OutcomeReceiver<Location, QueryLocationException> callback) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        final int code =
                (mConnectionProperties & PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL) == 0
                        ? QueryLocationException.ERROR_NOT_ALLOWED_FOR_NON_EMERGENCY_CONNECTIONS
                        : QueryLocationException.ERROR_SERVICE_UNAVAILABLE;
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(
                                new QueryLocationException(
                                        "Emergency location query unavailable", code));
                    }
                });
    }

    public void onCallAudioStateChanged(CallAudioState state) {
    }

    public void onCallEndpointChanged(CallEndpoint callEndpoint) {
        mCurrentCallEndpoint = callEndpoint;
    }

    public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {
    }

    public void onMuteStateChanged(boolean isMuted) {
    }

    public void onUsingAlternativeUi(boolean isUsingAlternativeUi) {
    }

    public void onTrackedByNonUiService(boolean isTracked) {
    }

    public void onStateChanged(int state) {
    }

    public void onPlayDtmfTone(char c) {
    }

    public void onStopDtmfTone() {
    }

    public void onDisconnect() {
    }

    public void onSeparate() {
    }

    public void onAddConferenceParticipants(List<Uri> participants) {
    }

    public void onAbort() {
    }

    public void onHold() {
    }

    public void onUnhold() {
    }

    public void onAnswer(int videoState) {
    }

    public void onAnswer() {
        onAnswer(VideoProfile.STATE_AUDIO_ONLY);
    }

    public void onDeflect(Uri address) {
    }

    public void onReject() {
    }

    public void onReject(int rejectReason) {
    }

    public void onReject(String replyMessage) {
    }

    public void onSilence() {
    }

    public void onPostDialContinue(boolean proceed) {
    }

    public void onPullExternalCall() {
    }

    public void onCallEvent(String event, Bundle extras) {
    }

    public void onHandoverComplete() {
    }

    public void onExtrasChanged(Bundle extras) {
    }

    public void onShowIncomingCallUi() {
    }

    public void onStartRtt(RttTextStream rttTextStream) {
    }

    public void onStopRtt() {
    }

    public void handleRttUpgradeResponse(RttTextStream rttTextStream) {
    }

    public static Connection createFailedConnection(DisconnectCause disconnectCause) {
        Connection failed =
                new Connection() {};
        failed.setDisconnected(
                disconnectCause != null
                        ? disconnectCause
                        : new DisconnectCause(DisconnectCause.ERROR));
        return failed;
    }

    public static Connection createCanceledConnection() {
        Connection canceled =
                new Connection() {};
        canceled.setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
        return canceled;
    }

    public final void notifyConferenceMergeFailed() {
        sendConnectionEvent(EVENT_CALL_MERGE_FAILED, null);
    }

    public void sendConnectionEvent(String event, Bundle extras) {
        for (Listener l : mListeners) {
            l.onConnectionEvent(this, event, extras);
        }
    }

    public final int getCallerNumberVerificationStatus() {
        return mCallerNumberVerificationStatus;
    }

    public final void setCallerNumberVerificationStatus(int callerNumberVerificationStatus) {
        mCallerNumberVerificationStatus = callerNumberVerificationStatus;
    }

    private void setState(int state) {
        if (mState == STATE_DISCONNECTED && mState != state) {
            return;
        }
        if (mState != state) {
            mState = state;
            onStateChanged(state);
            for (Listener l : mListeners) {
                l.onStateChanged(this, state);
            }
        }
    }
}
