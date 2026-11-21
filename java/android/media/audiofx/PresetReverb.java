// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.StringTokenizer;

public class PresetReverb extends AudioEffect {
    public static final int PARAM_PRESET = 0;

    public static final short PRESET_NONE = 0;
    public static final short PRESET_SMALLROOM = 1;
    public static final short PRESET_MEDIUMROOM = 2;
    public static final short PRESET_LARGEROOM = 3;
    public static final short PRESET_MEDIUMHALL = 4;
    public static final short PRESET_LARGEHALL = 5;
    public static final short PRESET_PLATE = 6;

    public interface OnParameterChangeListener {
        void onParameterChange(PresetReverb effect, int status, int param, short value);
    }

    public static class Settings {
        public short preset;

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
            if (!"PresetReverb".equals(key)) {
                throw new IllegalArgumentException(
                        "invalid settings for PresetReverb: " + key);
            }
            try {
                key = st.nextToken();
                if (!"preset".equals(key)) {
                    throw new IllegalArgumentException("invalid key name: " + key);
                }
                preset = Short.parseShort(st.nextToken());
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("bad value for key: " + key, nfe);
            }
        }

        @Override
        public String toString() {
            return "PresetReverb;preset=" + Short.toString(preset);
        }
    }

    private short mPreset = PRESET_NONE;
    private OnParameterChangeListener mParamListener;

    public PresetReverb(int priority, int audioSession)
            throws IllegalArgumentException, UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_PRESET_REVERB, null, priority, audioSession);
    }

    public void setPreset(short preset)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (preset < PRESET_NONE || preset > PRESET_PLATE) {
            throw new IllegalArgumentException("Invalid preset: " + preset);
        }
        mPreset = preset;
        if (mParamListener != null) {
            mParamListener.onParameterChange(this, SUCCESS, PARAM_PRESET, preset);
        }
    }

    public short getPreset()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mPreset;
    }

    public void setParameterListener(OnParameterChangeListener listener) {
        mParamListener = listener;
    }

    public Settings getProperties()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        Settings s = new Settings();
        s.preset = mPreset;
        return s;
    }

    public void setProperties(Settings settings)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        setPreset(settings.preset);
    }
}
