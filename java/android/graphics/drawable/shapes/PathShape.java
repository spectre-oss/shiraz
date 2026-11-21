// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.Objects;

public class PathShape extends Shape {
    private Path path;
    private final float stdWidth;
    private final float stdHeight;
    private float scaleX;
    private float scaleY;

    public PathShape(Path path, float stdWidth, float stdHeight) {
        if (!Float.isFinite(stdWidth) || !Float.isFinite(stdHeight)) {
            throw new IllegalArgumentException("PathShape standard dimensions must be finite");
        }
        this.path = path == null ? new Path() : new Path(path);
        this.stdWidth = stdWidth;
        this.stdHeight = stdHeight;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        int saved = canvas.save();
        try {
            canvas.scale(scaleX, scaleY);
            canvas.drawPath(path, paint);
        } finally {
            canvas.restoreToCount(saved);
        }
    }

    @Override
    protected void onResize(float width, float height) {
        scaleX = stdWidth > 0f ? width / stdWidth : 0f;
        scaleY = stdHeight > 0f ? height / stdHeight : 0f;
    }

    @Override
    public PathShape clone() throws CloneNotSupportedException {
        PathShape shape = (PathShape) super.clone();
        shape.path = new Path(path);
        return shape;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PathShape that = (PathShape) o;
        return Float.compare(that.getWidth(), getWidth()) == 0
                && Float.compare(that.getHeight(), getHeight()) == 0
                && Float.compare(that.stdWidth, stdWidth) == 0
                && Float.compare(that.stdHeight, stdHeight) == 0
                && Float.compare(that.scaleX, scaleX) == 0
                && Float.compare(that.scaleY, scaleY) == 0
                && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Float.valueOf(getWidth()),
                Float.valueOf(getHeight()),
                Float.valueOf(stdWidth),
                Float.valueOf(stdHeight),
                Float.valueOf(scaleX),
                Float.valueOf(scaleY),
                path);
    }
}
