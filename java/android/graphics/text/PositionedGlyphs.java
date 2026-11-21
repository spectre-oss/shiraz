// SPDX-License-Identifier: GPL-3.0-only
package android.graphics.text;

import android.graphics.fonts.Font;
import java.util.Arrays;
import java.util.Objects;

public final class PositionedGlyphs {
    public static final float NO_OVERRIDE = Float.MIN_VALUE;

    private final float advance;
    private final float ascent;
    private final float descent;
    private final float offsetX;
    private final float offsetY;
    private final int[] glyphIds;
    private final float[] glyphX;
    private final float[] glyphY;
    private final Font[] fonts;
    private final boolean[] fakeBold;
    private final boolean[] fakeItalic;
    private final float[] weightOverrides;
    private final float[] italicOverrides;

    PositionedGlyphs(
            float advance,
            float ascent,
            float descent,
            float offsetX,
            float offsetY,
            int[] glyphIds,
            float[] glyphX,
            float[] glyphY,
            Font[] fonts,
            boolean[] fakeBold,
            boolean[] fakeItalic,
            float[] weightOverrides,
            float[] italicOverrides) {
        this.advance = advance;
        this.ascent = ascent;
        this.descent = descent;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.glyphIds = glyphIds;
        this.glyphX = glyphX;
        this.glyphY = glyphY;
        this.fonts = fonts;
        this.fakeBold = fakeBold;
        this.fakeItalic = fakeItalic;
        this.weightOverrides = weightOverrides;
        this.italicOverrides = italicOverrides;
    }

    public float getAdvance() {
        return advance;
    }

    public float getAscent() {
        return ascent;
    }

    public float getDescent() {
        return descent;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    public int glyphCount() {
        return glyphIds.length;
    }

    public Font getFont(int index) {
        checkIndex(index);
        return fonts[index];
    }

    public int getGlyphId(int index) {
        checkIndex(index);
        return glyphIds[index];
    }

    public float getGlyphX(int index) {
        checkIndex(index);
        return glyphX[index];
    }

    public float getGlyphY(int index) {
        checkIndex(index);
        return glyphY[index];
    }

    public boolean getFakeBold(int index) {
        checkIndex(index);
        return fakeBold[index];
    }

    public boolean getFakeItalic(int index) {
        checkIndex(index);
        return fakeItalic[index];
    }

    public float getWeightOverride(int index) {
        checkIndex(index);
        return weightOverrides[index];
    }

    public float getItalicOverride(int index) {
        checkIndex(index);
        return italicOverrides[index];
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= glyphIds.length) {
            throw new IndexOutOfBoundsException(
                    "index=" + index + ", count=" + glyphIds.length);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PositionedGlyphs)) {
            return false;
        }
        PositionedGlyphs that = (PositionedGlyphs) o;
        return Float.compare(that.advance, advance) == 0
                && Float.compare(that.ascent, ascent) == 0
                && Float.compare(that.descent, descent) == 0
                && Float.compare(that.offsetX, offsetX) == 0
                && Float.compare(that.offsetY, offsetY) == 0
                && Arrays.equals(glyphIds, that.glyphIds)
                && Arrays.equals(glyphX, that.glyphX)
                && Arrays.equals(glyphY, that.glyphY)
                && Arrays.equals(fonts, that.fonts)
                && Arrays.equals(fakeBold, that.fakeBold)
                && Arrays.equals(fakeItalic, that.fakeItalic)
                && Arrays.equals(weightOverrides, that.weightOverrides)
                && Arrays.equals(italicOverrides, that.italicOverrides);
    }

    @Override
    public int hashCode() {
        int result =
                Objects.hash(
                        Float.valueOf(advance),
                        Float.valueOf(ascent),
                        Float.valueOf(descent),
                        Float.valueOf(offsetX),
                        Float.valueOf(offsetY));
        result = 31 * result + Arrays.hashCode(glyphIds);
        result = 31 * result + Arrays.hashCode(glyphX);
        result = 31 * result + Arrays.hashCode(glyphY);
        result = 31 * result + Arrays.hashCode(fonts);
        return result;
    }

    @Override
    public String toString() {
        return "PositionedGlyphs{glyphCount="
                + glyphIds.length
                + ", advance="
                + advance
                + ", ascent="
                + ascent
                + ", descent="
                + descent
                + ", offset=("
                + offsetX
                + ", "
                + offsetY
                + ")}";
    }
}
