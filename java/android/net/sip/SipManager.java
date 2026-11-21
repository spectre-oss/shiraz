// SPDX-License-Identifier: GPL-3.0-only
package android.net.sip;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.text.ParseException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SipManager {
    public static final int INCOMING_CALL_RESULT_CODE = 101;
    public static final String EXTRA_CALL_ID = "android:sipCallID";
    public static final String EXTRA_OFFER_SD = "android:sipOfferSD";

    private final Context mContext;
    private final Map<String, SipProfile> mOpenProfiles =
            new ConcurrentHashMap<String, SipProfile>();
    private final Map<String, Boolean> mRegistered =
            new ConcurrentHashMap<String, Boolean>();
    private final Map<String, SipRegistrationListener> mListeners =
            new ConcurrentHashMap<String, SipRegistrationListener>();

    private SipManager(Context context) {
        this.mContext = context;
    }

    public static SipManager newInstance(Context context) {
        return context == null ? null : new SipManager(context);
    }

    public static boolean isApiSupported(Context context) {
        return false;
    }

    public static boolean isVoipSupported(Context context) {
        return false;
    }

    public static boolean isSipWifiOnly(Context context) {
        return true;
    }

    public void open(SipProfile localProfile) throws SipException {
        Objects.requireNonNull(localProfile, "localProfile");
        mOpenProfiles.put(localProfile.getUriString(), localProfile);
    }

    public void open(
            SipProfile localProfile,
            PendingIntent incomingCallPendingIntent,
            SipRegistrationListener listener)
            throws SipException {
        Objects.requireNonNull(localProfile, "localProfile");
        if (incomingCallPendingIntent == null) {
            throw new NullPointerException("incomingCallPendingIntent cannot be null");
        }
        String uri = localProfile.getUriString();
        mOpenProfiles.put(uri, localProfile);
        if (listener != null) {
            mListeners.put(uri, listener);
        }
        if (localProfile.getAutoRegistration()) {
            mRegistered.put(uri, Boolean.TRUE);
            if (listener != null) {
                listener.onRegistering(uri);
                listener.onRegistrationDone(uri, System.currentTimeMillis() + 3600_000L);
            }
        }
    }

    public void setRegistrationListener(String localProfileUri, SipRegistrationListener listener)
            throws SipException {
        if (localProfileUri == null) {
            return;
        }
        if (listener == null) {
            mListeners.remove(localProfileUri);
        } else {
            mListeners.put(localProfileUri, listener);
        }
    }

    public void close(String localProfileUri) throws SipException {
        if (localProfileUri != null) {
            mOpenProfiles.remove(localProfileUri);
            mRegistered.remove(localProfileUri);
            mListeners.remove(localProfileUri);
        }
    }

    public boolean isOpened(String localProfileUri) throws SipException {
        return localProfileUri != null && mOpenProfiles.containsKey(localProfileUri);
    }

    public boolean isRegistered(String localProfileUri) throws SipException {
        return localProfileUri != null
                && Boolean.TRUE.equals(mRegistered.get(localProfileUri));
    }

    public SipAudioCall makeAudioCall(
            SipProfile localProfile,
            SipProfile peerProfile,
            SipAudioCall.Listener listener,
            int timeout)
            throws SipException {
        SipAudioCall call = new SipAudioCall(mContext, localProfile);
        call.setListener(listener);
        SipSession session = createSipSession(localProfile, null);
        call.makeCall(peerProfile, session, timeout);
        return call;
    }

    public SipAudioCall makeAudioCall(
            String localProfileUri,
            String peerProfileUri,
            SipAudioCall.Listener listener,
            int timeout)
            throws SipException {
        try {
            return makeAudioCall(
                    new SipProfile.Builder(localProfileUri).build(),
                    new SipProfile.Builder(peerProfileUri).build(),
                    listener,
                    timeout);
        } catch (ParseException e) {
            throw new SipException("build SipProfile", e);
        }
    }

    public SipAudioCall takeAudioCall(Intent incomingCallIntent, SipAudioCall.Listener listener)
            throws SipException {
        if (incomingCallIntent == null) {
            throw new SipException("Cannot retrieve session with null intent");
        }
        SipSession session = getSessionFor(incomingCallIntent);
        SipAudioCall call = new SipAudioCall(mContext, session.getLocalProfile());
        call.attachCall(session, getOfferSessionDescription(incomingCallIntent));
        call.setListener(listener);
        return call;
    }

    public static boolean isIncomingCallIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        return getCallId(intent) != null && getOfferSessionDescription(intent) != null;
    }

    public static String getCallId(Intent incomingCallIntent) {
        return incomingCallIntent == null
                ? null
                : incomingCallIntent.getStringExtra(EXTRA_CALL_ID);
    }

    public static String getOfferSessionDescription(Intent incomingCallIntent) {
        return incomingCallIntent == null
                ? null
                : incomingCallIntent.getStringExtra(EXTRA_OFFER_SD);
    }

    public void register(
            SipProfile localProfile, int expiryTime, SipRegistrationListener listener)
            throws SipException {
        Objects.requireNonNull(localProfile, "localProfile");
        String uri = localProfile.getUriString();
        mOpenProfiles.put(uri, localProfile);
        mRegistered.put(uri, Boolean.TRUE);
        if (listener != null) {
            mListeners.put(uri, listener);
            listener.onRegistering(uri);
            listener.onRegistrationDone(uri, System.currentTimeMillis() + expiryTime * 1000L);
        }
    }

    public void unregister(SipProfile localProfile, SipRegistrationListener listener)
            throws SipException {
        Objects.requireNonNull(localProfile, "localProfile");
        String uri = localProfile.getUriString();
        mRegistered.remove(uri);
        if (listener != null) {
            listener.onRegistrationDone(uri, 0L);
        }
    }

    public SipSession getSessionFor(Intent incomingCallIntent) throws SipException {
        if (!isIncomingCallIntent(incomingCallIntent)) {
            throw new SipException("Not an incoming call intent");
        }
        try {
            return new SipSession(new SipProfile.Builder("user", "localhost").build(), null);
        } catch (ParseException e) {
            throw new SipException("Failed to create default session", e);
        }
    }

    public SipSession createSipSession(SipProfile localProfile, SipSession.Listener listener)
            throws SipException {
        return new SipSession(Objects.requireNonNull(localProfile, "localProfile"), listener);
    }
}
