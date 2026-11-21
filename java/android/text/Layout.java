// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.text.LineBreakConfig;
import java.util.List;

/** Base metrics contract for the supported software text layout. */
public abstract class Layout {
    public static final int BREAK_STRATEGY_SIMPLE = 0;
    public static final int BREAK_STRATEGY_HIGH_QUALITY = 1;
    public static final int BREAK_STRATEGY_BALANCED = 2;

    public static final int HYPHENATION_FREQUENCY_NONE = 0;
    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;
    public static final int HYPHENATION_FREQUENCY_FULL = 2;
    public static final int HYPHENATION_FREQUENCY_NORMAL_FAST = 3;
    public static final int HYPHENATION_FREQUENCY_FULL_FAST = 4;

    public static final int JUSTIFICATION_MODE_NONE = 0;
    public static final int JUSTIFICATION_MODE_INTER_WORD = 1;
    public static final int JUSTIFICATION_MODE_INTER_CHARACTER = 2;

    public static final float DEFAULT_LINESPACING_MULTIPLIER = 1.0f;
    public static final float DEFAULT_LINESPACING_ADDITION = 0.0f;

    public static final int DIR_LEFT_TO_RIGHT = 1;
    public static final int DIR_RIGHT_TO_LEFT = -1;

    public interface TextInclusionStrategy {
        boolean isSegmentInside(RectF segmentBounds, RectF area);
    }

    public static final TextInclusionStrategy INCLUSION_STRATEGY_ANY_OVERLAP =
            new TextInclusionStrategy() {
                @Override
                public boolean isSegmentInside(RectF segmentBounds, RectF area) {
                    return RectF.intersects(segmentBounds, area);
                }
            };

    public static final TextInclusionStrategy INCLUSION_STRATEGY_CONTAINS_CENTER =
            new TextInclusionStrategy() {
                @Override
                public boolean isSegmentInside(RectF segmentBounds, RectF area) {
                    return area.contains(segmentBounds.centerX(), segmentBounds.centerY());
                }
            };

    public static final TextInclusionStrategy INCLUSION_STRATEGY_CONTAINS_ALL =
            new TextInclusionStrategy() {
                @Override
                public boolean isSegmentInside(RectF segmentBounds, RectF area) {
                    return area.contains(segmentBounds);
                }
            };

    public static final class Builder {
        private final CharSequence mText;
        private final int mStart;
        private final int mEnd;
        private final TextPaint mPaint;
        private final int mWidth;
        private Alignment mAlignment = Alignment.ALIGN_NORMAL;
        private float mSpacingMult = DEFAULT_LINESPACING_MULTIPLIER;
        private float mSpacingAdd = DEFAULT_LINESPACING_ADDITION;
        private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private boolean mIncludePad = true;
        private boolean mFallbackLineSpacing = false;
        private int mEllipsizedWidth;
        private TextUtils.TruncateAt mEllipsize = null;
        private int mMaxLines = Integer.MAX_VALUE;
        private int mBreakStrategy = BREAK_STRATEGY_SIMPLE;
        private int mHyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private int[] mLeftIndents = null;
        private int[] mRightIndents = null;
        private int mJustificationMode = JUSTIFICATION_MODE_NONE;
        private LineBreakConfig mLineBreakConfig = new LineBreakConfig.Builder().build();
        private boolean mUseBoundsForWidth = false;
        private boolean mShiftDrawingOffsetForStartOverhang = false;
        private Paint.FontMetrics mMinimumFontMetrics = null;

        public Builder(CharSequence text, int start, int end, TextPaint paint, int width) {
            if (text == null) throw new IllegalArgumentException("text cannot be null");
            if (paint == null) throw new IllegalArgumentException("paint cannot be null");
            if (width < 0) throw new IllegalArgumentException("width: " + width + " cannot be negative");
            if (start < 0 || end < start || end > text.length()) {
                throw new IllegalArgumentException("Invalid range: " + start + ".." + end);
            }
            mText = text;
            mStart = start;
            mEnd = end;
            mPaint = paint;
            mWidth = width;
            mEllipsizedWidth = width;
        }

        public Builder setAlignment(Alignment alignment) {
            mAlignment = alignment != null ? alignment : Alignment.ALIGN_NORMAL;
            return this;
        }

