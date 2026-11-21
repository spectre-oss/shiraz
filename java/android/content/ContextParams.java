// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import java.util.Objects;

public final class ContextParams {
    public static final ContextParams EMPTY = new Builder().build();

    private final String mAttributionTag;
    private final AttributionSource mNext;
    private final boolean mShouldRegisterAttributionSource;

    private ContextParams(
            String attributionTag,
            AttributionSource next,
            boolean shouldRegister) {
        mAttributionTag = attributionTag;
        mNext = next;
        mShouldRegisterAttributionSource = shouldRegister;
    }

    public String getAttributionTag() {
        return mAttributionTag;
    }

    public AttributionSource getNextAttributionSource() {
        return mNext;
    }

    public boolean shouldRegisterAttributionSource() {
        return mShouldRegisterAttributionSource;
    }

    public static final class Builder {
        private String mAttributionTag;
        private AttributionSource mNext;
        private boolean mShouldRegisterAttributionSource;

        public Builder() {
        }

        public Builder(ContextParams params) {
            Objects.requireNonNull(params, "params must not be null");
            mAttributionTag = params.mAttributionTag;
            mNext = params.mNext;
            mShouldRegisterAttributionSource = params.mShouldRegisterAttributionSource;
        }

        public Builder setAttributionTag(String attributionTag) {
            mAttributionTag = attributionTag;
            return this;
        }

        public Builder setNextAttributionSource(AttributionSource next) {
            mNext = next;
            return this;
        }

        public Builder setShouldRegisterAttributionSource(boolean shouldRegister) {
            mShouldRegisterAttributionSource = shouldRegister;
            return this;
        }

        public ContextParams build() {
            return new ContextParams(
                    mAttributionTag,
                    mNext,
                    mShouldRegisterAttributionSource);
        }
    }
}
