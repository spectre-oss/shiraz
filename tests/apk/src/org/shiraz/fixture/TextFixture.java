// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;
final class TextFixture {
    static void check() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(20); paint.setColor(0x80ffffff);
        if (paint.measureText("café") <= 0 || paint.measureText("") != 0) throw new AssertionError("Text advance");
        Paint.FontMetrics metrics = paint.getFontMetrics();
        // Glyph bounds and recommended line metrics are independent font values.
        if (!Float.isFinite(metrics.top) || !Float.isFinite(metrics.ascent) || !Float.isFinite(metrics.descent)
                || !Float.isFinite(metrics.bottom) || !Float.isFinite(metrics.leading) || metrics.top >= metrics.bottom
                || metrics.ascent >= 0 || metrics.descent < 0 || paint.getFontMetricsInt().descent < 0)
            throw new AssertionError("Finite font bounds and recommended baseline metrics");
        Bitmap first = Bitmap.createBitmap(128, 48, Bitmap.Config.ARGB_8888), second = Bitmap.createBitmap(128, 48, Bitmap.Config.ARGB_8888);
        try {
            Canvas a = new Canvas(first), b = new Canvas(second);
            a.drawText("café", 0, 32, paint);
            paint.setTextAlign(Paint.Align.RIGHT); b.drawText("café", paint.measureText("café"), 32, paint);
            int ink = 0;
            for (int y = 0; y < 48; ++y) for (int x = 0; x < 128; ++x) {
                int pixel = first.getPixel(x, y);
                if (pixel != second.getPixel(x, y)) throw new AssertionError("Text alignment/alpha");
                if (pixel != 0) ++ink;
            }
            if (ink == 0) throw new AssertionError("No rasterized glyph pixels");
            first.eraseColor(0); paint.setTextAlign(Paint.Align.LEFT); a.drawText("O", 2, 32, paint);
            for (int y = 0; y < 48; ++y) for (int x = 0; x < 128; ++x)
                if ((first.getPixel(x, y) >>> 24) > 128) throw new AssertionError("Single glyph alpha overflow");
            a.drawText("clipped", -10, 5, paint);
            int[] beforeInvalid=new int[128*48],afterInvalid=new int[128*48];first.getPixels(beforeInvalid,0,128,0,0,128,48);
            for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}){a.drawText("bad",invalid,20,paint);a.drawText("bad",2,invalid,paint);}
            first.getPixels(afterInvalid,0,128,0,0,128,48);if(!java.util.Arrays.equals(beforeInvalid,afterInvalid))throw new AssertionError("Nonfinite text position changed pixels");
            paint.setTextSize(0);if(paint.getTextSize()!=0||paint.measureText("café")!=0)throw new AssertionError("Zero text size/advance");
            Paint.FontMetrics zero=paint.getFontMetrics();Paint.FontMetricsInt zeroInt=paint.getFontMetricsInt();
            if(zero.top!=0||zero.ascent!=0||zero.descent!=0||zero.bottom!=0||zero.leading!=0||zeroInt.top!=0||zeroInt.ascent!=0||zeroInt.descent!=0||zeroInt.bottom!=0||zeroInt.leading!=0)throw new AssertionError("Zero text metrics");
            first.eraseColor(0xff123456);a.drawText("invisible",2,32,paint);for(int y=0;y<48;y++)for(int x=0;x<128;x++)if(first.getPixel(x,y)!=0xff123456)throw new AssertionError("Zero-size text changed pixels");
            for(float size:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY,513}){try{paint.setTextSize(size);throw new AssertionError("Invalid size accepted");}catch(IllegalArgumentException expected){}}
            if(paint.getTextSize()!=0)throw new AssertionError("Failed size change altered paint");paint.setTextSize(20);if(paint.measureText("café")<=0)throw new AssertionError("Zero size poisoned cached font");
        } finally { first.recycle(); second.recycle(); }
        System.out.println("SHIRAZ_FIXTURE_TEXT_OK");
    }
}
