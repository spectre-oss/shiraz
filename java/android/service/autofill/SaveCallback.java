// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.content.IntentSender;

/** Handles save callbacks from AutofillService.onSaveRequest. */
public final class SaveCallback {
    private boolean mCalled;

    public SaveCallback() {
    }

    public void onSuccess() {
        if (mCalled) {
            throw new IllegalStateException("onSuccess() or onFailure() already called");
        }
        mCalled = true;
    }

    public void onSuccess(IntentSender intentSender) {
        if (intentSender == null) {
            throw new NullPointerException("intentSender cannot be null");
        }
        if (mCalled) {
            throw new IllegalStateException("onSuccess() or onFailure() already called");
        }
        mCalled = true;
    }

    public void onFailure(CharSequence message) {
        if (mCalled) {
            throw new IllegalStateException("onSuccess() or onFailure() already called");
        }
        mCalled = true;
    }
}
