// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

public abstract class SandboxedSdkProvider {
    private Context mContext;

    public SandboxedSdkProvider() {
    }

    public final void attachContext(Context context) {
        if (mContext != null) {
            throw new IllegalStateException("Context already set");
        }
        if (context == null) {
            throw new NullPointerException("context");
        }
        mContext = context;
    }

    public final Context getContext() {
        return mContext;
    }

    public abstract SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException;

    public void beforeUnloadSdk() {
    }

    public abstract View getView(
            Context windowContext, Bundle params, int widthInPixels, int heightInPixels);
}
