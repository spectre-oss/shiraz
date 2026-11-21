// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Arrays;
import java.util.Objects;

/** Model inference configuration and input tensors passed to {@link ModelManager#run}. */
public final class InferenceInput {
    private final Params mParams;
    private final Object[] mInputData;
    private final int mBatchSize;
    private final InferenceOutput mExpectedOutputStructure;

    private InferenceInput(
            Params params,
            Object[] inputData,
            int batchSize,
            InferenceOutput expectedOutputStructure) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.mParams = Objects.requireNonNull(params, "params");
        Objects.requireNonNull(inputData, "inputData");
        this.mInputData = inputData.clone();
        this.mBatchSize = batchSize;
        this.mExpectedOutputStructure =
                Objects.requireNonNull(
                        expectedOutputStructure, "expectedOutputStructure");
    }

    public Params getParams() {
        return mParams;
    }

    public Object[] getInputData() {
        return mInputData.clone();
    }

    public int getBatchSize() {
        return mBatchSize;
    }

    public InferenceOutput getExpectedOutputStructure() {
        return mExpectedOutputStructure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InferenceInput)) {
            return false;
        }
        InferenceInput that = (InferenceInput) o;
        return mBatchSize == that.mBatchSize
                && Objects.equals(mParams, that.mParams)
                && Arrays.deepEquals(mInputData, that.mInputData)
                && Objects.equals(
                        mExpectedOutputStructure, that.mExpectedOutputStructure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mParams,
                Integer.valueOf(Arrays.deepHashCode(mInputData)),
                Integer.valueOf(mBatchSize),
                mExpectedOutputStructure);
    }

    public static class Params {
        public static final int DELEGATE_CPU = 1;
        public static final int MODEL_TYPE_TENSORFLOW_LITE = 1;

        private final KeyValueStore mKeyValueStore;
        private final String mModelKey;
        private final int mDelegateType;
        private final int mModelType;
        private final int mRecommendedNumThreads;

        private Params(
                KeyValueStore keyValueStore,
                String modelKey,
                int delegateType,
                int modelType,
                int recommendedNumThreads) {
            if (recommendedNumThreads <= 0) {
                throw new IllegalArgumentException(
                        "recommendedNumThreads must be positive");
            }
            this.mKeyValueStore =
                    Objects.requireNonNull(keyValueStore, "keyValueStore");
            this.mModelKey = Objects.requireNonNull(modelKey, "modelKey");
            this.mDelegateType = delegateType;
            this.mModelType = modelType;
            this.mRecommendedNumThreads = recommendedNumThreads;
        }

        public KeyValueStore getKeyValueStore() {
            return mKeyValueStore;
        }

        public String getModelKey() {
            return mModelKey;
        }

        public int getDelegateType() {
            return mDelegateType;
        }

        public int getModelType() {
            return mModelType;
        }

        public int getRecommendedNumThreads() {
            return mRecommendedNumThreads;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Params)) {
                return false;
            }
            Params that = (Params) o;
            return mDelegateType == that.mDelegateType
                    && mModelType == that.mModelType
                    && mRecommendedNumThreads == that.mRecommendedNumThreads
                    && Objects.equals(mKeyValueStore, that.mKeyValueStore)
                    && Objects.equals(mModelKey, that.mModelKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mKeyValueStore,
                    mModelKey,
                    Integer.valueOf(mDelegateType),
                    Integer.valueOf(mModelType),
                    Integer.valueOf(mRecommendedNumThreads));
        }

        public static final class Builder {
            private KeyValueStore mKeyValueStore;
            private String mModelKey;
            private int mDelegateType = DELEGATE_CPU;
            private int mModelType = MODEL_TYPE_TENSORFLOW_LITE;
            private int mRecommendedNumThreads = 1;

            public Builder(KeyValueStore keyValueStore, String modelKey) {
                this.mKeyValueStore =
                        Objects.requireNonNull(keyValueStore, "keyValueStore");
                this.mModelKey = Objects.requireNonNull(modelKey, "modelKey");
            }

            public Builder setKeyValueStore(KeyValueStore value) {
                this.mKeyValueStore = Objects.requireNonNull(value, "keyValueStore");
                return this;
            }

            public Builder setModelKey(String value) {
                this.mModelKey = Objects.requireNonNull(value, "modelKey");
                return this;
            }

            public Builder setDelegateType(int value) {
                this.mDelegateType = value;
                return this;
            }

            public Builder setModelType(int value) {
                this.mModelType = value;
                return this;
            }

            public Builder setRecommendedNumThreads(int value) {
                if (value <= 0) {
                    throw new IllegalArgumentException(
                            "recommendedNumThreads must be positive");
                }
                this.mRecommendedNumThreads = value;
                return this;
            }

            public Params build() {
                return new Params(
                        mKeyValueStore,
                        mModelKey,
                        mDelegateType,
                        mModelType,
                        mRecommendedNumThreads);
            }
        }
    }

    public static final class Builder {
        private Params mParams;
        private Object[] mInputData;
        private int mBatchSize = 1;
        private InferenceOutput mExpectedOutputStructure;

        public Builder(
                Params params,
                Object[] inputData,
                InferenceOutput expectedOutputStructure) {
            this.mParams = Objects.requireNonNull(params, "params");
            this.mInputData = Objects.requireNonNull(inputData, "inputData").clone();
            this.mExpectedOutputStructure =
                    Objects.requireNonNull(
                            expectedOutputStructure, "expectedOutputStructure");
        }

        public Builder setParams(Params value) {
            this.mParams = Objects.requireNonNull(value, "params");
            return this;
        }

        public Builder setInputData(Object... value) {
            this.mInputData = Objects.requireNonNull(value, "inputData").clone();
            return this;
        }

        public Builder setBatchSize(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("batchSize must be positive");
            }
            this.mBatchSize = value;
            return this;
        }

        public Builder setExpectedOutputStructure(InferenceOutput value) {
            this.mExpectedOutputStructure =
                    Objects.requireNonNull(value, "expectedOutputStructure");
            return this;
        }

        public InferenceInput build() {
            return new InferenceInput(
                    mParams, mInputData, mBatchSize, mExpectedOutputStructure);
        }
    }
}
