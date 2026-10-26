// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
final class StrokeFixture {
    static void check() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (!paint.isAntiAlias() || paint.getStyle() != Paint.Style.FILL || paint.getStrokeWidth() != 0
                || paint.getStrokeMiter() != 4 || paint.getStrokeJoin() != Paint.Join.MITER || paint.getStrokeCap() != Paint.Cap.BUTT)
            throw new AssertionError("Paint defaults/flags");
        paint.setAntiAlias(false); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(0xff123456);
        Paint copy = new Paint(paint); paint.reset();
        if (copy.getStyle() != Paint.Style.STROKE || copy.getStrokeWidth() != 2 || paint.getStyle() != Paint.Style.FILL)
            throw new AssertionError("Paint copy/reset state");
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.drawRect(2, 2, 8, 8, copy);
        if (bitmap.getPixel(0, 0) != 0 || bitmap.getPixel(1, 1) != 0xff123456 || bitmap.getPixel(2, 2) != 0xff123456
                || bitmap.getPixel(3, 3) != 0 || bitmap.getPixel(7, 7) != 0xff123456 || bitmap.getPixel(8, 8) != 0xff123456
                || bitmap.getPixel(9, 9) != 0) throw new AssertionError("Centered rectangular outline");
        copy.setStyle(Paint.Style.FILL_AND_STROKE); copy.setColor(0x80ff0000); bitmap.eraseColor(0);
        canvas.drawRect(2, 2, 8, 8, copy);
        if (bitmap.getPixel(2, 2) != 0x80ff0000 || bitmap.getPixel(4, 4) != 0x80ff0000) throw new AssertionError("Fill and stroke overlaps blended twice");
        copy.setStyle(Paint.Style.STROKE); copy.setStrokeWidth(4); copy.setColor(0xffffffff); copy.setStrokeJoin(Paint.Join.BEVEL);
        bitmap.eraseColor(0); canvas.drawRect(3, 3, 7, 7, copy);
        if (bitmap.getPixel(1, 1) != 0 || bitmap.getPixel(2, 2) != 0xffffffff) throw new AssertionError("Bevel stroke corner");
        copy.setStrokeJoin(Paint.Join.ROUND); bitmap.eraseColor(0); canvas.drawRect(3, 3, 7, 7, copy);
        if (bitmap.getPixel(1, 1) != 0 || bitmap.getPixel(1, 2) != 0xffffffff) throw new AssertionError("Round stroke corner");
        copy.setStrokeJoin(Paint.Join.MITER); copy.setStrokeMiter(1); bitmap.eraseColor(0); canvas.drawRect(3, 3, 7, 7, copy);
        if (bitmap.getPixel(1, 1) != 0) throw new AssertionError("Miter limit fallback");
        copy.setStyle(Paint.Style.FILL); copy.setAntiAlias(true); bitmap.eraseColor(0); canvas.drawRect(.5f, .5f, 4.5f, 4.5f, copy);
        if (bitmap.getPixel(0, 0) != 0x40ffffff || bitmap.getPixel(1, 1) != 0xffffffff) throw new AssertionError("Rectangle edge coverage");
        copy.setAntiAlias(false); copy.setStyle(Paint.Style.STROKE); copy.setStrokeWidth(0); copy.setStrokeMiter(4);
        bitmap.eraseColor(0); canvas.drawRect(2, 2, 8, 8, copy);
        if (bitmap.getPixel(1, 2) != 0xffffffff || bitmap.getPixel(2, 2) != 0 || bitmap.getPixel(7, 7) != 0xffffffff)
            throw new AssertionError("One device pixel hairline");
        try { copy.setStrokeWidth(Float.NaN); throw new AssertionError("Nonfinite stroke accepted"); } catch (IllegalArgumentException expected) { }
        try { copy.setStrokeMiter(-1); throw new AssertionError("Negative miter accepted"); } catch (IllegalArgumentException expected) { }
        copy.setStrokeCap(Paint.Cap.ROUND); if (copy.getStrokeCap() != Paint.Cap.ROUND) throw new AssertionError("Stroke cap state");
        bitmap.recycle(); System.out.println("SHIRAZ_FIXTURE_STROKE_OK");
    }
}
