// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.graphics.*;

/** Stock-SDK bitmap drawing and map-coordinate value checks. */
public final class BitmapFixture {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void pixel(Bitmap value, int x, int color) { if (value.getPixel(x, 0) != color) throw new AssertionError("Bitmap pixel " + x + ": " + Integer.toHexString(value.getPixel(x, 0)) + " expected " + Integer.toHexString(color)); }
    public static void check() {
        PointF point = new PointF(3,4), copy = new PointF(point); copy.negate(); copy.offset(1,2);
        check(point.length() == 5 && copy.equals(-2,-2) && point.equals(3,4), "map point copy and geometry");
        check(Float.isFinite(PointF.length(Float.MAX_VALUE / 4, Float.MAX_VALUE / 4)), "point distance avoids intermediate float overflow");
        check(!new PointF(0,0).equals(new PointF(-0f,-0f)) && new PointF(0,0).equals(-0f,-0f), "point object/coordinate signed-zero semantics");
        check(new PointF(Float.NaN,0).equals(new PointF(Float.NaN,0)) && !new PointF(Float.NaN,0).equals(Float.NaN,0), "point NaN semantics");
        Rect bounds = new Rect(1,2,5,8), previous = new Rect(bounds);
        check(bounds.contains(1,2) && !bounds.contains(5,8) && bounds.width() == 4 && bounds.height() == 6, "half-open map bounds");
        check(!bounds.intersect(20,20,30,30) && bounds.equals(previous), "failed intersection retains viewport");
        check(bounds.intersect(3,4,10,10) && bounds.equals(new Rect(3,4,5,8)), "viewport intersection");
        check(bounds.equals(Rect.unflattenFromString(bounds.flattenToString())) && Rect.unflattenFromString("invalid") == null, "rectangle persistence text");
        RectF fractional = new RectF(-.2f,1.1f,3.8f,5.2f); fractional.roundOut(bounds);
        check(bounds.equals(new Rect(-1,1,4,6)), "outward pixel bounds"); fractional.round(bounds); check(bounds.equals(new Rect(0,1,4,5)), "nearest pixel bounds");
        Bitmap source = Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888), target = Bitmap.createBitmap(4,1,Bitmap.Config.ARGB_8888);
        source.setPixel(0,0,Color.RED); source.setPixel(1,0,Color.BLUE);
        Canvas canvas = new Canvas(target); Paint paint = new Paint();
        canvas.drawBitmap(source,null,new Rect(0,0,4,1),paint);
        pixel(target,0,Color.RED); pixel(target,1,Color.RED); pixel(target,2,Color.BLUE); pixel(target,3,Color.BLUE);
        paint.setFilterBitmap(true); check(paint.isFilterBitmap() && (paint.getFlags() & Paint.FILTER_BITMAP_FLAG) != 0, "bitmap filter flag");
        Paint filtered = new Paint(paint); paint.reset(); check(filtered.isFilterBitmap() && !paint.isFilterBitmap(), "filter copy and reset");
        target.eraseColor(0); canvas.drawBitmap(source,null,new RectF(0,0,4,1),filtered);
        pixel(target,0,Color.RED); pixel(target,1,0xffbf0040); pixel(target,2,0xff4000bf); pixel(target,3,Color.BLUE);
        target.eraseColor(0); canvas.drawBitmap(source,new Rect(1,0,2,1),new Rect(0,0,4,1),filtered);
        for (int x = 0; x < 4; ++x) pixel(target,x,Color.BLUE); // Strict source crop: no adjacent tile bleed.
        target.eraseColor(0); canvas.drawBitmap(source,null,new RectF(-2,0,2,1),null);
        pixel(target,0,Color.BLUE); pixel(target,1,Color.BLUE); pixel(target,2,0); pixel(target,3,0);
        target.eraseColor(0); canvas.drawBitmap(source,1f,0f,null);
        pixel(target,0,0); pixel(target,1,Color.RED); pixel(target,2,Color.BLUE); pixel(target,3,0);
        source.setPixel(0,0,0x00ff0000); target.eraseColor(0); canvas.drawBitmap(source,null,new Rect(0,0,4,1),filtered);
        pixel(target,0,0); pixel(target,1,0x400000ff); pixel(target,2,0xbf0000ff); pixel(target,3,Color.BLUE);
        source.eraseColor(Color.RED); target.eraseColor(Color.BLUE); paint.setAlpha(128);
        canvas.drawBitmap(source,null,new Rect(0,0,4,1),paint); for (int x = 0; x < 4; ++x) pixel(target,x,0xff80007f);
        paint.setAlpha(0); canvas.drawBitmap(source,null,new Rect(0,0,4,1),paint); pixel(target,0,0xff80007f);
        target.setPixel(0,0,Color.RED); target.setPixel(1,0,Color.GREEN); target.setPixel(2,0,Color.BLUE); target.setPixel(3,0,Color.WHITE);
        canvas.drawBitmap(target,new Rect(0,0,3,1),new Rect(1,0,4,1),null);
        pixel(target,0,Color.RED); pixel(target,1,Color.RED); pixel(target,2,Color.GREEN); pixel(target,3,Color.BLUE);
        target.setPixel(0,0,Color.RED); target.setPixel(1,0,Color.BLUE);
        canvas.drawBitmap(target,new Rect(0,0,2,1),new Rect(0,0,4,1),filtered);
        pixel(target,0,Color.RED); pixel(target,1,0xffbf0040); pixel(target,2,0xff4000bf); pixel(target,3,Color.BLUE);
        Bitmap grid = Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888), scaled = Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888);
        grid.setPixel(0,0,Color.RED); grid.setPixel(1,0,Color.GREEN); grid.setPixel(0,1,Color.BLUE); grid.setPixel(1,1,Color.WHITE);
        new Canvas(scaled).drawBitmap(grid,null,new Rect(0,0,4,4),null);
        check(scaled.getPixel(0,0) == Color.RED && scaled.getPixel(3,0) == Color.GREEN && scaled.getPixel(0,3) == Color.BLUE && scaled.getPixel(3,3) == Color.WHITE, "two-dimensional sampling");
        grid.recycle(); scaled.recycle(); source.recycle(); target.recycle();
        System.out.println("SHIRAZ_FIXTURE_BITMAPS_OK");
    }
}
