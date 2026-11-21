// SPDX-License-Identifier: GPL-3.0-only
package android.gesture;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class Gesture implements Parcelable {
    private static final long GESTURE_ID_BASE = System.currentTimeMillis();
    private static final int BITMAP_RENDERING_WIDTH = 2;
    private static final boolean BITMAP_RENDERING_ANTIALIAS = true;
    private static final boolean BITMAP_RENDERING_DITHER = true;
    private static final AtomicInteger sGestureCount = new AtomicInteger(0);

    private final RectF mBoundingBox = new RectF();
    private long mGestureID;
    private final ArrayList<GestureStroke> mStrokes = new ArrayList<GestureStroke>();

    public Gesture() {
        mGestureID = GESTURE_ID_BASE + sGestureCount.incrementAndGet();
    }

    @Override
    public Object clone() {
        Gesture gesture = new Gesture();
        gesture.mBoundingBox.set(mBoundingBox.left, mBoundingBox.top,
                mBoundingBox.right, mBoundingBox.bottom);
        final int count = mStrokes.size();
        for (int i = 0; i < count; i++) {
            GestureStroke stroke = mStrokes.get(i);
            gesture.mStrokes.add((GestureStroke) stroke.clone());
        }
        return gesture;
    }

    public ArrayList<GestureStroke> getStrokes() {
        return mStrokes;
    }

    public int getStrokesCount() {
        return mStrokes.size();
    }

    public void addStroke(GestureStroke stroke) {
        mStrokes.add(stroke);
        if (mStrokes.size() == 1) {
            mBoundingBox.set(stroke.boundingBox.left, stroke.boundingBox.top,
                    stroke.boundingBox.right, stroke.boundingBox.bottom);
        } else {
            mBoundingBox.left = Math.min(mBoundingBox.left, stroke.boundingBox.left);
            mBoundingBox.top = Math.min(mBoundingBox.top, stroke.boundingBox.top);
            mBoundingBox.right = Math.max(mBoundingBox.right, stroke.boundingBox.right);
            mBoundingBox.bottom = Math.max(mBoundingBox.bottom, stroke.boundingBox.bottom);
        }
    }

    public float getLength() {
        float len = 0.0f;
        final ArrayList<GestureStroke> strokes = mStrokes;
        final int count = strokes.size();
        for (int i = 0; i < count; i++) {
            len += strokes.get(i).length;
        }
        return len;
    }

    public RectF getBoundingBox() {
        return mBoundingBox;
    }

    public Path toPath() {
        return toPath(null);
    }

    public Path toPath(Path path) {
        if (path == null) {
            path = new Path();
        }
        final ArrayList<GestureStroke> strokes = mStrokes;
        final int count = strokes.size();
        for (int i = 0; i < count; i++) {
            path.addPath(strokes.get(i).getPath());
        }
        return path;
    }

    public Path toPath(int width, int height, int edge, int numSample) {
        return toPath(null, width, height, edge, numSample);
    }

    public Path toPath(Path path, int width, int height, int edge, int numSample) {
        if (path == null) {
            path = new Path();
        }
        final ArrayList<GestureStroke> strokes = mStrokes;
        final int count = strokes.size();
        for (int i = 0; i < count; i++) {
            path.addPath(strokes.get(i).toPath(width - 2 * edge, height - 2 * edge, numSample),
                    edge, edge);
        }
        return path;
    }

    void setID(long id) {
        mGestureID = id;
    }

    public long getID() {
        return mGestureID;
    }

    public Bitmap toBitmap(int width, int height, int edge, int numSample, int color) {
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);

        canvas.translate(edge, edge);

        final Paint paint = new Paint();
        paint.setAntiAlias(BITMAP_RENDERING_ANTIALIAS);
        paint.setDither(BITMAP_RENDERING_DITHER);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(BITMAP_RENDERING_WIDTH);

        final ArrayList<GestureStroke> strokes = mStrokes;
        final int count = strokes.size();
        for (int i = 0; i < count; i++) {
            Path path = strokes.get(i).toPath(width - 2 * edge, height - 2 * edge, numSample);
            canvas.drawPath(path, paint);
        }

        return bitmap;
    }

    public Bitmap toBitmap(int width, int height, int inset, int color) {
        final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);

        final Paint paint = new Paint();
        paint.setAntiAlias(BITMAP_RENDERING_ANTIALIAS);
        paint.setDither(BITMAP_RENDERING_DITHER);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(BITMAP_RENDERING_WIDTH);

        final Path path = toPath();
        final RectF bounds = new RectF(mBoundingBox.left, mBoundingBox.top,
                mBoundingBox.right, mBoundingBox.bottom);

        float bw = bounds.width();
        float bh = bounds.height();
        float sx = bw > 0.0f ? (width - 2.0f * inset) / bw : 1.0f;
        float sy = bh > 0.0f ? (height - 2.0f * inset) / bh : 1.0f;
        float scale;
        if (bw > 0.0f && bh > 0.0f) {
            scale = sx > sy ? sy : sx;
        } else if (bw > 0.0f) {
            scale = sx;
        } else if (bh > 0.0f) {
            scale = sy;
        } else {
            scale = 1.0f;
        }
        paint.setStrokeWidth(2.0f / scale);

        path.offset(-bounds.left + (width - bw * scale) / 2.0f,
                -bounds.top + (height - bh * scale) / 2.0f);

        canvas.translate(inset, inset);
        canvas.scale(scale, scale);
        canvas.drawPath(path, paint);

        return bitmap;
    }

    public void serialize(DataOutputStream out) throws IOException {
        final ArrayList<GestureStroke> strokes = mStrokes;
        final int count = strokes.size();

        out.writeLong(mGestureID);
        out.writeInt(count);

        for (int i = 0; i < count; i++) {
            strokes.get(i).serialize(out);
        }
    }

    public static Gesture deserialize(DataInputStream in) throws IOException {
        final Gesture gesture = new Gesture();
        gesture.mGestureID = in.readLong();
        final int count = in.readInt();
        for (int i = 0; i < count; i++) {
            gesture.addStroke(GestureStroke.deserialize(in));
        }
        return gesture;
    }

    public static final Parcelable.Creator<Gesture> CREATOR = new Parcelable.Creator<Gesture>() {
        @Override
        public Gesture createFromParcel(Parcel in) {
            Gesture gesture = null;
            final long gestureID = in.readLong();
            final byte[] bytes = in.createByteArray();
            if (bytes != null) {
                final DataInputStream inStream =
                        new DataInputStream(new ByteArrayInputStream(bytes));
                try {
                    gesture = deserialize(inStream);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to deserialize Gesture from Parcel", e);
                } finally {
                    GestureUtils.closeStream(inStream);
                }
                if (gesture != null) {
                    gesture.mGestureID = gestureID;
                }
            }
            return gesture;
        }

        @Override
        public Gesture[] newArray(int size) {
            return new Gesture[size];
        }
    };

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(mGestureID);
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(32768);
        final DataOutputStream outStream = new DataOutputStream(byteStream);
        try {
            serialize(outStream);
            out.writeByteArray(byteStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize Gesture to Parcel", e);
        } finally {
            GestureUtils.closeStream(outStream);
            GestureUtils.closeStream(byteStream);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
