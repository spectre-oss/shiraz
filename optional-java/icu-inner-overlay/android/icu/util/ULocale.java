// SPDX-License-Identifier: GPL-3.0-only
package android.icu.util;

/**
 * Overlay holder providing API 35 {@code ULocale.AvailableType}.
 */
public final class ULocale {
    private ULocale() {
    }

    public enum AvailableType {
        DEFAULT,
        ONLY_LEGACY_ALIASES,
        WITH_LEGACY_ALIASES
    }
}
