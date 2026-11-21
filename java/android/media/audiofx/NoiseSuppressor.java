// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

public class NoiseSuppressor extends AudioEffect {
    private NoiseSuppressor(int audioSession) {
        super(EFFECT_TYPE_NS, null, 0, audioSession);
    }

    public static boolean isAvailable() {
        return true;
    }

    public static NoiseSuppressor create(int audioSession) {
        return new NoiseSuppressor(audioSession);
    }
}
