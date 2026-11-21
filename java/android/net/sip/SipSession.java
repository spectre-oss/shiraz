// SPDX-License-Identifier: GPL-3.0-only
package android.net.sip;

import java.util.UUID;

public final class SipSession {
    private final SipProfile mLocalProfile;
    private SipProfile mPeerProfile;
    private Listener mListener;
    private int mState = State.READY_TO_CALL;
    private boolean mInCall;
    private final String mCallId = UUID.randomUUID().toString();

    SipSession(SipProfile localProfile, Listener listener) {
        this.mLocalProfile = localProfile;
        this.mListener = listener;
    }

    public String getLocalIp() {
        return "127.0.0.1";
    }

    public SipProfile getLocalProfile() {
        return mLocalProfile;
    }

    public SipProfile getPeerProfile() {
        return mPeerProfile;
    }

    public int getState() {
        return mState;
    }

    public boolean isInCall() {
        return mInCall;
    }

    public String getCallId() {
        return mCallId;
    }

    public void setListener(Listener listener) {
        this.mListener = listener;
    }

    public void register(int duration) {
        this.mState = State.READY_TO_CALL;
        if (mListener != null) {
            mListener.onRegistering(this);
            mListener.onRegistrationDone(this, duration);
        }
    }

    public void unregister() {
        this.mState = State.READY_TO_CALL;
        if (mListener != null) {
            mListener.onRegistrationDone(this, 0);
        }
    }

    public void makeCall(SipProfile callee, String sessionDescription, int timeout) {
        this.mPeerProfile = callee;
        this.mState = State.OUTGOING_CALL;
        if (mListener != null) {
            mListener.onCalling(this);
        }
    }

    public void answerCall(String sessionDescription, int timeout) {
        this.mState = State.IN_CALL;
        this.mInCall = true;
        if (mListener != null) {
            mListener.onCallEstablished(this, sessionDescription);
        }
    }

    public void endCall() {
        this.mState = State.READY_TO_CALL;
        this.mInCall = false;
        if (mListener != null) {
            mListener.onCallEnded(this);
        }
    }

    public void changeCall(String sessionDescription, int timeout) {
        if (mListener != null) {
            mListener.onCallEstablished(this, sessionDescription);
        }
    }

    public static class Listener {
        public Listener() {}

        public void onCalling(SipSession session) {}

        public void onRinging(SipSession session, SipProfile caller, String sessionDescription) {}

        public void onRingingBack(SipSession session) {}

        public void onCallEstablished(SipSession session, String sessionDescription) {}

        public void onCallEnded(SipSession session) {}

        public void onCallBusy(SipSession session) {}

        public void onError(SipSession session, int errorCode, String errorMessage) {}

        public void onCallChangeFailed(SipSession session, int errorCode, String errorMessage) {}

        public void onRegistering(SipSession session) {}

        public void onRegistrationDone(SipSession session, int duration) {}

        public void onRegistrationFailed(
                SipSession session, int errorCode, String errorMessage) {}

        public void onRegistrationTimeout(SipSession session) {}
    }

    public static class State {
        public static final int READY_TO_CALL = 0;
        public static final int REGISTERING = 1;
        public static final int DEREGISTERING = 2;
        public static final int INCOMING_CALL = 3;
        public static final int INCOMING_CALL_ANSWERING = 4;
        public static final int OUTGOING_CALL = 5;
        public static final int OUTGOING_CALL_RING_BACK = 6;
        public static final int OUTGOING_CALL_CANCELING = 7;
        public static final int IN_CALL = 8;
        public static final int PINGING = 9;
        public static final int NOT_DEFINED = 101;

        private State() {}

        public static String toString(int state) {
            switch (state) {
                case READY_TO_CALL:
                    return "READY_TO_CALL";
                case REGISTERING:
                    return "REGISTERING";
                case DEREGISTERING:
                    return "DEREGISTERING";
                case INCOMING_CALL:
                    return "INCOMING_CALL";
                case INCOMING_CALL_ANSWERING:
                    return "INCOMING_CALL_ANSWERING";
                case OUTGOING_CALL:
                    return "OUTGOING_CALL";
                case OUTGOING_CALL_RING_BACK:
                    return "OUTGOING_CALL_RING_BACK";
                case OUTGOING_CALL_CANCELING:
                    return "OUTGOING_CALL_CANCELING";
                case IN_CALL:
                    return "IN_CALL";
                case PINGING:
                    return "PINGING";
                default:
                    return "NOT_DEFINED";
            }
        }
    }
}
