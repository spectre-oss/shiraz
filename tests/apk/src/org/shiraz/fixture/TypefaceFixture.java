// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
import java.util.Arrays;
final class TypefaceFixture {
    static int[] image(Paint paint) {
        Bitmap bitmap = Bitmap.createBitmap(160, 64, Bitmap.Config.ARGB_8888);
        try {
            new Canvas(bitmap).drawText("Agim", 8, 48, paint);
            int[] pixels = new int[160 * 64]; bitmap.getPixels(pixels, 0, 160, 0, 0, 160, 64); return pixels;
        } finally { bitmap.recycle(); }
    }
    static void check() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(32);
        if (paint.getTypeface() != null || paint.setTypeface(Typeface.MONOSPACE) != null) throw new AssertionError("Initial typeface state");
        int[][] images = new int[4][];
        for (int style = 0; style < 4; ++style) {
            Typeface face = Typeface.create(Typeface.MONOSPACE, style);
            if (face.getStyle() != style || face.isBold() != ((style & 1) != 0) || face.isItalic() != ((style & 2) != 0))
                throw new AssertionError("Typeface style metadata");
            paint.setTypeface(face);
            if (paint.measureText("iiii") != paint.measureText("WWWW")) throw new AssertionError("Monospace font selection");
            images[style] = image(paint);
            Paint copy = new Paint(paint);
            if (copy.getTypeface() != face || !Arrays.equals(images[style], image(copy))) throw new AssertionError("Copied typeface drawing");
        }
        for (int first = 0; first < 4; ++first) for (int second = first + 1; second < 4; ++second)
            if (Arrays.equals(images[first], images[second])) throw new AssertionError("Font style did not change rendered glyphs");
        if (!Typeface.create("monospace", 1).equals(Typeface.create(Typeface.MONOSPACE, 1))
                || Typeface.create("monospace", 1).hashCode() != Typeface.create(Typeface.MONOSPACE, 1).hashCode()) throw new AssertionError("Typeface identity");
        Typeface old = paint.getTypeface(); if (paint.setTypeface(null) != old || paint.getTypeface() != null) throw new AssertionError("Reset to default typeface");
        if (!Arrays.equals(image(paint), image(new Paint(paint)))) throw new AssertionError("Null default typeface");
        paint.setTypeface(Typeface.DEFAULT_BOLD); paint.reset(); if (paint.getTypeface() != null) throw new AssertionError("Paint reset retained typeface");
        if (Typeface.defaultFromStyle(Typeface.BOLD).getStyle() != Typeface.BOLD || Typeface.create((String)null, 99).getStyle() != Typeface.NORMAL)
            throw new AssertionError("Typeface default style normalization");
        System.out.println("SHIRAZ_FIXTURE_TYPEFACE_OK");
    }
}
