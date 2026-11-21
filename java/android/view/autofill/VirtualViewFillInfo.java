// SPDX-License-Identifier: GPL-3.0-only

package android.view.autofill;

public final class VirtualViewFillInfo {
    private final String[] mAutofillHints;

    private VirtualViewFillInfo(String[] autofillHints) {
        mAutofillHints = autofillHints != null ? autofillHints.clone() : null;
    }

    public String[] getAutofillHints() {
        return mAutofillHints != null ? mAutofillHints.clone() : null;
    }

    public static final class Builder {
        private String[] mAutofillHints;

        public Builder() {
        }

        public Builder setAutofillHints(String... value) {
            mAutofillHints = value != null ? value.clone() : null;
            return this;
        }

        public VirtualViewFillInfo build() {
            return new VirtualViewFillInfo(mAutofillHints);
        }
    }
}