        public Builder setTextDirectionHeuristic(TextDirectionHeuristic textDirection) {
            mTextDir = textDirection != null ? textDirection : TextDirectionHeuristics.FIRSTSTRONG_LTR;
            return this;
        }

        public Builder setLineSpacingAmount(float amount) {
            mSpacingAdd = amount;
            return this;
        }

        public Builder setLineSpacingMultiplier(float multiplier) {
            mSpacingMult = multiplier;
            return this;
        }

        public Builder setFontPaddingIncluded(boolean includeFontPadding) {
            mIncludePad = includeFontPadding;
            return this;
        }

        public Builder setFallbackLineSpacingEnabled(boolean fallbackLineSpacing) {
            mFallbackLineSpacing = fallbackLineSpacing;
            return this;
        }

        public Builder setEllipsizedWidth(int ellipsizeWidth) {
            mEllipsizedWidth = ellipsizeWidth;
            return this;
        }

        public Builder setEllipsize(TextUtils.TruncateAt ellipsize) {
            mEllipsize = ellipsize;
            return this;
        }

        public Builder setMaxLines(int maxLines) {
            mMaxLines = maxLines;
            return this;
        }

        public Builder setBreakStrategy(int breakStrategy) {
            mBreakStrategy = breakStrategy;
            return this;
        }

        public Builder setHyphenationFrequency(int hyphenationFrequency) {
            mHyphenationFrequency = hyphenationFrequency;
            return this;
        }

        public Builder setLeftIndents(int[] leftIndents) {
            mLeftIndents = leftIndents;
            return this;
        }

        public Builder setRightIndents(int[] rightIndents) {
            mRightIndents = rightIndents;
            return this;
        }

        public Builder setJustificationMode(int justificationMode) {
            mJustificationMode = justificationMode;
            return this;
        }

        public Builder setLineBreakConfig(LineBreakConfig lineBreakConfig) {
            mLineBreakConfig = lineBreakConfig != null
                    ? lineBreakConfig
                    : new LineBreakConfig.Builder().build();
            return this;
        }

        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) {
            mUseBoundsForWidth = useBoundsForWidth;
            return this;
        }

        public Builder setShiftDrawingOffsetForStartOverhang(
                boolean shiftDrawingOffsetForStartOverhang) {
            mShiftDrawingOffsetForStartOverhang = shiftDrawingOffsetForStartOverhang;
            return this;
        }

        public Builder setMinimumFontMetrics(Paint.FontMetrics minimumFontMetrics) {
            mMinimumFontMetrics = minimumFontMetrics;
            return this;
        }

