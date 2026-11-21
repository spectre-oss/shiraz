// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.Arrays;
import java.util.StringTokenizer;

public class Equalizer extends AudioEffect {
    public static final int PARAM_NUM_BANDS = 0;
    public static final int PARAM_LEVEL_RANGE = 1;
    public static final int PARAM_BAND_LEVEL = 2;
    public static final int PARAM_CENTER_FREQ = 3;
    public static final int PARAM_BAND_FREQ_RANGE = 4;
    public static final int PARAM_GET_BAND = 5;
    public static final int PARAM_CURRENT_PRESET = 6;
    public static final int PARAM_GET_NUM_OF_PRESETS = 7;
    public static final int PARAM_GET_PRESET_NAME = 8;
    public static final int PARAM_STRING_SIZE_MAX = 32;

    public interface OnParameterChangeListener {
        void onParameterChange(Equalizer effect, int status, int param1, int param2, int value);
    }

    public static class Settings {
        public short curPreset;
        public short numBands = 0;
        public short[] bandLevels = null;

        public Settings() {
        }

        public Settings(String settings) {
            if (settings == null) {
                throw new IllegalArgumentException("settings must not be null");
            }
            StringTokenizer st = new StringTokenizer(settings, "=;");
            if (!st.hasMoreTokens()) {
                throw new IllegalArgumentException("settings: " + settings);
            }
            String className = st.nextToken();
            if (!"Equalizer".equals(className)) {
                throw new IllegalArgumentException("invalid settings for Equalizer: " + className);
            }
            try {
                boolean hasPreset = false;
                boolean hasNumBands = false;
                while (st.hasMoreTokens()) {
                    String key = st.nextToken();
                    if (!st.hasMoreTokens()) {
                        throw new IllegalArgumentException("missing value for key: " + key);
                    }
                    String val = st.nextToken();
                    if ("curPreset".equals(key)) {
                        curPreset = Short.parseShort(val);
                        hasPreset = true;
                    } else if ("numBands".equals(key)) {
                        numBands = Short.parseShort(val);
                        if (numBands < 0) {
                            throw new IllegalArgumentException("invalid numBands: " + numBands);
                        }
                        bandLevels = new short[numBands];
                        hasNumBands = true;
                    } else if (key.startsWith("band") && key.endsWith("Level")) {
                        String idxStr = key.substring(4, key.length() - 5);
                        int idx = Integer.parseInt(idxStr) - 1;
                        if (bandLevels == null || idx < 0 || idx >= bandLevels.length) {
                            throw new IllegalArgumentException("invalid band key: " + key);
                        }
                        bandLevels[idx] = Short.parseShort(val);
                    } else {
                        throw new IllegalArgumentException("unknown key: " + key);
                    }
                }
                if (!hasPreset || !hasNumBands) {
                    throw new IllegalArgumentException("incomplete settings: " + settings);
                }
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("bad value in settings: " + settings, nfe);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Equalizer;curPreset=").append(Short.toString(curPreset));
            sb.append(";numBands=").append(Short.toString(numBands));
            for (int i = 0; i < numBands; i++) {
                short level = (bandLevels != null && i < bandLevels.length) ? bandLevels[i] : 0;
                sb.append(";band").append(i + 1).append("Level=").append(Short.toString(level));
            }
            return sb.toString();
        }
    }

    private static final short NUM_BANDS = 5;
    private static final short MIN_LEVEL_MB = -1500;
    private static final short MAX_LEVEL_MB = 1500;
    private static final int[] CENTER_FREQS_MHZ = {
        60000, 230000, 910000, 3600000, 14000000
    };
    private static final int[][] BAND_RANGES_MHZ = {
        {30000, 120000},
        {120001, 460000},
        {460001, 1800000},
        {1800001, 7000000},
        {7000001, 20000000}
    };
    private static final String[] PRESET_NAMES = {
        "Normal", "Classical", "Dance", "Flat", "Folk",
        "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"
    };
    private static final short[][] PRESET_LEVELS = {
        {300, 0, 0, 0, 300},
        {500, 300, -200, 400, 400},
        {600, 0, 200, 400, 100},
        {0, 0, 0, 0, 0},
        {300, 0, 0, 200, -100},
        {400, 100, 900, 300, 0},
        {500, 300, 0, 100, 300},
        {400, 200, -200, 200, 500},
        {-100, 200, 500, 100, -200},
        {500, 300, -100, 300, 500}
    };

