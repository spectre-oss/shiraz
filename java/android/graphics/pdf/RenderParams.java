// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.pdf;

public final class RenderParams {
    public static final int RENDER_MODE_FOR_DISPLAY = 1;
    public static final int RENDER_MODE_FOR_PRINT = 2;

    public static final int FLAG_RENDER_TEXT_ANNOTATIONS = 2;
    public static final int FLAG_RENDER_HIGHLIGHT_ANNOTATIONS = 4;

    private static final int FLAGS_MASK =
            FLAG_RENDER_TEXT_ANNOTATIONS | FLAG_RENDER_HIGHLIGHT_ANNOTATIONS;

    private final int mRenderMode;
    private final int mRenderFlags;

    private RenderParams(int renderMode, int renderFlags) {
        this.mRenderMode = renderMode;
        this.mRenderFlags = renderFlags;
    }

    public int getRenderMode() {
        return mRenderMode;
    }

    public int getRenderFlags() {
        return mRenderFlags;
    }

    public static final class Builder {
        private final int mRenderMode;
        private int mRenderFlags;

        public Builder(int renderMode) {
            if (renderMode != RENDER_MODE_FOR_DISPLAY && renderMode != RENDER_MODE_FOR_PRINT) {
                throw new IllegalArgumentException("Invalid render mode: " + renderMode);
            }
            this.mRenderMode = renderMode;
        }

        public Builder setRenderFlags(int renderFlags) {
            return setRenderFlags(renderFlags, FLAGS_MASK);
        }

        public Builder setRenderFlags(int renderFlags, int mask) {
            mask &= FLAGS_MASK;
            renderFlags &= mask;
            this.mRenderFlags = (this.mRenderFlags & ~mask) | renderFlags;
            return this;
        }

        public RenderParams build() {
            return new RenderParams(mRenderMode, mRenderFlags);
        }
    }
}
