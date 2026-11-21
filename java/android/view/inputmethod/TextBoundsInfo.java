// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Layout;
import android.text.SegmentFinder;
import java.util.Arrays;
import java.util.Objects;

public final class TextBoundsInfo implements Parcelable {
    public static final int FLAG_CHARACTER_WHITESPACE = 1;
    public static final int FLAG_CHARACTER_LINEFEED = 2;
    public static final int FLAG_CHARACTER_PUNCTUATION = 4;
    public static final int FLAG_LINE_IS_RTL = 8;

    private final float[] mMatrixValues;
    private final int mStart;
    private final int mEnd;
    private final float[] mCharacterBounds;
    private final int[] mCharacterFlags;
    private final int[] mCharacterBidiLevels;
    private final SegmentFinder mGraphemeSegmentFinder;
    private final SegmentFinder mWordSegmentFinder;
    private final SegmentFinder mLineSegmentFinder;

    private TextBoundsInfo(Builder builder) {
        mMatrixValues = builder.mMatrixValues.clone();
        mStart = builder.mStart;
        mEnd = builder.mEnd;
        int len = mEnd - mStart;
        mCharacterBounds = builder.mCharacterBounds != null
                ? builder.mCharacterBounds.clone() : new float[len * 4];
        mCharacterFlags = builder.mCharacterFlags != null
                ? builder.mCharacterFlags.clone() : new int[len];
        mCharacterBidiLevels = builder.mCharacterBidiLevels != null
                ? builder.mCharacterBidiLevels.clone() : new int[len];
        mGraphemeSegmentFinder = builder.mGraphemeSegmentFinder;
        mWordSegmentFinder = builder.mWordSegmentFinder;
        mLineSegmentFinder = builder.mLineSegmentFinder;
    }

    private TextBoundsInfo(Parcel source) {
        mStart = source.readInt();
        mEnd = source.readInt();
        mMatrixValues = source.createFloatArray();
        mCharacterBounds = source.createFloatArray();
        mCharacterFlags = source.createIntArray();
        mCharacterBidiLevels = source.createIntArray();
        mGraphemeSegmentFinder = null;
        mWordSegmentFinder = null;
        mLineSegmentFinder = null;
    }

    public void getMatrix(Matrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        matrix.setValues(mMatrixValues);
    }

    public int getStartIndex() {
        return mStart;
    }

    public int getEndIndex() {
        return mEnd;
    }

    public void getCharacterBounds(int index, RectF bounds) {
        if (index < mStart || index >= mEnd) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        Objects.requireNonNull(bounds, "bounds");
        int offset = (index - mStart) * 4;
        bounds.set(
                mCharacterBounds[offset],
                mCharacterBounds[offset + 1],
                mCharacterBounds[offset + 2],
                mCharacterBounds[offset + 3]);
    }

    public int getCharacterFlags(int index) {
        if (index < mStart || index >= mEnd) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        return mCharacterFlags[index - mStart];
    }

    public int getCharacterBidiLevel(int index) {
        if (index < mStart || index >= mEnd) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        return mCharacterBidiLevels[index - mStart];
    }

    public SegmentFinder getWordSegmentFinder() {
        return mWordSegmentFinder;
    }

    public SegmentFinder getGraphemeSegmentFinder() {
        return mGraphemeSegmentFinder;
    }

    public SegmentFinder getLineSegmentFinder() {
        return mLineSegmentFinder;
    }

