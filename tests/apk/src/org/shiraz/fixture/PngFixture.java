// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.TypedValue;
import java.io.*;
import java.util.Arrays;

public final class PngFixture {
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static int id(Resources r,String name) { int value=r.getIdentifier("org.shiraz.fixture:"+name,null,null); check(value != 0,"fixture image resource "+name); return value; }
    private static byte[] bytes(InputStream input) throws IOException { ByteArrayOutputStream result=new ByteArrayOutputStream(); byte[] data=new byte[1024]; int count; while ((count=input.read(data)) != -1) result.write(data,0,count); return result.toByteArray(); }
    private static byte[] asset(Resources r,String name) throws IOException { try (InputStream input=r.getAssets().open("images/"+name)) { return bytes(input); } }
    private static void grid(Bitmap bitmap) {
        check(bitmap != null && bitmap.getWidth() == 2 && bitmap.getHeight() == 2,"PNG grid dimensions");
        check(bitmap.getPixel(0,0) == Color.RED && bitmap.getPixel(1,0) == 0x8000ff00 && bitmap.getPixel(0,1) == Color.BLUE && bitmap.getPixel(1,1) == 0,"PNG channels/alpha");
    }
    public static void check(Resources resources) throws Exception {
        byte[] encoded=asset(resources,"grid.png");
        for (String name:new String[]{"grid.png","interlaced.png","palette.png"}) { byte[] data=asset(resources,name); Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length); grid(bitmap); check(!bitmap.isMutable(),"decoded PNG defaults immutable"); bitmap.recycle(); }
        for (String name:new String[]{"gray1.png","gray16.png"}) {
            byte[] data=asset(resources,name); Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length);
            check(bitmap.getPixel(0,0) == Color.BLACK && bitmap.getPixel(1,0) == Color.WHITE,"grayscale PNG expansion"); bitmap.recycle();
        }
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        check(BitmapFactory.decodeByteArray(encoded,0,encoded.length,options) == null && options.outWidth == 2 && options.outHeight == 2 && "image/png".equals(options.outMimeType),"bounds-only decode");
        byte[] padded=new byte[encoded.length+9]; System.arraycopy(encoded,0,padded,3,encoded.length); grid(BitmapFactory.decodeByteArray(padded,3,encoded.length));
        try { BitmapFactory.decodeByteArray(encoded,-1,encoded.length); throw new AssertionError("invalid byte range"); } catch (ArrayIndexOutOfBoundsException expected) { }
        for (String name:new String[]{"corrupt.png","truncated.png"}) { byte[] bad=asset(resources,name); options.inJustDecodeBounds=false; check(BitmapFactory.decodeByteArray(bad,0,bad.length,options) == null && options.outWidth == -1 && options.outHeight == -1 && options.outMimeType == null,"malformed PNG resets metadata"); }
        // Native decoding already ignores optional ICC metadata and keeps the raster.
        byte[] profiled=asset(resources,"profile.png");Bitmap profileBitmap=BitmapFactory.decodeByteArray(profiled,0,profiled.length);
        check(profileBitmap!=null&&profileBitmap.getWidth()==1&&profileBitmap.getPixel(0,0)==Color.BLACK,"ignored invalid ICC metadata preserves raster");profileBitmap.recycle();
        byte[] invalidPatch=asset(resources,"ninepatch.png");try{BitmapFactory.decodeByteArray(invalidPatch,0,invalidPatch.length);throw new AssertionError("Malformed nine-patch accepted");}catch(IllegalArgumentException expected){}
        byte[] meta=asset(resources,"metadata.png"); check(BitmapFactory.decodeByteArray(meta,0,meta.length).getPixel(0,0) == Color.RED,"bounded compressed/uncompressed PNG text metadata");
        byte[] bomb=asset(resources,"metadata_bomb.png"); try { BitmapFactory.decodeByteArray(bomb,0,bomb.length); throw new AssertionError("PNG metadata inflate bomb accepted"); } catch (IllegalArgumentException expected) { }
        byte[] oversized=asset(resources,"oversized.png"); options.inJustDecodeBounds=true;
        check(BitmapFactory.decodeByteArray(oversized,0,oversized.length,options) == null && options.outWidth == 8193,"oversized bounds query avoids pixel allocation");
        options.inJustDecodeBounds=false; try { BitmapFactory.decodeByteArray(oversized,0,oversized.length,options); throw new AssertionError("oversized pixel decode accepted"); } catch (IllegalArgumentException expected) { }
        options=new BitmapFactory.Options(); options.inMutable=true; Bitmap mutable=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options); mutable.setPixel(0,0,Color.WHITE); check(mutable.isMutable(),"mutable PNG option");
        Bitmap immutable=mutable.copy(Bitmap.Config.ARGB_8888,false); try { immutable.eraseColor(0); throw new AssertionError("immutable bitmap changed"); } catch (IllegalStateException expected) { }
        try { new Canvas(immutable); throw new AssertionError("immutable canvas target accepted"); } catch (IllegalStateException expected) { }
        mutable.setPixel(0,0,Color.RED); check(immutable.getPixel(0,0) == Color.WHITE,"bitmap copy owns pixels");
        options=new BitmapFactory.Options(); options.inDensity=160; options.inTargetDensity=320; Bitmap doubled=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        check(doubled.getWidth() == 4 && doubled.getHeight() == 4 && doubled.getDensity() == 320 && options.outWidth == 4,"decode density scaling and metadata");
        options.inScaled=false; Bitmap unscaled=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options); check(unscaled.getWidth() == 2 && unscaled.getDensity() == 160 && unscaled.getScaledWidth(320) == 4,"density metadata without eager scaling");
        Bitmap target=Bitmap.createBitmap(5,5,Bitmap.Config.ARGB_8888); target.setDensity(320); new Canvas(target).drawBitmap(unscaled,0,0,null);
        check(target.getPixel(3,0) >>> 24 != 0 && target.getPixel(4,0) == 0,"canvas position bitmap drawing honors density");
        final boolean[] closed={false}; InputStream stream=new ByteArrayInputStream(encoded) { public void close() { closed[0]=true; } };
        Rect padding=new Rect(); grid(BitmapFactory.decodeStream(stream,padding,null)); check(!closed[0] && padding.equals(new Rect(-1,-1,-1,-1)),"decoder leaves caller stream open"); stream.close();
        options.outWidth=7; InputStream failure=new InputStream() { public int read() throws IOException { throw new IOException("fixture read failure"); } };
        check(BitmapFactory.decodeStream(failure,null,options) == null && options.outWidth == -1,"stream failures clear decode metadata");
        int resource=id(resources,"drawable/png_grid"); Bitmap loaded=BitmapFactory.decodeResource(resources,resource); grid(loaded);
        BitmapDrawable drawable=(BitmapDrawable)resources.getDrawable(id(resources,"drawable/png_alias")); check(drawable.getIntrinsicWidth() == 2 && drawable.getBitmap().getDensity() == 160,"PNG drawable alias");
        target.eraseColor(0); drawable.setBounds(0,0,2,2); drawable.draw(new Canvas(target)); check(target.getPixel(0,0) == Color.RED,"PNG drawable actual pixels");
        Resources.Theme theme=resources.newTheme(); theme.applyStyle(id(resources,"style/FixturePng"),true);
        check(resources.getDrawable(id(resources,"drawable/png_theme_drawable"),theme) instanceof BitmapDrawable,"themed file drawable");
        check(((ColorDrawable)resources.getDrawable(id(resources,"color/accent"))).getColor() == 0xff123456,"literal color drawable");
        TypedValue value=new TypedValue(); byte[] app,framework;
        try (InputStream input=resources.openRawResource(id(resources,"color/primary_text_light"),value)) { app=bytes(input); }
        try (InputStream input=resources.openRawResource(android.R.color.primary_text_light)) { framework=bytes(input); }
        check(!Arrays.equals(app,framework),"same resource path opens exact owning APK cookie");
        Configuration original=resources.getConfiguration();
        try {
            Configuration density=new Configuration(original); density.densityDpi=320; resources.updateConfiguration(density,null);
            BitmapDrawable high=(BitmapDrawable)resources.getDrawable(id(resources,"drawable/png_density")); check(high.getIntrinsicWidth() == 4 && high.getBitmap().getWidth() == 4 && high.getBitmap().getDensity() == 320,"xhdpi PNG selection");
            BitmapDrawable noScale=(BitmapDrawable)resources.getDrawable(id(resources,"drawable/png_noscale")); check(noScale.getIntrinsicWidth() == 2 && noScale.getBitmap().getDensity() == 0,"nodpi image preserves dimensions");
            BitmapDrawable low=(BitmapDrawable)resources.getDrawable(resource); check(low.getIntrinsicWidth() == 4,"mdpi image scales in xhdpi resources");
        } finally { resources.updateConfiguration(original,null); }
        System.out.println("SHIRAZ_FIXTURE_PNG_IMAGES_OK");
    }
    public static void checkContext(Context context) {
        android.widget.ImageView image=new android.widget.ImageView(context); image.setImageResource(id(context.getResources(),"drawable/png_grid"));
        check(image.getDrawable() instanceof BitmapDrawable && context.getDrawable(id(context.getResources(),"color/accent")) instanceof ColorDrawable,"context/image-view drawable loading");
        System.out.println("SHIRAZ_FIXTURE_PNG_CONTEXT_OK");
    }
}
