// SPDX-License-Identifier: GPL-3.0-only
package android.view.accessibility;

import android.os.Bundle;
import android.os.Message;
import android.view.View;
import java.lang.ref.WeakReference;

public abstract class AccessibilityRequestPreparer {
    public static final int REQUEST_TYPE_EXTRA_DATA = 1;

    private final WeakReference<View> mViewRef;
    private final int mRequestTypes;

    public AccessibilityRequestPreparer(View view, int requestTypes) {
        if (view == null) {
            throw new NullPointerException("view");
        }
        mViewRef = new WeakReference<View>(view);
        mRequestTypes = requestTypes;
    }

    public abstract void onPrepareExtraData(
            int virtualViewId,
            String extraDataKey,
            Bundle args,
            Message preparationFinishedMessage);

    public View getView() {
        return mViewRef.get();
    }

    int getRequestTypes() {
        return mRequestTypes;
    }
}
