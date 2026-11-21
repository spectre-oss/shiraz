// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.text.LineBreakConfig;
import java.lang.ref.WeakReference;

/** Editable layout using the existing software line breaker. Edits invalidate
 * the whole layout; incremental paragraph reflow is not implemented. */
public class DynamicLayout extends Layout {
    public static final class Builder {
        private final CharSequence mBase;
        private CharSequence mDisplay;
        private final TextPaint mPaint;
        private final int mWidth;
        private Alignment mAlignment = Alignment.ALIGN_NORMAL;
        private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        private float mSpacingMult = 1.0f;
        private float mSpacingAdd = 0.0f;
        private boolean mIncludePad = true;
        private boolean mFallbackLineSpacing = false;
        private int mEllipsizedWidth;
        private TextUtils.TruncateAt mEllipsize = null;
        private int mBreakStrategy = BREAK_STRATEGY_SIMPLE;
        private int mHyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private int mJustificationMode = JUSTIFICATION_MODE_NONE;
        private LineBreakConfig mLineBreakConfig = new LineBreakConfig.Builder().build();
        private boolean mUseBoundsForWidth = false;
        private boolean mShiftDrawingOffsetForStartOverhang = false;
        private Paint.FontMetrics mMinimumFontMetrics = null;

        private Builder(CharSequence base, TextPaint paint, int width) {
            if (base == null || paint == null) {
                throw new NullPointerException();
            }
            if (width < 0) {
                throw new IllegalArgumentException("width < 0");
            }
            mBase = base;
            mDisplay = base;
            mPaint = paint;
            mWidth = width;
            mEllipsizedWidth = width;
        }

        public static Builder obtain(CharSequence base, TextPaint paint, int width) {
            return new Builder(base, paint, width);
        }

        public Builder setDisplayText(CharSequence display) {
            if (display == null) {
                throw new NullPointerException("display");
            }
            mDisplay = display;
            return this;
        }

        public Builder setAlignment(Alignment alignment) {
            mAlignment = alignment != null ? alignment : Alignment.ALIGN_NORMAL;
            return this;
        }

        public Builder setTextDirection(TextDirectionHeuristic textDir) {
            mTextDir = textDir != null ? textDir : TextDirectionHeuristics.FIRSTSTRONG_LTR;
            return this;
        }

        public Builder setLineSpacing(float spacingAdd, float spacingMult) {
            mSpacingAdd = spacingAdd;
            mSpacingMult = spacingMult;
            return this;
        }

        public Builder setIncludePad(boolean includePad) {
            mIncludePad = includePad;
            return this;
        }

        public Builder setUseLineSpacingFromFallbacks(boolean useLineSpacingFromFallbacks) {
            mFallbackLineSpacing = useLineSpacingFromFallbacks;
            return this;
        }

        public Builder setEllipsizedWidth(int ellipsizedWidth) {
            mEllipsizedWidth = ellipsizedWidth;
            return this;
        }

        public Builder setEllipsize(TextUtils.TruncateAt ellipsize) {
            mEllipsize = ellipsize;
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

        public DynamicLayout build() {
            return new DynamicLayout(this);
        }
    }

    private final boolean includePad;
    private final TextUtils.TruncateAt ellipsize;
    private final int ellipsizedWidth;
    private final TextDirectionHeuristic textDir;
    private final int breakStrategy;
    private final int hyphenationFrequency;
    private StaticLayout cached;
    private boolean dirty = true;
    private final boolean unobserved;

    public DynamicLayout(CharSequence base, TextPaint paint, int width, Alignment align, float mult, float add, boolean pad) {
        this(base, base, paint, width, align, mult, add, pad, null, width);
    }

    public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint, int width, Alignment align, float mult, float add, boolean pad) {
        this(base, display, paint, width, align, mult, add, pad, null, width);
    }