    public int getOffsetForPosition(float x, float y) {
        int bestIndex = -1;
        float bestDist = Float.MAX_VALUE;
        RectF r = new RectF();
        for (int i = mStart; i < mEnd; i++) {
            getCharacterBounds(i, r);
            if (r.contains(x, y)) {
                return i;
            }
            float dx = x - r.centerX();
            float dy = y - r.centerY();
            float dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    public int[] getRangeForRect(
            RectF area,
            SegmentFinder segmentFinder,
            Layout.TextInclusionStrategy inclusionStrategy) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(segmentFinder, "segmentFinder");
        Objects.requireNonNull(inclusionStrategy, "inclusionStrategy");
        int min = -1;
        int max = -1;
        RectF charRect = new RectF();
        for (int i = mStart; i < mEnd; i++) {
            getCharacterBounds(i, charRect);
            if (inclusionStrategy.isSegmentInside(charRect, area)) {
                if (min < 0) {
                    min = i;
                }
                max = i + 1;
            }
        }
        if (min < 0) {
            return null;
        }
        return new int[] { min, max };
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mStart);
        dest.writeInt(mEnd);
        dest.writeFloatArray(mMatrixValues);
        dest.writeFloatArray(mCharacterBounds);
        dest.writeIntArray(mCharacterFlags);
        dest.writeIntArray(mCharacterBidiLevels);
    }

    public static final Parcelable.Creator<TextBoundsInfo> CREATOR =
            new Parcelable.Creator<TextBoundsInfo>() {
                @Override
                public TextBoundsInfo createFromParcel(Parcel source) {
                    return new TextBoundsInfo(source);
                }

                @Override
                public TextBoundsInfo[] newArray(int size) {
                    return new TextBoundsInfo[size];
                }
            };

    public static final class Builder {
        private final float[] mMatrixValues = new float[9];
        private boolean mMatrixInitialized;
        private int mStart = -1;
        private int mEnd = -1;
        private float[] mCharacterBounds;
        private int[] mCharacterFlags;
        private int[] mCharacterBidiLevels;
        private SegmentFinder mGraphemeSegmentFinder;
        private SegmentFinder mWordSegmentFinder;
        private SegmentFinder mLineSegmentFinder;

        public Builder(int start, int end) {
            new Matrix().getValues(mMatrixValues);
            setStartAndEnd(start, end);
        }

        public Builder clear() {
            mMatrixInitialized = false;
            new Matrix().getValues(mMatrixValues);
            mStart = -1;
            mEnd = -1;
            mCharacterBounds = null;
            mCharacterFlags = null;
            mCharacterBidiLevels = null;
            mGraphemeSegmentFinder = null;
            mWordSegmentFinder = null;
            mLineSegmentFinder = null;
            return this;
        }

        public Builder setMatrix(Matrix matrix) {
            Objects.requireNonNull(matrix, "matrix").getValues(mMatrixValues);
            mMatrixInitialized = true;
            return this;
        }

        public Builder setStartAndEnd(int start, int end) {
            if (start < 0 || start > end) {
                throw new IllegalArgumentException("Invalid start/end: " + start + ", " + end);
            }
            mStart = start;
            mEnd = end;
            return this;
        }

        public Builder setCharacterBounds(float[] characterBounds) {
            mCharacterBounds = Objects.requireNonNull(characterBounds, "characterBounds");
            return this;
        }

        public Builder setCharacterFlags(int[] characterFlags) {
            Objects.requireNonNull(characterFlags, "characterFlags");
            mCharacterFlags = characterFlags;
            return this;
        }

        public Builder setCharacterBidiLevel(int[] characterBidiLevels) {
            Objects.requireNonNull(characterBidiLevels, "characterBidiLevels");
            mCharacterBidiLevels = characterBidiLevels;
            return this;
        }

        public Builder setGraphemeSegmentFinder(SegmentFinder graphemeSegmentFinder) {
            mGraphemeSegmentFinder = Objects.requireNonNull(graphemeSegmentFinder);
            return this;
        }

        public Builder setWordSegmentFinder(SegmentFinder wordSegmentFinder) {
            mWordSegmentFinder = Objects.requireNonNull(wordSegmentFinder);
            return this;
        }

        public Builder setLineSegmentFinder(SegmentFinder lineSegmentFinder) {
            mLineSegmentFinder = Objects.requireNonNull(lineSegmentFinder);
            return this;
        }

        public TextBoundsInfo build() {
            if (mStart < 0 || mEnd < 0) {
                throw new IllegalStateException("Start and end must be set.");
            }
            if (!mMatrixInitialized) {
                throw new IllegalStateException("Matrix must be set.");
            }
            return new TextBoundsInfo(this);
        }
    }
}
