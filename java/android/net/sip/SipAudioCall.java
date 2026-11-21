// SPDX-License-Identifier: GPL-3.0-only
package android.net.sip;

import android.content.Context;
import android.os.Message;
import java.util.Objects;

public class SipAudioCall {
    private final Context mContext;
    private final SipProfile mLocalProfile;
    private SipProfile mPeerProfile;
    private SipSession mSipSession;
    private Listener mListener;
    private boolean mInCall;
    private boolean mHold;
    private boolean mMuted;

    public SipAudioCall(Context context, SipProfile localProfile) {
        this.mContext = Objects.requireNonNull(context, "context");
        this.mLocalProfile = Objects.requireNonNull(localProfile, "localProfile");
    }

    public void setListener(Listener listener) {
        setListener(listener, false);
    }

    public void setListener(Listener listener, boolean callbackImmediately) {
        this.mListener = listener;
        if (callbackImmediately && listener != null) {
            if (mInCall) {
                if (mHold) {
                    listener.onCallHeld(this);
                } else {
                    listener.onCallEstablished(this);
                }
            } else {
                listener.onReadyToCall(this);
            }
        }
    }

    public boolean isInCall() {
        return mInCall;
    }

    public boolean isOnHold() {
        return mHold;
    }

    public void close() {
        this.mInCall = false;
        this.mHold = false;
        this.mSipSession = null;
    }

    public SipProfile getLocalProfile() {
        return mLocalProfile;
    }

    public SipProfile getPeerProfile() {
        return mPeerProfile != null
                ? mPeerProfile
                : (mSipSession != null ? mSipSession.getPeerProfile() : null);
    }

    public int getState() {
        return mSipSession != null ? mSipSession.getState() : SipSession.State.READY_TO_CALL;
    }

    public void attachCall(SipSession session, String sessionDescription) throws SipException {
        this.mSipSession = Objects.requireNonNull(session, "session");
        this.mPeerProfile = session.getPeerProfile();
    }

    public void makeCall(SipProfile peerProfile, SipSession sipSession, int timeout)
            throws SipException {
        this.mPeerProfile = Objects.requireNonNull(peerProfile, "peerProfile");
        this.mSipSession = Objects.requireNonNull(sipSession, "sipSession");
        sipSession.makeCall(peerProfile, "", timeout);
        if (mListener != null) {
            mListener.onCalling(this);
        }
    }

    public void endCall() throws SipException {
        this.mInCall = false;
        this.mHold = false;
        if (mSipSession != null) {
            mSipSession.endCall();
        }
        if (mListener != null) {
            mListener.onCallEnded(this);
        }
    }

    public void holdCall(int timeout) throws SipException {
        this.mHold = true;
        if (mListener != null) {
            mListener.onCallHeld(this);
        }
    }

    public void answerCall(int timeout) throws SipException {
        this.mInCall = true;
        this.mHold = false;
        if (mSipSession != null) {
            mSipSession.answerCall("", timeout);
        }
        if (mListener != null) {
            mListener.onCallEstablished(this);
        }
    }

    public void continueCall(int timeout) throws SipException {
        this.mHold = false;
        if (mListener != null) {
            mListener.onCallEstablished(this);
        }
    }

    public void toggleMute() {
        this.mMuted = !mMuted;
    }

    public boolean isMuted() {
        return mMuted;
    }

    public void setSpeakerMode(boolean speakerMode) {}

    public void sendDtmf(int code) {
        sendDtmf(code, null);
    }

    public void sendDtmf(int code, Message result) {
        if (code < 0 || code > 15) {
            return;
        }
        if (result != null) {
            result.sendToTarget();
        }
    }

    public void startAudio() {}

    public static class Listener {
        public Listener() {}

        public void onReadyToCall(SipAudioCall call) {
            onChanged(call);
        }

        public void onCalling(SipAudioCall call) {
            onChanged(call);
        }

        public void onRinging(SipAudioCall call, SipProfile caller) {
            onChanged(call);
        }

        public void onRingingBack(SipAudioCall call) {
            onChanged(call);
        }

        public void onCallEstablished(SipAudioCall call) {
            onChanged(call);
        }

        public void onCallEnded(SipAudioCall call) {
            onChanged(call);
        }

        public void onCallBusy(SipAudioCall call) {
            onChanged(call);
        }

        public void onCallHeld(SipAudioCall call) {
            onChanged(call);
        }

        public void onError(SipAudioCall call, int errorCode, String errorMessage) {}

        public void onChanged(SipAudioCall call) {}
    }
}
