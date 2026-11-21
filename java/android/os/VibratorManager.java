// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.Objects;

/** System service base class managing device vibrators. */
public abstract class VibratorManager {
    public VibratorManager() {
    }

    public abstract int[] getVibratorIds();

    public abstract Vibrator getVibrator(int vibratorId);

    public abstract Vibrator getDefaultVibrator();

    public final void vibrate(CombinedVibration effect) {
        vibrate(effect, null);
    }

    public final void vibrate(CombinedVibration effect, VibrationAttributes attributes) {
        Objects.requireNonNull(effect, "effect");
        Vibrator def = getDefaultVibrator();
        if (def != null && def.hasVibrator()) {
            def.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), attributes);
        }
    }

    public abstract void cancel();
}
