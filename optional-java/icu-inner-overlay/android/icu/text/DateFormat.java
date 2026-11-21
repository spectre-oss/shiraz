// SPDX-License-Identifier: GPL-3.0-only
package android.icu.text;

/**
 * Overlay holder providing API 35 {@code DateFormat.HourCycle}.
 */
public abstract class DateFormat {
    protected DateFormat() {
    }

    public enum HourCycle {
        HOUR_CYCLE_11,
        HOUR_CYCLE_12,
        HOUR_CYCLE_23,
        HOUR_CYCLE_24
    }
}
