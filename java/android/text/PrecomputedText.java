// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.text.LineBreakConfig;
import android.graphics.text.MeasuredText;
import android.text.style.MetricAffectingSpan;
import android.text.style.ReplacementSpan;
import java.util.ArrayList;
import java.util.Objects;

/** Text whose paragraph glyph advances and font metrics have been precomputed. */
public class PrecomputedText implements Spannable {
    public static final class Params {
        public static class Builder {
            private final TextPaint mPaint;
            private TextDirectionHeuristic mTextDir = TextDirectionHeuristics.FIRSTSTRONG_LTR;
            private int mBreakStrategy = Layout.BREAK_STRATEGY_HIGH_QUALITY;
            private int mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL;
            private LineBreakConfig mLineBreakConfig = new LineBreakConfig.Builder().build();

            public Builder(TextPaint paint) {
                if (paint == null) {
                    throw new NullPointerException("paint");
                }
                mPaint = paint;
            }

            public Builder(Params params) {
                if (params == null) {
                    throw new NullPointerException("params");
                }
                mPaint = params.mPaint;
                mTextDir = params.mTextDir;
                mBreakStrategy = params.mBreakStrategy;
                mHyphenationFrequency = params.mHyphenationFrequency;
                mLineBreakConfig = params.mLineBreakConfig;
            }

            public Builder setBreakStrategy(int strategy) {
                mBreakStrategy = strategy;
                return this;
            }

            public Builder setHyphenationFrequency(int frequency) {
                mHyphenationFrequency = frequency;
                return this;
            }

            public Builder setTextDirection(TextDirectionHeuristic textDir) {
                if (textDir == null) {
                    throw new NullPointerException("textDir");
                }
                mTextDir = textDir;
                return this;
            }

            public Builder setLineBreakConfig(LineBreakConfig lineBreakConfig) {
                mLineBreakConfig = lineBreakConfig != null
                        ? lineBreakConfig
                        : new LineBreakConfig.Builder().build();
                return this;
            }

            public Params build() {
                return new Params(
                        mPaint,
                        mLineBreakConfig,
                        mTextDir,
                        mBreakStrategy,
                        mHyphenationFrequency);
            }
        }

        private final TextPaint mPaint;
        private final TextDirectionHeuristic mTextDir;
        private final int mBreakStrategy;
        private final int mHyphenationFrequency;
        private final LineBreakConfig mLineBreakConfig;

        Params(
                TextPaint paint,
                LineBreakConfig lineBreakConfig,
                TextDirectionHeuristic textDir,
                int strategy,
                int frequency) {
            mPaint = paint;
            mTextDir = textDir;
            mBreakStrategy = strategy;
            mHyphenationFrequency = frequency;
            mLineBreakConfig = lineBreakConfig;
        }

        public TextPaint getTextPaint() {
            return mPaint;
        }

        public TextDirectionHeuristic getTextDirection() {
            return mTextDir;
        }

        public int getBreakStrategy() {
            return mBreakStrategy;
        }

        public int getHyphenationFrequency() {
            return mHyphenationFrequency;
        }

