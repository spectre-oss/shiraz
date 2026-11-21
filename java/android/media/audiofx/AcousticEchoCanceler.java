// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

public class AcousticEchoCanceler extends AudioEffect {
    private AcousticEchoCanceler(int audioSession) {
        super(EFFECT_TYPE_AEC, null, 0, audioSession);
    }

    public static boolean isAvailable() {
        return true;
    }

    public static AcousticEchoCanceler create(int audioSession) {
        return new AcousticEchoCanceler(audioSession);
    }
}
