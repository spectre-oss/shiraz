// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf;

public final class LoadParams {
    private final String mPassword;

    private LoadParams(String password) {
        this.mPassword = password;
    }

    public String getPassword() {
        return mPassword;
    }

    public static final class Builder {
        private String mPassword;

        public Builder() {
        }

        public Builder setPassword(String password) {
            this.mPassword = password;
            return this;
        }

        public LoadParams build() {
            return new LoadParams(mPassword);
        }
    }
}
