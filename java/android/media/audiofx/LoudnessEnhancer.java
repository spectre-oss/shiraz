// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

public class LoudnessEnhancer extends AudioEffect {
    public static final int PARAM_TARGET_GAIN_MB = 0;

    private int mTargetGainmB = 0;

    public LoudnessEnhancer(int audioSession)
            throws IllegalStateException, IllegalArgumentException,
            UnsupportedOperationException, RuntimeException {
        super(EFFECT_TYPE_LOUDNESS_ENHANCER, null, 0, audioSession);
    }

    public void setTargetGain(int gainmB)
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        mTargetGainmB = gainmB;
    }

    public float getTargetGain()
            throws IllegalStateException, IllegalArgumentException, UnsupportedOperationException {
        checkInitialized();
        return (float) mTargetGainmB;
    }
}
