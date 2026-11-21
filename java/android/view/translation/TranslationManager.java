// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.app.PendingIntent;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class TranslationManager {
    TranslationManager() {
    }

    public void createOnDeviceTranslator(
            TranslationContext translationContext,
            Executor executor,
            final Consumer<Translator> callback) {
        Objects.requireNonNull(translationContext, "translationContext");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(null);
            }
        });
    }

    public Set<TranslationCapability> getOnDeviceTranslationCapabilities(
            int sourceFormat, int targetFormat) {
        return Collections.emptySet();
    }

    public void addOnDeviceTranslationCapabilityUpdateListener(
            Executor executor, Consumer<TranslationCapability> capabilityListener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(capabilityListener, "capabilityListener");
    }

    public void removeOnDeviceTranslationCapabilityUpdateListener(
            Consumer<TranslationCapability> capabilityListener) {
        Objects.requireNonNull(capabilityListener, "capabilityListener");
    }

    public PendingIntent getOnDeviceTranslationSettingsActivityIntent() {
        return null;
    }
}
