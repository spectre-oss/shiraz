// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

import java.util.StringTokenizer;

public class EnvironmentalReverb extends AudioEffect {
    public static final int PARAM_ROOM_LEVEL = 0;
    public static final int PARAM_ROOM_HF_LEVEL = 1;
    public static final int PARAM_DECAY_TIME = 2;
    public static final int PARAM_DECAY_HF_RATIO = 3;
    public static final int PARAM_REFLECTIONS_LEVEL = 4;
    public static final int PARAM_REFLECTIONS_DELAY = 5;
    public static final int PARAM_REVERB_LEVEL = 6;
    public static final int PARAM_REVERB_DELAY = 7;
    public static final int PARAM_DIFFUSION = 8;
    public static final int PARAM_DENSITY = 9;

    public interface OnParameterChangeListener {
        void onParameterChange(EnvironmentalReverb effect, int status, int param, int value);
    }

    public static class Settings {
        public short roomLevel;
        public short roomHFLevel;
        public int decayTime;
        public short decayHFRatio;
        public short reflectionsLevel;
        public int reflectionsDelay;
        public short reverbLevel;
        public int reverbDelay;
        public short diffusion;
        public short density;

        public Settings() {
        }

        public Settings(String settings) {
            if (settings == null) {
                throw new IllegalArgumentException("settings must not be null");
            }
            StringTokenizer st = new StringTokenizer(settings, "=;");
            if (st.countTokens() != 21) {
                throw new IllegalArgumentException("settings: " + settings);
            }
            String key = st.nextToken();
            if (!"EnvironmentalReverb".equals(key)) {
                throw new IllegalArgumentException(
                        "invalid settings for EnvironmentalReverb: " + key);
            }
            try {
                while (st.hasMoreTokens()) {
                    key = st.nextToken();
                    String val = st.nextToken();
                    if ("roomLevel".equals(key)) {
                        roomLevel = Short.parseShort(val);
                    } else if ("roomHFLevel".equals(key)) {
                        roomHFLevel = Short.parseShort(val);
                    } else if ("decayTime".equals(key)) {
                        decayTime = Integer.parseInt(val);
                    } else if ("decayHFRatio".equals(key)) {
                        decayHFRatio = Short.parseShort(val);
                    } else if ("reflectionsLevel".equals(key)) {
                        reflectionsLevel = Short.parseShort(val);
                    } else if ("reflectionsDelay".equals(key)) {
                        reflectionsDelay = Integer.parseInt(val);
                    } else if ("reverbLevel".equals(key)) {
                        reverbLevel = Short.parseShort(val);
                    } else if ("reverbDelay".equals(key)) {
                        reverbDelay = Integer.parseInt(val);
                    } else if ("diffusion".equals(key)) {
                        diffusion = Short.parseShort(val);
                    } else if ("density".equals(key)) {
                        density = Short.parseShort(val);
                    } else {
                        throw new IllegalArgumentException("invalid key name: " + key);
                    }
                }
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("bad value for key: " + key, nfe);
            }
        }

        @Override
        public String toString() {
            return "EnvironmentalReverb"
                    + ";roomLevel=" + Short.toString(roomLevel)
                    + ";roomHFLevel=" + Short.toString(roomHFLevel)
                    + ";decayTime=" + Integer.toString(decayTime)
                    + ";decayHFRatio=" + Short.toString(decayHFRatio)
                    + ";reflectionsLevel=" + Short.toString(reflectionsLevel)
                    + ";reflectionsDelay=" + Integer.toString(reflectionsDelay)
                    + ";reverbLevel=" + Short.toString(reverbLevel)
                    + ";reverbDelay=" + Integer.toString(reverbDelay)
                    + ";diffusion=" + Short.toString(diffusion)
                    + ";density=" + Short.toString(density);
        }
    }

    private short mRoomLevel = -9000;
    private short mRoomHFLevel = 0;
    private int mDecayTime = 1490;
    private short mDecayHFRatio = 830;
    private short mReflectionsLevel = -2602;
    private int mReflectionsDelay = 7;
    private short mReverbLevel = 200;
    private int mReverbDelay = 11;
    private short mDiffusion = 1000;
    private short mDensity = 1000;
    private OnParameterChangeListener mParamListener;

