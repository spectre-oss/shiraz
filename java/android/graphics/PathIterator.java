// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Verb-by-verb iterator over a snapshot of a Path's geometry. */
public class PathIterator implements Iterator<PathIterator.Segment> {
    public static final int VERB_MOVE = 0;
    public static final int VERB_LINE = 1;
    public static final int VERB_QUAD = 2;
    public static final int VERB_CONIC = 3;
    public static final int VERB_CUBIC = 4;
    public static final int VERB_CLOSE = 5;
    public static final int VERB_DONE = 6;

    public static class Segment {
        private final int verb;
        private final float[] points;
        private final float conicWeight;

        Segment(int verb, float[] points, float conicWeight) {
            this.verb = verb;
            this.points = points;
            this.conicWeight = conicWeight;
        }

        public int getVerb() {
            return verb;
        }

        public float[] getPoints() {
            return points;
        }

        public float getConicWeight() {
            return conicWeight;
        }
    }

    private final Path owner;
    private final int initialGeneration;
    private final int[] verbs;
    private final float[][] segPoints;
    private final float[] weights;
    private int cursor;

    PathIterator(Path path, int[] verbs, float[][] segPoints, float[] weights, int generation) {
        this.owner = path;
        this.verbs = verbs;
        this.segPoints = segPoints;
        this.weights = weights;
        this.initialGeneration = generation;
    }

    private void checkUnmodified() {
        if (owner.getGenerationId() != initialGeneration) {
            throw new java.util.ConcurrentModificationException("Path modified during iteration");
        }
    }

    @Override
    public boolean hasNext() {
        checkUnmodified();
        return cursor < verbs.length;
    }

    public int peek() {
        checkUnmodified();
        return cursor < verbs.length ? verbs[cursor] : VERB_DONE;
    }

    public int next(float[] points, int offset) {
        checkUnmodified();
        if (points == null) {
            throw new NullPointerException("points");
        }
        if (offset < 0 || points.length - offset < 8) {
            throw new ArrayIndexOutOfBoundsException("points array must have at least 8 elements from offset");
        }
        if (cursor >= verbs.length) {
            return VERB_DONE;
        }
        int verb = verbs[cursor];
        float[] pts = segPoints[cursor];
        float w = weights[cursor];
        cursor++;
        if (pts.length > 0) {
            System.arraycopy(pts, 0, points, offset, pts.length);
        }
        if (verb == VERB_CONIC) {
            points[offset + 6] = w;
        }
        return verb;
    }

    @Override
    public Segment next() {
        checkUnmodified();
        if (cursor >= verbs.length) {
            throw new NoSuchElementException("No more path segments");
        }
        int verb = verbs[cursor];
        float[] pts = Arrays.copyOf(segPoints[cursor], segPoints[cursor].length);
        float w = weights[cursor];
        cursor++;
        return new Segment(verb, pts, w);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("remove");
    }
}