    public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint, int width, Alignment align, float mult, float add, boolean pad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        super(display, paint, width, align, mult, add);
        if (base == null) throw new NullPointerException("base");
        if (ellipsizedWidth < 0) throw new IllegalArgumentException("ellipsis width");
        includePad = pad;
        this.ellipsize = ellipsize;
        this.ellipsizedWidth = ellipsizedWidth;
        this.textDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
        this.breakStrategy = 0;
        this.hyphenationFrequency = 0;
        observe(base);
        if (display != base) observe(display);
        unobserved = !(display instanceof Spannable) && !(display instanceof String) && !(display instanceof SpannedString);
        layout();
    }

    private DynamicLayout(Builder b) {
        super(b.mDisplay, b.mPaint, b.mWidth, b.mAlignment, b.mSpacingMult, b.mSpacingAdd);
        if (b.mEllipsizedWidth < 0) throw new IllegalArgumentException("ellipsis width");
        this.includePad = b.mIncludePad;
        this.ellipsize = b.mEllipsize;
        this.ellipsizedWidth = b.mEllipsizedWidth;
        this.textDir = b.mTextDir;
        this.breakStrategy = b.mBreakStrategy;
        this.hyphenationFrequency = b.mHyphenationFrequency;
        observe(b.mBase);
        if (b.mDisplay != b.mBase) observe(b.mDisplay);
        unobserved = !(b.mDisplay instanceof Spannable) && !(b.mDisplay instanceof String) && !(b.mDisplay instanceof SpannedString);
        layout();
    }

    private void observe(CharSequence value) {
        if (value instanceof Spannable) {
            Spannable spans = (Spannable) value;
            for (ChangeWatcher watcher : spans.getSpans(0, value.length(), ChangeWatcher.class)) {
                if (watcher.owner.get() == null) spans.removeSpan(watcher);
            }
            spans.setSpan(new ChangeWatcher(this), 0, value.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
    }

    private StaticLayout layout() {
        if (dirty || unobserved) {
            CharSequence text = getText();
            StaticLayout.Builder sb = StaticLayout.Builder.obtain(text, 0, text.length(), getPaint(), getWidth())
                    .setAlignment(getAlignment())
                    .setTextDirection(textDir)
                    .setLineSpacing(getSpacingAdd(), getSpacingMultiplier())
                    .setIncludePad(includePad)
                    .setEllipsize(ellipsize)
                    .setEllipsizedWidth(ellipsizedWidth);
            if (breakStrategy >= 0 && breakStrategy <= 2) {
                sb.setBreakStrategy(breakStrategy);
            }
            if (hyphenationFrequency >= 0 && hyphenationFrequency <= 4) {
                sb.setHyphenationFrequency(hyphenationFrequency);
            }
            cached = sb.build();
            dirty = false;
        }
        return cached;
    }

    private static final class ChangeWatcher implements TextWatcher, SpanWatcher, NoCopySpan {
        final WeakReference<DynamicLayout> owner;
        ChangeWatcher(DynamicLayout value) { owner = new WeakReference<DynamicLayout>(value); }
        private void changed() { DynamicLayout value = owner.get(); if (value != null) value.dirty = true; }
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) { changed(); }
        public void afterTextChanged(Editable s) { changed(); }
        public void onSpanAdded(Spannable s, Object what, int start, int end) { if (!(what instanceof NoCopySpan)) changed(); }
        public void onSpanRemoved(Spannable s, Object what, int start, int end) { if (!(what instanceof NoCopySpan)) changed(); }
        public void onSpanChanged(Spannable s, Object what, int oldStart, int oldEnd, int newStart, int newEnd) { if (!(what instanceof NoCopySpan)) changed(); }
    }

    public int getLineCount() { return layout().getLineCount(); }
    public int getLineTop(int line) { return layout().getLineTop(line); }
    public int getLineDescent(int line) { return layout().getLineDescent(line); }
    public int getLineStart(int line) { return layout().getLineStart(line); }
    public float getLineWidth(int line) { return layout().getLineWidth(line); }
    public float getLineMax(int line) { return layout().getLineMax(line); }
    public int getLineVisibleEnd(int line) { return layout().getLineVisibleEnd(line); }
    public int getParagraphDirection(int line) { return layout().getParagraphDirection(line); }
    public boolean getLineContainsTab(int line) { return layout().getLineContainsTab(line); }
    public Directions getLineDirections(int line) { return layout().getLineDirections(line); }
    public int getTopPadding() { return layout().getTopPadding(); }
    public int getBottomPadding() { return layout().getBottomPadding(); }
    public int getEllipsisStart(int line) { return layout().getEllipsisStart(line); }
    public int getEllipsisCount(int line) { return layout().getEllipsisCount(line); }
    public int getEllipsizedWidth() { return ellipsizedWidth; }
    public void draw(Canvas canvas, Path highlight, Paint highlightPaint, int cursorOffset) { layout().draw(canvas, highlight, highlightPaint, cursorOffset); }
}
