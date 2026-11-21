// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import android.graphics.Path;

public class OrientedBoundingBox {
    public final float squareness;
    public final float width;
    public final float height;
    public final float orientation;
    public final float centerX;
    public final float centerY;

    OrientedBoundingBox(float angle, float cx, float cy, float w, float h) {
        orientation = angle;
        width = w;
        height = h;
        centerX = cx;
        centerY = cy;
        if (w == 0f && h == 0f) {
            squareness = 1.0f;
        } else if (h == 0f || w == 0f) {
            squareness = 0.0f;
        } else {
            float ratio = w / h;
            if (ratio > 1.0f) {
                squareness = 1.0f / ratio;
            } else {
                squareness = ratio;
            }
        }
    }

    public Path toPath() {
        Path path = new Path();
        double radians = Math.toRadians(orientation);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float hw = width * 0.5f;
        float hh = height * 0.5f;

        float x0 = -hw * cos - hh * sin + centerX;
        float y0 = -hw * sin + hh * cos + centerY;
        path.moveTo(x0, y0);

        float x1 = -hw * cos + hh * sin + centerX;
        float y1 = -hw * sin - hh * cos + centerY;
        path.lineTo(x1, y1);

        float x2 = hw * cos + hh * sin + centerX;
        float y2 = hw * sin - hh * cos + centerY;
        path.lineTo(x2, y2);

        float x3 = hw * cos - hh * sin + centerX;
        float y3 = hw * sin + hh * cos + centerY;
        path.lineTo(x3, y3);

        path.close();
        return path;
    }
}
