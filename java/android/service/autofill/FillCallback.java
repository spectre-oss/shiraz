// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

/** Handles autofill response callbacks from AutofillService.onFillRequest. */
public final class FillCallback {
    private boolean mCalled;
    private FillResponse mResponse;
    private CharSequence mError;

    public FillCallback() {
    }

    public void onSuccess(FillResponse response) {
        if (mCalled) {
            throw new IllegalStateException("onSuccess() or onFailure() already called");
        }
        mCalled = true;
        mResponse = response;
    }

    public void onFailure(CharSequence message) {
        if (mCalled) {
            throw new IllegalStateException("onSuccess() or onFailure() already called");
        }
        mCalled = true;
        mError = message;
    }
}
