// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Objects;

/** Input passed to {@link IsolatedWorker#onRender}. */
public final class RenderInput {
    private final int mWidth;
    private final int mHeight;
    private final RenderingConfig mRenderingConfig;

    RenderInput(int width, int height, RenderingConfig renderingConfig) {
        this.mWidth = width;
        this.mHeight = height;
        this.mRenderingConfig = renderingConfig;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public RenderingConfig getRenderingConfig() {
        return mRenderingConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RenderInput)) {
            return false;
        }
        RenderInput that = (RenderInput) o;
        return mWidth == that.mWidth
                && mHeight == that.mHeight
                && Objects.equals(mRenderingConfig, that.mRenderingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Integer.valueOf(mWidth), Integer.valueOf(mHeight), mRenderingConfig);
    }
}
