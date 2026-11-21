// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Insets;
import android.graphics.Path;
import android.graphics.Rect;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable cutout geometry. Host desktop surfaces supply no physical cutout. */
public final class DisplayCutout {
    private final Insets safe;
    private final Insets waterfall;
    private final Rect[] bounds;
    private final Path cutoutPath;

    public DisplayCutout(Insets safeInsets, Rect left, Rect top, Rect right, Rect bottom) {
        this(safeInsets, left, top, right, bottom, Insets.NONE, null);
    }

    public DisplayCutout(Insets safeInsets, Rect left, Rect top, Rect right, Rect bottom,
            Insets waterfallInsets) {
        this(safeInsets, left, top, right, bottom, waterfallInsets, null);
    }

    private DisplayCutout(Insets safeInsets, Rect left, Rect top, Rect right, Rect bottom,
            Insets waterfallInsets, Path path) {
        if (safeInsets == null) throw new NullPointerException("safeInsets");
        if (waterfallInsets == null) throw new NullPointerException("waterfallInsets");
        safe = safeInsets;
        waterfall = waterfallInsets;
        bounds = new Rect[] {
            left != null ? new Rect(left) : new Rect(),
            top != null ? new Rect(top) : new Rect(),
            right != null ? new Rect(right) : new Rect(),
            bottom != null ? new Rect(bottom) : new Rect()
        };
        cutoutPath = path != null ? new Path(path) : null;
    }

    @Deprecated
    public DisplayCutout(Rect safeInsets, List<Rect> rectangles) {
        safe = Insets.of(safeInsets);
        waterfall = Insets.NONE;
        cutoutPath = null;
        bounds = new Rect[] { new Rect(), new Rect(), new Rect(), new Rect() };
        if (safeInsets != null && rectangles != null) {
            for (Rect r : rectangles) {
                int edge = r.left == 0 ? 0 : r.top == 0 ? 1 : safe.right > 0 ? 2 : safe.bottom > 0 ? 3 : -1;
                if (edge >= 0) bounds[edge] = new Rect(r);
            }
        }
    }

    public Insets getWaterfallInsets() {
        return waterfall;
    }

    public int getSafeInsetLeft() { return safe.left; }
    public int getSafeInsetTop() { return safe.top; }
    public int getSafeInsetRight() { return safe.right; }
    public int getSafeInsetBottom() { return safe.bottom; }
    public Rect getSafeInsets() { return safe.toRect(); }
    public Rect getBoundingRectLeft() { return new Rect(bounds[0]); }
    public Rect getBoundingRectTop() { return new Rect(bounds[1]); }
    public Rect getBoundingRectRight() { return new Rect(bounds[2]); }
    public Rect getBoundingRectBottom() { return new Rect(bounds[3]); }

    public List<Rect> getBoundingRects() {
        ArrayList<Rect> result = new ArrayList<Rect>();
        for (Rect r : bounds) {
            if (!r.isEmpty()) result.add(new Rect(r));
        }
        return result;
    }

    public Path getCutoutPath() {
        return cutoutPath != null ? new Path(cutoutPath) : null;
    }

    public boolean isEmpty() {
        return safe.equals(Insets.NONE) && waterfall.equals(Insets.NONE) && isBoundsEmpty();
    }

    public boolean isBoundsEmpty() {
        for (Rect r : bounds) {
            if (!r.isEmpty()) return false;
        }
        return waterfall.equals(Insets.NONE);
    }

    DisplayCutout shirazInset(int l, int t, int r, int b) {
        if ((isBoundsEmpty() && safe.equals(Insets.NONE)) || (l | t | r | b) == 0) return this;
        Rect[] moved = new Rect[4];
        for (int i = 0; i < 4; i++) {
            moved[i] = new Rect(bounds[i]);
            if (!moved[i].isEmpty()) moved[i].offset(-l, -t);
        }
        Insets nextSafe = Insets.of(
                Math.max(0, safe.left - l),
                Math.max(0, safe.top - t),
                Math.max(0, safe.right - r),
                Math.max(0, safe.bottom - b));
        Insets nextWaterfall = Insets.of(
                Math.max(0, waterfall.left - l),
                Math.max(0, waterfall.top - t),
                Math.max(0, waterfall.right - r),
                Math.max(0, waterfall.bottom - b));
        return new DisplayCutout(nextSafe, moved[0], moved[1], moved[2], moved[3],
                nextWaterfall, cutoutPath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DisplayCutout)) return false;
        DisplayCutout c = (DisplayCutout) o;
        return safe.equals(c.safe)
                && waterfall.equals(c.waterfall)
                && Arrays.equals(bounds, c.bounds)
                && Objects.equals(cutoutPath, c.cutoutPath);
    }

    @Override
    public int hashCode() {
        int result = safe.hashCode();
        result = 31 * result + waterfall.hashCode();
        result = 31 * result + Arrays.hashCode(bounds);
        return result;
    }

    @Override
    public String toString() {
        return "DisplayCutout{insets=" + safe
                + " waterfall=" + waterfall
                + " boundingRect={" + Arrays.toString(bounds) + "}"
                + " cutoutPath=" + cutoutPath + "}";
    }

    public static final class Builder {
        private Insets mSafeInsets = Insets.NONE;
        private Insets mWaterfallInsets = Insets.NONE;
        private Path mCutoutPath;
        private final Rect mBoundingRectLeft = new Rect();
        private final Rect mBoundingRectTop = new Rect();
        private final Rect mBoundingRectRight = new Rect();
        private final Rect mBoundingRectBottom = new Rect();

        public Builder() {
        }

        public DisplayCutout build() {
            return new DisplayCutout(
                    mSafeInsets,
                    mBoundingRectLeft,
                    mBoundingRectTop,
                    mBoundingRectRight,
                    mBoundingRectBottom,
                    mWaterfallInsets,
                    mCutoutPath);
        }

        public Builder setSafeInsets(Insets safeInsets) {
            mSafeInsets = Objects.requireNonNull(safeInsets, "safeInsets must not be null");
            return this;
        }

        public Builder setWaterfallInsets(Insets waterfallInsets) {
            mWaterfallInsets = Objects.requireNonNull(waterfallInsets,
                    "waterfallInsets must not be null");
            return this;
        }

        public Builder setBoundingRectLeft(Rect boundingRectLeft) {
            mBoundingRectLeft.set(Objects.requireNonNull(boundingRectLeft));
            return this;
        }

        public Builder setBoundingRectTop(Rect boundingRectTop) {
            mBoundingRectTop.set(Objects.requireNonNull(boundingRectTop));
            return this;
        }

        public Builder setBoundingRectRight(Rect boundingRectRight) {
            mBoundingRectRight.set(Objects.requireNonNull(boundingRectRight));
            return this;
        }

        public Builder setBoundingRectBottom(Rect boundingRectBottom) {
            mBoundingRectBottom.set(Objects.requireNonNull(boundingRectBottom));
            return this;
        }

        public Builder setCutoutPath(Path cutoutPath) {
            mCutoutPath = cutoutPath;
            return this;
        }
    }
}
