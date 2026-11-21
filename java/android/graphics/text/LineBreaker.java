// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.text;

import android.graphics.Paint;
import java.util.ArrayList;

public class LineBreaker {
    public static final int BREAK_STRATEGY_SIMPLE = 0;
    public static final int BREAK_STRATEGY_HIGH_QUALITY = 1;
    public static final int BREAK_STRATEGY_BALANCED = 2;

    public static final int HYPHENATION_FREQUENCY_NONE = 0;
    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;
    public static final int HYPHENATION_FREQUENCY_FULL = 2;

    public static final int JUSTIFICATION_MODE_NONE = 0;
    public static final int JUSTIFICATION_MODE_INTER_WORD = 1;
    public static final int JUSTIFICATION_MODE_INTER_CHARACTER = 2;

    private final int breakStrategy;
    private final int hyphenationFrequency;
    private final int justificationMode;
    private final int[] indents;
    private final boolean useBoundsForWidth;

    LineBreaker(
            int breakStrategy,
            int hyphenationFrequency,
            int justificationMode,
            int[] indents,
            boolean useBoundsForWidth) {
        this.breakStrategy = breakStrategy;
        this.hyphenationFrequency = hyphenationFrequency;
        this.justificationMode = justificationMode;
        this.indents = indents == null ? null : indents.clone();
        this.useBoundsForWidth = useBoundsForWidth;
    }

    public Result computeLineBreaks(
            MeasuredText measuredPara, ParagraphConstraints constraints, int lineNumber) {
        if (measuredPara == null) {
            throw new NullPointerException("measuredPara");
        }
        if (constraints == null) {
            throw new NullPointerException("constraints");
        }
        if (lineNumber < 0) {
            throw new IllegalArgumentException("lineNumber must be >= 0");
        }

        char[] chars = measuredPara.chars();
        float[] widths = measuredPara.charWidths();
        int n = chars.length;
        if (n == 0) {
            return new Result(
                    new int[0],
                    new float[0],
                    new float[0],
                    new float[0],
                    new boolean[0],
                    new int[0],
                    new int[0]);
        }

        ArrayList<Integer> breakOffsets = new ArrayList<Integer>();
        ArrayList<Float> lineWidths = new ArrayList<Float>();
        ArrayList<Float> lineAscents = new ArrayList<Float>();
        ArrayList<Float> lineDescents = new ArrayList<Float>();
        ArrayList<Boolean> lineTabs = new ArrayList<Boolean>();
        ArrayList<Integer> startHyphens = new ArrayList<Integer>();
        ArrayList<Integer> endHyphens = new ArrayList<Integer>();

        int segStart = 0;
        int currentLineIndex = lineNumber;
        while (segStart < n) {
            int hardEnd = segStart;
            boolean hasNewline = false;
            while (hardEnd < n) {
                if (chars[hardEnd] == '\n') {
                    hasNewline = true;
                    break;
                }
                hardEnd++;
            }

            if (breakStrategy == BREAK_STRATEGY_SIMPLE) {
                currentLineIndex =
                        breakSegmentGreedy(
                                measuredPara,
                                chars,
                                widths,
                                segStart,
                                hardEnd,
                                hasNewline,
                                constraints,
                                currentLineIndex,
                                breakOffsets,
                                lineWidths,
                                lineAscents,
                                lineDescents,
                                lineTabs,
                                startHyphens,
                                endHyphens);
            } else {
                currentLineIndex =
                        breakSegmentOptimal(
                                measuredPara,
                                chars,
                                widths,
                                segStart,
                                hardEnd,
                                hasNewline,
                                constraints,
                                currentLineIndex,
                                breakStrategy == BREAK_STRATEGY_BALANCED,
                                breakOffsets,
                                lineWidths,
                                lineAscents,
                                lineDescents,
                                lineTabs,
                                startHyphens,
                                endHyphens);
            }
            segStart = hasNewline ? (hardEnd + 1) : hardEnd;
        }

        int count = breakOffsets.size();
        int[] outBreaks = new int[count];
        float[] outWidths = new float[count];
        float[] outAscents = new float[count];
        float[] outDescents = new float[count];
        boolean[] outTabs = new boolean[count];
        int[] outStartHyphens = new int[count];
        int[] outEndHyphens = new int[count];
        for (int i = 0; i < count; i++) {
            outBreaks[i] = breakOffsets.get(i).intValue();
            outWidths[i] = lineWidths.get(i).floatValue();
            outAscents[i] = lineAscents.get(i).floatValue();
            outDescents[i] = lineDescents.get(i).floatValue();
            outTabs[i] = lineTabs.get(i).booleanValue();
            outStartHyphens[i] = startHyphens.get(i).intValue();
            outEndHyphens[i] = endHyphens.get(i).intValue();
        }
        return new Result(
                outBreaks,
                outWidths,
                outAscents,
                outDescents,
                outTabs,
                outStartHyphens,
                outEndHyphens);
    }

