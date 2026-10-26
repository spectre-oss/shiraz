// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.NativeBitmap;
import android.graphics.*;
import org.shiraz.fixture.BitmapFixture;
public final class BitmapSmoke {
    private static void rejected(Runnable action) { try { action.run(); throw new AssertionError("invalid bitmap request accepted"); } catch (IllegalArgumentException expected) { } }
    private static int blend(int src,int dst){int a=src>>>24,rest=((dst>>>24)*(255-a)+127)/255,total=a+rest;if(total==0)return 0;int out=total<<24;for(int shift=0;shift<=16;shift+=8)out|=((((src>>>shift)&255)*a+((dst>>>shift)&255)*rest+total/2)/total)<<shift;return out;}
    private static void fills(){
        java.util.Random random=new java.util.Random(726);
        for(int pass=0;pass<612;pass++){
            int w=13,h=11,color=random.nextInt();boolean aa=pass%2==0;double l=random.nextDouble()*15-3,t=random.nextDouble()*12-2,r=l+random.nextDouble()*15,b=t+random.nextDouble()*12;
            // Exercise every source alpha over opaque destinations, and opaque
            // replacement over arbitrary alpha, with fractional clipped edges.
            if(pass>=100)color=(color&0xffffff)|((pass<356?pass-100:255)<<24);
            int[] pixels=new int[w*h];for(int i=0;i<pixels.length;i++){pixels[i]=random.nextInt();if(pass>=100&&pass<356)pixels[i]|=0xff000000;}int[] expected=pixels.clone();
            for(int y=1;y<9;y++)for(int x=2;x<12;x++){int hits=0,grid=aa?4:1;for(int sy=0;sy<grid;sy++)for(int sx=0;sx<grid;sx++){double px=x+(sx+.5)/grid,py=y+(sy+.5)/grid;if(px>=l&&px<r&&py>=t&&py<b)hits++;}if(hits>0){int src=(color&0xffffff)|((((color>>>24)*hits+grid*grid/2)/(grid*grid))<<24);expected[y*w+x]=blend(src,expected[y*w+x]);}}
            NativeBitmap.fillRect(pixels,w,h,l,t,r,b,color,aa,2,1,12,9);if(!java.util.Arrays.equals(pixels,expected))throw new AssertionError("fill differs from scalar coverage/alpha reference "+pass);
        }
        final int[] pixel={0xff123456};rejected(new Runnable(){public void run(){NativeBitmap.fillRect(pixel,1,1,Double.NaN,0,1,1,0,false,0,0,1,1);}});
        rejected(new Runnable(){public void run(){NativeBitmap.fillRect(pixel,1,1,0,0,1,1,0,false,-1,0,1,1);}});if(pixel[0]!=0xff123456)throw new AssertionError("invalid fill changed pixels");
        NativeBitmap.fillRect(pixel,1,1,-Double.MAX_VALUE,-Double.MAX_VALUE,Double.MAX_VALUE,Double.MAX_VALUE,0xffabcdef,true,0,0,1,1);if(pixel[0]!=0xffabcdef)throw new AssertionError("extreme fill clipping");
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.clipRect(1,1,7,7);canvas.drawColor(0xff112233);canvas.translate(2,2);canvas.scale(2,2);android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(0xffff0000);canvas.drawRect(0,0,2,2,paint);if(bitmap.getPixel(0,0)!=0||bitmap.getPixel(1,1)!=0xff112233||bitmap.getPixel(2,2)!=0xffff0000||bitmap.getPixel(6,6)!=0xff112233)throw new AssertionError("Canvas fill transform/clip integration");bitmap.recycle();
    }
    // AOSP8.1 SkRasterPipelineBlitter.cpp120-170: sampled shader, paint alpha,
    // then color filter, then geometric coverage and destination composition.
    private static void transformedCopies(){
        Bitmap source=Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888);source.setPixels(new int[]{Color.RED,Color.GREEN,Color.BLUE,Color.WHITE,Color.BLACK,Color.YELLOW},0,3,0,0,3,2);source.setDensity(320);
        Matrix turn=new Matrix();turn.setRotate(90);Bitmap rotated=Bitmap.createBitmap(source,1,0,2,2,turn,false);if(rotated.getWidth()!=2||rotated.getHeight()!=2||rotated.getPixel(0,0)!=Color.BLACK||rotated.getPixel(1,0)!=Color.GREEN||rotated.getPixel(0,1)!=Color.YELLOW||rotated.getPixel(1,1)!=Color.BLUE||rotated.getDensity()!=320)throw new AssertionError("cropped rotated bitmap pixels/density");
        Bitmap scaled=Bitmap.createScaledBitmap(source,6,4,false);if(scaled.getPixel(0,0)!=Color.RED||scaled.getPixel(5,3)!=Color.YELLOW||scaled.getPixel(2,0)!=Color.GREEN||scaled.getWidth()!=6)throw new AssertionError("scaled bitmap nearest pixels");
        Bitmap copy=Bitmap.createBitmap(source);copy.setPixel(0,0,Color.BLACK);if(source.getPixel(0,0)!=Color.RED||copy==source)throw new AssertionError("mutable bitmap copies independently");Bitmap frozen=source.copy(source.getConfig(),false);if(Bitmap.createBitmap(frozen)!=frozen||Bitmap.createScaledBitmap(source,3,2,true)!=source)throw new AssertionError("identity bitmap reuse");
        rejected(new Runnable(){public void run(){Bitmap.createBitmap(source,2,0,2,2);}});Matrix huge=new Matrix();huge.setScale(100000,100000);try{Bitmap.createBitmap(source,0,0,3,2,huge,false);throw new AssertionError("unbounded transform accepted");}catch(IllegalArgumentException expected){}
        source.recycle();rotated.recycle();scaled.recycle();copy.recycle();frozen.recycle();
    }
    private static void matrixBitmap(){
        Bitmap source=Bitmap.createBitmap(2,1,Bitmap.Config.ARGB_8888),target=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
        source.setPixels(new int[]{Color.RED,Color.BLUE},0,2,0,0,2,1);source.setDensity(320);target.setDensity(160);
        Canvas canvas=new Canvas(target);canvas.translate(1,1);Matrix matrix=new Matrix();matrix.setRotate(90);matrix.postTranslate(3,1);
        int saved=canvas.getSaveCount();canvas.drawBitmap(source,matrix,null);
        if(target.getPixel(3,2)!=Color.RED||target.getPixel(3,3)!=Color.BLUE||target.getPixel(4,2)!=0||target.getPixel(3,4)!=0)throw new AssertionError("Matrix bitmap pixel rotation, composition, density");
        if(canvas.getSaveCount()!=saved)throw new AssertionError("Matrix bitmap leaked save");
        Paint p=new Paint();p.setColor(Color.GREEN);canvas.drawRect(0,0,1,1,p);if(target.getPixel(1,1)!=Color.GREEN)throw new AssertionError("Matrix bitmap leaked transform");
        matrix.setValues(new float[]{1,0,0,0,1,0,.1f,0,1});try{canvas.drawBitmap(source,matrix,null);throw new AssertionError("Perspective accepted");}catch(UnsupportedOperationException expected){}
        if(canvas.getSaveCount()!=saved)throw new AssertionError("Failed matrix draw leaked save");
        canvas.drawRect(1,0,2,1,p);if(target.getPixel(2,1)!=Color.GREEN)throw new AssertionError("Failed matrix draw leaked transform");
        source.recycle();target.recycle();
    }
    private static void affineFilters(){
        Bitmap source=Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888);source.setPixels(new int[]{0x40ff0000,0xff00ff00,0x800000ff,0xffffffff,0x00000000,0xff987654},0,3,0,0,3,2);
        java.util.ArrayList<ColorFilter> filters=new java.util.ArrayList<ColorFilter>();for(PorterDuff.Mode mode:PorterDuff.Mode.values())filters.add(new PorterDuffColorFilter(0x907733ee,mode));
        filters.add(new ColorMatrixColorFilter(new float[]{2,0,0,0,-70,0,.5f,0,0,20,0,0,1,0,0,0,0,0,.7f,10}));
        for(ColorFilter filter:filters)for(boolean smooth:new boolean[]{false,true}){
            Paint p=new Paint();p.setFilterBitmap(smooth);p.setAlpha(173);p.setColorFilter(filter);
            Bitmap flat=Bitmap.createBitmap(6,4,Bitmap.Config.ARGB_8888),rotated=Bitmap.createBitmap(4,6,Bitmap.Config.ARGB_8888);
            new Canvas(flat).drawBitmap(source,null,new Rect(0,0,6,4),p);Canvas c=new Canvas(rotated);c.translate(4,0);c.rotate(90);c.drawBitmap(source,null,new Rect(0,0,6,4),p);
            for(int y=0;y<4;y++)for(int x=0;x<6;x++){int a=flat.getPixel(x,y),b=rotated.getPixel(3-y,x);for(int shift=0;shift<=24;shift+=8)if(Math.abs(((a>>>shift)&255)-((b>>>shift)&255))>2)throw new AssertionError("Affine filter parity "+filter+" smooth="+smooth+" "+Integer.toHexString(a)+"/"+Integer.toHexString(b));}
            if(p.getAlpha()!=173||p.getColorFilter()!=filter||p.getShader()!=null)throw new AssertionError("Affine bitmap mutates paint");flat.recycle();rotated.recycle();
        }
        // Nonlinear matrix must run after bilinear sampling, not on source texels.
        Bitmap plain=Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888),filtered=Bitmap.createBitmap(30,30,Bitmap.Config.ARGB_8888);Paint p=new Paint();p.setFilterBitmap(true);ColorFilter matrix=filters.get(filters.size()-1);
        for(int pass=0;pass<2;pass++){Canvas c=new Canvas(pass==0?plain:filtered);c.translate(15,4);c.rotate(30);p.setColorFilter(pass==0?null:matrix);c.drawBitmap(source,null,new Rect(0,0,12,8),p);}
        int checked=0;for(int y=0;y<30;y++)for(int x=0;x<30;x++){double dx=x+.5-15,dy=y+.5-4,u=dx*Math.cos(Math.PI/6)+dy*.5,v=-dx*.5+dy*Math.cos(Math.PI/6);if(u>1&&u<11&&v>1&&v<7){int expected=ColorFilter.shirazApply(matrix,plain.getPixel(x,y));if(filtered.getPixel(x,y)!=expected)throw new AssertionError("Filter after sampled color");checked++;}}
        if(checked<20)throw new AssertionError("Insufficient transformed interior coverage");plain.recycle();filtered.recycle();source.recycle();
    }
    private static void presentation(){
        final Bitmap bitmap=Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888);
        bitmap.setPixel(2,1,0x80402010);
        try(final shiraz.runtime.NativeSurface surface=new shiraz.runtime.NativeSurface(3,2)){
            bitmap.shirazPostToSurface(surface);
            if(surface.snapshot()[5]!=0x80402010)throw new AssertionError("Bitmap presentation pixel");
            bitmap.eraseColor(0xffabcdef);
            if(surface.snapshot()[5]!=0x80402010)throw new AssertionError("Presentation retained mutable bitmap pixels");
            bitmap.shirazPostToSurface(surface);
            if(surface.snapshot()[5]!=0xffabcdef)throw new AssertionError("Repeated bitmap presentation");
            surface.resize(2,3);
            rejected(new Runnable(){public void run(){bitmap.shirazPostToSurface(surface);}});
            surface.resize(3,2);
            final Bitmap alpha=Bitmap.createBitmap(3,2,Bitmap.Config.ALPHA_8);
            rejected(new Runnable(){public void run(){alpha.shirazPostToSurface(surface);}});alpha.recycle();
            bitmap.recycle();
            try{bitmap.shirazPostToSurface(surface);throw new AssertionError("Recycled presentation accepted");}catch(IllegalStateException expected){}
        }
    }
    private static void sparseLargeBitmap() {
        int side=2048;int[] source=new int[side*side], destination=new int[side*side];
        for(int y=100;y<116;y++)for(int x=200;x<216;x++)source[y*side+x]=0xff123456;
        long[] samples=new long[9];
        for(int i=-2;i<samples.length;i++){
            long begin=System.nanoTime();
            NativeBitmap.drawClipped(destination,side,side,source,side,side,200,100,216,116,400,300,416,316,true,255,400,300,416,316);
            if(i>=0)samples[i]=System.nanoTime()-begin;
        }
        if(destination[300*side+400]!=0xff123456||destination[315*side+415]!=0xff123456||destination[299*side+400]!=0||destination[300*side+416]!=0)throw new AssertionError("Large bitmap touched-region isolation");
        source[100*side+200]=0xffabcdef;
        NativeBitmap.drawClipped(destination,side,side,source,side,side,200,100,216,116,400,300,416,316,false,255,400,300,416,316);
        if(destination[300*side+400]!=0xffabcdef)throw new AssertionError("Scanline samples retained between draws");
        java.util.Arrays.sort(samples);System.out.println("BITMAP_LARGE_CLIPPED_MEDIAN_NS="+samples[samples.length/2]);
    }
    private static void opaqueCopies() {
        java.util.Random random=new java.util.Random(927);
        for(int pass=0;pass<200;pass++) {
            int w=19,h=13,dx=pass%7-3,dy=pass%5-2;
            int[] source=new int[w*h],destination=new int[w*h];
            for(int i=0;i<source.length;i++){source[i]=random.nextInt()|0xff000000;destination[i]=random.nextInt();}
            // Include mixed rows and overlapping self copies in both directions.
            if(pass%3==0)source[6*w+8]=0x80123456;
            if(pass%4==0)destination=source;
            int[] before=source.clone(),expected=destination.clone();
            for(int y=2;y<11;y++)for(int x=3;x<17;x++) {
                int sx=x-dx+1,sy=y-dy+1;
                if(x>=dx&&x<dx+w-2&&y>=dy&&y<dy+h-2)
                    expected[y*w+x]=blend(before[sy*w+sx],expected[y*w+x]);
            }
            NativeBitmap.drawEffects(destination,w,h,source,w,h,1,1,w-1,h-1,dx,dy,dx+w-2,dy+h-2,pass%2==0,255,3,2,17,11,0,-1,null);
            if(!java.util.Arrays.equals(destination,expected))throw new AssertionError("Opaque/clipped/alias copy differs from scalar reference "+pass);
        }
    }
    private static void clippedSampling() {
        java.util.Random random=new java.util.Random(841);
        for(int pass=0;pass<160;pass++){
            int w=67,h=29;int[] src=new int[w*h],dst=new int[w*h];
            for(int i=0;i<src.length;i++){src[i]=random.nextInt();dst[i]=random.nextInt();}
            boolean alias=pass%3==0,filter=pass%2==0;if(alias)dst=src;
            int[] original=dst.clone(),expected=dst.clone(),stable=src.clone();
            float left=(pass%5)-3.25f,top=-1.75f,right=left+20+pass%71,bottom=top+17+pass%21;
            int cl=20+pass%19,cr=cl+1+pass%7,ct=3,cb=23;
            NativeBitmap.drawEffects(expected,w,h,stable,w,h,2,1,w-2,h-1,left,top,right,bottom,filter,173,0,0,w,h,0,-1,null);
            NativeBitmap.drawEffects(dst,w,h,src,w,h,2,1,w-2,h-1,left,top,right,bottom,filter,173,cl,ct,cr,cb,0,-1,null);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(dst[y*w+x]!=(x>=cl&&x<cr&&y>=ct&&y<cb?expected[y*w+x]:original[y*w+x]))throw new AssertionError("Clipped sampler differs from full draw "+pass);
        }
    }
    private static void identitySampling() {
        int[] source={0x00112233,0x01345678,0x80765432,0xff123456},destination=new int[4];
        NativeBitmap.drawEffects(destination,4,1,source,4,1,0,0,4,1,0,0,4,1,true,255,0,0,4,1,0,-1,null);
        if(destination[0]!=0||destination[1]!=source[1]||destination[2]!=source[2]||destination[3]!=source[3])throw new AssertionError("Exact texel alpha preservation");
        float[] matrix={1,0,0,0,0,0,1,0,0,0,0,0,1,0,0,0,0,0,0,255};
        java.util.Arrays.fill(destination,0);
        NativeBitmap.drawEffects(destination,4,1,source,4,1,0,0,4,1,0,0,4,1,true,255,0,0,4,1,0,-2,matrix);
        if(destination[0]!=0xff000000||destination[2]!=0xff765432)throw new AssertionError("Identity sampling canonical transparent color before alpha-changing filter");
        int w=720,h=1280;source=new int[w*h];destination=new int[w*h];java.util.Arrays.fill(source,0xff123456);long[] samples=new long[9];
        for(int i=-2;i<samples.length;i++){long start=System.nanoTime();NativeBitmap.drawEffects(destination,w,h,source,w,h,0,0,w,h,0,0,w,h,true,255,0,0,w,h,0,-1,null);long elapsed=System.nanoTime()-start;if(i>=0)samples[i]=elapsed;}
        if(destination[0]!=0xff123456||destination[w*h-1]!=0xff123456)throw new AssertionError("Identity frame pixels");java.util.Arrays.sort(samples);System.out.println("BITMAP_IDENTITY_MEDIAN_NS="+samples[samples.length/2]);
    }
    private static void transparentBlends(){
        java.util.Random random=new java.util.Random(8124);
        for(int opacity=1;opacity<=255;opacity++)for(int kind=0;kind<4;kind++){
            int[] src=new int[256],dst=new int[256],expected=new int[256];
            for(int a=0;a<256;a++){
                src[a]=(random.nextInt()&0xffffff)|(a<<24);int d=random.nextInt();
                dst[a]=kind==0?d&0xffffff:kind==1?d|0xff000000:kind==2?0:d;
                int scaled=(a*opacity+127)/255;
                expected[a]=blend((src[a]&0xffffff)|(scaled<<24),dst[a]);
            }
            NativeBitmap.draw(dst,256,1,src,256,1,0,0,256,1,0,0,256,1,false,opacity);
            if(!java.util.Arrays.equals(dst,expected))throw new AssertionError("Native transparent blend mismatch opacity="+opacity+" kind="+kind);
        }
    }
    public static void main(String[] args) {
        Bitmap stored = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); stored.eraseColor(0x11223344);
        java.nio.ByteBuffer encoded = java.nio.ByteBuffer.allocate(4); stored.copyPixelsToBuffer(encoded);
        if (encoded.position() != 4 || encoded.get(0) != 0x11 || encoded.get(1) != 0x22 || encoded.get(2) != 0x33 || encoded.get(3) != 0x44) throw new AssertionError("ARGB pixel copy");
        try { stored.copyPixelsToBuffer(java.nio.ByteBuffer.allocate(3)); throw new AssertionError("short pixel buffer accepted"); } catch (RuntimeException expected) {}
        stored.recycle();
        transparentBlends();
        clippedSampling();
        opaqueCopies();
        presentation();
        transformedCopies();
        matrixBitmap();
        affineFilters();
        BitmapFixture.check();
        android.graphics.ColorSpace srgb=android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB);
        if(!srgb.isSrgb()||srgb.isWideGamut()||srgb.getId()!=0||srgb.getComponentCount()!=3||srgb.getModel()!=android.graphics.ColorSpace.Model.RGB||srgb.getMinValue(0)!=0||srgb.getMaxValue(2)!=1)throw new AssertionError("sRGB metadata");
        android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();metrics.densityDpi=240;
        android.graphics.Bitmap colored=android.graphics.Bitmap.createBitmap(metrics,2,2,android.graphics.Bitmap.Config.ARGB_8888,true,srgb);
        if(colored.getColorSpace()!=srgb||colored.getDensity()!=240||!colored.hasAlpha()||colored.getPixel(0,0)!=0)throw new AssertionError("sRGB bitmap metadata/storage");
        colored.setPixel(0,0,0x80402010);if(colored.copy(android.graphics.Bitmap.Config.ARGB_8888,true).getColorSpace()!=srgb||colored.getPixel(0,0)!=0x80402010)throw new AssertionError("sRGB pixels/copy");
        try{android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.DISPLAY_P3);throw new AssertionError("Unsupported gamut accepted");}catch(UnsupportedOperationException expected){}
        try{android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888,false,srgb);throw new AssertionError("Opaque bitmap silently accepted");}catch(UnsupportedOperationException expected){}
        colored.recycle();try{colored.getColorSpace();throw new AssertionError("Recycled color space accepted");}catch(IllegalStateException expected){}

        android.graphics.Bitmap alpha=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ALPHA_8), tinted=android.graphics.Bitmap.createBitmap(4,4,android.graphics.Bitmap.Config.ARGB_8888);
        alpha.setPixel(0,0,0x80ff0022);if(alpha.getPixel(0,0)!=0x80000000||alpha.getColorSpace()!=null||alpha.getByteCount()!=4||alpha.getAllocationByteCount()!=16)throw new AssertionError("Alpha bitmap storage contract");
        android.graphics.Canvas ac=new android.graphics.Canvas(alpha);android.graphics.Paint ap=new android.graphics.Paint();ap.setColor(0x80ff0000);ac.drawRect(0,0,2,2,ap);if(alpha.getPixel(1,1)!=0x80000000)throw new AssertionError("Canvas writes alpha only");
        ap.setColor(0xff00ff00);new android.graphics.Canvas(tinted).drawBitmap(alpha,0,0,ap);if(tinted.getPixel(1,1)!=0x8000ff00)throw new AssertionError("Alpha bitmap uses paint color");
        ap.setColorFilter(new android.graphics.PorterDuffColorFilter(0xff0000ff,android.graphics.PorterDuff.Mode.SRC_IN));tinted.eraseColor(0);new android.graphics.Canvas(tinted).drawBitmap(alpha,0,0,ap);if(tinted.getPixel(1,1)!=0x800000ff)throw new AssertionError("Alpha bitmap tint filter");
        int[] alphaPixels=new int[4];alpha.getPixels(alphaPixels,0,2,0,0,2,2);if(alphaPixels[3]!=0x80000000||alpha.copy(android.graphics.Bitmap.Config.ARGB_8888,true).getPixel(1,1)!=0x80000000)throw new AssertionError("Alpha reads and copy discard native RGB");
        ap.setColorFilter(null);ap.setColor(0xffff0000);tinted.eraseColor(0);android.graphics.Canvas rotated=new android.graphics.Canvas(tinted);rotated.translate(2,0);rotated.rotate(90);rotated.drawBitmap(alpha,new android.graphics.Rect(1,0,2,2),new android.graphics.Rect(0,0,1,2),ap);if(tinted.getPixel(0,0)!=0x80ff0000)throw new AssertionError("Cropped rotated alpha mask");ap.setColorFilter(new PorterDuffColorFilter(Color.BLUE,PorterDuff.Mode.SRC_IN));tinted.eraseColor(0);rotated.clipRect(0,0,1,1);rotated.drawBitmap(alpha,new Rect(1,0,2,2),new Rect(0,0,1,2),ap);if(tinted.getPixel(1,0)!=0x800000ff||tinted.getPixel(0,0)!=0)throw new AssertionError("Cropped rotated filtered alpha mask with clip");alpha.prepareToDraw();alpha.recycle();tinted.recycle();
        fills();
        final int[] pixel = {0xff123456};
        rejected(new Runnable() { public void run() { NativeBitmap.draw(pixel,8192,8192,pixel,1,1,0,0,1,1,0,0,1,1,false,255); } });
        rejected(new Runnable() { public void run() { NativeBitmap.draw(pixel,1,1,pixel,1,1,-1,0,1,1,0,0,1,1,false,255); } });
        rejected(new Runnable() { public void run() { NativeBitmap.draw(pixel,1,1,pixel,1,1,0,0,1,1,Float.NaN,0,1,1,false,255); } });
        rejected(new Runnable() { public void run() { NativeBitmap.draw(pixel,1,1,pixel,1,1,0,0,1,1,0,0,1,1,false,256); } });
        rejected(new Runnable() { public void run() { NativeBitmap.drawClipped(pixel,1,1,pixel,1,1,0,0,1,1,0,0,1,1,false,255,-1,0,1,1); } });
        rejected(new Runnable() { public void run() { NativeBitmap.drawClipped(pixel,1,1,pixel,1,1,0,0,1,1,0,0,1,1,false,0,0,0,2,1); } });
        NativeBitmap.drawClipped(pixel,1,1,pixel,1,1,0,0,1,1,0,0,1,1,false,255,0,0,0,0);
        if (pixel[0] != 0xff123456) throw new AssertionError("empty device clip changed target");
        // Huge finite destinations must clip safely before any integer conversion.
        NativeBitmap.draw(pixel,1,1,pixel,1,1,0,0,1,1,-Float.MAX_VALUE,-Float.MAX_VALUE,Float.MAX_VALUE,Float.MAX_VALUE,true,255);
        if (pixel[0] != 0xff123456) throw new AssertionError("extreme bitmap clipping");
        for (int count = 0; count < 1000; ++count) NativeBitmap.draw(pixel,1,1,pixel,1,1,0,0,1,1,0,0,1,1,true,255);
        java.util.Random random = new java.util.Random(42);
        for (int pass = 0; pass < 30; ++pass) {
            int sw = 2 + random.nextInt(5), sh = 2 + random.nextInt(5), dw = 3 + random.nextInt(9), dh = 3 + random.nextInt(9);
            int[] source = new int[sw * sh], output = new int[dw * dh];
            for (int i = 0; i < source.length; ++i) source[i] = 0xff000000 | random.nextInt(1 << 24);
            NativeBitmap.draw(output,dw,dh,source,sw,sh,0,0,sw,sh,0,0,dw,dh,true,255);
            for (int y = 0; y < dh; ++y) for (int x = 0; x < dw; ++x) {
                double u = (x + .5) * sw / dw - .5, v = (y + .5) * sh / dh - .5;
                int ix = (int)Math.floor(u), iy = (int)Math.floor(v); double fx = u - ix, fy = v - iy;
                int expected = 0xff000000;
                for (int shift = 0; shift <= 16; shift += 8) {
                    double value = 0;
                    for (int ry = 0; ry < 2; ++ry) for (int rx = 0; rx < 2; ++rx) {
                        int sx = Math.max(0, Math.min(sw - 1, ix + rx)), sy = Math.max(0, Math.min(sh - 1, iy + ry));
                        value += ((source[sy * sw + sx] >>> shift) & 255) * (rx == 0 ? 1 - fx : fx) * (ry == 0 ? 1 - fy : fy);
                    }
                    expected |= ((int)Math.floor(value + .5)) << shift;
                    if (Math.abs(((output[y * dw + x] >>> shift) & 255) - ((expected >>> shift) & 255)) > 1) throw new AssertionError("bitmap interpolation deviates from scalar reference");
                }
            }
        }
        int[] screen = new int[720 * 1280], tile = new int[256 * 256];
        java.util.Arrays.fill(tile, 0xff123456);
        for (int pass = 0; pass < 2; ++pass) NativeBitmap.draw(screen,720,1280,tile,256,256,0,0,256,256,0,0,720,1280,true,255);
        long[] times = new long[5];
        for (int pass = 0; pass < times.length; ++pass) {
            long start = System.nanoTime(); NativeBitmap.draw(screen,720,1280,tile,256,256,0,0,256,256,0,0,720,1280,true,255); times[pass] = System.nanoTime() - start;
        }
        if (screen[0] != 0xff123456 || screen[screen.length - 1] != 0xff123456) throw new AssertionError("full buffer bitmap sampling");
        java.util.Arrays.sort(times); System.out.println("BITMAP_FILTER_720X1280_MEDIAN_NS=" + times[times.length / 2]);
        sparseLargeBitmap();identitySampling();System.out.println("BITMAP_NATIVE_BOUNDS_FILTERING_AND_ALIASING_OK");
    }
}
