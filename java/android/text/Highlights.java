// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Paint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/** Collection of text character highlight ranges paired with Paint instances. */
public class Highlights {
    public static final class Builder {
        private final ArrayList<Paint> mPaints = new ArrayList<Paint>();
        private final ArrayList<int[]> mRanges = new ArrayList<int[]>();

        public Builder() {
        }

        public Builder addRange(Paint paint, int start, int end) {
            Objects.requireNonNull(paint, "paint");
            if (start < 0 || start > end) {
                throw new IllegalArgumentException("Invalid range: " + start + ", " + end);
            }
            mPaints.add(paint);
            mRanges.add(new int[] { start, end });
            return this;
        }

        public Builder addRanges(Paint paint, int... ranges) {
            Objects.requireNonNull(paint, "paint");
            Objects.requireNonNull(ranges, "ranges");
            if (ranges.length % 2 != 0) {
                throw new IllegalArgumentException("Flatten ranges must have even numbered elements");
            }
            for (int i = 0; i < ranges.length; i += 2) {
                if (ranges[i] < 0 || ranges[i] > ranges[i + 1]) {
                    throw new IllegalArgumentException(
                            "Invalid range: " + ranges[i] + ", " + ranges[i + 1]);
                }
            }
            mPaints.add(paint);
            mRanges.add(Arrays.copyOf(ranges, ranges.length));
            return this;
        }

        public Highlights build() {
            return new Highlights(mPaints, mRanges);
        }
    }

    private final Paint[] mPaints;
    private final int[][] mRanges;

    private Highlights(ArrayList<Paint> paints, ArrayList<int[]> ranges) {
        mPaints = paints.toArray(new Paint[paints.size()]);
        mRanges = ranges.toArray(new int[ranges.size()][]);
    }

    public int getSize() {
        return mPaints.length;
    }

    public Paint getPaint(int index) {
        return mPaints[index];
    }

    public int[] getRanges(int index) {
        return mRanges[index];
    }
}