    private final short[] mBandLevels = Arrays.copyOf(PRESET_LEVELS[0], NUM_BANDS);
    private short mCurrentPreset = 0;
    private OnParameterChangeListener mParamListener;

    public Equalizer(int priority, int audioSession)
            throws IllegalStateException, IllegalArgumentException,
            UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_EQUALIZER, null, priority, audioSession);
    }

    public short getNumberOfBands()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return NUM_BANDS;
    }

    public short[] getBandLevelRange()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return new short[]{MIN_LEVEL_MB, MAX_LEVEL_MB};
    }

    public void setBandLevel(short band, short level)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (band < 0 || band >= NUM_BANDS) {
            throw new IllegalArgumentException("Invalid band: " + band);
        }
        short clamped = level < MIN_LEVEL_MB ? MIN_LEVEL_MB
                : (level > MAX_LEVEL_MB ? MAX_LEVEL_MB : level);
        mBandLevels[band] = clamped;
        mCurrentPreset = -1;
        if (mParamListener != null) {
            mParamListener.onParameterChange(this, SUCCESS, PARAM_BAND_LEVEL, band, clamped);
        }
    }

    public short getBandLevel(short band)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (band < 0 || band >= NUM_BANDS) {
            throw new IllegalArgumentException("Invalid band: " + band);
        }
        return mBandLevels[band];
    }

    public int getCenterFreq(short band)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (band < 0 || band >= NUM_BANDS) {
            throw new IllegalArgumentException("Invalid band: " + band);
        }
        return CENTER_FREQS_MHZ[band];
    }

    public int[] getBandFreqRange(short band)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (band < 0 || band >= NUM_BANDS) {
            throw new IllegalArgumentException("Invalid band: " + band);
        }
        return Arrays.copyOf(BAND_RANGES_MHZ[band], 2);
    }

    public short getBand(int frequency)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        for (short i = 0; i < NUM_BANDS; i++) {
            if (frequency >= BAND_RANGES_MHZ[i][0] && frequency <= BAND_RANGES_MHZ[i][1]) {
                return i;
            }
        }
        if (frequency < BAND_RANGES_MHZ[0][0]) {
            return 0;
        }
        return (short) (NUM_BANDS - 1);
    }

    public short getCurrentPreset()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mCurrentPreset;
    }

    public void usePreset(short preset)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (preset < 0 || preset >= PRESET_NAMES.length) {
            throw new IllegalArgumentException("Invalid preset: " + preset);
        }
        mCurrentPreset = preset;
        System.arraycopy(PRESET_LEVELS[preset], 0, mBandLevels, 0, NUM_BANDS);
        if (mParamListener != null) {
            mParamListener.onParameterChange(this, SUCCESS, PARAM_CURRENT_PRESET, -1, preset);
        }
    }

    public short getNumberOfPresets()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return (short) PRESET_NAMES.length;
    }

    public String getPresetName(short preset) {
        if (preset < 0 || preset >= PRESET_NAMES.length) {
            return "";
        }
        return PRESET_NAMES[preset];
    }

    public void setParameterListener(OnParameterChangeListener listener) {
        mParamListener = listener;
    }

    public Settings getProperties()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        Settings s = new Settings();
        s.curPreset = mCurrentPreset;
        s.numBands = NUM_BANDS;
        s.bandLevels = Arrays.copyOf(mBandLevels, NUM_BANDS);
        return s;
    }

    public void setProperties(Settings settings)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (settings == null || settings.numBands != NUM_BANDS || settings.bandLevels == null
                || settings.bandLevels.length < NUM_BANDS) {
            throw new IllegalArgumentException("Invalid Equalizer.Settings");
        }
        if (settings.curPreset >= 0 && settings.curPreset < PRESET_NAMES.length) {
            mCurrentPreset = settings.curPreset;
        } else {
            mCurrentPreset = -1;
        }
        for (short i = 0; i < NUM_BANDS; i++) {
            short lvl = settings.bandLevels[i];
            mBandLevels[i] = lvl < MIN_LEVEL_MB ? MIN_LEVEL_MB
                    : (lvl > MAX_LEVEL_MB ? MAX_LEVEL_MB : lvl);
        }
    }
}
