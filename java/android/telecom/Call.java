// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Represents a phone call presented to an {@link InCallService}. */
public final class Call {
    public static final int STATE_NEW = 0;
    public static final int STATE_DIALING = 1;
    public static final int STATE_RINGING = 2;
    public static final int STATE_HOLDING = 3;
    public static final int STATE_ACTIVE = 4;
    public static final int STATE_DISCONNECTED = 7;
    public static final int STATE_SELECT_PHONE_ACCOUNT = 8;
    public static final int STATE_CONNECTING = 9;
    public static final int STATE_DISCONNECTING = 10;
    public static final int STATE_PULLING_CALL = 11;
    public static final int STATE_AUDIO_PROCESSING = 12;
    public static final int STATE_SIMULATED_RINGING = 13;

    public static final String AVAILABLE_PHONE_ACCOUNTS = "selectPhoneAccountAccounts";
    public static final String EXTRA_LAST_EMERGENCY_CALLBACK_TIME_MILLIS =
            "android.telecom.extra.LAST_EMERGENCY_CALLBACK_TIME_MILLIS";
    public static final String EXTRA_SILENT_RINGING_REQUESTED =
            "android.telecom.extra.SILENT_RINGING_REQUESTED";
    public static final String EXTRA_SUGGESTED_PHONE_ACCOUNTS =
            "android.telecom.extra.SUGGESTED_PHONE_ACCOUNTS";
    public static final String EVENT_DISPLAY_DIAGNOSTIC_MESSAGE =
            "android.telecom.event.DISPLAY_DIAGNOSTIC_MESSAGE";
    public static final String EVENT_CLEAR_DIAGNOSTIC_MESSAGE =
            "android.telecom.event.CLEAR_DIAGNOSTIC_MESSAGE";
    public static final String EXTRA_DIAGNOSTIC_MESSAGE_ID =
            "android.telecom.extra.DIAGNOSTIC_MESSAGE_ID";
    public static final String EXTRA_DIAGNOSTIC_MESSAGE =
            "android.telecom.extra.DIAGNOSTIC_MESSAGE";
    public static final String EXTRA_IS_SUPPRESSED_BY_DO_NOT_DISTURB =
            "android.telecom.extra.IS_SUPPRESSED_BY_DO_NOT_DISTURB";
    public static final String EXTRA_ASSERTED_DISPLAY_NAME =
            "android.telecom.extra.ASSERTED_DISPLAY_NAME";
    public static final String EXTRA_IS_BUSINESS_CALL =
            "android.telecom.extra.IS_BUSINESS_CALL";

    public static final int REJECT_REASON_DECLINED = 1;
    public static final int REJECT_REASON_UNWANTED = 2;

    public static class Details {
        public static final int DIRECTION_UNKNOWN = -1;
        public static final int DIRECTION_INCOMING = 0;
        public static final int DIRECTION_OUTGOING = 1;

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
        public static final int CAPABILITY_CAN_PAUSE_VIDEO = 0x00100000;
        public static final int CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO = 0x00400000;
        public static final int CAPABILITY_CAN_PULL_CALL = 0x00800000;
        public static final int CAPABILITY_SUPPORT_DEFLECT = 0x01000000;
        public static final int CAPABILITY_ADD_PARTICIPANT = 0x02000000;
        public static final int CAPABILITY_REMOTE_PARTY_SUPPORTS_RTT = 0x10000000;

        public static final int PROPERTY_CONFERENCE = 0x00000001;
        public static final int PROPERTY_GENERIC_CONFERENCE = 0x00000002;
        public static final int PROPERTY_EMERGENCY_CALLBACK_MODE = 0x00000004;
        public static final int PROPERTY_WIFI = 0x00000008;
        public static final int PROPERTY_HIGH_DEF_AUDIO = 0x00000010;
        public static final int PROPERTY_ENTERPRISE_CALL = 0x00000020;
        public static final int PROPERTY_IS_EXTERNAL_CALL = 0x00000040;
        public static final int PROPERTY_HAS_CDMA_VOICE_PRIVACY = 0x00000080;
        public static final int PROPERTY_SELF_MANAGED = 0x00000100;
        public static final int PROPERTY_ASSISTED_DIALING = 0x00000200;
        public static final int PROPERTY_RTT = 0x00000400;
        public static final int PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL = 0x00000800;
        public static final int PROPERTY_VOIP_AUDIO_MODE = 0x00001000;
        public static final int PROPERTY_IS_ADHOC_CONFERENCE = 0x00002000;
        public static final int PROPERTY_CROSS_SIM = 0x00004000;
        public static final int PROPERTY_IS_TRANSACTIONAL = 0x00008000;

