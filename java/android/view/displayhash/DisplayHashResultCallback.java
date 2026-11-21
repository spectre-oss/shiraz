// SPDX-License-Identifier: GPL-3.0-only

package android.view.displayhash;

public interface DisplayHashResultCallback {
    int DISPLAY_HASH_ERROR_UNKNOWN = -1;
    int DISPLAY_HASH_ERROR_INVALID_BOUNDS = -2;
    int DISPLAY_HASH_ERROR_MISSING_WINDOW = -3;
    int DISPLAY_HASH_ERROR_NOT_VISIBLE_ON_SCREEN = -4;
    int DISPLAY_HASH_ERROR_INVALID_HASH_ALGORITHM = -5;
    int DISPLAY_HASH_ERROR_TOO_MANY_REQUESTS = -6;

    void onDisplayHashResult(DisplayHash displayHash);

    void onDisplayHashError(int errorCode);
}
