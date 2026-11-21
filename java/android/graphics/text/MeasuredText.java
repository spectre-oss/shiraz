// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.text;

import android.graphics.Paint;
import android.graphics.Rect;
import java.util.ArrayList;

public class MeasuredText {
    static final class Run {
        final int start;
        final int end;
        final Paint paint;
        final boolean isRtl;
        final boolean isReplacement;
        final float replacementWidth;
        final LineBreakConfig lineBreakConfig;
        final Paint.FontMetricsInt metrics;

        Run(
                int start,
                int end,
                Paint paint,
                boolean isRtl,
                boolean isReplacement,
                float replacementWidth,
                LineBreakConfig lineBreakConfig) {
            this.start = start;
            this.end = end;
            this.paint = new Paint(paint);
            this.isRtl = isRtl;
            this.isReplacement = isReplacement;
            this.replacementWidth = replacementWidth;
            this.lineBreakConfig = lineBreakConfig;
            this.metrics = new Paint.FontMetricsInt();
            this.paint.getFontMetricsInt(this.metrics);
        }
    }

    private final char[] chars;
    private final float[] charWidths;
    private final Run[] runs;
    private final int hyphenationMode;
    private final boolean computeLayout;

    MeasuredText(
            char[] chars,
            float[] charWidths,
            Run[] runs,
            int hyphenationMode,
            boolean computeLayout) {
        this.chars = chars;
        this.charWidths = charWidths;
        this.runs = runs;
        this.hyphenationMode = hyphenationMode;
        this.computeLayout = computeLayout;
    }

    public char[] shirazChars() {
        return chars;
    }

    char[] chars() {
        return chars;
    }

    float[] charWidths() {
        return charWidths;
    }

    Run[] runs() {
        return runs;
    }

    int hyphenationMode() {
        return hyphenationMode;
    }

