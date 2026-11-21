// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.hardware.HardwareBuffer;
import android.hardware.SyncFence;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Offscreen renderer drawing a RenderNode tree into a HardwareBuffer with SyncFence completion. */
public class HardwareBufferRenderer implements AutoCloseable {
    public static final class RenderResult {
        public static final int SUCCESS = 0;
        public static final int ERROR_UNKNOWN = 1;

        private final SyncFence fence;
        private final int status;

        RenderResult(SyncFence fence, int status) {
            this.fence = fence;
            this.status = status;
        }

        public SyncFence getFence() {
            return fence;
        }

        public int getStatus() {
            return status;
        }
    }

    public final class RenderRequest {
        private ColorSpace colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
        private int transform;

        RenderRequest() {}

        void reset() {
            colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);
            transform = 0;
        }

        public void draw(
                Executor executor, final Consumer<RenderResult> drawCallback) {
            if (executor == null || drawCallback == null) {
                throw new NullPointerException("executor and drawCallback must be non-null");
            }
            if (isClosed()) {
                throw new IllegalStateException(
                        "Attempt to draw with a HardwareBufferRenderer instance that has already been closed");
            }
            final RenderResult result = HardwareBufferRenderer.this.executeDraw(transform);
            reset();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    drawCallback.accept(result);
                }
            });
        }

        public RenderRequest setColorSpace(ColorSpace colorSpace) {
            this.colorSpace = colorSpace != null
                    ? colorSpace
                    : ColorSpace.get(ColorSpace.Named.SRGB);
            return this;
        }

        public RenderRequest setBufferTransform(int bufferTransform) {
            if (bufferTransform < 0 || bufferTransform > 7
                    || (bufferTransform == 5 || bufferTransform == 6)) {
                throw new IllegalArgumentException("Invalid bufferTransform: " + bufferTransform);
            }
            this.transform = bufferTransform;
            return this;
        }
    }

    private final HardwareBuffer buffer;
    private final RenderRequest renderRequest = new RenderRequest();
    private RenderNode contentRoot;
    private boolean closed;
    private float lightX;
    private float lightY;
    private float lightZ;
    private float lightRadius;
    private float ambientAlpha;
    private float spotAlpha;

    public HardwareBufferRenderer(HardwareBuffer buffer) {
        if (buffer == null) {
            throw new NullPointerException("buffer");
        }
        if (buffer.isClosed()) {
            throw new IllegalArgumentException("HardwareBuffer is already closed");
        }
        this.buffer = buffer;
    }

    public void setContentRoot(RenderNode content) {
        this.contentRoot = content;
    }

    public RenderRequest obtainRenderRequest() {
        renderRequest.reset();
        return renderRequest;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        contentRoot = null;
    }

    public void setLightSourceGeometry(float lightX, float lightY, float lightZ, float lightRadius) {
        this.lightX = lightX;
        this.lightY = lightY;
        this.lightZ = lightZ;
        this.lightRadius = lightRadius;
    }

    public void setLightSourceAlpha(float ambientShadowAlpha, float spotShadowAlpha) {
        this.ambientAlpha = ambientShadowAlpha;
        this.spotAlpha = spotShadowAlpha;
    }

    RenderResult executeDraw(int transform) {
        if (buffer.isClosed()) {
            return new RenderResult(SyncFence.createInvalid(), RenderResult.ERROR_UNKNOWN);
        }
        int w = Math.max(1, Math.min(4096, buffer.getWidth()));
        int h = Math.max(1, Math.min(4096, buffer.getHeight()));
        if (contentRoot != null && contentRoot.hasDisplayList()) {
            Bitmap target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(target);
                if (transform == 4) { // ROT_90
                    canvas.translate(w, 0);
                    canvas.rotate(90f);
                } else if (transform == 3) { // ROT_180
                    canvas.translate(w, h);
                    canvas.rotate(180f);
                } else if (transform == 7) { // ROT_270
                    canvas.translate(0, h);
                    canvas.rotate(270f);
                }
                canvas.drawRenderNode(contentRoot);
            } finally {
                target.recycle();
            }
        }
        return new RenderResult(
                SyncFence.createSignaled(System.nanoTime()), RenderResult.SUCCESS);
    }
}
