// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.StringTokenizer;

public class Virtualizer extends AudioEffect {
    public static final int PARAM_STRENGTH_SUPPORTED = 0;
    public static final int PARAM_STRENGTH = 1;

    public static final int VIRTUALIZATION_MODE_OFF = 0;
    public static final int VIRTUALIZATION_MODE_AUTO = 1;
    public static final int VIRTUALIZATION_MODE_BINAURAL = 2;
    public static final int VIRTUALIZATION_MODE_TRANSAURAL = 3;

    public interface OnParameterChangeListener {
        void onParameterChange(Virtualizer effect, int status, int param, short value);
    }

    public static class Settings {
        public short strength;

        public Settings() {
        }

        public Settings(String settings) {
            if (settings == null) {
                throw new IllegalArgumentException("settings must not be null");
            }
            StringTokenizer st = new StringTokenizer(settings, "=;");
            if (st.countTokens() != 3) {
                throw new IllegalArgumentException("settings: " + settings);
            }
            String key = st.nextToken();
            if (!"Virtualizer".equals(key)) {
                throw new IllegalArgumentException(
                        "invalid settings for Virtualizer: " + key);
            }
            try {
                key = st.nextToken();
                if (!"strength".equals(key)) {
                    throw new IllegalArgumentException("invalid key name: " + key);
                }
                strength = Short.parseShort(st.nextToken());
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("bad value for key: " + key, nfe);
            }
        }

        @Override
        public String toString() {
            return "Virtualizer;strength=" + Short.toString(strength);
        }
    }

    private short mStrength = 0;
    private int mVirtualizationMode = VIRTUALIZATION_MODE_BINAURAL;
    private OnParameterChangeListener mParamListener;

    public Virtualizer(int priority, int audioSession)
            throws IllegalStateException, IllegalArgumentException,
            UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_VIRTUALIZER, null, priority, audioSession);
    }

    public boolean getStrengthSupported() {
        return true;
    }

    public void setStrength(short strength)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (strength < 0 || strength > 1000) {
            throw new IllegalArgumentException("Invalid strength: " + strength);
        }
        mStrength = strength;
        if (mParamListener != null) {
            mParamListener.onParameterChange(this, SUCCESS, PARAM_STRENGTH, strength);
        }
    }

    public short getRoundedStrength()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mStrength;
    }

    public boolean canVirtualize(int inputChannelMask, int virtualizationMode)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (virtualizationMode < VIRTUALIZATION_MODE_AUTO
                || virtualizationMode > VIRTUALIZATION_MODE_TRANSAURAL) {
            throw new IllegalArgumentException(
                    "Invalid virtualizationMode: " + virtualizationMode);
        }
        return inputChannelMask != 0;
    }

    public boolean getSpeakerAngles(int inputChannelMask, int virtualizationMode, int[] angles)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (angles == null) {
            throw new IllegalArgumentException("angles must not be null");
        }
        if (!canVirtualize(inputChannelMask, virtualizationMode)) {
            return false;
        }
        int channelCount = Integer.bitCount(inputChannelMask >> 2);
        if (channelCount <= 0) {
            channelCount = 2;
        }
        if (angles.length < channelCount * 3) {
            throw new IllegalArgumentException("angles array too small");
        }
        for (int i = 0; i < channelCount; i++) {
            angles[i * 3] = 1 << (i + 2);
            angles[i * 3 + 1] = (i % 2 == 0) ? -90 : 90;
            angles[i * 3 + 2] = 0;
        }
        return true;
    }

    public boolean forceVirtualizationMode(int virtualizationMode)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (virtualizationMode != VIRTUALIZATION_MODE_AUTO
                && virtualizationMode != VIRTUALIZATION_MODE_BINAURAL
                && virtualizationMode != VIRTUALIZATION_MODE_TRANSAURAL) {
            throw new IllegalArgumentException(
                    "Invalid virtualizationMode: " + virtualizationMode);
        }
        mVirtualizationMode = (virtualizationMode == VIRTUALIZATION_MODE_AUTO)
                ? VIRTUALIZATION_MODE_BINAURAL
                : virtualizationMode;
        return true;
    }

    public int getVirtualizationMode()
            throws IllegalStateException, UnsupportedOperationException {
        checkInitialized();
        if (!getEnabled()) {
            return VIRTUALIZATION_MODE_OFF;
        }
        return mVirtualizationMode;
    }

    public void setParameterListener(OnParameterChangeListener listener) {
        mParamListener = listener;
    }

    public Settings getProperties()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        Settings s = new Settings();
        s.strength = mStrength;
        return s;
    }

    public void setProperties(Settings settings)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        setStrength(settings.strength);
    }
}
