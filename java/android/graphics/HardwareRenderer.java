// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import android.view.Surface;
import java.util.concurrent.Executor;

/** Renders a RenderNode hierarchy onto a Surface with frame-commit synchronization. */
public class HardwareRenderer {
    public static final int SYNC_OK = 0;
    public static final int SYNC_REDRAW_REQUESTED = 1;
    public static final int SYNC_LOST_SURFACE_REWARD_IF_FOUND = 2;
    public static final int SYNC_CONTEXT_IS_STOPPED = 4;
    public static final int SYNC_FRAME_DROPPED = 8;

    private static volatile boolean sDrawingEnabled = true;

    private String name;
    private RenderNode contentRoot;
    private Surface surface;
    private boolean opaque = true;
    private boolean stopped;
    private boolean destroyed;
    private float lightX;
    private float lightY;
    private float lightZ;
    private float lightRadius;
    private float ambientShadowAlpha;
    private float spotShadowAlpha;

    public final class FrameRenderRequest {
        private long vsyncTime;
        private Executor commitExecutor;
        private Runnable commitCallback;
        private boolean waitForPresent;

        FrameRenderRequest() {}

        void reset() {
            vsyncTime = 0L;
            commitExecutor = null;
            commitCallback = null;
            waitForPresent = false;
        }

        public FrameRenderRequest setVsyncTime(long vsyncTime) {
            this.vsyncTime = vsyncTime;
            return this;
        }

        public FrameRenderRequest setFrameCommitCallback(
                Executor executor, Runnable frameCommitCallback) {
            this.commitExecutor = executor;
            this.commitCallback = frameCommitCallback;
            return this;
        }

        public FrameRenderRequest setWaitForPresent(boolean shouldWait) {
            this.waitForPresent = shouldWait;
            return this;
        }

        public int syncAndDraw() {
            int result = HardwareRenderer.this.renderFrame(commitExecutor, commitCallback);
            reset();
            return result;
        }
    }

    private final FrameRenderRequest renderRequest = new FrameRenderRequest();

    public HardwareRenderer() {}

    public void destroy() {
        destroyed = true;
        stopped = true;
        surface = null;
        contentRoot = null;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLightSourceGeometry(float lightX, float lightY, float lightZ, float lightRadius) {
        this.lightX = lightX;
        this.lightY = lightY;
        this.lightZ = lightZ;
        this.lightRadius = lightRadius;
    }

    public void setLightSourceAlpha(float ambientShadowAlpha, float spotShadowAlpha) {
        this.ambientShadowAlpha = ambientShadowAlpha;
        this.spotShadowAlpha = spotShadowAlpha;
    }

    public void setContentRoot(RenderNode content) {
        this.contentRoot = content;
    }

    public void setSurface(Surface surface) {
        if (surface != null && !surface.isValid()) {
            throw new IllegalArgumentException("Surface must be valid: " + surface);
        }
        this.surface = surface;
    }

    public FrameRenderRequest createRenderRequest() {
        renderRequest.reset();
        return renderRequest;
    }

    public void stop() {
        stopped = true;
    }

    public void start() {
        if (!destroyed) {
            stopped = false;
        }
    }

    public void clearContent() {
        if (surface != null && surface.isValid()) {
            Canvas c = surface.lockCanvas(null);
            try {
                c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            } finally {
                surface.unlockCanvasAndPost(c);
            }
        }
    }

    public void notifyFramePending() {}

    public void setOpaque(boolean opaque) {
        this.opaque = opaque;
    }

    public boolean isOpaque() {
        return opaque;
    }

    public static boolean isDrawingEnabled() {
        return sDrawingEnabled;
    }

    public static void setDrawingEnabled(boolean drawingEnabled) {
        sDrawingEnabled = drawingEnabled;
    }

    int renderFrame(Executor executor, Runnable callback) {
        if (destroyed || stopped) {
            return SYNC_CONTEXT_IS_STOPPED;
        }
        if (!sDrawingEnabled) {
            return SYNC_FRAME_DROPPED;
        }
        if (surface == null || !surface.isValid()) {
            return SYNC_LOST_SURFACE_REWARD_IF_FOUND;
        }
        if (contentRoot == null || !contentRoot.hasDisplayList()) {
            return SYNC_FRAME_DROPPED;
        }
        Canvas canvas = surface.lockCanvas(null);
        try {
            if (!opaque) {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            }
            canvas.drawRenderNode(contentRoot);
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
        if (executor != null && callback != null) {
            executor.execute(callback);
        }
        return SYNC_OK;
    }
}
