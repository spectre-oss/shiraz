// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PostProcessor;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.AttributeSet;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class AnimatedImageDrawable extends Drawable implements Animatable2 {
    public static final int REPEAT_INFINITE = -1;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private Bitmap[] frames = new Bitmap[0];
    private int[] delaysMs = new int[0];
    private int intrinsicWidth = -1;
    private int intrinsicHeight = -1;
    private int repeatCount = REPEAT_INFINITE;
    private int completedLoops;
    private int currentFrame;
    private boolean running;
    private boolean autoMirrored;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dstRect = new Rect();
    private boolean dstRectDirty = true;
    private final ArrayList<AnimationCallback> callbacks = new ArrayList<AnimationCallback>();

    private final Runnable frameTask =
            new Runnable() {
                @Override
                public void run() {
                    advanceFrame();
                }
            };

    public AnimatedImageDrawable() {
    }

    public void shirazConfigure(
            Movie movie,
            int targetWidth,
            int targetHeight,
            Rect cropRect,
            PostProcessor postProcessor) {
        if (movie == null) {
            return;
        }
        int count = movie.shirazFrameCount();
        int outW = targetWidth > 0 ? targetWidth : movie.width();
        int outH = targetHeight > 0 ? targetHeight : movie.height();
        if (cropRect != null) {
            outW = cropRect.width();
            outH = cropRect.height();
        }
        Bitmap[] outFrames = new Bitmap[count];
        int[] outDelays = new int[count];
        for (int i = 0; i < count; i++) {
            Bitmap src = movie.shirazFrameBitmap(i);
            Bitmap transformed = transformFrame(src, targetWidth, targetHeight, cropRect, postProcessor);
            outFrames[i] = transformed;
            outDelays[i] = Math.max(10, movie.shirazFrameDelay(i));
        }
        this.frames = outFrames;
        this.delaysMs = outDelays;
        this.intrinsicWidth = outW;
        this.intrinsicHeight = outH;
        this.currentFrame = 0;
        this.dstRectDirty = true;
    }

    private static Bitmap transformFrame(
            Bitmap src,
            int targetWidth,
            int targetHeight,
            Rect cropRect,
            PostProcessor postProcessor) {
        Bitmap result = src;
        if (targetWidth > 0 && targetHeight > 0
                && (result.getWidth() != targetWidth || result.getHeight() != targetHeight)) {
            result = Bitmap.createScaledBitmap(result, targetWidth, targetHeight, true);
        }
        if (cropRect != null) {
            result = Bitmap.createBitmap(
                    result, cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
        }
        if (postProcessor != null) {
            if (!result.isMutable()) {
                result = result.copy(Bitmap.Config.ARGB_8888, true);
            }
            postProcessor.onPostProcess(new Canvas(result));
        }
        return result;
    }

    public void setRepeatCount(int repeatCount) {
        if (repeatCount < REPEAT_INFINITE) {
            throw new IllegalArgumentException(
                    "trying to set repeatCount to " + repeatCount + " must be >= -1");
        }
        if (this.repeatCount != repeatCount) {
            this.repeatCount = repeatCount;
        }
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    @Override
    public void inflate(
            Resources r, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme)
            throws XmlPullParserException, IOException {
        if (attrs == null) {
            return;
        }
        boolean mirrored = attrs.getAttributeBooleanValue(ANDROID_NS, "autoMirrored", false);
        setAutoMirrored(mirrored);
        int repeat = attrs.getAttributeIntValue(ANDROID_NS, "repeatCount", REPEAT_INFINITE);
        if (repeat >= REPEAT_INFINITE) {
            setRepeatCount(repeat);
        }
        int srcRes = attrs.getAttributeResourceValue(ANDROID_NS, "src", 0);
        if (srcRes != 0 && r != null) {
            InputStream in = r.openRawResource(srcRes);
            try {
                Movie movie = Movie.decodeStream(in);
                if (movie != null) {
                    shirazConfigure(movie, movie.width(), movie.height(), null, null);
                }
            } finally {
                in.close();
            }
        }
        boolean autoStart = attrs.getAttributeBooleanValue(ANDROID_NS, "autoStart", false);
        if (autoStart) {
            start();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null) {
            throw new NullPointerException("canvas");
        }
        if (frames.length == 0) {
            return;
        }
        if (dstRectDirty) {
            dstRect.set(getBounds());
            if (dstRect.isEmpty() && intrinsicWidth > 0 && intrinsicHeight > 0) {
                dstRect.set(0, 0, intrinsicWidth, intrinsicHeight);
            }
            dstRectDirty = false;
        }
        if (dstRect.isEmpty()) {
            return;
        }
        Bitmap bmp = frames[Math.min(currentFrame, frames.length - 1)];
        boolean mirror = autoMirrored && getLayoutDirection() == 1;
        if (mirror) {
            int save = canvas.save();
            try {
                canvas.translate(dstRect.left + dstRect.right, 0f);
                canvas.scale(-1f, 1f);
                canvas.drawBitmap(bmp, null, dstRect, paint);
            } finally {
                canvas.restoreToCount(save);
            }
        } else {
            canvas.drawBitmap(bmp, null, dstRect, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if ((alpha & ~0xFF) != 0) {
            throw new IllegalArgumentException(
                    "Alpha must be between 0 and 255! provided " + alpha);
        }
        if (paint.getAlpha() != alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public int getAlpha() {
        return paint.getAlpha();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public ColorFilter getColorFilter() {
        return paint.getColorFilter();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAutoMirrored(boolean mirrored) {
        if (autoMirrored != mirrored) {
            autoMirrored = mirrored;
            if (getLayoutDirection() == 1) {
                invalidateSelf();
            }
        }
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        return autoMirrored;
    }

    @Override
    public final boolean isAutoMirrored() {
        return autoMirrored;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        completedLoops = 0;
        currentFrame = 0;
        notifyStart();
        invalidateSelf();
        if (frames.length > 1) {
            scheduleSelf(frameTask, SystemClock.uptimeMillis() + delaysMs[currentFrame]);
        } else {
            running = false;
            notifyEnd();
        }
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        unscheduleSelf(frameTask);
        notifyEnd();
    }

    private void advanceFrame() {
        if (!running || frames.length <= 1) {
            return;
        }
        int next = currentFrame + 1;
        if (next >= frames.length) {
            completedLoops++;
            if (repeatCount != REPEAT_INFINITE && completedLoops > repeatCount) {
                running = false;
                unscheduleSelf(frameTask);
                invalidateSelf();
                notifyEnd();
                return;
            }
            next = 0;
        }
        currentFrame = next;
        invalidateSelf();
        scheduleSelf(frameTask, SystemClock.uptimeMillis() + delaysMs[currentFrame]);
    }

    private void notifyStart() {
        ArrayList<AnimationCallback> snapshot = new ArrayList<AnimationCallback>(callbacks);
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).onAnimationStart(this);
        }
    }

    private void notifyEnd() {
        ArrayList<AnimationCallback> snapshot = new ArrayList<AnimationCallback>(callbacks);
        for (int i = 0; i < snapshot.size(); i++) {
            snapshot.get(i).onAnimationEnd(this);
        }
    }

    @Override
    public void registerAnimationCallback(AnimationCallback callback) {
        if (callback == null) {
            return;
        }
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    @Override
    public boolean unregisterAnimationCallback(AnimationCallback callback) {
        if (callback == null) {
            return false;
        }
        return callbacks.remove(callback);
    }

    @Override
    public void clearAnimationCallbacks() {
        callbacks.clear();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        dstRectDirty = true;
    }
}
