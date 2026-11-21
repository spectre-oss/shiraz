// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import java.util.Objects;
import java.util.concurrent.Executor;

public final class UiTranslationManager {
    UiTranslationManager() {
    }

    public void registerUiTranslationStateCallback(
            Executor executor, UiTranslationStateCallback callback) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
    }

    public void unregisterUiTranslationStateCallback(UiTranslationStateCallback callback) {
        Objects.requireNonNull(callback, "callback");
    }
}