        public LineBreakConfig getLineBreakConfig() {
            return mLineBreakConfig;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Params)) return false;
            Params p = (Params) o;
            return mBreakStrategy == p.mBreakStrategy
                    && mHyphenationFrequency == p.mHyphenationFrequency
                    && Objects.equals(mTextDir, p.mTextDir)
                    && Objects.equals(mLineBreakConfig, p.mLineBreakConfig)
                    && sameTextMetrics(mPaint, p.mPaint);
        }

        private static boolean sameTextMetrics(TextPaint a, TextPaint b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return a.getTextSize() == b.getTextSize()
                    && a.getTextScaleX() == b.getTextScaleX()
                    && a.getTextSkewX() == b.getTextSkewX()
                    && a.getLetterSpacing() == b.getLetterSpacing()
                    && a.getFlags() == b.getFlags()
                    && Objects.equals(a.getTextLocale(), b.getTextLocale())
                    && Objects.equals(a.getTypeface(), b.getTypeface())
                    && Objects.equals(a.getFontFeatureSettings(), b.getFontFeatureSettings());
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    Float.valueOf(mPaint.getTextSize()),
                    Float.valueOf(mPaint.getTextScaleX()),
                    Float.valueOf(mPaint.getTextSkewX()),
                    Float.valueOf(mPaint.getLetterSpacing()),
                    Integer.valueOf(mPaint.getFlags()),
                    mPaint.getTextLocale(),
                    mPaint.getTypeface(),
                    mTextDir,
                    Integer.valueOf(mBreakStrategy),
                    Integer.valueOf(mHyphenationFrequency),
                    mLineBreakConfig);
        }

        @Override
        public String toString() {
            return "{textSize=" + mPaint.getTextSize()
                    + ", textScaleX=" + mPaint.getTextScaleX()
                    + ", textSkewX=" + mPaint.getTextSkewX()
                    + ", letterSpacing=" + mPaint.getLetterSpacing()
                    + ", textLocale=" + mPaint.getTextLocale()
                    + ", typeface=" + mPaint.getTypeface()
                    + ", variationSettings=" + mPaint.getFontVariationSettings()
                    + ", elegantTextHeight=" + mPaint.isElegantTextHeight()
                    + ", textDir=" + mTextDir
                    + ", breakStrategy=" + mBreakStrategy
                    + ", hyphenationFrequency=" + mHyphenationFrequency
                    + ", lineBreakStyle=" + mLineBreakConfig.getLineBreakStyle()
                    + ", lineBreakWordStyle=" + mLineBreakConfig.getLineBreakWordStyle()
                    + "}";
        }
    }

    private static final class ParagraphInfo {
        final int paraStart;
        final int paraEnd;
        final MeasuredText measured;

        ParagraphInfo(int paraStart, int paraEnd, MeasuredText measured) {
            this.paraStart = paraStart;
            this.paraEnd = paraEnd;
            this.measured = measured;
        }
    }

    private final SpannableString mText;
    private final Params mParams;
    private final ParagraphInfo[] mParagraphInfo;

    private PrecomputedText(
            CharSequence text, Params params, ParagraphInfo[] paragraphInfo) {
        mText = new SpannableString(text);
        mParams = params;
        mParagraphInfo = paragraphInfo;
    }

    public static PrecomputedText create(CharSequence text, Params params) {
        if (text == null || params == null) {
            throw new NullPointerException();
        }
        SpannableString spanned = new SpannableString(text);
        ArrayList<ParagraphInfo> list = new ArrayList<ParagraphInfo>();
        int len = spanned.length();
        if (len == 0) {
            MeasuredText mt = new MeasuredText.Builder(new char[0]).build();
            list.add(new ParagraphInfo(0, 0, mt));
        } else {
            int paraStart = 0;
            while (paraStart < len) {
                int paraEnd = TextUtils.indexOf(spanned, '\n', paraStart, len);
                if (paraEnd < 0) {
                    paraEnd = len;
                } else {
                    paraEnd++;
                }
                MeasuredText mt = buildMeasuredParagraph(spanned, paraStart, paraEnd, params);
                list.add(new ParagraphInfo(paraStart, paraEnd, mt));
                paraStart = paraEnd;
            }
        }
        return new PrecomputedText(
                spanned, params, list.toArray(new ParagraphInfo[list.size()]));
    }

    private static MeasuredText buildMeasuredParagraph(
            Spanned text, int paraStart, int paraEnd, Params params) {
        int paraLen = paraEnd - paraStart;
        char[] chars = new char[paraLen];
        TextUtils.getChars(text, paraStart, paraEnd, chars, 0);
        for (int i = 0; i < paraLen; i++) {
            if (chars[i] == '\n' || chars[i] == '\r') {
                chars[i] = ' ';
            }
        }
        MeasuredText.Builder mb = new MeasuredText.Builder(chars);
        int hyph = params.getHyphenationFrequency();
        if (hyph == Layout.HYPHENATION_FREQUENCY_NORMAL_FAST
                || hyph == Layout.HYPHENATION_FREQUENCY_FULL_FAST) {
            mb.setComputeHyphenation(MeasuredText.Builder.HYPHENATION_MODE_FAST);
        } else if (hyph != Layout.HYPHENATION_FREQUENCY_NONE) {
            mb.setComputeHyphenation(MeasuredText.Builder.HYPHENATION_MODE_NORMAL);
        }
        boolean paraRtl = params.getTextDirection().isRtl(chars, 0, paraLen);
        java.text.Bidi bidi = new java.text.Bidi(
                new String(chars),
                paraRtl ? java.text.Bidi.DIRECTION_RIGHT_TO_LEFT
                        : java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);

        int cursor = paraStart;
        while (cursor < paraEnd) {
            int spanEnd = text.nextSpanTransition(cursor, paraEnd, MetricAffectingSpan.class);
            int relStart = cursor - paraStart;
            int runIdx = bidi.getRunCount() > 0 ? findBidiRun(bidi, relStart) : -1;
            int bidiEnd = runIdx >= 0 ? paraStart + bidi.getRunLimit(runIdx) : paraEnd;
            int next = Math.min(spanEnd, Math.min(paraEnd, Math.max(cursor + 1, bidiEnd)));
            boolean isRtl = runIdx >= 0 ? (bidi.getRunLevel(runIdx) & 1) != 0 : paraRtl;

            TextPaint workPaint = new TextPaint(params.getTextPaint());
            MetricAffectingSpan[] spans =
                    text.getSpans(cursor, next, MetricAffectingSpan.class);
            ReplacementSpan replacement = null;
            for (int i = 0; i < spans.length; i++) {
                if (spans[i] instanceof ReplacementSpan) {
                    replacement = (ReplacementSpan) spans[i];
                } else {
                    spans[i].updateMeasureState(workPaint);
                }
            }
            if (replacement != null) {
                float w = replacement.getSize(workPaint, text, cursor, next, null);
                mb.appendReplacementRun(workPaint, next - cursor, Math.max(0f, w));
            } else {
                mb.appendStyleRun(
                        workPaint, params.getLineBreakConfig(), next - cursor, isRtl);
            }
            cursor = next;
        }
        return mb.build();
    }

    private static int findBidiRun(java.text.Bidi bidi, int relOffset) {
        int count = bidi.getRunCount();
        for (int i = 0; i < count; i++) {
            if (relOffset >= bidi.getRunStart(i) && relOffset < bidi.getRunLimit(i)) {
                return i;
            }
        }
        return -1;
    }

    public Params getParams() {
        return mParams;
    }

    public int getParagraphCount() {
        return mParagraphInfo.length;
    }

    public int getParagraphStart(int paraIndex) {
        if (paraIndex < 0 || paraIndex >= mParagraphInfo.length) {
            throw new IndexOutOfBoundsException("paraIndex=" + paraIndex);
        }
        return mParagraphInfo[paraIndex].paraStart;
    }

    public int getParagraphEnd(int paraIndex) {
        if (paraIndex < 0 || paraIndex >= mParagraphInfo.length) {
            throw new IndexOutOfBoundsException("paraIndex=" + paraIndex);
        }
        return mParagraphInfo[paraIndex].paraEnd;
    }

    private ParagraphInfo findParagraphInfo(int start, int end) {
        if (start < 0 || end < start || end > mText.length()) {
            throw new IllegalArgumentException(
                    "Invalid range: [" + start + ", " + end + ") for length " + mText.length());
        }
        for (int i = 0; i < mParagraphInfo.length; i++) {
            ParagraphInfo info = mParagraphInfo[i];
            if (start >= info.paraStart && end <= info.paraEnd) {
                return info;
            }
        }
        throw new IllegalArgumentException(
                "Cannot measured across the paragraph:para: [" + start + ", " + end + ")");
    }

    public float getWidth(int start, int end) {
        if (start == end && start >= 0 && start <= mText.length()) {
            return 0f;
        }
        ParagraphInfo info = findParagraphInfo(start, end);
        return info.measured.getWidth(start - info.paraStart, end - info.paraStart);
    }

    public void getBounds(int start, int end, Rect bounds) {
        if (bounds == null) {
            throw new NullPointerException("bounds");
        }
        if (start == end && start >= 0 && start <= mText.length()) {
            bounds.set(0, 0, 0, 0);
            return;
        }
        ParagraphInfo info = findParagraphInfo(start, end);
        info.measured.getBounds(start - info.paraStart, end - info.paraStart, bounds);
    }

    public void getFontMetricsInt(int start, int end, Paint.FontMetricsInt outMetrics) {
        if (outMetrics == null) {
            throw new NullPointerException("outMetrics");
        }
        ParagraphInfo info = findParagraphInfo(start, end);
        info.measured.getFontMetricsInt(
                start - info.paraStart, end - info.paraStart, outMetrics);
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        if (what instanceof MetricAffectingSpan) {
            throw new IllegalArgumentException(
                    "MetricAffectingSpan can not be set to PrecomputedText.");
        }
        mText.setSpan(what, start, end, flags);
    }

    @Override
    public void removeSpan(Object what) {
        if (what instanceof MetricAffectingSpan) {
            throw new IllegalArgumentException(
                    "MetricAffectingSpan can not be removed from PrecomputedText.");
        }
        mText.removeSpan(what);
    }

    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        return mText.getSpans(start, end, type);
    }

    @Override
    public int getSpanStart(Object tag) {
        return mText.getSpanStart(tag);
    }

    @Override
    public int getSpanEnd(Object tag) {
        return mText.getSpanEnd(tag);
    }

    @Override
    public int getSpanFlags(Object tag) {
        return mText.getSpanFlags(tag);
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        return mText.nextSpanTransition(start, limit, type);
    }

    @Override
    public int length() {
        return mText.length();
    }

    @Override
    public char charAt(int index) {
        return mText.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return create(mText.subSequence(start, end), mParams);
    }

    @Override
    public String toString() {
        return mText.toString();
    }
}
