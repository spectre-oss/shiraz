// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.graphics.Insets;
import android.view.View;
import android.view.ViewStructure;
import android.view.autofill.AutofillId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ContentCaptureSession implements AutoCloseable {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final int mId;
    private ContentCaptureContext mClientContext;
    private boolean mDestroyed;

    ContentCaptureSession() {
        this(NEXT_ID.getAndIncrement());
    }

    ContentCaptureSession(int id) {
        mId = id;
    }

    public final ContentCaptureSessionId getContentCaptureSessionId() {
        return new ContentCaptureSessionId(mId);
    }

    public final ContentCaptureSession createContentCaptureSession(ContentCaptureContext context) {
        Objects.requireNonNull(context, "context");
        ContentCaptureSession child = new ChildContentCaptureSession(NEXT_ID.getAndIncrement());
        child.setContentCaptureContext(context);
        return child;
    }

    public final void setContentCaptureContext(ContentCaptureContext context) {
        mClientContext = context;
    }

    public final ContentCaptureContext getContentCaptureContext() {
        return mClientContext;
    }

    public final void destroy() {
        mDestroyed = true;
    }

    @Override
    public void close() {
        destroy();
    }

    public final void notifyViewAppeared(ViewStructure node) {
        Objects.requireNonNull(node, "node");
    }

    public final void notifyViewDisappeared(AutofillId id) {
        Objects.requireNonNull(id, "id");
    }

    public final void notifyViewsAppeared(List<ViewStructure> appearedNodes) {
        Objects.requireNonNull(appearedNodes, "appearedNodes");
    }

    public final void notifyViewsDisappeared(AutofillId hostId, long[] virtualIds) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(virtualIds, "virtualIds");
    }

    public final void notifyViewTextChanged(AutofillId id, CharSequence text) {
        Objects.requireNonNull(id, "id");
    }

    public final void notifyViewInsetsChanged(Insets viewInsets) {
        Objects.requireNonNull(viewInsets, "viewInsets");
    }

    public final void notifySessionResumed() {
    }

    public final void notifySessionPaused() {
    }

    public final ViewStructure newViewStructure(View view) {
        Objects.requireNonNull(view, "view");
        return null;
    }

    public AutofillId newAutofillId(AutofillId hostId, long virtualChildId) {
        Objects.requireNonNull(hostId, "hostId");
        return new AutofillId(hostId, (int) virtualChildId);
    }

    public final ViewStructure newVirtualViewStructure(AutofillId parentId, long virtualId) {
        Objects.requireNonNull(parentId, "parentId");
        return null;
    }

    @Override
    public String toString() {
        return Integer.toString(mId) + (mDestroyed ? "[destroyed]" : "");
    }

    private static final class ChildContentCaptureSession extends ContentCaptureSession {
        ChildContentCaptureSession(int id) {
            super(id);
        }
    }
}
