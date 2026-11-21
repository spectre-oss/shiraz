// SPDX-License-Identifier: GPL-3.0-only
package android.media.audiofx;

public class HapticGenerator extends AudioEffect implements AutoCloseable {
    private HapticGenerator(int audioSession) {
        super(EFFECT_TYPE_HAPTIC_GENERATOR, null, 0, audioSession);
    }

    public static boolean isAvailable() {
        return true;
    }

    public static HapticGenerator create(int audioSession) {
        return new HapticGenerator(audioSession);
    }

    @Override
    public int setEnabled(boolean enabled) {
        return super.setEnabled(enabled);
    }

    @Override
    public void release() {
        super.release();
    }

    @Override
    public void close() {
        release();
    }
}
