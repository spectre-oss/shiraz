// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import java.util.Set;

/** Callback for AutofillService.onSavedDatasetsInfoRequest. */
public interface SavedDatasetsInfoCallback {
    int ERROR_OTHER = 0;
    int ERROR_UNSUPPORTED = 1;
    int ERROR_NEEDS_USER_ACTION = 2;

    void onSuccess(Set<SavedDatasetsInfo> results);

    void onError(int error);
}