        public Layout build() {
            StaticLayout.Builder sb = StaticLayout.Builder.obtain(mText, mStart, mEnd, mPaint, mWidth)
                    .setAlignment(mAlignment)
                    .setTextDirection(mTextDir)
                    .setLineSpacing(mSpacingAdd, mSpacingMult)
                    .setIncludePad(mIncludePad)
                    .setEllipsizedWidth(mEllipsizedWidth)
                    .setEllipsize(mEllipsize);
            if (mMaxLines >= 1) {
                sb.setMaxLines(mMaxLines);
            }
            if (mBreakStrategy >= 0 && mBreakStrategy <= 2) {
                sb.setBreakStrategy(mBreakStrategy);
            }
            if (mHyphenationFrequency >= 0 && mHyphenationFrequency <= 4) {
                sb.setHyphenationFrequency(mHyphenationFrequency);
            }
            StaticLayout layout = sb.build();
            layout.textDir = mTextDir;
            layout.fontPaddingIncluded = mIncludePad;
            layout.fallbackLineSpacing = mFallbackLineSpacing;
            layout.ellipsizeMode = mEllipsize;
            layout.maxLinesVal = mMaxLines;
            layout.breakStrategyVal = mBreakStrategy;
            layout.hyphenationFrequencyVal = mHyphenationFrequency;
            layout.leftIndentsVal = mLeftIndents;
            layout.rightIndentsVal = mRightIndents;
            layout.justificationModeVal = mJustificationMode;
            layout.lineBreakConfigVal = mLineBreakConfig;
            layout.useBoundsForWidthVal = mUseBoundsForWidth;
            layout.shiftDrawingOffsetVal = mShiftDrawingOffsetForStartOverhang;
            layout.minimumFontMetricsVal = mMinimumFontMetrics;
            return layout;
        }
    }

    public enum Alignment { ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER }

    private CharSequence text;
    private TextPaint paint;
    private int width;
    private Alignment alignment;
    private float spacingMult;
    private float spacingAdd;
    TextDirectionHeuristic textDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
    boolean fontPaddingIncluded = true;
    boolean fallbackLineSpacing = false;
    TextUtils.TruncateAt ellipsizeMode = null;
    int maxLinesVal = Integer.MAX_VALUE;
    int breakStrategyVal = BREAK_STRATEGY_SIMPLE;
    int hyphenationFrequencyVal = HYPHENATION_FREQUENCY_NONE;
    int[] leftIndentsVal = null;
    int[] rightIndentsVal = null;
    int justificationModeVal = JUSTIFICATION_MODE_NONE;
    LineBreakConfig lineBreakConfigVal = new LineBreakConfig.Builder().build();
    boolean useBoundsForWidthVal = false;
    boolean shiftDrawingOffsetVal = false;
    Paint.FontMetrics minimumFontMetricsVal = null;

    protected Layout(CharSequence text, TextPaint paint, int width, Alignment alignment, float spacingMult, float spacingAdd) {
        replaceWith(text, paint, width, alignment, spacingMult, spacingAdd);
    }

    protected void replaceWith(CharSequence text, TextPaint paint, int width, Alignment alignment, float spacingMult, float spacingAdd) {
        if (text == null || paint == null || alignment == null) throw new NullPointerException();
        if (width < 0) throw new IllegalArgumentException("Layout width");
        this.text = text;
        this.paint = paint;
        this.width = width;
        this.alignment = alignment;
        this.spacingMult = spacingMult;
        this.spacingAdd = spacingAdd;
    }

    public final void increaseWidthTo(int wid) {
        if (wid < width) {
            throw new RuntimeException("attempted to reduce Layout width");
        }
        width = wid;
    }

    public final CharSequence getText() { return text; }
    public final TextPaint getPaint() { return paint; }
    public final int getWidth() { return width; }
    public final Alignment getAlignment() { return alignment; }
    public final TextDirectionHeuristic getTextDirectionHeuristic() { return textDir; }
    public final float getSpacingMultiplier() { return spacingMult; }
    public final float getLineSpacingMultiplier() { return spacingMult; }
    public final float getSpacingAdd() { return spacingAdd; }
    public final float getLineSpacingAmount() { return spacingAdd; }
    public final boolean isFontPaddingIncluded() { return fontPaddingIncluded; }
    public boolean isFallbackLineSpacingEnabled() { return fallbackLineSpacing; }
    public final TextUtils.TruncateAt getEllipsize() { return ellipsizeMode; }
    public final int getMaxLines() { return maxLinesVal; }
    public final int getBreakStrategy() { return breakStrategyVal; }
    public final int getHyphenationFrequency() { return hyphenationFrequencyVal; }
    public final int[] getLeftIndents() { return leftIndentsVal; }
    public final int[] getRightIndents() { return rightIndentsVal; }
    public final int getJustificationMode() { return justificationModeVal; }
    public LineBreakConfig getLineBreakConfig() { return lineBreakConfigVal; }
    public boolean getUseBoundsForWidth() { return useBoundsForWidthVal; }
    public boolean getShiftDrawingOffsetForStartOverhang() { return shiftDrawingOffsetVal; }
    public Paint.FontMetrics getMinimumFontMetrics() { return minimumFontMetricsVal; }
    protected final boolean isSpanned() { return text instanceof Spanned; }

    public abstract int getLineCount();
    public abstract int getLineTop(int line);
    public abstract int getLineDescent(int line);
    public abstract int getLineStart(int line);
    public float getLineWidth(int line) { return getLineMax(line); }

    public final int getLineAscent(int line) { return getLineTop(line) - getLineBaseline(line); }
    public int getHeight() { return getLineTop(getLineCount()); }
    public final int getLineBottom(int line) { return getLineTop(line + 1); }
    public int getLineBottom(int line, boolean includeLineSpacing) { return getLineBottom(line); }
    public final int getLineBaseline(int line) { return getLineBottom(line) - getLineDescent(line); }
    public final int getLineEnd(int line) { return getLineStart(line + 1); }

    public int getLineBounds(int line, Rect bounds) {
        int baseline = getLineBaseline(line);
        if (bounds != null) bounds.set(0, getLineTop(line), width, getLineBottom(line));
        return baseline;
    }

    public RectF computeDrawingBoundingBox() {
        int count = getLineCount();
        if (count <= 0) return new RectF(0, 0, 0, 0);
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            left = Math.min(left, getLineLeft(i));
            right = Math.max(right, getLineRight(i));
        }
        return new RectF(left, getLineTop(0), right, getLineBottom(count - 1));
    }

    public int getLineForVertical(int vertical) {
        int count = getLineCount();
        if (count <= 0) return 0;
        int low = 0, high = count - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (getLineTop(mid + 1) <= vertical) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    public int getLineForOffset(int offset) {
        int count = getLineCount();
        if (count <= 0) return 0;
        if (offset <= 0) return 0;
        int low = 0, high = count - 1;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (getLineEnd(mid) <= offset) low = mid + 1;
            else high = mid;
        }
        return low;
    }

    public int getOffsetForHorizontal(int line, float horiz) {
        int count = getLineCount();
        if (count <= 0 || line < 0 || line >= count) return 0;
        int start = getLineStart(line), end = getLineVisibleEnd(line);
        if (start >= end) return start;
        String value = text.subSequence(start, end).toString();
        float local = horiz - getLineLeft(line);
        int baseLevel = 0;
        try { baseLevel = getParagraphDirection(line) == DIR_RIGHT_TO_LEFT ? 1 : 0; } catch (RuntimeException ignored) {}
        return Math.min(text.length(), start + new shiraz.runtime.TextGeometry(value, paint, baseLevel).hit(local));
    }

    public float getPrimaryHorizontal(int offset) {
        int count = getLineCount();
        if (count <= 0) return 0;
        int line = getLineForOffset(offset);
        int start = getLineStart(line), end = getLineVisibleEnd(line);
        int clamped = Math.max(start, Math.min(end, offset)) - start;
        String value = text.subSequence(start, end).toString();
        int baseLevel = 0;
        try { baseLevel = getParagraphDirection(line) == DIR_RIGHT_TO_LEFT ? 1 : 0; } catch (RuntimeException ignored) {}
        return getLineLeft(line) + new shiraz.runtime.TextGeometry(value, paint, baseLevel).caret(clamped);
    }

    public int getOffsetToLeftOf(int offset) {
        if (offset <= 0) return 0;
        return TextUtils.getOffsetBefore(text, offset);
    }

    public int getOffsetToRightOf(int offset) {
        if (offset >= text.length()) return text.length();
        return TextUtils.getOffsetAfter(text, offset);
    }

    public void getCursorPath(int point, Path dest, CharSequence editingBuffer) {
        dest.reset();
        int line = getLineForOffset(point);
        int top = getLineTop(line);
        int bottom = getLineBottom(line);
        float h1 = getPrimaryHorizontal(point) - 0.5f;
        dest.moveTo(h1, top);
        dest.lineTo(h1, bottom);
    }

    public void fillCharacterBounds(int start, int end, float[] bounds, int boundsStart) {
        if (start < 0 || end < start || end > text.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (bounds == null) {
            throw new IllegalArgumentException("bounds cannot be null");
        }
        int needed = (end - start) * 4;
        if (boundsStart < 0 || boundsStart + needed > bounds.length) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = start; i < end; i++) {
            int line = getLineForOffset(i);
            float x0 = getPrimaryHorizontal(i);
            float x1 = i + 1 <= getLineEnd(line) ? getPrimaryHorizontal(i + 1) : x0;
            int idx = boundsStart + (i - start) * 4;
            bounds[idx] = Math.min(x0, x1);
            bounds[idx + 1] = getLineTop(line);
            bounds[idx + 2] = Math.max(x0, x1);
            bounds[idx + 3] = getLineBottom(line);
        }
    }

    public int getLineLetterSpacingUnitCount(int line, boolean includeTrailingWhitespace) {
        int start = getLineStart(line);
        int end = includeTrailingWhitespace ? getLineEnd(line) : getLineVisibleEnd(line);
        if (end > start && text.charAt(end - 1) == '\n') end--;
        return Math.max(0, end - start);
    }

    public int[] getRangeForRect(
            RectF area, SegmentFinder segmentFinder, TextInclusionStrategy inclusionStrategy) {
        if (area == null || segmentFinder == null || inclusionStrategy == null) {
            throw new NullPointerException();
        }
        int len = text.length();
        if (len == 0 || getLineCount() == 0) {
            return null;
        }
        int startLine = getLineForVertical((int) Math.floor(area.top));
        int endLine = getLineForVertical((int) Math.ceil(area.bottom));
        if (area.bottom < getLineTop(0) || area.top > getLineBottom(getLineCount() - 1)) {
            return null;
        }
        int minOffset = getLineStart(startLine);
        int maxOffset = getLineEnd(endLine);
        int segStart = segmentFinder.previousStartBoundary(minOffset + 1);
        if (segStart == SegmentFinder.DONE) {
            segStart = segmentFinder.nextStartBoundary(minOffset);
        }
        int firstIncluded = -1;
        int lastIncluded = -1;
        RectF segRect = new RectF();
        while (segStart != SegmentFinder.DONE && segStart < maxOffset) {
            int segEnd = segmentFinder.nextEndBoundary(segStart);
            if (segEnd == SegmentFinder.DONE) {
                break;
            }
            int line = getLineForOffset(segStart);
            float x0 = getPrimaryHorizontal(segStart);
            float x1 = getPrimaryHorizontal(Math.min(segEnd, getLineVisibleEnd(line)));
            segRect.set(Math.min(x0, x1), getLineTop(line), Math.max(x0, x1), getLineBottom(line));
            if (inclusionStrategy.isSegmentInside(segRect, area)) {
                if (firstIncluded < 0) firstIncluded = segStart;
                lastIncluded = segEnd;
            }
            segStart = segmentFinder.nextStartBoundary(segStart + 1);
        }
        if (firstIncluded < 0) {
            return null;
        }
        return new int[] { firstIncluded, lastIncluded };
    }

    public boolean isRtlCharAt(int offset) {
        if (offset < 0 || offset > text.length()) throw new IndexOutOfBoundsException();
        int line = getLineForOffset(offset), relative = offset - getLineStart(line);
        int[] runs = getLineDirections(line).runs;
        for (int i = 0; i + 1 < runs.length; i += 2) {
            if (relative >= runs[i] && relative - runs[i] < (runs[i + 1] & 0x3ffffff)) {
                return (runs[i + 1] & (1 << 26)) != 0;
            }
        }
        return getParagraphDirection(line) == DIR_RIGHT_TO_LEFT;
    }

    /** Selection/link hit region, split into visual ranges for bidirectional text. */
    public void getSelectionPath(int start, int end, Path destination) {
        if (destination == null) throw new NullPointerException("destination");
        destination.reset();
        if (start > end) { int swap = start; start = end; end = swap; }
        if (start < 0 || end > text.length()) throw new IndexOutOfBoundsException();
        if (start == end) return;
        Spanned spans = text instanceof Spanned ? (Spanned) text : null;
        for (int line = getLineForOffset(start); line < getLineCount() && getLineStart(line) < end; line++) {
            int first = getLineStart(line), last = getLineVisibleEnd(line);
            float advance = 0;
            for (int at = first; at < last;) {
                int next = spans == null ? last : spans.nextSpanTransition(at, last, android.text.style.MetricAffectingSpan.class);
                TextPaint run = new TextPaint(paint);
                if (spans != null) {
                    for (android.text.style.MetricAffectingSpan span : spans.getSpans(at, next, android.text.style.MetricAffectingSpan.class)) {
                        if (spans.getSpanStart(span) < next && spans.getSpanEnd(span) > at) span.updateMeasureState(run);
                    }
                }
                int a = Math.max(start, at), b = Math.min(end, next);
                String value = text.subSequence(at, next).toString();
                if (a < b) {
                    float[] ranges = new shiraz.runtime.TextGeometry(value, run, getParagraphDirection(line) == DIR_RIGHT_TO_LEFT ? 1 : 0).ranges(a - at, b - at);
                    for (int i = 0; i < ranges.length; i += 2) {
                        destination.addRect(getLineLeft(line) + advance + ranges[i], getLineTop(line), getLineLeft(line) + advance + ranges[i + 1], getLineBottom(line), Path.Direction.CW);
                    }
                }
                advance += getDesiredWidth(text, at, next, paint);
                at = next;
            }
        }
    }

    public float getSecondaryHorizontal(int offset) { return getPrimaryHorizontal(offset); }

    /** Maximum paragraph advance, including metric span runs and default tab stops. */
    public static float getDesiredWidth(CharSequence text, TextPaint paint) {
        return getDesiredWidth(text, 0, text.length(), paint);
    }

    public static float getDesiredWidth(CharSequence text, int start, int end, TextPaint paint) {
        if (text == null || paint == null) throw new NullPointerException();
        if (start < 0 || end < start || end > text.length()) throw new IndexOutOfBoundsException();
        Spanned spans = text instanceof Spanned ? (Spanned) text : null;
        float maximum = 0, advance = 0;
        for (int at = start; at < end;) {
            char c = text.charAt(at);
            if (c == '\n') { maximum = Math.max(maximum, advance); advance = 0; at++; continue; }
            if (c == '\t') { advance = ((float) Math.floor(advance / 20) + 1) * 20; at++; continue; }
            int next = spans == null ? end : spans.nextSpanTransition(at, end, android.text.style.MetricAffectingSpan.class);
            for (int i = at; i < next; i++) if (text.charAt(i) == '\n' || text.charAt(i) == '\t') { next = i; break; }
            TextPaint run = paint;
            if (spans != null) {
                run = new TextPaint();
                run.set(paint);
                for (android.text.style.MetricAffectingSpan span : spans.getSpans(at, next, android.text.style.MetricAffectingSpan.class)) {
                    if (spans.getSpanStart(span) < next && spans.getSpanEnd(span) > at) span.updateMeasureState(run);
                }
            }
            android.text.style.ReplacementSpan replacement = replacement(spans, at, next);
            advance += replacement == null ? run.measureText(text.subSequence(at, next).toString()) : replacementSize(replacement, run, text, at, next, null);
            at = next;
        }
        return Math.max(maximum, advance);
    }

    static android.text.style.ReplacementSpan replacement(Spanned spans, int start, int end) {
        if (spans == null || start == end) return null;
        android.text.style.ReplacementSpan result = null;
        for (android.text.style.ReplacementSpan span : spans.getSpans(start, end, android.text.style.ReplacementSpan.class)) {
            int from = spans.getSpanStart(span), to = spans.getSpanEnd(span);
            if (from >= end || to <= start) continue;
            if (result != null || from != start || to != end) throw new UnsupportedOperationException("Overlapping or partial replacement span");
            result = span;
        }
        return result;
    }

    static int replacementSize(android.text.style.ReplacementSpan span, TextPaint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        int size = span.getSize(paint, text, start, end, metrics);
        if (size < 0) throw new IllegalArgumentException("Negative replacement advance");
        return size;
    }

    public static class Directions {
        final int[] runs;
        public Directions(int[] runs) { this.runs = runs.clone(); }
    }

    public static final Directions DIRS_ALL_LEFT_TO_RIGHT = new Directions(new int[] { 0, 0x3ffffff });

    public final Alignment getParagraphAlignment(int line) {
        if (line < 0 || line >= getLineCount()) throw new IndexOutOfBoundsException("line");
        Alignment result = alignment;
        if (text instanceof Spanned) {
            Spanned spans = (Spanned) text;
            int start = getLineStart(line), end = getLineEnd(line);
            for (android.text.style.AlignmentSpan span : spans.getSpans(start, end, android.text.style.AlignmentSpan.class)) {
                if (spans.getSpanStart(span) < end && spans.getSpanEnd(span) > start) result = span.getAlignment();
            }
        }
        return result;
    }

    public final int getParagraphLeft(int line) {
        return 0;
    }

    public final int getParagraphRight(int line) {
        return width;
    }

    public int getParagraphDirection(int line) { throw new UnsupportedOperationException("Paragraph direction"); }
    public boolean getLineContainsTab(int line) { throw new UnsupportedOperationException("Tab metadata"); }
    public Directions getLineDirections(int line) { throw new UnsupportedOperationException("Direction runs"); }

    /** Horizontal scrolling bounds for the supported paragraphs (no leading margins). */
    public float getLineLeft(int line) {
        Alignment alignment = getParagraphAlignment(line);
        float extent = getLineMax(line);
        if (alignment == Alignment.ALIGN_CENTER) return (width - (((int) extent) & ~1)) / 2;
        boolean right = alignment == Alignment.ALIGN_NORMAL ? getParagraphDirection(line) == DIR_RIGHT_TO_LEFT : getParagraphDirection(line) == DIR_LEFT_TO_RIGHT;
        return right ? width - extent : 0;
    }

    public float getLineRight(int line) {
        Alignment alignment = getParagraphAlignment(line);
        float extent = getLineMax(line);
        if (alignment == Alignment.ALIGN_CENTER) return width - (width - (((int) extent) & ~1)) / 2;
        boolean right = alignment == Alignment.ALIGN_NORMAL ? getParagraphDirection(line) == DIR_RIGHT_TO_LEFT : getParagraphDirection(line) == DIR_LEFT_TO_RIGHT;
        return right ? width : extent;
    }

    public int getLineVisibleEnd(int line) {
        int start = getLineStart(line), end = getLineEnd(line);
        if (line == getLineCount() - 1) return end;
        while (end > start) {
            char c = text.charAt(end - 1);
            if (c == '\n') return end - 1;
            if (c != ' ' && c != '\t' && c != 0x1680 && !(c >= 0x2000 && c <= 0x200a && c != 0x2007) && c != 0x205f && c != 0x3000) break;
            end--;
        }
        if (text instanceof Spanned) {
            Spanned spans = (Spanned) text;
            for (android.text.style.ReplacementSpan span : spans.getSpans(end, end, android.text.style.ReplacementSpan.class)) {
                if (spans.getSpanStart(span) <= end && spans.getSpanEnd(span) > end && spans.getSpanEnd(span) <= getLineEnd(line)) {
                    end = spans.getSpanEnd(span);
                }
            }
        }
        return end;
    }

    public float getLineMax(int line) { return getDesiredWidth(text, getLineStart(line), getLineVisibleEnd(line), paint); }
    public int getTopPadding() { return 0; }
    public int getBottomPadding() { return 0; }
    public int getEllipsisStart(int line) { return 0; }
    public int getEllipsisCount(int line) { return 0; }
    public int getEllipsizedWidth() { return width; }

    public void draw(Canvas canvas) { draw(canvas, null, null, 0); }
    public void draw(Canvas canvas, Path highlight, Paint paint, int cursorOffset) { throw new UnsupportedOperationException("Layout drawing"); }

    public void draw(
            Canvas canvas,
            List<Path> highlightPaths,
            List<Paint> highlightPaints,
            Path selectionPath,
            Paint selectionPaint,
            int cursorOffsetVertical) {
        drawBackground(canvas);
        if (highlightPaths != null && highlightPaints != null) {
            int n = Math.min(highlightPaths.size(), highlightPaints.size());
            for (int i = 0; i < n; i++) {
                Path hp = highlightPaths.get(i);
                Paint p = highlightPaints.get(i);
                if (hp != null && p != null) {
                    canvas.drawPath(hp, p);
                }
            }
        }
        draw(canvas, selectionPath, selectionPaint, cursorOffsetVertical);
    }

    public void drawText(Canvas canvas) {
        draw(canvas, null, null, 0);
    }

    public void drawBackground(Canvas canvas) {
        if (!(text instanceof Spanned)) return;
        Spanned spanned = (Spanned) text;
        int lineCount = getLineCount();
        for (int i = 0; i < lineCount; i++) {
            int start = getLineStart(i);
            int end = getLineEnd(i);
            android.text.style.LineBackgroundSpan[] spans =
                    spanned.getSpans(start, end, android.text.style.LineBackgroundSpan.class);
            for (int j = 0; j < spans.length; j++) {
                spans[j].drawBackground(
                        canvas, paint, 0, width, getLineTop(i), getLineBaseline(i),
                        getLineBottom(i), text, start, end, i);
            }
        }
    }
}
