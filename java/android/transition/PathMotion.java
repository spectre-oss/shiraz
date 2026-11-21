// SPDX-License-Identifier: GPL-3.0-only
package android.transition;

import android.content.Context;
import android.graphics.Path;
import android.util.AttributeSet;

/** Base 2D path generator between two coordinates for path-based transitions. */
public abstract class PathMotion {
    public PathMotion() {}

    public PathMotion(Context context, AttributeSet attrs) {}

    public abstract Path getPath(float startX, float startY, float endX, float endY);
}