        private final int mState;
        private final String mTelecomCallId;
        private final Uri mHandle;
        private final int mHandlePresentation;
        private final String mCallerDisplayName;
        private final int mCallerDisplayNamePresentation;
        private final PhoneAccountHandle mAccountHandle;
        private final int mCallCapabilities;
        private final int mCallProperties;
        private final DisconnectCause mDisconnectCause;
        private final long mConnectTimeMillis;
        private final GatewayInfo mGatewayInfo;
        private final int mVideoState;
        private final StatusHints mStatusHints;
        private final Bundle mExtras;
        private final Bundle mIntentExtras;
        private final long mCreationTimeMillis;
        private final String mContactDisplayName;
        private final int mCallDirection;
        private final int mCallerNumberVerificationStatus;
        private final Uri mContactPhotoUri;

        Details(
                int state,
                String telecomCallId,
                Uri handle,
                int handlePresentation,
                String callerDisplayName,
                int callerDisplayNamePresentation,
                PhoneAccountHandle accountHandle,
                int capabilities,
                int properties,
                DisconnectCause disconnectCause,
                long connectTimeMillis,
                GatewayInfo gatewayInfo,
                int videoState,
                StatusHints statusHints,
                Bundle extras,
                Bundle intentExtras,
                long creationTimeMillis,
                int callDirection,
                int callerNumberVerificationStatus,
                String contactDisplayName,
                Uri contactPhotoUri) {
            this.mState = state;
            this.mTelecomCallId = telecomCallId;
            this.mHandle = handle;
            this.mHandlePresentation = handlePresentation;
            this.mCallerDisplayName = callerDisplayName;
            this.mCallerDisplayNamePresentation = callerDisplayNamePresentation;
            this.mAccountHandle = accountHandle;
            this.mCallCapabilities = capabilities;
            this.mCallProperties = properties;
            this.mDisconnectCause = disconnectCause;
            this.mConnectTimeMillis = connectTimeMillis;
            this.mGatewayInfo = gatewayInfo;
            this.mVideoState = videoState;
            this.mStatusHints = statusHints;
            this.mExtras = extras;
            this.mIntentExtras = intentExtras;
            this.mCreationTimeMillis = creationTimeMillis;
            this.mCallDirection = callDirection;
            this.mCallerNumberVerificationStatus = callerNumberVerificationStatus;
            this.mContactDisplayName = contactDisplayName;
            this.mContactPhotoUri = contactPhotoUri;
        }

        public static boolean can(int capabilities, int capability) {
            return (capabilities & capability) == capability;
        }

        public boolean can(int capability) {
            return can(mCallCapabilities, capability);
        }

