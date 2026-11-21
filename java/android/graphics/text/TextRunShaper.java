// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.text;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.SystemFonts;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class TextRunShaper {
    private TextRunShaper() {
    }

    public static PositionedGlyphs shapeTextRun(
            char[] text,
            int start,
            int count,
            int contextStart,
            int contextCount,
            float xOffset,
            float yOffset,
            boolean isRtl,
            Paint paint) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        if (paint == null) {
            throw new NullPointerException("paint");
        }
        if ((start
                        | count
                        | contextStart
                        | contextCount
                        | (start - contextStart)
                        | (contextCount - count)
                        | ((contextStart + contextCount) - (start + count))
                        | (text.length - (contextStart + contextCount)))
                < 0) {
            throw new IndexOutOfBoundsException(
                    "Invalid range: start="
                            + start
                            + ", count="
                            + count
                            + ", contextStart="
                            + contextStart
                            + ", contextCount="
                            + contextCount
                            + ", len="
                            + text.length);
        }

        Paint.FontMetrics fm = paint.getFontMetrics();
        if (count == 0) {
            return new PositionedGlyphs(
                    0f,
                    fm.ascent,
                    fm.descent,
                    xOffset,
                    yOffset,
                    new int[0],
                    new float[0],
                    new float[0],
                    new Font[0],
                    new boolean[0],
                    new boolean[0],
                    new float[0],
                    new float[0]);
        }

        float[] charAdvances = new float[count];
        float totalAdvance =
                paint.getTextRunAdvances(
                        text, start, count, contextStart, contextCount, isRtl, charAdvances, 0);

        ArrayList<Integer> codePoints = new ArrayList<Integer>();
        ArrayList<Float> clusterWidths = new ArrayList<Float>();
        int idx = 0;
        while (idx < count) {
            char c = text[start + idx];
            int cp;
            float w = charAdvances[idx];
            if (Character.isHighSurrogate(c)
                    && idx + 1 < count
                    && Character.isLowSurrogate(text[start + idx + 1])) {
                cp = Character.toCodePoint(c, text[start + idx + 1]);
                w += charAdvances[idx + 1];
                idx += 2;
            } else {
                cp = c;
                idx++;
            }
            codePoints.add(Integer.valueOf(cp));
            clusterWidths.add(Float.valueOf(w));
        }

        int glyphCount = codePoints.size();
        int[] glyphIds = new int[glyphCount];
        float[] glyphX = new float[glyphCount];
        float[] glyphY = new float[glyphCount];
        Font[] fonts = new Font[glyphCount];
        boolean[] fakeBold = new boolean[glyphCount];
        boolean[] fakeItalic = new boolean[glyphCount];
        float[] weightOverrides = new float[glyphCount];
        float[] italicOverrides = new float[glyphCount];

        Font resolvedFont = resolveFontForPaint(paint);
        boolean isFakeBold = paint.isFakeBoldText();
        boolean isFakeItalic = paint.getTextSkewX() != 0f;

        float cursorX = xOffset;
        for (int i = 0; i < glyphCount; i++) {
            int srcIdx = isRtl ? (glyphCount - 1 - i) : i;
            glyphIds[i] = codePoints.get(srcIdx).intValue();
            glyphX[i] = cursorX;
            glyphY[i] = yOffset;
            fonts[i] = resolvedFont;
            fakeBold[i] = isFakeBold;
            fakeItalic[i] = isFakeItalic;
            weightOverrides[i] = PositionedGlyphs.NO_OVERRIDE;
            italicOverrides[i] = PositionedGlyphs.NO_OVERRIDE;
            cursorX += clusterWidths.get(srcIdx).floatValue();
        }

        return new PositionedGlyphs(
                totalAdvance,
                fm.ascent,
                fm.descent,
                xOffset,
                yOffset,
                glyphIds,
                glyphX,
                glyphY,
                fonts,
                fakeBold,
                fakeItalic,
                weightOverrides,
                italicOverrides);
    }

    public static PositionedGlyphs shapeTextRun(
            CharSequence text,
            int start,
            int count,
            int contextStart,
            int contextCount,
            float xOffset,
            float yOffset,
            boolean isRtl,
            Paint paint) {
        if (text == null) {
            throw new NullPointerException("text");
        }
        if (paint == null) {
            throw new NullPointerException("paint");
        }
        if ((start
                        | count
                        | contextStart
                        | contextCount
                        | (start - contextStart)
                        | (contextCount - count)
                        | ((contextStart + contextCount) - (start + count))
                        | (text.length() - (contextStart + contextCount)))
                < 0) {
            throw new IndexOutOfBoundsException(
                    "Invalid range: start="
                            + start
                            + ", count="
                            + count
                            + ", contextStart="
                            + contextStart
                            + ", contextCount="
                            + contextCount
                            + ", len="
                            + text.length());
        }
        char[] buf = new char[contextCount];
        for (int i = 0; i < contextCount; i++) {
            buf[i] = text.charAt(contextStart + i);
        }
        return shapeTextRun(
                buf, start - contextStart, count, 0, contextCount, xOffset, yOffset, isRtl, paint);
    }

    private static Font resolveFontForPaint(Paint paint) {
        Set<Font> available = SystemFonts.getAvailableFonts();
        if (available.isEmpty()) {
            return null;
        }
        Typeface tf = paint.getTypeface();
        int desiredWeight = (tf != null && tf.isBold()) ? 700 : 400;
        int desiredSlant =
                (tf != null && tf.isItalic())
                        ? FontStyle.FONT_SLANT_ITALIC
                        : FontStyle.FONT_SLANT_UPRIGHT;
        Font best = null;
        int bestScore = Integer.MAX_VALUE;
        Iterator<Font> it = available.iterator();
        while (it.hasNext()) {
            Font candidate = it.next();
            FontStyle st = candidate.getStyle();
            int score =
                    Math.abs(st.getWeight() - desiredWeight)
                            + (st.getSlant() == desiredSlant ? 0 : 200);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