    public float getWidth(int start, int end) {
        if (start < 0 || start > chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + start + ") must be 0 <= offset <= " + chars.length);
        }
        if (end < 0 || end > chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + end + ") must be 0 <= offset <= " + chars.length);
        }
        if (start > end) {
            throw new IllegalArgumentException(
                    "start(" + start + ") is larger than end(" + end + ")");
        }
        float sum = 0f;
        for (int i = start; i < end; i++) {
            sum += charWidths[i];
        }
        return sum;
    }

    public void getBounds(int start, int end, Rect rect) {
        if (start < 0 || start > chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + start + ") must be 0 <= offset <= " + chars.length);
        }
        if (end < 0 || end > chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + end + ") must be 0 <= offset <= " + chars.length);
        }
        if (start > end) {
            throw new IllegalArgumentException(
                    "start(" + start + ") is larger than end(" + end + ")");
        }
        if (rect == null) {
            throw new NullPointerException("rect");
        }
        if (start == end) {
            rect.set(0, 0, 0, 0);
            return;
        }
        int left = Integer.MAX_VALUE;
        int top = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int bottom = Integer.MIN_VALUE;
        float xCursor = 0f;
        Rect tmp = new Rect();
        for (int r = 0; r < runs.length; r++) {
            Run run = runs[r];
            int s = Math.max(start, run.start);
            int e = Math.min(end, run.end);
            if (s >= e) {
                continue;
            }
            float segWidth = getWidth(s, e);
            if (run.isReplacement) {
                int segLeft = Math.round(xCursor);
                int segRight = Math.round(xCursor + segWidth);
                left = Math.min(left, segLeft);
                right = Math.max(right, segRight);
                top = Math.min(top, run.metrics.ascent);
                bottom = Math.max(bottom, run.metrics.descent);
            } else {
                run.paint.getTextBounds(chars, s, e - s, tmp);
                int xOff = Math.round(xCursor);
                left = Math.min(left, xOff + tmp.left);
                right = Math.max(right, xOff + Math.max(tmp.right, Math.round(segWidth)));
                top = Math.min(top, tmp.top);
                bottom = Math.max(bottom, tmp.bottom);
            }
            xCursor += segWidth;
        }
        if (left == Integer.MAX_VALUE) {
            rect.set(0, 0, 0, 0);
        } else {
            rect.set(left, top, right, bottom);
        }
    }

    public void getFontMetricsInt(int start, int end, Paint.FontMetricsInt outMetrics) {
        if (start < 0 || start > chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + start + ") must be 0 <= offset <= " + chars.length);
        }
        if (end < 0 || end > chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + end + ") must be 0 <= offset <= " + chars.length);
        }
        if (start > end) {
            throw new IllegalArgumentException(
                    "start(" + start + ") is larger than end(" + end + ")");
        }
        if (outMetrics == null) {
            throw new NullPointerException("outMetrics");
        }
        outMetrics.top = 0;
        outMetrics.ascent = 0;
        outMetrics.descent = 0;
        outMetrics.bottom = 0;
        outMetrics.leading = 0;
        boolean first = true;
        for (int r = 0; r < runs.length; r++) {
            Run run = runs[r];
            int s = Math.max(start, run.start);
            int e = Math.min(end, run.end);
            if (s >= e && !(start == end && start >= run.start && start <= run.end)) {
                continue;
            }
            Paint.FontMetricsInt fm = run.metrics;
            if (first) {
                outMetrics.top = fm.top;
                outMetrics.ascent = fm.ascent;
                outMetrics.descent = fm.descent;
                outMetrics.bottom = fm.bottom;
                outMetrics.leading = fm.leading;
                first = false;
            } else {
                outMetrics.top = Math.min(outMetrics.top, fm.top);
                outMetrics.ascent = Math.min(outMetrics.ascent, fm.ascent);
                outMetrics.descent = Math.max(outMetrics.descent, fm.descent);
                outMetrics.bottom = Math.max(outMetrics.bottom, fm.bottom);
                outMetrics.leading = Math.max(outMetrics.leading, fm.leading);
            }
        }
    }

    public float getCharWidthAt(int offset) {
        if (offset < 0 || offset >= chars.length) {
            throw new IllegalArgumentException(
                    "offset(" + offset + ") must be 0 <= offset < " + chars.length);
        }
        return charWidths[offset];
    }

    public static final class Builder {
        public static final int HYPHENATION_MODE_NONE = 0;
        public static final int HYPHENATION_MODE_NORMAL = 1;
        public static final int HYPHENATION_MODE_FAST = 2;

        private final char[] text;
        private final ArrayList<Run> runs = new ArrayList<Run>();
        private int currentOffset;
        private int hyphenationMode = HYPHENATION_MODE_NONE;
        private boolean computeLayout = true;
        private boolean built;

        public Builder(char[] text) {
            if (text == null) {
                throw new NullPointerException("text");
            }
            this.text = text;
        }

        public Builder(MeasuredText text) {
            if (text == null) {
                throw new NullPointerException("text");
            }
            if (!text.computeLayout) {
                throw new IllegalArgumentException(
                        "Cannot construct MeasuredText.Builder from MeasuredText with computeLayout=false");
            }
            this.text = text.chars;
            this.hyphenationMode = text.hyphenationMode;
            this.computeLayout = text.computeLayout;
            for (int i = 0; i < text.runs.length; i++) {
                this.runs.add(text.runs[i]);
            }
            this.currentOffset = text.chars.length;
        }

        public Builder appendStyleRun(Paint paint, int length, boolean isRtl) {
            return appendStyleRun(paint, null, length, isRtl);
        }

        public Builder appendStyleRun(
                Paint paint, LineBreakConfig lineBreakConfig, int length, boolean isRtl) {
            ensureNotBuilt();
            if (paint == null) {
                throw new NullPointerException("paint");
            }
            if (length <= 0) {
                throw new IllegalArgumentException("length can not be negative or zero");
            }
            int end = currentOffset + length;
            if (end > text.length) {
                throw new IllegalArgumentException(
                        "Style exceeds the text length: end=" + end + ", len=" + text.length);
            }
            runs.add(new Run(currentOffset, end, paint, isRtl, false, 0f, lineBreakConfig));
            currentOffset = end;
            return this;
        }

        public Builder appendReplacementRun(Paint paint, int length, float width) {
            ensureNotBuilt();
            if (paint == null) {
                throw new NullPointerException("paint");
            }
            if (length <= 0) {
                throw new IllegalArgumentException("length can not be negative or zero");
            }
            if (width < 0f || !Float.isFinite(width)) {
                throw new IllegalArgumentException("Replacement width must be >= 0 and finite");
            }
            int end = currentOffset + length;
            if (end > text.length) {
                throw new IllegalArgumentException(
                        "Replacement exceeds the text length: end=" + end + ", len=" + text.length);
            }
            runs.add(new Run(currentOffset, end, paint, false, true, width, null));
            currentOffset = end;
            return this;
        }

        @Deprecated
        public Builder setComputeHyphenation(boolean computeHyphenation) {
            setComputeHyphenation(
                    computeHyphenation ? HYPHENATION_MODE_NORMAL : HYPHENATION_MODE_NONE);
            return this;
        }

        public Builder setComputeHyphenation(int mode) {
            ensureNotBuilt();
            if (mode != HYPHENATION_MODE_NONE
                    && mode != HYPHENATION_MODE_NORMAL
                    && mode != HYPHENATION_MODE_FAST) {
                throw new IllegalArgumentException("Invalid hyphenation mode: " + mode);
            }
            this.hyphenationMode = mode;
            return this;
        }

        public Builder setComputeLayout(boolean computeLayout) {
            ensureNotBuilt();
            this.computeLayout = computeLayout;
            return this;
        }

        public MeasuredText build() {
            ensureNotBuilt();
            if (currentOffset != text.length) {
                throw new IllegalStateException(
                        "Style info has not been provided for all text: "
                                + currentOffset
                                + " != "
                                + text.length);
            }
            built = true;
            float[] widths = new float[text.length];
            for (int r = 0; r < runs.size(); r++) {
                Run run = runs.get(r);
                int len = run.end - run.start;
                if (run.isReplacement) {
                    widths[run.start] = run.replacementWidth;
                    for (int i = run.start + 1; i < run.end; i++) {
                        widths[i] = 0f;
                    }
                } else {
                    run.paint.getTextRunAdvances(
                            text,
                            run.start,
                            len,
                            run.start,
                            len,
                            run.isRtl,
                            widths,
                            run.start);
                }
            }
            Run[] runArray = runs.toArray(new Run[runs.size()]);
            return new MeasuredText(
                    text.clone(), widths, runArray, hyphenationMode, computeLayout);
        }

        private void ensureNotBuilt() {
            if (built) {
                throw new IllegalStateException("MeasuredText.Builder can not be reused");
            }
        }
    }
}
