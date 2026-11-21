// SPDX-License-Identifier: GPL-3.0-only
package android.security;

public abstract class ConfirmationCallback {
    public ConfirmationCallback() {
    }

    public void onConfirmed(byte[] dataThatWasConfirmed) {
    }

    public void onDismissed() {
    }

    public void onCanceled() {
    }

    public void onError(Throwable e) {
    }
}
