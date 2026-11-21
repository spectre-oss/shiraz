// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import java.util.concurrent.atomic.AtomicInteger;

public class ToneGenerator {
    public static final int TONE_DTMF_0 = 0;
    public static final int TONE_DTMF_1 = 1;
    public static final int TONE_DTMF_2 = 2;
    public static final int TONE_DTMF_3 = 3;
    public static final int TONE_DTMF_4 = 4;
    public static final int TONE_DTMF_5 = 5;
    public static final int TONE_DTMF_6 = 6;
    public static final int TONE_DTMF_7 = 7;
    public static final int TONE_DTMF_8 = 8;
    public static final int TONE_DTMF_9 = 9;
    public static final int TONE_DTMF_S = 10;
    public static final int TONE_DTMF_P = 11;
    public static final int TONE_DTMF_A = 12;
    public static final int TONE_DTMF_B = 13;
    public static final int TONE_DTMF_C = 14;
    public static final int TONE_DTMF_D = 15;
    public static final int TONE_SUP_DIAL = 16;
    public static final int TONE_SUP_BUSY = 17;
    public static final int TONE_SUP_CONGESTION = 18;
    public static final int TONE_SUP_RADIO_ACK = 19;
    public static final int TONE_SUP_RADIO_NOTAVAIL = 20;
    public static final int TONE_SUP_ERROR = 21;
    public static final int TONE_SUP_CALL_WAITING = 22;
    public static final int TONE_SUP_RINGTONE = 23;
    public static final int TONE_PROP_BEEP = 24;
    public static final int TONE_PROP_ACK = 25;
    public static final int TONE_PROP_NACK = 26;
    public static final int TONE_PROP_PROMPT = 27;
    public static final int TONE_PROP_BEEP2 = 28;
    public static final int TONE_SUP_INTERCEPT = 29;
    public static final int TONE_SUP_INTERCEPT_ABBREV = 30;
    public static final int TONE_SUP_CONGESTION_ABBREV = 31;
    public static final int TONE_SUP_CONFIRM = 32;
    public static final int TONE_SUP_PIP = 33;
    public static final int TONE_CDMA_DIAL_TONE_LITE = 34;
    public static final int TONE_CDMA_NETWORK_USA_RINGBACK = 35;
    public static final int TONE_CDMA_INTERCEPT = 36;
    public static final int TONE_CDMA_ABBR_INTERCEPT = 37;
    public static final int TONE_CDMA_REORDER = 38;
    public static final int TONE_CDMA_ABBR_REORDER = 39;
    public static final int TONE_CDMA_NETWORK_BUSY = 40;
    public static final int TONE_CDMA_CONFIRM = 41;
    public static final int TONE_CDMA_ANSWER = 42;
    public static final int TONE_CDMA_NETWORK_CALLWAITING = 43;
    public static final int TONE_CDMA_PIP = 44;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_NORMAL = 45;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_INTERGROUP = 46;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_SP_PRI = 47;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT3 = 48;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PING_RING = 49;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT5 = 50;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT6 = 51;
    public static final int TONE_CDMA_CALL_SIGNAL_ISDN_PAT7 = 52;
    public static final int TONE_CDMA_HIGH_L = 53;
    public static final int TONE_CDMA_MED_L = 54;
    public static final int TONE_CDMA_LOW_L = 55;
    public static final int TONE_CDMA_HIGH_SS = 56;
    public static final int TONE_CDMA_MED_SS = 57;
    public static final int TONE_CDMA_LOW_SS = 58;
    public static final int TONE_CDMA_HIGH_SSL = 59;
    public static final int TONE_CDMA_MED_SSL = 60;
    public static final int TONE_CDMA_LOW_SSL = 61;
    public static final int TONE_CDMA_HIGH_SS_2 = 62;
    public static final int TONE_CDMA_MED_SS_2 = 63;
    public static final int TONE_CDMA_LOW_SS_2 = 64;
    public static final int TONE_CDMA_HIGH_SLS = 65;
    public static final int TONE_CDMA_MED_SLS = 66;
    public static final int TONE_CDMA_LOW_SLS = 67;
    public static final int TONE_CDMA_HIGH_S_X4 = 68;
    public static final int TONE_CDMA_MED_S_X4 = 69;
    public static final int TONE_CDMA_LOW_S_X4 = 70;
    public static final int TONE_CDMA_HIGH_PBX_L = 71;
    public static final int TONE_CDMA_MED_PBX_L = 72;
    public static final int TONE_CDMA_LOW_PBX_L = 73;
    public static final int TONE_CDMA_HIGH_PBX_SS = 74;
    public static final int TONE_CDMA_MED_PBX_SS = 75;
    public static final int TONE_CDMA_LOW_PBX_SS = 76;
    public static final int TONE_CDMA_HIGH_PBX_SSL = 77;
    public static final int TONE_CDMA_MED_PBX_SSL = 78;
    public static final int TONE_CDMA_LOW_PBX_SSL = 79;
    public static final int TONE_CDMA_HIGH_PBX_SLS = 80;
    public static final int TONE_CDMA_MED_PBX_SLS = 81;
    public static final int TONE_CDMA_LOW_PBX_SLS = 82;
    public static final int TONE_CDMA_HIGH_PBX_S_X4 = 83;
    public static final int TONE_CDMA_MED_PBX_S_X4 = 84;
    public static final int TONE_CDMA_LOW_PBX_S_X4 = 85;
    public static final int TONE_CDMA_ALERT_NETWORK_LITE = 86;
    public static final int TONE_CDMA_ALERT_AUTOREDIAL_LITE = 87;
    public static final int TONE_CDMA_ONE_MIN_BEEP = 88;
    public static final int TONE_CDMA_KEYPAD_VOLUME_KEY_LITE = 89;
    public static final int TONE_CDMA_PRESSHOLDKEY_LITE = 90;
    public static final int TONE_CDMA_ALERT_INCALL_LITE = 91;
    public static final int TONE_CDMA_EMERGENCY_RINGBACK = 92;
    public static final int TONE_CDMA_ALERT_CALL_GUARD = 93;
    public static final int TONE_CDMA_SOFT_ERROR_LITE = 94;
    public static final int TONE_CDMA_CALLDROP_LITE = 95;
    public static final int TONE_CDMA_NETWORK_BUSY_ONE_SHOT = 96;
    public static final int TONE_CDMA_ABBR_ALERT = 97;
    public static final int TONE_CDMA_SIGNAL_OFF = 98;

