// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.ImageView;
import java.util.Random;

public final class ColorFilterFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    // Floating-point compositing reference, independent of the renderer's integer products.
    private static int reference(int source,int destination,PorterDuff.Mode mode) {
        double sa=(source >>> 24)/255.0, da=(destination >>> 24)/255.0, fs=0,fd=0,alpha;
        boolean linear=true;
        switch (mode) {
            case CLEAR: break;
            case SRC: fs=1; break;
            case DST: fd=1; break;
            case SRC_OVER: fs=1; fd=1-sa; break;
            case DST_OVER: fs=1-da; fd=1; break;
            case SRC_IN: fs=da; break;
            case DST_IN: fd=sa; break;
            case SRC_OUT: fs=1-da; break;
            case DST_OUT: fd=1-sa; break;
            case SRC_ATOP: fs=da; fd=1-sa; break;
            case DST_ATOP: fs=1-da; fd=sa; break;
            case XOR: fs=1-da; fd=1-sa; break;
            default: linear=false;
        }
        alpha=linear ? sa*fs+da*fd : mode == PorterDuff.Mode.MULTIPLY ? sa*da : mode == PorterDuff.Mode.ADD ? Math.min(1,sa+da) : sa+da-sa*da;
        int a=(int)Math.round(alpha*255); if (a == 0) return 0;
        int result=a << 24;
        for (int shift=0;shift<=16;shift+=8) {
            double s=((source >>> shift)&255)/255.0,d=((destination >>> shift)&255)/255.0,c;
            if (linear) c=s*sa*fs+d*da*fd;
            else if (mode == PorterDuff.Mode.MULTIPLY) c=s*sa*d*da;
            else if (mode == PorterDuff.Mode.ADD) c=Math.min(1,s*sa+d*da);
            else {
                double blend=mode == PorterDuff.Mode.DARKEN ? Math.min(s,d) : mode == PorterDuff.Mode.LIGHTEN ? Math.max(s,d) : mode == PorterDuff.Mode.SCREEN ? s+d-s*d : d <= .5 ? 2*s*d : 1-2*(1-s)*(1-d);
                c=s*sa*(1-da)+d*da*(1-sa)+sa*da*blend;
            }
            result |= Math.min(255,(int)Math.round(c/alpha*255)) << shift;
        }
        return result;
    }
    private static void near(int actual,int expected,String message) {
        for (int shift=0;shift<=24;shift+=8) if (Math.abs(((actual >>> shift)&255)-((expected >>> shift)&255)) > 1)
            throw new AssertionError(message+": actual="+Integer.toHexString(actual)+" expected="+Integer.toHexString(expected));
    }
    public static void check() {
        Bitmap source=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888),target=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(target); Paint paint=new Paint(); Random random=new Random(54);
        int[] corners={0,0x00abcdef,0xff000000,0xffffffff,0x80ff0000,0x4000ff00,0x01010203,0xfe010203};
        for (PorterDuff.Mode mode:PorterDuff.Mode.values()) for (int n=0;n<64;n++) {
            int s=n < corners.length ? corners[n] : random.nextInt(), d=n < corners.length ? corners[corners.length-1-n] : random.nextInt();
            PorterDuffColorFilter filter=new PorterDuffColorFilter(s,mode); int expected=reference(s,d,mode);
            paint.setColor(d); paint.setColorFilter(filter); target.eraseColor(0); canvas.drawRect(0,0,1,1,paint);
            near(target.getPixel(0,0),expected,"solid filter "+mode);
            int paintAlpha=new int[]{0,1,127,128,254,255}[n%6];
            source.setPixel(0,0,d); paint.setAlpha(paintAlpha); target.eraseColor(0); canvas.drawBitmap(source,0,0,paint);
            int attenuated=(d & 0xffffff) | ((((d >>> 24)*paintAlpha+127)/255) << 24);
            near(target.getPixel(0,0),reference(s,attenuated,mode),"bitmap filter with paint alpha "+mode);
            ColorDrawable drawable=new ColorDrawable(d); drawable.setColorFilter(filter); drawable.setBounds(0,0,1,1); target.eraseColor(0); drawable.draw(canvas);
            near(target.getPixel(0,0),expected,"drawable filter "+mode);
        }
        PorterDuffColorFilter red=new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.SRC_IN),equal=new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.SRC_IN);
        check(red.equals(equal) && red.hashCode() == equal.hashCode() && !red.equals(new PorterDuffColorFilter(Color.BLUE,PorterDuff.Mode.SRC_IN)) && !red.equals(null),"filter value equality");
        try { new PorterDuffColorFilter(0,null); throw new AssertionError("null mode accepted"); } catch (NullPointerException expected) { }
        paint.setColorFilter(red); Paint copied=new Paint(paint); paint.reset(); check(paint.getColorFilter() == null && copied.getColorFilter() == red,"paint filter copy/reset");
        try { copied.setColorFilter(new ColorFilter()); throw new AssertionError("unknown filter silently accepted"); } catch (UnsupportedOperationException expected) { }
        check(copied.getColorFilter() == red,"failed filter set preserves old state");
        source.setPixel(0,0,0x800000ff); paint.setAlpha(0); paint.setColorFilter(new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.SRC)); target.eraseColor(0); canvas.drawBitmap(source,0,0,paint);
        check(target.getPixel(0,0) == Color.RED,"filter can create opacity after zero paint alpha");
        paint.setAlpha(128); paint.setColorFilter(red); target.eraseColor(0); canvas.drawBitmap(source,0,0,paint); check(target.getPixel(0,0) == 0x40ff0000,"paint alpha modulates bitmap before tint");
        target.eraseColor(Color.BLUE); canvas.drawBitmap(source,0,0,paint); check(target.getPixel(0,0) == 0xff4000bf,"filtered pixel source-over destination");
        BitmapDrawable drawable=new BitmapDrawable(source); drawable.setBounds(0,0,1,1); drawable.setColorFilter(red); check(drawable.getColorFilter() == red,"bitmap filter getter");
        drawable.clearColorFilter(); target.eraseColor(0); drawable.draw(canvas); check(drawable.getColorFilter() == null && target.getPixel(0,0) == 0x800000ff,"clear restores bitmap colors");
        drawable.setAlpha(0); drawable.setColorFilter(Color.RED,PorterDuff.Mode.SRC); target.eraseColor(0); drawable.draw(canvas);
        check(drawable.getOpacity() != PixelFormat.TRANSPARENT && target.getPixel(0,0) == Color.RED,"drawable opacity accounts for filter-generated alpha");
        Bitmap gradient=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888),scaled=Bitmap.createBitmap(4,1,Bitmap.Config.ARGB_8888);
        gradient.setPixel(0,0,0x00ffffff); gradient.setPixel(1,0,Color.BLUE); paint.setAlpha(255); paint.setFilterBitmap(true); paint.setColorFilter(red);
        new Canvas(scaled).drawBitmap(gradient,null,new Rect(0,0,4,1),paint);
        check(scaled.getPixel(0,0) == 0 && scaled.getPixel(1,0) == 0x40ff0000 && scaled.getPixel(2,0) == 0xbfff0000 && scaled.getPixel(3,0) == Color.RED,"bilinear premultiplied sampling precedes icon tint");
        paint.setFilterBitmap(false); paint.setColorFilter(new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.MULTIPLY)); gradient.setPixel(0,0,Color.WHITE); gradient.setPixel(1,0,Color.WHITE);
        new Canvas(gradient).drawBitmap(gradient,null,new Rect(0,0,2,1),paint); check(gradient.getPixel(0,0) == Color.RED && gradient.getPixel(1,0) == Color.RED,"filtered overlapping source snapshot");
        Bitmap whiteText=Bitmap.createBitmap(48,24,Bitmap.Config.ARGB_8888),redText=Bitmap.createBitmap(48,24,Bitmap.Config.ARGB_8888);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG); text.setColor(Color.WHITE); text.setTextSize(18); new Canvas(whiteText).drawText("Map",0,20,text);
        text.setColor(0); text.setColorFilter(new PorterDuffColorFilter(Color.RED,PorterDuff.Mode.SRC)); new Canvas(redText).drawText("Map",0,20,text); int ink=0;
        for (int y=0;y<24;y++) for (int x=0;x<48;x++) { int alpha=whiteText.getPixel(x,y) >>> 24, expected=alpha == 0 ? 0 : alpha << 24 | 0xff0000; check(redText.getPixel(x,y) == expected,"text filter precedes glyph coverage"); if (alpha != 0) ink++; }
        check(ink > 0,"tinted glyph output");
        for (int n=0;n<20;n++) {
            Bitmap tile=Bitmap.createBitmap(5,4,Bitmap.Config.ARGB_8888),plain=Bitmap.createBitmap(11,9,Bitmap.Config.ARGB_8888),tinted=Bitmap.createBitmap(11,9,Bitmap.Config.ARGB_8888);
            for (int y=0;y<4;y++) for (int x=0;x<5;x++) tile.setPixel(x,y,random.nextInt());
            Paint sampler=new Paint(Paint.FILTER_BITMAP_FLAG); new Canvas(plain).drawBitmap(tile,null,new Rect(0,0,11,9),sampler);
            sampler.setAlpha(128); sampler.setColorFilter(red); new Canvas(tinted).drawBitmap(tile,null,new Rect(0,0,11,9),sampler);
            for (int y=0;y<9;y++) for (int x=0;x<11;x++) {
                int alpha=((plain.getPixel(x,y) >>> 24)*128+127)/255, expected=alpha == 0 ? 0 : alpha << 24 | 0xff0000;
                check(tinted.getPixel(x,y) == expected,"alpha-only icon tint matches general bilinear sampler");
            }
            tile.recycle(); plain.recycle(); tinted.recycle();
        }
        final int[] invalidations={0}; ColorDrawable observed=new ColorDrawable(Color.BLUE);
        Drawable.Callback owner=new Drawable.Callback() {
            public void invalidateDrawable(Drawable value) { invalidations[0]++; }
            public void scheduleDrawable(Drawable value,Runnable task,long when) { }
            public void unscheduleDrawable(Drawable value,Runnable task) { }
        };
        observed.setCallback(owner); observed.setColorFilter(red); observed.setColorFilter(red);
        check(invalidations[0] == 1 && observed.getColorFilter() == red,"changed filter invalidates once");
        try { observed.setColorFilter(new ColorFilter()); throw new AssertionError("unknown drawable filter accepted"); } catch (UnsupportedOperationException expected) { }
        check(observed.getColorFilter() == red && invalidations[0] == 1,"failed drawable filter leaves state and invalidation intact");
        observed.clearColorFilter(); check(invalidations[0] == 2 && observed.getColorFilter() == null && owner != null,"clear filter invalidates owner");
        System.out.println("SHIRAZ_FIXTURE_COLOR_FILTERS_OK");
    }
    public static void checkView(Context context) {
        ImageView image=new ImageView(context); ColorDrawable blue=new ColorDrawable(Color.BLUE); image.setImageDrawable(blue);
        image.setColorFilter(Color.RED,PorterDuff.Mode.SRC_IN); ColorFilter filter=image.getColorFilter();
        check(blue.getColorFilter() == filter,"image applies filter"); ColorDrawable replacement=new ColorDrawable(Color.GREEN); image.setImageDrawable(replacement);
        check(replacement.getColorFilter() == filter,"image retains filter on replacement"); image.clearColorFilter(); check(image.getColorFilter() == null && replacement.getColorFilter() == null,"image clears filter");
        image.setColorFilter(Color.BLUE); check(image.getColorFilter().equals(new PorterDuffColorFilter(Color.BLUE,PorterDuff.Mode.SRC_ATOP)),"image default filter mode");
        System.out.println("SHIRAZ_FIXTURE_COLOR_FILTER_VIEW_OK");
    }
}