        public static String capabilitiesToString(int capabilities) {
            StringBuilder sb = new StringBuilder("[Capabilities:");
            if (can(capabilities, CAPABILITY_HOLD)) sb.append(" CAPABILITY_HOLD");
            if (can(capabilities, CAPABILITY_SUPPORT_HOLD)) sb.append(" CAPABILITY_SUPPORT_HOLD");
            if (can(capabilities, CAPABILITY_MERGE_CONFERENCE)) {
                sb.append(" CAPABILITY_MERGE_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_SWAP_CONFERENCE)) {
                sb.append(" CAPABILITY_SWAP_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_RESPOND_VIA_TEXT)) {
                sb.append(" CAPABILITY_RESPOND_VIA_TEXT");
            }
            if (can(capabilities, CAPABILITY_MUTE)) sb.append(" CAPABILITY_MUTE");
            if (can(capabilities, CAPABILITY_MANAGE_CONFERENCE)) {
                sb.append(" CAPABILITY_MANAGE_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_SEPARATE_FROM_CONFERENCE)) {
                sb.append(" CAPABILITY_SEPARATE_FROM_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_DISCONNECT_FROM_CONFERENCE)) {
                sb.append(" CAPABILITY_DISCONNECT_FROM_CONFERENCE");
            }
            if (can(capabilities, CAPABILITY_CAN_PAUSE_VIDEO)) {
                sb.append(" CAPABILITY_CAN_PAUSE_VIDEO");
            }
            if (can(capabilities, CAPABILITY_CAN_PULL_CALL)) {
                sb.append(" CAPABILITY_CAN_PULL_CALL");
            }
            if (can(capabilities, CAPABILITY_SUPPORT_DEFLECT)) {
                sb.append(" CAPABILITY_SUPPORT_DEFLECT");
            }
            if (can(capabilities, CAPABILITY_ADD_PARTICIPANT)) {
                sb.append(" CAPABILITY_ADD_PARTICIPANT");
            }
            if (can(capabilities, CAPABILITY_REMOTE_PARTY_SUPPORTS_RTT)) {
                sb.append(" CAPABILITY_REMOTE_PARTY_SUPPORTS_RTT");
            }
            sb.append("]");
            return sb.toString();
        }

        public static boolean hasProperty(int properties, int property) {
            return (properties & property) == property;
        }

        public boolean hasProperty(int property) {
            return hasProperty(mCallProperties, property);
        }

        public static String propertiesToString(int properties) {
            StringBuilder sb = new StringBuilder("[Properties:");
            if (hasProperty(properties, PROPERTY_CONFERENCE)) sb.append(" PROPERTY_CONFERENCE");
            if (hasProperty(properties, PROPERTY_GENERIC_CONFERENCE)) {
                sb.append(" PROPERTY_GENERIC_CONFERENCE");
            }
            if (hasProperty(properties, PROPERTY_WIFI)) sb.append(" PROPERTY_WIFI");
            if (hasProperty(properties, PROPERTY_HIGH_DEF_AUDIO)) {
                sb.append(" PROPERTY_HIGH_DEF_AUDIO");
            }
            if (hasProperty(properties, PROPERTY_IS_EXTERNAL_CALL)) {
                sb.append(" PROPERTY_IS_EXTERNAL_CALL");
            }
            if (hasProperty(properties, PROPERTY_SELF_MANAGED)) {
                sb.append(" PROPERTY_SELF_MANAGED");
            }
            if (hasProperty(properties, PROPERTY_RTT)) sb.append(" PROPERTY_RTT");
            if (hasProperty(properties, PROPERTY_VOIP_AUDIO_MODE)) {
                sb.append(" PROPERTY_VOIP_AUDIO_MODE");
            }
            if (hasProperty(properties, PROPERTY_IS_ADHOC_CONFERENCE)) {
                sb.append(" PROPERTY_IS_ADHOC_CONFERENCE");
            }
            if (hasProperty(properties, PROPERTY_IS_TRANSACTIONAL)) {
                sb.append(" PROPERTY_IS_TRANSACTIONAL");
            }
            sb.append("]");
            return sb.toString();
        }

        public final int getState() {
            return mState;
        }

        public String getId() {
            return mTelecomCallId;
        }

        public Uri getHandle() {
            return mHandle;
        }

        public int getHandlePresentation() {
            return mHandlePresentation;
        }

        public Uri getContactPhotoUri() {
            return mContactPhotoUri;
        }

        public String getCallerDisplayName() {
            return mCallerDisplayName;
        }

        public int getCallerDisplayNamePresentation() {
            return mCallerDisplayNamePresentation;
        }

        public PhoneAccountHandle getAccountHandle() {
            return mAccountHandle;
        }

        public int getCallCapabilities() {
            return mCallCapabilities;
        }

        public int getCallProperties() {
            return mCallProperties;
        }

        public DisconnectCause getDisconnectCause() {
            return mDisconnectCause;
        }

        public final long getConnectTimeMillis() {
            return mConnectTimeMillis;
        }

        public GatewayInfo getGatewayInfo() {
            return mGatewayInfo;
        }

        public int getVideoState() {
            return mVideoState;
        }

        public StatusHints getStatusHints() {
            return mStatusHints;
        }

        public Bundle getExtras() {
            return mExtras;
        }

        public Bundle getIntentExtras() {
            return mIntentExtras;
        }

        public long getCreationTimeMillis() {
            return mCreationTimeMillis;
        }

        public String getContactDisplayName() {
            return mContactDisplayName;
        }

        public int getCallDirection() {
            return mCallDirection;
        }

        public int getCallerNumberVerificationStatus() {
            return mCallerNumberVerificationStatus;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Details)) {
                return false;
            }
            Details d = (Details) o;
            return mState == d.mState
                    && Objects.equals(mTelecomCallId, d.mTelecomCallId)
                    && Objects.equals(mHandle, d.mHandle)
                    && mHandlePresentation == d.mHandlePresentation
                    && Objects.equals(mCallerDisplayName, d.mCallerDisplayName)
                    && mCallerDisplayNamePresentation == d.mCallerDisplayNamePresentation
                    && Objects.equals(mAccountHandle, d.mAccountHandle)
                    && mCallCapabilities == d.mCallCapabilities
                    && mCallProperties == d.mCallProperties
                    && Objects.equals(mDisconnectCause, d.mDisconnectCause)
                    && mConnectTimeMillis == d.mConnectTimeMillis
                    && Objects.equals(mGatewayInfo, d.mGatewayInfo)
                    && mVideoState == d.mVideoState
                    && Objects.equals(mStatusHints, d.mStatusHints)
                    && mCreationTimeMillis == d.mCreationTimeMillis
                    && Objects.equals(mContactDisplayName, d.mContactDisplayName)
                    && mCallDirection == d.mCallDirection
                    && mCallerNumberVerificationStatus == d.mCallerNumberVerificationStatus
                    && Objects.equals(mContactPhotoUri, d.mContactPhotoUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Integer.valueOf(mState),
                    mTelecomCallId,
                    mHandle,
                    Integer.valueOf(mHandlePresentation),
                    mCallerDisplayName,
                    Integer.valueOf(mCallerDisplayNamePresentation),
                    mAccountHandle,
                    Integer.valueOf(mCallCapabilities),
                    Integer.valueOf(mCallProperties),
                    mDisconnectCause,
                    Long.valueOf(mConnectTimeMillis),
                    mGatewayInfo,
                    Integer.valueOf(mVideoState),
                    mStatusHints,
                    Long.valueOf(mCreationTimeMillis),
                    mContactDisplayName,
                    Integer.valueOf(mCallDirection),
                    Integer.valueOf(mCallerNumberVerificationStatus),
                    mContactPhotoUri);
        }

        @Override
        public String toString() {
            return "[id: "
                    + mTelecomCallId
                    + ", state: "
                    + mState
                    + ", handle: "
                    + mHandle
                    + ", caps: "
                    + capabilitiesToString(mCallCapabilities)
                    + ", props: "
                    + propertiesToString(mCallProperties)
                    + "]";
        }
    }

    public abstract static class Callback {
        public static final int HANDOVER_FAILURE_DEST_APP_REJECTED = 1;
        public static final int HANDOVER_FAILURE_NOT_SUPPORTED = 2;
        public static final int HANDOVER_FAILURE_USER_REJECTED = 3;
        public static final int HANDOVER_FAILURE_ONGOING_EMERGENCY_CALL = 4;
        public static final int HANDOVER_FAILURE_UNKNOWN = 5;

        public Callback() {
        }

        public void onStateChanged(Call call, int state) {
        }

        public void onParentChanged(Call call, Call parent) {
        }

        public void onChildrenChanged(Call call, List<Call> children) {
        }

        public void onDetailsChanged(Call call, Details details) {
        }

        public void onCannedTextResponsesLoaded(Call call, List<String> cannedTextResponses) {
        }

        public void onPostDialWait(Call call, String remainingPostDialSequence) {
        }

        public void onVideoCallChanged(Call call, InCallService.VideoCall videoCall) {
        }

        public void onCallDestroyed(Call call) {
        }

        public void onConferenceableCallsChanged(Call call, List<Call> conferenceableCalls) {
        }

        public void onConnectionEvent(Call call, String event, Bundle extras) {
        }

        public void onRttModeChanged(Call call, int mode) {
        }

        public void onRttStatusChanged(Call call, boolean enabled, RttCall rttCall) {
        }

        public void onRttRequest(Call call, int id) {
        }

        public void onRttInitiationFailure(Call call, int reason) {
        }

        public void onHandoverComplete(Call call) {
        }

        public void onHandoverFailed(Call call, int failureReason) {
        }
    }

    public static final class RttCall {
        public static final int RTT_MODE_FULL = 1;
        public static final int RTT_MODE_HCO = 2;
        public static final int RTT_MODE_VCO = 3;

        private final Connection.RttTextStream mStream;
        private volatile int mRttMode;
        private final Call mOwner;

        RttCall(Call owner, Connection.RttTextStream stream, int mode) {
            this.mOwner = owner;
            this.mStream = Objects.requireNonNull(stream, "stream");
            this.mRttMode = mode;
        }

        public int getRttAudioMode() {
            return mRttMode;
        }

        public void setRttMode(int mode) {
            mRttMode = mode;
            if (mOwner != null) {
                mOwner.notifyRttModeChanged(mode);
            }
        }

        public void write(String input) throws IOException {
            mStream.write(input);
        }

        public String read() {
            try {
                return mStream.read();
            } catch (IOException e) {
                return null;
            }
        }

        public String readImmediately() throws IOException {
            return mStream.readImmediately();
        }
    }

    private static final class CallbackRecord {
        final Callback mCallback;
        final Handler mHandler;

        CallbackRecord(Callback callback, Handler handler) {
            this.mCallback = callback;
            this.mHandler = handler;
        }
    }

    private final List<CallbackRecord> mCallbacks = new CopyOnWriteArrayList<CallbackRecord>();
    private final List<Call> mChildren = new CopyOnWriteArrayList<Call>();
    private final List<Call> mConferenceableCalls = new CopyOnWriteArrayList<Call>();
    private final List<String> mCannedTextResponses = new CopyOnWriteArrayList<String>();
    private volatile int mState;
    private volatile Call mParent;
    private volatile Call mActiveGenericChild;
    private volatile Details mDetails;
    private volatile InCallService.VideoCall mVideoCall;
    private volatile RttCall mRttCall;
    private volatile String mRemainingPostDialSequence;
    private Bundle mExtras;

    Call(Uri handle, PhoneAccountHandle accountHandle, int state, int direction) {
        this.mState = state;
        long now = System.currentTimeMillis();
        this.mExtras = new Bundle();
        this.mDetails =
                new Details(
                        state,
                        UUID.randomUUID().toString(),
                        handle,
                        TelecomManager.PRESENTATION_ALLOWED,
                        null,
                        TelecomManager.PRESENTATION_ALLOWED,
                        accountHandle,
                        Details.CAPABILITY_HOLD | Details.CAPABILITY_SUPPORT_HOLD | Details.CAPABILITY_MUTE,
                        0,
                        new DisconnectCause(DisconnectCause.UNKNOWN),
                        0L,
                        null,
                        VideoProfile.STATE_AUDIO_ONLY,
                        null,
                        this.mExtras,
                        new Bundle(),
                        now,
                        direction,
                        Connection.VERIFICATION_STATUS_NOT_VERIFIED,
                        null,
                        null);
    }

    public String getRemainingPostDialSequence() {
        return mRemainingPostDialSequence;
    }

    public void answer(int videoState) {
        setStateInternal(STATE_ACTIVE, videoState, null);
    }

    public void deflect(Uri address) {
        Objects.requireNonNull(address, "address");
        setStateInternal(
                STATE_DISCONNECTED,
                mDetails.getVideoState(),
                new DisconnectCause(DisconnectCause.CANCELED));
    }

    public void reject(boolean rejectWithMessage, String textMessage) {
        setStateInternal(
                STATE_DISCONNECTED,
                mDetails.getVideoState(),
                new DisconnectCause(DisconnectCause.REJECTED, textMessage));
    }

    public void reject(int rejectReason) {
        setStateInternal(
                STATE_DISCONNECTED,
                mDetails.getVideoState(),
                new DisconnectCause(DisconnectCause.REJECTED));
    }

    public void disconnect() {
        setStateInternal(
                STATE_DISCONNECTED,
                mDetails.getVideoState(),
                new DisconnectCause(DisconnectCause.LOCAL));
    }

    public void hold() {
        setStateInternal(STATE_HOLDING, mDetails.getVideoState(), mDetails.getDisconnectCause());
    }

    public void unhold() {
        setStateInternal(STATE_ACTIVE, mDetails.getVideoState(), mDetails.getDisconnectCause());
    }

    public void playDtmfTone(char digit) {
    }

    public void stopDtmfTone() {
    }

    public void postDialContinue(boolean proceed) {
        mRemainingPostDialSequence = null;
    }

    public void phoneAccountSelected(PhoneAccountHandle accountHandle, boolean setDefault) {
        Details d = mDetails;
        mDetails =
                new Details(
                        d.getState(),
                        d.getId(),
                        d.getHandle(),
                        d.getHandlePresentation(),
                        d.getCallerDisplayName(),
                        d.getCallerDisplayNamePresentation(),
                        accountHandle,
                        d.getCallCapabilities(),
                        d.getCallProperties(),
                        d.getDisconnectCause(),
                        d.getConnectTimeMillis(),
                        d.getGatewayInfo(),
                        d.getVideoState(),
                        d.getStatusHints(),
                        d.getExtras(),
                        d.getIntentExtras(),
                        d.getCreationTimeMillis(),
                        d.getCallDirection(),
                        d.getCallerNumberVerificationStatus(),
                        d.getContactDisplayName(),
                        d.getContactPhotoUri());
        notifyDetailsChanged(mDetails);
    }

    public void conference(Call callToConferenceWith) {
        if (callToConferenceWith != null && !mChildren.contains(callToConferenceWith)) {
            mChildren.add(callToConferenceWith);
            callToConferenceWith.mParent = this;
            notifyChildrenChanged();
        }
    }

    public void splitFromConference() {
        Call parent = mParent;
        if (parent != null) {
            mParent = null;
            parent.mChildren.remove(this);
            parent.notifyChildrenChanged();
            notifyParentChanged(null);
        }
    }

    public void mergeConference() {
    }

    public void swapConference() {
    }

    public void addConferenceParticipants(List<Uri> participants) {
        Objects.requireNonNull(participants, "participants");
    }

    public void pullExternalCall() {
        setStateInternal(
                STATE_PULLING_CALL, mDetails.getVideoState(), mDetails.getDisconnectCause());
    }

    public void sendCallEvent(final String event, final Bundle extras) {
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onConnectionEvent(Call.this, event, extras);
                        }
                    });
        }
    }

    public void sendRttRequest() {
        try {
            Connection.RttTextStream[] pair = Connection.RttTextStream.createPair();
            mRttCall = new RttCall(this, pair[0], RttCall.RTT_MODE_FULL);
            for (final CallbackRecord rec : mCallbacks) {
                dispatch(
                        rec,
                        new Runnable() {
                            @Override
                            public void run() {
                                rec.mCallback.onRttStatusChanged(Call.this, true, mRttCall);
                            }
                        });
            }
        } catch (IOException ignored) {
            for (final CallbackRecord rec : mCallbacks) {
                dispatch(
                        rec,
                        new Runnable() {
                            @Override
                            public void run() {
                                rec.mCallback.onRttInitiationFailure(
                                        Call.this,
                                        Connection.RttModifyStatus.SESSION_MODIFY_REQUEST_FAIL);
                            }
                        });
            }
        }
    }

    public void respondToRttRequest(int id, boolean accept) {
        if (accept) {
            sendRttRequest();
        }
    }

    public void handoverTo(PhoneAccountHandle toHandle, int videoState, Bundle extras) {
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onHandoverComplete(Call.this);
                        }
                    });
        }
    }

    public void stopRtt() {
        mRttCall = null;
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onRttStatusChanged(Call.this, false, null);
                        }
                    });
        }
    }

    public void putExtras(Bundle extras) {
        if (extras == null) {
            return;
        }
        if (mExtras == null) {
            mExtras = new Bundle();
        }
        mExtras.putAll(extras);
        notifyDetailsChanged(mDetails);
    }

    public void removeExtras(List<String> keys) {
        if (mExtras != null && keys != null) {
            for (String key : keys) {
                mExtras.remove(key);
            }
            notifyDetailsChanged(mDetails);
        }
    }

    public void removeExtras(String... keys) {
        if (keys != null) {
            removeExtras(Arrays.asList(keys));
        }
    }

    public Call getParent() {
        return mParent;
    }

    public List<Call> getChildren() {
        return Collections.unmodifiableList(new ArrayList<Call>(mChildren));
    }

    public List<Call> getConferenceableCalls() {
        return Collections.unmodifiableList(new ArrayList<Call>(mConferenceableCalls));
    }

    public int getState() {
        return mState;
    }

    public Call getGenericConferenceActiveChildCall() {
        return mActiveGenericChild;
    }

    public List<String> getCannedTextResponses() {
        return Collections.unmodifiableList(new ArrayList<String>(mCannedTextResponses));
    }

    public InCallService.VideoCall getVideoCall() {
        return mVideoCall;
    }

    public Details getDetails() {
        return mDetails;
    }

    public RttCall getRttCall() {
        return mRttCall;
    }

    public boolean isRttActive() {
        return mRttCall != null;
    }

    public void registerCallback(Callback callback) {
        registerCallback(callback, null);
    }

    public void registerCallback(Callback callback, Handler handler) {
        if (callback == null) {
            return;
        }
        unregisterCallback(callback);
        mCallbacks.add(new CallbackRecord(callback, handler));
    }

    public void unregisterCallback(Callback callback) {
        if (callback == null) {
            return;
        }
        for (CallbackRecord rec : mCallbacks) {
            if (rec.mCallback == callback) {
                mCallbacks.remove(rec);
            }
        }
    }

    @Override
    public String toString() {
        return "Call[id=" + mDetails.getId() + ", state=" + mState + "]";
    }

    private void setStateInternal(int newState, int videoState, DisconnectCause cause) {
        int prev = mState;
        mState = newState;
        Details d = mDetails;
        long connectTime =
                newState == STATE_ACTIVE && d.getConnectTimeMillis() == 0L
                        ? System.currentTimeMillis()
                        : d.getConnectTimeMillis();
        mDetails =
                new Details(
                        newState,
                        d.getId(),
                        d.getHandle(),
                        d.getHandlePresentation(),
                        d.getCallerDisplayName(),
                        d.getCallerDisplayNamePresentation(),
                        d.getAccountHandle(),
                        d.getCallCapabilities(),
                        d.getCallProperties(),
                        cause != null ? cause : d.getDisconnectCause(),
                        connectTime,
                        d.getGatewayInfo(),
                        videoState,
                        d.getStatusHints(),
                        mExtras,
                        d.getIntentExtras(),
                        d.getCreationTimeMillis(),
                        d.getCallDirection(),
                        d.getCallerNumberVerificationStatus(),
                        d.getContactDisplayName(),
                        d.getContactPhotoUri());
        if (prev != newState) {
            for (final CallbackRecord rec : mCallbacks) {
                final int stateSnapshot = newState;
                dispatch(
                        rec,
                        new Runnable() {
                            @Override
                            public void run() {
                                rec.mCallback.onStateChanged(Call.this, stateSnapshot);
                            }
                        });
            }
        }
        notifyDetailsChanged(mDetails);
    }

    private void notifyDetailsChanged(final Details details) {
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onDetailsChanged(Call.this, details);
                        }
                    });
        }
    }

    private void notifyChildrenChanged() {
        final List<Call> snapshot = getChildren();
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onChildrenChanged(Call.this, snapshot);
                        }
                    });
        }
    }

    private void notifyParentChanged(final Call parent) {
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onParentChanged(Call.this, parent);
                        }
                    });
        }
    }

    void notifyRttModeChanged(final int mode) {
        for (final CallbackRecord rec : mCallbacks) {
            dispatch(
                    rec,
                    new Runnable() {
                        @Override
                        public void run() {
                            rec.mCallback.onRttModeChanged(Call.this, mode);
                        }
                    });
        }
    }

    private static void dispatch(CallbackRecord rec, Runnable action) {
        if (rec.mHandler != null) {
            rec.mHandler.post(action);
        } else {
            action.run();
        }
    }
}