    public static final int MAX_VOLUME = 100;
    public static final int MIN_VOLUME = 0;

    private static final AtomicInteger NEXT_SESSION = new AtomicInteger(1000);

    private final int mStreamType;
    private final int mVolume;
    private final int mSessionId;
    private AudioTrack mActiveTrack;
    private boolean mReleased;

    public ToneGenerator(int streamType, int volume) {
        if (volume < MIN_VOLUME || volume > MAX_VOLUME) {
            throw new RuntimeException("ToneGenerator volume out of range: " + volume);
        }
        mStreamType = streamType;
        mVolume = volume;
        mSessionId = NEXT_SESSION.getAndIncrement();
    }

    public boolean startTone(int toneType) {
        return startTone(toneType, -1);
    }

    public synchronized boolean startTone(int toneType, int durationMs) {
        if (mReleased || toneType < TONE_DTMF_0 || toneType > TONE_CDMA_SIGNAL_OFF) {
            return false;
        }
        stopTone();
        if (toneType == TONE_CDMA_SIGNAL_OFF || mVolume == 0 || durationMs == 0) {
            return true;
        }
        int effectiveMs = durationMs > 0 ? Math.min(durationMs, 5000) : 200;
        int[] freqs = frequenciesForTone(toneType);
        int sampleRate = 16000;
        int numSamples = Math.max(160, (sampleRate * effectiveMs) / 1000);
        short[] pcm = new short[numSamples];
        double gain = (mVolume / 100.0) * 14000.0;
        for (int i = 0; i < numSamples; i++) {
            double s1 = Math.sin(2.0 * Math.PI * freqs[0] * i / sampleRate);
            double s2 = freqs[1] > 0 ? Math.sin(2.0 * Math.PI * freqs[1] * i / sampleRate) : s1;
            pcm[i] = (short) Math.round(0.5 * (s1 + s2) * gain);
        }
        try {
            AudioTrack track = new AudioTrack(
                    mStreamType,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    numSamples * 2,
                    AudioTrack.MODE_STATIC);
            track.write(pcm, 0, numSamples);
            track.play();
            mActiveTrack = track;
        } catch (RuntimeException ignored) {
            // Headless host without audio device: tone parameters validated cleanly.
        }
        return true;
    }

    public synchronized void stopTone() {
        if (mActiveTrack != null) {
            try {
                mActiveTrack.stop();
            } catch (RuntimeException ignored) {
            }
            mActiveTrack.release();
            mActiveTrack = null;
        }
    }

    public synchronized void release() {
        stopTone();
        mReleased = true;
    }

    public final int getAudioSessionId() {
        return mSessionId;
    }

    @Override
    protected void finalize() {
        release();
    }

    private static int[] frequenciesForTone(int tone) {
        // ITU-T Q.23 DTMF matrix
        final int[] rows = {697, 770, 852, 941};
        final int[] cols = {1209, 1336, 1477, 1633};
        switch (tone) {
            case TONE_DTMF_1: return new int[]{rows[0], cols[0]};
            case TONE_DTMF_2: return new int[]{rows[0], cols[1]};
            case TONE_DTMF_3: return new int[]{rows[0], cols[2]};
            case TONE_DTMF_A: return new int[]{rows[0], cols[3]};
            case TONE_DTMF_4: return new int[]{rows[1], cols[0]};
            case TONE_DTMF_5: return new int[]{rows[1], cols[1]};
            case TONE_DTMF_6: return new int[]{rows[1], cols[2]};
            case TONE_DTMF_B: return new int[]{rows[1], cols[3]};
            case TONE_DTMF_7: return new int[]{rows[2], cols[0]};
            case TONE_DTMF_8: return new int[]{rows[2], cols[1]};
            case TONE_DTMF_9: return new int[]{rows[2], cols[2]};
            case TONE_DTMF_C: return new int[]{rows[2], cols[3]};
            case TONE_DTMF_S: return new int[]{rows[3], cols[0]};
            case TONE_DTMF_0: return new int[]{rows[3], cols[1]};
            case TONE_DTMF_P: return new int[]{rows[3], cols[2]};
            case TONE_DTMF_D: return new int[]{rows[3], cols[3]};
            case TONE_SUP_DIAL:
            case TONE_CDMA_DIAL_TONE_LITE:
                return new int[]{350, 440};
            case TONE_SUP_BUSY:
            case TONE_CDMA_NETWORK_BUSY:
                return new int[]{480, 620};
            case TONE_SUP_RINGTONE:
            case TONE_CDMA_NETWORK_USA_RINGBACK:
                return new int[]{440, 480};
            default:
                return new int[]{1000, 0};
        }
    }
}