    private float availableWidth(ParagraphConstraints constraints, int lineIdx) {
        float base =
                lineIdx < constraints.getFirstWidthLineCount()
                        ? constraints.getFirstWidth()
                        : constraints.getWidth();
        if (indents != null && indents.length > 0) {
            int indent = indents[Math.min(lineIdx, indents.length - 1)];
            base -= indent;
        }
        return Math.max(0f, base);
    }

    private static float nextTabPosition(float x, ParagraphConstraints constraints) {
        float[] stops = constraints.getTabStops();
        if (stops != null) {
            for (int i = 0; i < stops.length; i++) {
                if (stops[i] > x) {
                    return stops[i];
                }
            }
        }
        float def = constraints.getDefaultTabStop();
        if (def > 0f) {
            return (float) (Math.floor(x / def) + 1.0) * def;
        }
        return x;
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\u1680'
                || (c >= '\u2000' && c <= '\u200A' && c != '\u2007')
                || c == '\u205F' || c == '\u3000';
    }

    private static boolean isBreakOpportunityAfter(char[] chars, int idx, int end) {
        if (idx + 1 >= end) {
            return true;
        }
        char c = chars[idx];
        char next = chars[idx + 1];
        if (isWhitespace(c) && !isWhitespace(next)) {
            return true;
        }
        if ((c == '-' || c == '\u2010' || c == '/' || c == '\u200B') && !isWhitespace(next)) {
            return true;
        }
        if (isCjk(c) || isCjk(next)) {
            return true;
        }
        return false;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x2E80 && c <= 0x9FFF)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFF00 && c <= 0xFFEF);
    }

    private int breakSegmentGreedy(
            MeasuredText measuredPara,
            char[] chars,
            float[] widths,
            int segStart,
            int segEnd,
            boolean hasNewline,
            ParagraphConstraints constraints,
            int lineIdx,
            ArrayList<Integer> breakOffsets,
            ArrayList<Float> lineWidths,
            ArrayList<Float> lineAscents,
            ArrayList<Float> lineDescents,
            ArrayList<Boolean> lineTabs,
            ArrayList<Integer> startHyphens,
            ArrayList<Integer> endHyphens) {
        if (segStart == segEnd) {
            int endOffset = hasNewline ? segEnd + 1 : segEnd;
            appendLine(
                    measuredPara,
                    chars,
                    widths,
                    segStart,
                    segEnd,
                    endOffset,
                    constraints,
                    breakOffsets,
                    lineWidths,
                    lineAscents,
                    lineDescents,
                    lineTabs,
                    startHyphens,
                    endHyphens);
            return lineIdx + 1;
        }

        int lineStart = segStart;
        while (lineStart < segEnd) {
            float limit = availableWidth(constraints, lineIdx);
            float x = 0f;
            float visibleWidth = 0f;
            int lastCandidate = -1;
            int i = lineStart;
            while (i < segEnd) {
                char c = chars[i];
                float nextX;
                if (c == '\t') {
                    nextX = nextTabPosition(x, constraints);
                } else {
                    nextX = x + widths[i];
                }
                boolean ws = isWhitespace(c) && c != '\t';
                float candidateVisible = ws ? visibleWidth : nextX;
                if (i > lineStart && !ws && candidateVisible > limit && limit > 0f) {
                    break;
                }
                x = nextX;
                if (!ws) {
                    visibleWidth = nextX;
                }
                if (isBreakOpportunityAfter(chars, i, segEnd)) {
                    lastCandidate = i + 1;
                }
                i++;
            }

            int lineEnd;
            if (i >= segEnd) {
                lineEnd = segEnd;
            } else if (lastCandidate > lineStart) {
                lineEnd = lastCandidate;
            } else {
                lineEnd = Math.max(lineStart + 1, i);
            }

            boolean isFinalInSegment = (lineEnd >= segEnd);
            int reportedBreak = (isFinalInSegment && hasNewline) ? (segEnd + 1) : lineEnd;
            appendLine(
                    measuredPara,
                    chars,
                    widths,
                    lineStart,
                    lineEnd,
                    reportedBreak,
                    constraints,
                    breakOffsets,
                    lineWidths,
                    lineAscents,
                    lineDescents,
                    lineTabs,
                    startHyphens,
                    endHyphens);
            lineStart = lineEnd;
            lineIdx++;
        }
        return lineIdx;
    }

    private int breakSegmentOptimal(
            MeasuredText measuredPara,
            char[] chars,
            float[] widths,
            int segStart,
            int segEnd,
            boolean hasNewline,
            ParagraphConstraints constraints,
            int lineIdx,
            boolean balanced,
            ArrayList<Integer> breakOffsets,
            ArrayList<Float> lineWidths,
            ArrayList<Float> lineAscents,
            ArrayList<Float> lineDescents,
            ArrayList<Boolean> lineTabs,
            ArrayList<Integer> startHyphens,
            ArrayList<Integer> endHyphens) {
        if (segEnd - segStart > 2048 || segStart == segEnd) {
            return breakSegmentGreedy(
                    measuredPara,
                    chars,
                    widths,
                    segStart,
                    segEnd,
                    hasNewline,
                    constraints,
                    lineIdx,
                    breakOffsets,
                    lineWidths,
                    lineAscents,
                    lineDescents,
                    lineTabs,
                    startHyphens,
                    endHyphens);
        }

        // Collect candidate break positions within [segStart, segEnd].
        ArrayList<Integer> candidates = new ArrayList<Integer>();
        candidates.add(Integer.valueOf(segStart));
        for (int i = segStart; i < segEnd; i++) {
            if (isBreakOpportunityAfter(chars, i, segEnd)) {
                candidates.add(Integer.valueOf(i + 1));
            }
        }
        if (candidates.get(candidates.size() - 1).intValue() != segEnd) {
            candidates.add(Integer.valueOf(segEnd));
        }

        int m = candidates.size();
        // Check if any single word exceeds available width; if so, fall back to greedy emergency breaks.
        float firstLimit = availableWidth(constraints, lineIdx);
        for (int a = 0; a < m - 1; a++) {
            int s = candidates.get(a).intValue();
            int e = candidates.get(a + 1).intValue();
            float w = measureVisibleWidth(chars, widths, s, e, constraints);
            if (firstLimit > 0f && w > firstLimit) {
                return breakSegmentGreedy(
                        measuredPara,
                        chars,
                        widths,
                        segStart,
                        segEnd,
                        hasNewline,
                        constraints,
                        lineIdx,
                        breakOffsets,
                        lineWidths,
                        lineAscents,
                        lineDescents,
                        lineTabs,
                        startHyphens,
                        endHyphens);
            }
        }

        double[] dp = new double[m];
        int[] prev = new int[m];
        int[] lineCountAt = new int[m];
        for (int i = 1; i < m; i++) {
            dp[i] = Double.POSITIVE_INFINITY;
            prev[i] = 0;
        }
        dp[0] = 0.0;
        lineCountAt[0] = 0;

        for (int i = 0; i < m - 1; i++) {
            if (Double.isInfinite(dp[i])) {
                continue;
            }
            int s = candidates.get(i).intValue();
            float limit = availableWidth(constraints, lineIdx + lineCountAt[i]);
            for (int j = i + 1; j < m; j++) {
                int e = candidates.get(j).intValue();
                float w = measureVisibleWidth(chars, widths, s, e, constraints);
                if (j > i + 1 && limit > 0f && w > limit) {
                    break;
                }
                double slack = Math.max(0.0, limit - w);
                boolean isLast = (j == m - 1);
                double penalty;
                if (isLast && !balanced) {
                    penalty = 0.0;
                } else {
                    penalty = slack * slack;
                }
                // Strongly prefer fewer lines when costs are comparable.
                double total = dp[i] + penalty + (double) (limit * limit + 1.0f);
                if (total < dp[j]) {
                    dp[j] = total;
                    prev[j] = i;
                    lineCountAt[j] = lineCountAt[i] + 1;
                }
            }
        }

        ArrayList<Integer> chosen = new ArrayList<Integer>();
        int cur = m - 1;
        while (cur > 0) {
            chosen.add(candidates.get(cur));
            cur = prev[cur];
        }
        int prevPos = segStart;
        for (int k = chosen.size() - 1; k >= 0; k--) {
            int endPos = chosen.get(k).intValue();
            boolean isFinal = (k == 0);
            int reported = (isFinal && hasNewline) ? (segEnd + 1) : endPos;
            appendLine(
                    measuredPara,
                    chars,
                    widths,
                    prevPos,
                    endPos,
                    reported,
                    constraints,
                    breakOffsets,
                    lineWidths,
                    lineAscents,
                    lineDescents,
                    lineTabs,
                    startHyphens,
                    endHyphens);
            prevPos = endPos;
            lineIdx++;
        }
        return lineIdx;
    }

    private static float measureVisibleWidth(
            char[] chars,
            float[] widths,
            int start,
            int end,
            ParagraphConstraints constraints) {
        int trimmedEnd = end;
        while (trimmedEnd > start && isWhitespace(chars[trimmedEnd - 1])
                && chars[trimmedEnd - 1] != '\t') {
            trimmedEnd--;
        }
        float x = 0f;
        for (int i = start; i < trimmedEnd; i++) {
            if (chars[i] == '\t') {
                x = nextTabPosition(x, constraints);
            } else {
                x += widths[i];
            }
        }
        return x;
    }

    private static void appendLine(
            MeasuredText measuredPara,
            char[] chars,
            float[] widths,
            int start,
            int contentEnd,
            int reportedBreakOffset,
            ParagraphConstraints constraints,
            ArrayList<Integer> breakOffsets,
            ArrayList<Float> lineWidths,
            ArrayList<Float> lineAscents,
            ArrayList<Float> lineDescents,
            ArrayList<Boolean> lineTabs,
            ArrayList<Integer> startHyphens,
            ArrayList<Integer> endHyphens) {
        boolean hasTab = false;
        for (int i = start; i < contentEnd; i++) {
            if (chars[i] == '\t') {
                hasTab = true;
                break;
            }
        }
        float width = measureVisibleWidth(chars, widths, start, contentEnd, constraints);
        Paint.FontMetricsInt fm = new Paint.FontMetricsInt();
        int metricEnd = Math.min(chars.length, Math.max(contentEnd, reportedBreakOffset));
        measuredPara.getFontMetricsInt(start, metricEnd, fm);
        breakOffsets.add(Integer.valueOf(reportedBreakOffset));
        lineWidths.add(Float.valueOf(width));
        lineAscents.add(Float.valueOf(fm.ascent));
        lineDescents.add(Float.valueOf(fm.descent));
        lineTabs.add(Boolean.valueOf(hasTab));
        startHyphens.add(Integer.valueOf(Paint.START_HYPHEN_EDIT_NO_EDIT));
        endHyphens.add(Integer.valueOf(Paint.END_HYPHEN_EDIT_NO_EDIT));
    }

    public static final class Builder {
        private int breakStrategy = BREAK_STRATEGY_SIMPLE;
        private int hyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private int justificationMode = JUSTIFICATION_MODE_NONE;
        private int[] indents;
        private boolean useBoundsForWidth;

        public Builder() {
        }

        public Builder setBreakStrategy(int breakStrategy) {
            if (breakStrategy != BREAK_STRATEGY_SIMPLE
                    && breakStrategy != BREAK_STRATEGY_HIGH_QUALITY
                    && breakStrategy != BREAK_STRATEGY_BALANCED) {
                throw new IllegalArgumentException("Invalid break strategy: " + breakStrategy);
            }
            this.breakStrategy = breakStrategy;
            return this;
        }

        public Builder setHyphenationFrequency(int hyphenationFrequency) {
            if (hyphenationFrequency != HYPHENATION_FREQUENCY_NONE
                    && hyphenationFrequency != HYPHENATION_FREQUENCY_NORMAL
                    && hyphenationFrequency != HYPHENATION_FREQUENCY_FULL) {
                throw new IllegalArgumentException(
                        "Invalid hyphenation frequency: " + hyphenationFrequency);
            }
            this.hyphenationFrequency = hyphenationFrequency;
            return this;
        }

        public Builder setJustificationMode(int justificationMode) {
            if (justificationMode != JUSTIFICATION_MODE_NONE
                    && justificationMode != JUSTIFICATION_MODE_INTER_WORD
                    && justificationMode != JUSTIFICATION_MODE_INTER_CHARACTER) {
                throw new IllegalArgumentException(
                        "Invalid justification mode: " + justificationMode);
            }
            this.justificationMode = justificationMode;
            return this;
        }

        public Builder setIndents(int[] indents) {
            this.indents = indents == null ? null : indents.clone();
            return this;
        }

        public Builder setUseBoundsForWidth(boolean useBoundsForWidth) {
            this.useBoundsForWidth = useBoundsForWidth;
            return this;
        }

        public LineBreaker build() {
            return new LineBreaker(
                    breakStrategy,
                    hyphenationFrequency,
                    justificationMode,
                    indents,
                    useBoundsForWidth);
        }
    }

    public static class ParagraphConstraints {
        private float width;
        private float firstWidth;
        private int firstWidthLineCount;
        private float[] variableTabStops;
        private float defaultTabStop;

        public ParagraphConstraints() {
        }

        public void setWidth(float width) {
            this.width = width;
        }

        public void setIndent(float firstWidth, int firstWidthLineCount) {
            this.firstWidth = firstWidth;
            this.firstWidthLineCount = firstWidthLineCount;
        }

        public void setTabStops(float[] tabStops, float defaultTabStop) {
            this.variableTabStops = tabStops == null ? null : tabStops.clone();
            this.defaultTabStop = defaultTabStop;
        }

        public float getWidth() {
            return width;
        }

        public float getFirstWidth() {
            return firstWidth;
        }

        public int getFirstWidthLineCount() {
            return firstWidthLineCount;
        }

        public float[] getTabStops() {
            return variableTabStops == null ? null : variableTabStops.clone();
        }

        public float getDefaultTabStop() {
            return defaultTabStop;
        }
    }

    public static class Result {
        private final int[] lineBreakOffsets;
        private final float[] lineWidths;
        private final float[] lineAscents;
        private final float[] lineDescents;
        private final boolean[] lineTabs;
        private final int[] startHyphenEdits;
        private final int[] endHyphenEdits;

        Result(
                int[] lineBreakOffsets,
                float[] lineWidths,
                float[] lineAscents,
                float[] lineDescents,
                boolean[] lineTabs,
                int[] startHyphenEdits,
                int[] endHyphenEdits) {
            this.lineBreakOffsets = lineBreakOffsets;
            this.lineWidths = lineWidths;
            this.lineAscents = lineAscents;
            this.lineDescents = lineDescents;
            this.lineTabs = lineTabs;
            this.startHyphenEdits = startHyphenEdits;
            this.endHyphenEdits = endHyphenEdits;
        }

        public int getLineCount() {
            return lineBreakOffsets.length;
        }

        public int getLineBreakOffset(int lineIndex) {
            return lineBreakOffsets[lineIndex];
        }

        public float getLineWidth(int lineIndex) {
            return lineWidths[lineIndex];
        }

        public float getLineAscent(int lineIndex) {
            return lineAscents[lineIndex];
        }

        public float getLineDescent(int lineIndex) {
            return lineDescents[lineIndex];
        }

        public boolean hasLineTab(int lineIndex) {
            return lineTabs[lineIndex];
        }

        public int getStartLineHyphenEdit(int lineIndex) {
            return startHyphenEdits[lineIndex];
        }

        public int getEndLineHyphenEdit(int lineIndex) {
            return endHyphenEdits[lineIndex];
        }
    }
}
