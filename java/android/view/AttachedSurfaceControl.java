// SPDX-License-Identifier: GPL-3.0-only
package android.view;

import android.graphics.Rect;
import android.graphics.Region;
import android.window.InputTransferToken;

public interface AttachedSurfaceControl {
    SurfaceControl.Transaction buildReparentTransaction(SurfaceControl child);

    boolean applyTransactionOnDraw(SurfaceControl.Transaction t);

    default int getBufferTransformHint() {
        return SurfaceControl.BUFFER_TRANSFORM_IDENTITY;
    }

    default void addOnBufferTransformHintChangedListener(
            OnBufferTransformHintChangedListener listener) {
    }

    default void removeOnBufferTransformHintChangedListener(
            OnBufferTransformHintChangedListener listener) {
    }

    default void setTouchableRegion(Region r) {
    }

    default void setChildBoundingInsets(Rect insets) {
    }

    default InputTransferToken getInputTransferToken() {
        throw new UnsupportedOperationException(
                "getInputTransferToken is not implemented by this AttachedSurfaceControl");
    }

    interface OnBufferTransformHintChangedListener {
        void onBufferTransformHintChanged(int hint);
    }
}
