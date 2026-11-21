// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

public interface BatchResultCallback<KeyType, ValueType> {
    void onResult(AppSearchBatchResult<KeyType, ValueType> result);

    default void onSystemError(Throwable throwable) {
    }
}
