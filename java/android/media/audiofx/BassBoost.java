// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.StringTokenizer;

public class BassBoost extends AudioEffect {
    public static final int PARAM_STRENGTH_SUPPORTED = 0;
    public static final int PARAM_STRENGTH = 1;

    public interface OnParameterChangeListener {
        void onParameterChange(BassBoost effect, int status, int param, short value);
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
            if (!"BassBoost".equals(key)) {
                throw new IllegalArgumentException(
                        "invalid settings for BassBoost: " + key);
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
            return "BassBoost;strength=" + Short.toString(strength);
        }
    }

    private short mStrength = 0;
    private OnParameterChangeListener mParamListener;

    public BassBoost(int priority, int audioSession)
            throws IllegalStateException, IllegalArgumentException,
            UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_BASS_BOOST, null, priority, audioSession);
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
