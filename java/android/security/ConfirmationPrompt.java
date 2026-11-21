// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import android.content.Context;
import java.util.Objects;
import java.util.concurrent.Executor;

public class ConfirmationPrompt {
    private final CharSequence mPromptText;
    private final byte[] mExtraData;

    ConfirmationPrompt(Context context, CharSequence promptText, byte[] extraData) {
        mPromptText = promptText;
        mExtraData = extraData;
    }

    public void presentPrompt(Executor executor, ConfirmationCallback callback)
            throws ConfirmationAlreadyPresentingException, ConfirmationNotAvailableException {
        Objects.requireNonNull(executor, "executor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        throw new ConfirmationNotAvailableException("Protected Confirmation hardware is not available");
    }

    public void cancelPrompt() {
        throw new IllegalStateException("Prompt is not currently presenting");
    }

    public static boolean isSupported(Context context) {
        return false;
    }

    public static final class Builder {
        private final Context mContext;
        private CharSequence mPromptText;
        private byte[] mExtraData;

        public Builder(Context context) {
            mContext = context;
        }

        public Builder setPromptText(CharSequence promptText) {
            mPromptText = promptText;
            return this;
        }

        public Builder setExtraData(byte[] extraData) {
            mExtraData = extraData != null ? extraData.clone() : null;
            return this;
        }

        public ConfirmationPrompt build() {
            if (mPromptText == null || mPromptText.length() == 0) {
                throw new IllegalArgumentException("prompt text must be set and non-empty");
            }
            if (mExtraData == null) {
                throw new IllegalArgumentException("extraData must be set");
            }
            return new ConfirmationPrompt(mContext, mPromptText, mExtraData);
        }
    }
}
