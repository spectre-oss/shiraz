// SPDX-License-Identifier: GPL-3.0-only
package android.app.appsearch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AppSearchBatchResult<KeyType, ValueType> {
    private final Map<KeyType, ValueType> mSuccesses;
    private final Map<KeyType, AppSearchResult<ValueType>> mFailures;
    private final Map<KeyType, AppSearchResult<ValueType>> mAll;

    private AppSearchBatchResult(
            Map<KeyType, ValueType> successes,
            Map<KeyType, AppSearchResult<ValueType>> failures,
            Map<KeyType, AppSearchResult<ValueType>> all) {
        mSuccesses = Collections.unmodifiableMap(new LinkedHashMap<KeyType, ValueType>(successes));
        mFailures = Collections.unmodifiableMap(
                new LinkedHashMap<KeyType, AppSearchResult<ValueType>>(failures));
        mAll = Collections.unmodifiableMap(
                new LinkedHashMap<KeyType, AppSearchResult<ValueType>>(all));
    }

    public boolean isSuccess() {
        return mFailures.isEmpty();
    }

    public Map<KeyType, ValueType> getSuccesses() {
        return mSuccesses;
    }

    public Map<KeyType, AppSearchResult<ValueType>> getFailures() {
        return mFailures;
    }

    public Map<KeyType, AppSearchResult<ValueType>> getAll() {
        return mAll;
    }

    @Override
    public String toString() {
        return "{\n  successes: " + mSuccesses + "\n  failures: " + mFailures + "\n}";
    }

    public static final class Builder<KeyType, ValueType> {
        private final Map<KeyType, ValueType> mSuccesses = new LinkedHashMap<KeyType, ValueType>();
        private final Map<KeyType, AppSearchResult<ValueType>> mFailures =
                new LinkedHashMap<KeyType, AppSearchResult<ValueType>>();
        private final Map<KeyType, AppSearchResult<ValueType>> mAll =
                new LinkedHashMap<KeyType, AppSearchResult<ValueType>>();

        public Builder() {
        }

        public Builder<KeyType, ValueType> setSuccess(KeyType key, ValueType value) {
            Objects.requireNonNull(key);
            return setResult(key, AppSearchResult.newSuccessfulResult(value));
        }

        public Builder<KeyType, ValueType> setFailure(
                KeyType key, int resultCode, String errorMessage) {
            Objects.requireNonNull(key);
            return setResult(key, AppSearchResult.<ValueType>newFailedResult(resultCode, errorMessage));
        }

        public Builder<KeyType, ValueType> setResult(
                KeyType key, AppSearchResult<ValueType> result) {
            Objects.requireNonNull(key);
            Objects.requireNonNull(result);
            if (result.isSuccess()) {
                mSuccesses.put(key, result.getResultValue());
                mFailures.remove(key);
            } else {
                mFailures.put(key, result);
                mSuccesses.remove(key);
            }
            mAll.put(key, result);
            return this;
        }

        public AppSearchBatchResult<KeyType, ValueType> build() {
            return new AppSearchBatchResult<KeyType, ValueType>(mSuccesses, mFailures, mAll);
        }
    }
}
