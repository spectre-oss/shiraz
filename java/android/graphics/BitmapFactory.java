// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import android.content.res.Resources;
import android.util.TypedValue;
import java.io.*;

/** Bounded PNG and static WebP decode. Other formats, sampling, bitmap reuse are explicit gaps. */
public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds,inMutable,inDither;
        public boolean inScaled=true,inPremultiplied=true;
        public int inDensity,inTargetDensity,inScreenDensity,inSampleSize=1;
        public Bitmap.Config inPreferredConfig=Bitmap.Config.ARGB_8888;
        public ColorSpace inPreferredColorSpace;
        public ColorSpace outColorSpace;
        public Bitmap.Config outConfig;
        public boolean inPurgeable,inInputShareable;
        public Bitmap inBitmap;
        public byte[] inTempStorage;
        public int outWidth=-1,outHeight=-1;
        public String outMimeType;
    }
    private static void validate(Options options) {
        if (!options.inPremultiplied) throw new UnsupportedOperationException("Unpremultiplied decoding is not implemented");
        for (int density:new int[]{options.inDensity,options.inTargetDensity,options.inScreenDensity}) if (density < 0) throw new IllegalArgumentException("decode density");
    }
    public static Bitmap decodeByteArray(byte[] data,int offset,int length) { return decodeByteArray(data,offset,length,null); }
    public static Bitmap decodeByteArray(byte[] data,int offset,int length,Options options) {
        if (data == null) throw new NullPointerException("data");
        Options o=options == null ? new Options() : options; validate(o); o.outWidth=o.outHeight=-1; o.outMimeType=null;
        if(offset<0||length<0||offset>data.length-length)throw new ArrayIndexOutOfBoundsException("Image byte range");
        boolean webp=length>=12&&data[offset]=='R'&&data[offset+1]=='I'&&data[offset+2]=='F'&&data[offset+3]=='F'&&data[offset+8]=='W'&&data[offset+9]=='E'&&data[offset+10]=='B'&&data[offset+11]=='P';
        boolean jpeg=length>=3&&(data[offset]&255)==0xFF&&(data[offset+1]&255)==0xD8&&(data[offset+2]&255)==0xFF;
        int[] dimensions={-1,-1}; int[] pixels=webp?shiraz.runtime.NativeImage.webp(data,offset,length,o.inJustDecodeBounds,dimensions):jpeg?shiraz.runtime.NativeImage.jpeg(data,offset,length,o.inJustDecodeBounds,dimensions):shiraz.runtime.NativeImage.png(data,offset,length,o.inJustDecodeBounds,dimensions);
        if (dimensions[0] < 0) return null;
        o.outWidth=dimensions[0]; o.outHeight=dimensions[1]; o.outMimeType=webp?"image/webp":jpeg?"image/jpeg":"image/png";
        byte[] patch=webp||jpeg||o.inSampleSize>1?null:NinePatch.fromPng(data,offset,length,dimensions[0],dimensions[1]);
        if (pixels == null) return null;
        int sample=Math.max(1,o.inSampleSize);
        if (sample > 1) {
            int width=(dimensions[0]+sample-1)/sample,height=(dimensions[1]+sample-1)/sample;
            int[] reduced=new int[width*height];
            for (int y=0;y<height;y++) for (int x=0;x<width;x++) reduced[y*width+x]=pixels[Math.min(dimensions[1]-1,y*sample)*dimensions[0]+Math.min(dimensions[0]-1,x*sample)];
            pixels=reduced; dimensions[0]=width; dimensions[1]=height;
        }
        if (sample > 1) patch=null;
        o.outConfig=Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        if(o.inBitmap!=null){
         if(o.inBitmap.isRecycled()||o.inBitmap.getWidth()!=dimensions[0]||o.inBitmap.getHeight()!=dimensions[1]||o.inBitmap.getConfig()!=Bitmap.Config.ARGB_8888)
          throw new IllegalArgumentException("Problem decoding into existing bitmap");
         o.inBitmap.setPixels(pixels,0,dimensions[0],0,0,dimensions[0],dimensions[1]);bitmap=o.inBitmap;
        }else bitmap=Bitmap.fromPixels(dimensions[0],dimensions[1],pixels);
        bitmap.ninePatchChunk=patch;
        if (o.inDensity != 0) bitmap.setDensity(o.inDensity);
        if (o.inScaled && o.inDensity != 0 && o.inTargetDensity != 0 && o.inDensity != o.inTargetDensity && o.inDensity != o.inScreenDensity) {
            if(o.inBitmap!=null)throw new IllegalArgumentException("Problem decoding into existing bitmap");
            int width=Math.max(1,bitmap.getScaledWidth(o.inTargetDensity)),height=Math.max(1,bitmap.getScaledHeight(o.inTargetDensity));
            Bitmap scaled=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            new Canvas(scaled).drawBitmap(bitmap,null,new Rect(0,0,width,height),new Paint(Paint.FILTER_BITMAP_FLAG));
            scaled.ninePatchChunk=NinePatch.scaled(patch,bitmap.getWidth(),bitmap.getHeight(),width,height);
            scaled.setDensity(o.inTargetDensity); bitmap.recycle(); bitmap=scaled;
        }
        o.outWidth=bitmap.getWidth(); o.outHeight=bitmap.getHeight();
        if (!o.inMutable&&o.inBitmap==null) bitmap.freeze(); return bitmap;
    }
    public static Bitmap decodeResource(Resources resources,int id) { return decodeResource(resources,id,null); }
    public static Bitmap decodeResource(Resources resources,int id,Options options) {
        TypedValue value=new TypedValue(); byte[] bytes=resources.shirazReadResourceFile(id,value);
        return decodeResourceBytes(resources,value,bytes,options);
    }
    public static Bitmap decodeResourceBytes(Resources resources,TypedValue value,byte[] bytes,Options options) {
        Options o=options == null ? new Options() : options;
        boolean noDensity=value.density == TypedValue.DENSITY_NONE && o.inDensity == 0;
        if (o.inDensity == 0 && !noDensity) o.inDensity=value.density == 0 ? 160 : value.density;
        if (o.inTargetDensity == 0) o.inTargetDensity=resources.getDisplayMetrics().densityDpi;
        Bitmap bitmap=decodeByteArray(bytes,0,bytes.length,o); if (bitmap != null && noDensity) bitmap.setDensity(0); return bitmap;
    }
    public static Bitmap decodeStream(InputStream stream) { return decodeStream(stream,null,null); }
    public static Bitmap decodeStream(InputStream stream,Rect padding,Options options) {
        if (stream == null) return null;
        if (padding != null) padding.set(-1,-1,-1,-1);
        try {
            ByteArrayOutputStream output=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int count;
            while ((count=stream.read(buffer)) != -1) {
                if (count == 0) { int value=stream.read(); if (value < 0) break; output.write(value); }
                else output.write(buffer,0,count);
                if (output.size() > 16*1024*1024) throw new IllegalArgumentException("Image stream exceeds 16MiB");
            }
            byte[] bytes=output.toByteArray(); Bitmap bitmap=decodeByteArray(bytes,0,bytes.length,options);if(bitmap!=null&&padding!=null&&bitmap.ninePatchChunk!=null)new NinePatch(bitmap,bitmap.ninePatchChunk,null).shirazPadding(padding);return bitmap;
        } catch (IOException failure) { if (options != null) { options.outWidth=options.outHeight=-1; options.outMimeType=null; } return null; }
    }
    public static Bitmap decodeFile(String path) { return decodeFile(path,null); }
    public static Bitmap decodeFile(String path,Options options) {
        try (InputStream stream=new FileInputStream(path)) { return decodeStream(stream,null,options); }
        catch (IOException failure) { if (options != null) { options.outWidth=options.outHeight=-1; options.outMimeType=null; } return null; }
    }
}
