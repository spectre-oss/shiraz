// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import android.graphics.Path;
import android.graphics.RectF;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class GestureStroke {
    static final float TOUCH_TOLERANCE = 3.0f;

    public final RectF boundingBox;
    public final float length;
    public final float[] points;

    private final long[] timestamps;
    private Path mCachedPath;

    public GestureStroke(ArrayList<GesturePoint> points) {
        final int count = points.size();
        final float[] tmpPoints = new float[count * 2];
        final long[] times = new long[count];

        RectF bx = null;
        float len = 0.0f;
        int index = 0;

        for (int i = 0; i < count; i++) {
            final GesturePoint p = points.get(i);
            tmpPoints[i * 2] = p.x;
            tmpPoints[i * 2 + 1] = p.y;
            times[index] = p.timestamp;

            if (bx == null) {
                bx = new RectF(p.x, p.y, p.x, p.y);
            } else {
                if (p.x < bx.left) bx.left = p.x;
                if (p.y < bx.top) bx.top = p.y;
                if (p.x > bx.right) bx.right = p.x;
                if (p.y > bx.bottom) bx.bottom = p.y;
                len += (float) Math.hypot(p.x - tmpPoints[(i - 1) * 2],
                        p.y - tmpPoints[(i - 1) * 2 + 1]);
            }
            index++;
        }

        this.timestamps = times;
        this.points = tmpPoints;
        this.boundingBox = bx != null ? bx : new RectF();
        this.length = len;
    }

    private GestureStroke(RectF bbx, float len, float[] pts, long[] times) {
        boundingBox = new RectF(bbx.left, bbx.top, bbx.right, bbx.bottom);
        length = len;
        points = pts.clone();
        timestamps = times.clone();
    }

    @Override
    public Object clone() {
        return new GestureStroke(boundingBox, length, points, timestamps);
    }

    public Path getPath() {
        if (mCachedPath == null) {
            makePath();
        }
        return mCachedPath;
    }

    private void makePath() {
        final float[] localPoints = points;
        final int count = localPoints.length;
        Path path = new Path();

        float mX = 0.0f;
        float mY = 0.0f;
        for (int i = 0; i < count; i += 2) {
            float x = localPoints[i];
            float y = localPoints[i + 1];
            if (i == 0) {
                path.moveTo(x, y);
                mX = x;
                mY = y;
            } else {
                float dx = Math.abs(x - mX);
                float dy = Math.abs(y - mY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    path.quadTo(mX, mY, (x + mX) * 0.5f, (y + mY) * 0.5f);
                    mX = x;
                    mY = y;
                }
            }
        }
        if (count >= 4) {
            path.lineTo(localPoints[count - 2], localPoints[count - 1]);
        } else if (count == 2) {
            path.lineTo(localPoints[0], localPoints[1]);
        }

        mCachedPath = path;
    }

    public Path toPath(float width, float height, int numSample) {
        final float[] pts = GestureUtils.temporalSampling(this, numSample);
        final RectF rect = boundingBox;

        GestureUtils.translate(pts, -rect.left, -rect.top);

        float sx = rect.width() != 0.0f ? width / rect.width() : 1.0f;
        float sy = rect.height() != 0.0f ? height / rect.height() : 1.0f;
        float scale = sx > sy ? sy : sx;
        GestureUtils.scale(pts, scale, scale);

        float mX = 0.0f;
        float mY = 0.0f;
        Path path = new Path();

        final int count = pts.length;
        for (int i = 0; i < count; i += 2) {
            float x = pts[i];
            float y = pts[i + 1];
            if (i == 0) {
                path.moveTo(x, y);
                mX = x;
                mY = y;
            } else {
                float dx = Math.abs(x - mX);
                float dy = Math.abs(y - mY);
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    path.quadTo(mX, mY, (x + mX) * 0.5f, (y + mY) * 0.5f);
                    mX = x;
                    mY = y;
                }
            }
        }
        if (count >= 4) {
            path.lineTo(pts[count - 2], pts[count - 1]);
        }

        return path;
    }

    void serialize(DataOutputStream out) throws IOException {
        final float[] pts = points;
        final long[] times = timestamps;
        final int count = points.length;

        out.writeInt(count / 2);
        for (int i = 0; i < count; i += 2) {
            out.writeFloat(pts[i]);
            out.writeFloat(pts[i + 1]);
            out.writeLong(times[i / 2]);
        }
    }

    static GestureStroke deserialize(DataInputStream in) throws IOException {
        final int count = in.readInt();
        final ArrayList<GesturePoint> points = new ArrayList<GesturePoint>(count);
        for (int i = 0; i < count; i++) {
            points.add(GesturePoint.deserialize(in));
        }
        return new GestureStroke(points);
    }

    public void clearPath() {
        if (mCachedPath != null) {
            mCachedPath.rewind();
            mCachedPath = null;
        }
    }

    public OrientedBoundingBox computeOrientedBoundingBox() {
        return GestureUtils.computeOrientedBoundingBox(points);
    }
}