    public EnvironmentalReverb(int priority, int audioSession)
            throws IllegalArgumentException, UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_ENV_REVERB, null, priority, audioSession);
    }

    private void notifyChanged(int param, int value) {
        if (mParamListener != null) {
            mParamListener.onParameterChange(this, SUCCESS, param, value);
        }
    }

    public void setRoomLevel(short room)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mRoomLevel = room;
        notifyChanged(PARAM_ROOM_LEVEL, room);
    }

    public short getRoomLevel()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mRoomLevel;
    }

    public void setRoomHFLevel(short roomHF)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mRoomHFLevel = roomHF;
        notifyChanged(PARAM_ROOM_HF_LEVEL, roomHF);
    }

    public short getRoomHFLevel()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mRoomHFLevel;
    }

    public void setDecayTime(int decayTime)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (decayTime < 0) {
            throw new IllegalArgumentException("Invalid decayTime: " + decayTime);
        }
        mDecayTime = decayTime;
        notifyChanged(PARAM_DECAY_TIME, decayTime);
    }

    public int getDecayTime()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mDecayTime;
    }

    public void setDecayHFRatio(short decayHFRatio)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mDecayHFRatio = decayHFRatio;
        notifyChanged(PARAM_DECAY_HF_RATIO, decayHFRatio);
    }

    public short getDecayHFRatio()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mDecayHFRatio;
    }

    public void setReflectionsLevel(short reflectionsLevel)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mReflectionsLevel = reflectionsLevel;
        notifyChanged(PARAM_REFLECTIONS_LEVEL, reflectionsLevel);
    }

    public short getReflectionsLevel()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mReflectionsLevel;
    }

    public void setReflectionsDelay(int reflectionsDelay)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (reflectionsDelay < 0) {
            throw new IllegalArgumentException("Invalid reflectionsDelay: " + reflectionsDelay);
        }
        mReflectionsDelay = reflectionsDelay;
        notifyChanged(PARAM_REFLECTIONS_DELAY, reflectionsDelay);
    }

    public int getReflectionsDelay()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mReflectionsDelay;
    }

    public void setReverbLevel(short reverbLevel)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mReverbLevel = reverbLevel;
        notifyChanged(PARAM_REVERB_LEVEL, reverbLevel);
    }

    public short getReverbLevel()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mReverbLevel;
    }

    public void setReverbDelay(int reverbDelay)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (reverbDelay < 0) {
            throw new IllegalArgumentException("Invalid reverbDelay: " + reverbDelay);
        }
        mReverbDelay = reverbDelay;
        notifyChanged(PARAM_REVERB_DELAY, reverbDelay);
    }

    public int getReverbDelay()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mReverbDelay;
    }

    public void setDiffusion(short diffusion)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mDiffusion = diffusion;
        notifyChanged(PARAM_DIFFUSION, diffusion);
    }

    public short getDiffusion()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mDiffusion;
    }

    public void setDensity(short density)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mDensity = density;
        notifyChanged(PARAM_DENSITY, density);
    }

    public short getDensity()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return mDensity;
    }

    public void setParameterListener(OnParameterChangeListener listener) {
        mParamListener = listener;
    }

    public Settings getProperties()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        Settings s = new Settings();
        s.roomLevel = mRoomLevel;
        s.roomHFLevel = mRoomHFLevel;
        s.decayTime = mDecayTime;
        s.decayHFRatio = mDecayHFRatio;
        s.reflectionsLevel = mReflectionsLevel;
        s.reflectionsDelay = mReflectionsDelay;
        s.reverbLevel = mReverbLevel;
        s.reverbDelay = mReverbDelay;
        s.diffusion = mDiffusion;
        s.density = mDensity;
        return s;
    }

    public void setProperties(Settings settings)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        mRoomLevel = settings.roomLevel;
        mRoomHFLevel = settings.roomHFLevel;
        mDecayTime = settings.decayTime;
        mDecayHFRatio = settings.decayHFRatio;
        mReflectionsLevel = settings.reflectionsLevel;
        mReflectionsDelay = settings.reflectionsDelay;
        mReverbLevel = settings.reverbLevel;
        mReverbDelay = settings.reverbDelay;
        mDiffusion = settings.diffusion;
        mDensity = settings.density;
    }
}
