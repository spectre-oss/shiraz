// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.icu.util.ULocale;

public interface UiTranslationStateCallback {
    @Deprecated
    default void onStarted(ULocale sourceLocale, ULocale targetLocale) {
    }

    default void onStarted(
            ULocale sourceLocale, ULocale targetLocale, String packageName) {
        onStarted(sourceLocale, targetLocale);
    }

    void onPaused();

    default void onPaused(String packageName) {
        onPaused();
    }

    default void onResumed(ULocale sourceLocale, ULocale targetLocale) {
    }

    default void onResumed(
            ULocale sourceLocale, ULocale targetLocale, String packageName) {
        onResumed(sourceLocale, targetLocale);
    }

    void onFinished();

    default void onFinished(String packageName) {
        onFinished();
    }
}
