// SPDX-License-Identifier: GPL-3.0-only
package android.app;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.View;
import java.util.List;
import java.util.Map;

public abstract class SharedElementCallback {
    public interface OnSharedElementsReadyListener {
        void onSharedElementsReady();
    }

    public SharedElementCallback() {
    }

    public void onSharedElementStart(List<String> sharedElementNames,
            List<View> sharedElements, List<View> sharedElementSnapshots) {
    }

    public void onSharedElementEnd(List<String> sharedElementNames,
            List<View> sharedElements, List<View> sharedElementSnapshots) {
    }

    public void onRejectSharedElements(List<View> rejectedSharedElements) {
    }

    public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
    }

    public Parcelable onCaptureSharedElementSnapshot(View sharedElement,
            Matrix viewToGlobalMatrix, RectF screenBounds) {
        return new Bundle();
    }

    public View onCreateSnapshotView(Context context, Parcelable snapshot) {
        return context != null ? new View(context) : null;
    }

    public void onSharedElementsArrived(List<String> sharedElementNames,
            List<View> sharedElements, OnSharedElementsReadyListener listener) {
        if (listener != null) {
            listener.onSharedElementsReady();
        }
    }
}
