// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import java.util.Arrays;

/** Finds text segment start and end boundaries (e.g. grapheme clusters or words). */
public abstract class SegmentFinder {
    public static final int DONE = -1;

    public SegmentFinder() {
    }

    public abstract int previousStartBoundary(int offset);

    public abstract int previousEndBoundary(int offset);

    public abstract int nextStartBoundary(int offset);

    public abstract int nextEndBoundary(int offset);

    public static class PrescribedSegmentFinder extends SegmentFinder {
        private final int[] mSegments;

        public PrescribedSegmentFinder(int[] segments) {
            if (segments == null) {
                throw new NullPointerException("segments");
            }
            if (segments.length % 2 != 0) {
                throw new IllegalArgumentException("segments length must be even");
            }
            int prev = 0;
            for (int i = 0; i < segments.length; i += 2) {
                int start = segments[i];
                int end = segments[i + 1];
                if (start < prev || end < start) {
                    throw new IllegalArgumentException("Invalid segment range at index " + i);
                }
                if (start == end) {
                    throw new IllegalArgumentException("Empty segment is not allowed");
                }
                prev = end;
            }
            mSegments = Arrays.copyOf(segments, segments.length);
        }

        @Override
        public int previousStartBoundary(int offset) {
            for (int i = mSegments.length - 2; i >= 0; i -= 2) {
                if (mSegments[i] < offset) {
                    return mSegments[i];
                }
            }
            return DONE;
        }

        @Override
        public int previousEndBoundary(int offset) {
            for (int i = mSegments.length - 1; i >= 1; i -= 2) {
                if (mSegments[i] < offset) {
                    return mSegments[i];
                }
            }
            return DONE;
        }

        @Override
        public int nextStartBoundary(int offset) {
            for (int i = 0; i < mSegments.length; i += 2) {
                if (mSegments[i] > offset) {
                    return mSegments[i];
                }
            }
            return DONE;
        }

        @Override
        public int nextEndBoundary(int offset) {
            for (int i = 1; i < mSegments.length; i += 2) {
                if (mSegments[i] > offset) {
                    return mSegments[i];
                }
            }
            return DONE;
        }
    }
}
