// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.os.Handler;
import android.os.Looper;

public class HttpAuthHandler extends Handler {
    private boolean mCancelled;
    private String mUsername;
    private String mPassword;

    public HttpAuthHandler() {
        super(Looper.getMainLooper() != null ? Looper.getMainLooper() : Looper.myLooper());
    }

    public boolean useHttpAuthUsernamePassword() {
        return false;
    }

    public void cancel() {
        mCancelled = true;
    }

    public void proceed(String username, String password) {
        mCancelled = false;
        mUsername = username;
        mPassword = password;
    }
}
