// SPDX-License-Identifier: GPL-3.0-only
package android.text;

import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextRunShaper;
import android.text.style.CharacterStyle;
import android.text.style.MetricAffectingSpan;

/** Shapes styled multi-run bidirectional text and delivers PositionedGlyphs to a GlyphsConsumer. */
public class TextShaper {
    public interface GlyphsConsumer {
        void accept(int start, int count, PositionedGlyphs glyphs, TextPaint paint);
    }

    private TextShaper() {
    }

    public static void shapeText(
            CharSequence text,
            int start,
            int count,
            TextDirectionHeuristic dir,
            TextPaint paint,
            GlyphsConsumer consumer) {
        if (text == null || dir == null || paint == null || consumer == null) {
            throw new NullPointerException();
        }
        int end = start + count;
        if (start < 0 || count < 0 || end > text.length()) {
            throw new IndexOutOfBoundsException(
                    "Invalid range: start=" + start + ", count=" + count + ", len=" + text.length());
        }
        if (count == 0) {
            return;
        }

        String slice = text.subSequence(start, end).toString();
        boolean paraRtl = dir.isRtl(text, start, count);
        java.text.Bidi bidi = new java.text.Bidi(
                slice,
                paraRtl ? java.text.Bidi.DIRECTION_RIGHT_TO_LEFT
                        : java.text.Bidi.DIRECTION_LEFT_TO_RIGHT);

        Spanned spanned = text instanceof Spanned ? (Spanned) text : null;
        int runCount = Math.max(1, bidi.getRunCount());

        int[] visualOrder = new int[runCount];
        byte[] levels = new byte[runCount];
        Object[] runIndices = new Object[runCount];
        for (int i = 0; i < runCount; i++) {
            levels[i] = bidi.getRunCount() > 0 ? (byte) bidi.getRunLevel(i) : (byte) (paraRtl ? 1 : 0);
            runIndices[i] = Integer.valueOf(i);
        }
        java.text.Bidi.reorderVisually(levels, 0, runIndices, 0, runCount);
        for (int i = 0; i < runCount; i++) {
            visualOrder[i] = ((Integer) runIndices[i]).intValue();
        }

        float xOffset = 0f;
        for (int v = 0; v < runCount; v++) {
            int r = visualOrder[v];
            int relStart = bidi.getRunCount() > 0 ? bidi.getRunStart(r) : 0;
            int relEnd = bidi.getRunCount() > 0 ? bidi.getRunLimit(r) : count;
            boolean isRtl = (levels[r] & 1) != 0;

            int absRunStart = start + relStart;
            int absRunEnd = start + relEnd;

            if (!isRtl) {
                int cur = absRunStart;
                while (cur < absRunEnd) {
                    int next = spanned != null
                            ? spanned.nextSpanTransition(cur, absRunEnd, CharacterStyle.class)
                            : absRunEnd;
                    xOffset = emitStyleRun(
                            text, start, count, cur, next - cur, xOffset, false, paint, spanned, consumer);
                    cur = next;
                }
            } else {
                java.util.ArrayList<int[]> subRuns = new java.util.ArrayList<int[]>();
                int cur = absRunStart;
                while (cur < absRunEnd) {
                    int next = spanned != null
                            ? spanned.nextSpanTransition(cur, absRunEnd, CharacterStyle.class)
                            : absRunEnd;
                    subRuns.add(new int[] { cur, next - cur });
                    cur = next;
                }
                for (int s = subRuns.size() - 1; s >= 0; s--) {
                    int[] sr = subRuns.get(s);
                    xOffset = emitStyleRun(
                            text, start, count, sr[0], sr[1], xOffset, true, paint, spanned, consumer);
                }
            }
        }
    }

    private static float emitStyleRun(
            CharSequence text,
            int contextStart,
            int contextCount,
            int runStart,
            int runCount,
            float xOffset,
            boolean isRtl,
            TextPaint basePaint,
            Spanned spanned,
            GlyphsConsumer consumer) {
        TextPaint workPaint = new TextPaint(basePaint);
        if (spanned != null) {
            CharacterStyle[] spans =
                    spanned.getSpans(runStart, runStart + runCount, CharacterStyle.class);
            for (int i = 0; i < spans.length; i++) {
                if (spans[i] instanceof MetricAffectingSpan) {
                    ((MetricAffectingSpan) spans[i]).updateMeasureState(workPaint);
                }
                spans[i].updateDrawState(workPaint);
            }
        }
        PositionedGlyphs glyphs = TextRunShaper.shapeTextRun(
                text, runStart, runCount, contextStart, contextCount, xOffset, 0f, isRtl, workPaint);
        consumer.accept(runStart, runCount, glyphs, workPaint);
        return xOffset + glyphs.getAdvance();
    }
}
