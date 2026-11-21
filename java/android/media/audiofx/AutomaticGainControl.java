// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

public class AutomaticGainControl extends AudioEffect {
    private AutomaticGainControl(int audioSession) {
        super(EFFECT_TYPE_AGC, null, 0, audioSession);
    }

    public static boolean isAvailable() {
        return true;
    }

    public static AutomaticGainControl create(int audioSession) {
        return new AutomaticGainControl(audioSession);
    }
}
