// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.CancellationSignal;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class Translator {
    private boolean mDestroyed;

    Translator() {
    }

    public void translate(
            TranslationRequest request,
            CancellationSignal cancellationSignal,
            Executor executor,
            final Consumer<TranslationResponse> callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(callback, "callback");
        if (mDestroyed) {
            throw new IllegalStateException("This translator has been destroyed");
        }
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return;
        }
        final TranslationResponse response = new TranslationResponse.Builder(
                TranslationResponse.TRANSLATION_STATUS_CONTEXT_UNSUPPORTED)
                .setFinalResponse(true)
                .build();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.accept(response);
            }
        });
    }

    public void destroy() {
        mDestroyed = true;
    }

    public boolean isDestroyed() {
        return mDestroyed;
    }
}
