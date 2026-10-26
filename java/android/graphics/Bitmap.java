// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.util.Arrays;

public final class Bitmap {
    public enum Config { ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE }
    public enum CompressFormat { JPEG, PNG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
    public boolean compress(CompressFormat format, int quality, java.io.OutputStream stream) {
        requireLive();
        cpuRead();
        if (format == null || stream == null) throw new NullPointerException("Bitmap compression format/stream");
        if (quality < 0 || quality > 100) throw new IllegalArgumentException("Bitmap compression quality");
        if (format == CompressFormat.PNG) return PngEncoder.write(width, height, pixels, config == Config.ALPHA_8, stream);
        if (format == CompressFormat.JPEG) {
            byte[] encoded = shiraz.runtime.NativeImage.jpegEncode(pixels, width, height, quality);
            if (encoded == null) return false;
            try { stream.write(encoded); return true; } catch (java.io.IOException failure) { return false; }
        }
        if (format == CompressFormat.WEBP || format == CompressFormat.WEBP_LOSSY || format == CompressFormat.WEBP_LOSSLESS) {
            byte[] encoded = shiraz.runtime.NativeImage.webpEncode(pixels,width,height,quality,format == CompressFormat.WEBP_LOSSLESS);
            if (encoded == null) return false;
            try { stream.write(encoded); return true; } catch (java.io.IOException failure) { return false; }
        }
        throw new IllegalArgumentException("Bitmap compression format");
    }
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_GENERATION=new java.util.concurrent.atomic.AtomicInteger(1);
    private volatile int generationId=nextGeneration();
    private static int nextGeneration(){int value;do{value=NEXT_GENERATION.getAndIncrement();}while(value==0);return value;}
    private void changed(){generationId=nextGeneration();}
    public int getGenerationId(){requireLive();return generationId;}
    int width, height;
    int[] pixels;
    private long gpu;
    private boolean gpuDirty;
    private Thread gpuThread;
    private void cpuRead(){if(gpu!=0&&gpuThread!=Thread.currentThread()){if(gpuDirty)throw new IllegalStateException("GPU screen bitmap used outside its render thread");releaseGpu();gpu=0;}if(gpu!=0&&!shiraz.runtime.NativeGpu.valid(gpu)){releaseGpu();gpu=0;gpuDirty=false;}if(gpuDirty){shiraz.runtime.NativeGpu.read(gpu,pixels);}}
    private void cpuWrite(){cpuRead();changed();if(gpu!=0){releaseGpu();gpu=0;gpuDirty=false;}}
    long shirazGpuImage(){requireLive();if(gpu!=0&&!shiraz.runtime.NativeGpu.valid(gpu)){releaseGpu();gpu=0;gpuDirty=false;}if(gpu==0){gpu=shiraz.runtime.NativeGpu.create(width,height,pixels);gpuLease=new GpuLease(gpu);gpuThread=Thread.currentThread();}return gpu;}
    long shirazGpuTarget(){long result=shirazGpuImage();changed();gpuDirty=true;return result;}
    public void shirazPrepareNativePixels(){requireLive();cpuWrite();}
    public boolean shirazHasGpu(){return gpu!=0&&gpuDirty;}
    private GpuLease gpuLease;
    private static final class GpuLease {
        long handle;
        GpuLease(long handle){this.handle=handle;}
        void release(){if(handle!=0){shiraz.runtime.NativeGpu.release(handle);handle=0;}}
        @Override protected void finalize() throws Throwable {try{release();}finally{super.finalize();}}
    }
    private void releaseGpu(){if(gpuLease!=null){gpuLease.release();gpuLease=null;}}


    private int capacity;
    private boolean mutable=true;
    private Config config=Config.ARGB_8888;
    private static volatile int defaultDensity=160;
    /** Host display density for newly allocated bitmaps; existing bitmaps retain theirs. */
    public static void shirazSetDefaultDensity(int density) {
        if(density<1||density>1280)throw new IllegalArgumentException("Default bitmap density");
        defaultDensity=density;
    }
    private int density=defaultDensity;
    byte[] ninePatchChunk;
    public byte[] getNinePatchChunk(){return ninePatchChunk==null?null:ninePatchChunk.clone();}
    public static final int DENSITY_NONE=0;
    // Programmatic map render targets may exceed the separately bounded decoded-image budget.
    private static final long MAX_RENDER_PIXELS=32L*1024*1024;
    private Bitmap(int width, int height) {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > MAX_RENDER_PIXELS)
            throw new IllegalArgumentException("Bitmap dimensions");
        this.width = width; this.height = height; pixels = new int[width * height];
    }
    public static Bitmap createBitmap(Bitmap source){if(source==null)throw new NullPointerException("source");return createBitmap(source,0,0,source.width,source.height,null,false);}
    public static Bitmap createBitmap(Bitmap source,int x,int y,int width,int height){return createBitmap(source,x,y,width,height,null,false);}
    public static Bitmap createScaledBitmap(Bitmap source,int width,int height,boolean filter){
        if(source==null)throw new NullPointerException("source");source.requireLive();
        if(width<=0||height<=0)throw new IllegalArgumentException("Scaled bitmap dimensions");
        if(width==source.width&&height==source.height)return source;
        Matrix scale=new Matrix();scale.setScale((float)width/source.width,(float)height/source.height);
        return createBitmap(source,0,0,source.width,source.height,scale,filter);
    }
    public static Bitmap createBitmap(Bitmap source,int x,int y,int width,int height,Matrix matrix,boolean filter){
        if(source==null)throw new NullPointerException("source");source.requireLive();
        if(x<0||y<0||width<=0||height<=0||(long)x+width>source.width||(long)y+height>source.height)throw new IllegalArgumentException("Bitmap crop bounds");
        if(matrix!=null){if(!matrix.isAffine())throw new UnsupportedOperationException("Perspective bitmap transforms are not implemented");float[] values=new float[9];matrix.getValues(values);for(float value:values)if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite bitmap transform");}
        boolean identity=matrix==null||matrix.isIdentity();
        if(!source.mutable&&x==0&&y==0&&width==source.width&&height==source.height&&identity)return source;
        RectF target=new RectF(0,0,width,height),bounds=new RectF(target);if(!identity)matrix.mapRect(bounds);
        Config format=source.config;if(!identity&&!matrix.rectStaysRect())format=Config.ARGB_8888;
        Bitmap result=createBitmap(Math.round(bounds.width()),Math.round(bounds.height()),format);result.density=source.density;
        try{Canvas canvas=new Canvas(result);canvas.translate(-bounds.left,-bounds.top);canvas.concat(matrix);Paint paint=new Paint();paint.setFilterBitmap(filter);paint.setAntiAlias(!identity&&!matrix.rectStaysRect());canvas.drawBitmap(source,new Rect(x,y,x+width,y+height),target,paint);return result;}
        catch(RuntimeException|Error failure){result.recycle();throw failure;}
    }
    public static Bitmap createBitmap(int width, int height, Config config) {
        if (config != Config.ARGB_8888 && config != Config.ALPHA_8) throw new UnsupportedOperationException("Only ARGB_8888 and ALPHA_8 are implemented");
        Bitmap result=new Bitmap(width,height);result.config=config;return result;
    }
    public static Bitmap createBitmap(int width,int height,Config config,boolean hasAlpha){return createBitmap(null,width,height,config,hasAlpha,ColorSpace.get(ColorSpace.Named.SRGB));}
    public static Bitmap createBitmap(int width,int height,Config config,boolean hasAlpha,ColorSpace colorSpace){return createBitmap(null,width,height,config,hasAlpha,colorSpace);}
    public static Bitmap createBitmap(android.util.DisplayMetrics metrics,int width,int height,Config config,boolean hasAlpha,ColorSpace colorSpace){
        if(colorSpace==null)throw new IllegalArgumentException("Bitmap color space is null");
        if(!colorSpace.isSrgb())throw new UnsupportedOperationException("Bitmap color space must be sRGB");
        if(!hasAlpha)throw new UnsupportedOperationException("Opaque-only bitmap storage is not implemented");
        Bitmap result=createBitmap(width,height,config);if(metrics!=null)result.setDensity(metrics.densityDpi);return result;
    }
    public ColorSpace getColorSpace(){requireLive();return config==Config.ALPHA_8?null:ColorSpace.get(ColorSpace.Named.SRGB);}
    public void prepareToDraw(){requireLive();}
    public boolean hasAlpha(){requireLive();return true;}
    public void setHasAlpha(boolean value){requireLive();if(!value)throw new UnsupportedOperationException("Opaque-only bitmap storage is not implemented");}
    static Bitmap fromPixels(int width,int height,int[] pixels) {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long)width*height > 16*1024*1024 || pixels == null || (long)width*height != pixels.length) throw new IllegalArgumentException("Decoded bitmap dimensions");
        return new Bitmap(width,height,pixels);
    }
    private Bitmap(int width,int height,int[] pixels) { this.width=width; this.height=height; this.pixels=pixels; }
    void freeze() { mutable=false; }
    void requireMutable() { requireLive(); cpuWrite(); if (!mutable) throw new IllegalStateException("Bitmap is immutable"); }
    public boolean isMutable() { return mutable && !isRecycled(); }
    public void reconfigure(int newWidth,int newHeight,Config newConfig){
        requireMutable();
        if(newConfig!=Config.ARGB_8888&&newConfig!=Config.ALPHA_8)throw new UnsupportedOperationException("Only ARGB_8888 and ALPHA_8 are implemented");
        if(newWidth<=0||newHeight<=0||newWidth>8192||newHeight>8192||(long)newWidth*newHeight>MAX_RENDER_PIXELS)throw new IllegalArgumentException("Bitmap dimensions");
        int allocated=capacity==0?pixels.length:capacity;
        if((long)newWidth*newHeight>(long)allocated)throw new IllegalArgumentException("New bitmap size is larger than the allocation");
        capacity=allocated;
        if(newWidth!=width||newHeight!=height)pixels=new int[newWidth*newHeight];
        width=newWidth;height=newHeight;config=newConfig;
    }
    public void setPremultiplied(boolean value){requireLive();if(!value)throw new UnsupportedOperationException("Unpremultiplied bitmap storage is not implemented");}
    public boolean sameAs(Bitmap other){
        requireLive();cpuRead();if(other==null)return false;other.requireLive();other.cpuRead();
        return other==this||other.width==width&&other.height==height&&other.config==config&&Arrays.equals(other.pixels,pixels);
    }
    public void copyPixelsFromBuffer(java.nio.Buffer source){
        requireMutable();if(source==null)throw new NullPointerException("source");
        if(config!=Config.ARGB_8888)throw new UnsupportedOperationException("Only ARGB_8888 pixel buffers are implemented");
        int count=width*height;
        if(source instanceof java.nio.IntBuffer){
            java.nio.IntBuffer ints=(java.nio.IntBuffer)source;if(ints.remaining()<count)throw new RuntimeException("Buffer not large enough for pixels");
            ints.get(pixels,0,count);return;
        }
        if(!(source instanceof java.nio.ByteBuffer))throw new UnsupportedOperationException("Only byte and int pixel buffers are implemented");
        java.nio.ByteBuffer bytes=(java.nio.ByteBuffer)source;if(bytes.remaining()<(long)count*4)throw new RuntimeException("Buffer not large enough for pixels");
        for(int i=0;i<count;i++){int r=bytes.get()&255,g=bytes.get()&255,b=bytes.get()&255,a=bytes.get()&255;pixels[i]=a<<24|r<<16|g<<8|b;}
    }
    public boolean isPremultiplied(){requireLive();return true;}
    public int getDensity() { return density; }
    public void setDensity(int value) { if (value < 0) throw new IllegalArgumentException("bitmap density"); density=value; }
    private int scaled(int size,int target) { if (density == 0 || target == 0 || density == target) return size; long result=((long)size*target+density/2)/density; if (target < 0 || result > Integer.MAX_VALUE) throw new IllegalArgumentException("scaled bitmap dimension"); return (int)result; }
    public int getScaledWidth(int target) { return scaled(width,target); }
    public int getScaledHeight(int target) { return scaled(height,target); }
    public int getScaledWidth(android.util.DisplayMetrics target) { return getScaledWidth(target.densityDpi); }
    public int getScaledHeight(android.util.DisplayMetrics target) { return getScaledHeight(target.densityDpi); }
    public int getScaledWidth(Canvas target) { return getScaledWidth(target.getDensity()); }
    public int getScaledHeight(Canvas target) { return getScaledHeight(target.getDensity()); }
    public Bitmap copy(Config config,boolean mutable) {
        requireLive();if(config!=Config.ARGB_8888&&config!=Config.ALPHA_8)throw new UnsupportedOperationException("Bitmap copy config");
        boolean onGpu=gpuDirty&&config==this.config&&config==Config.ARGB_8888;
        if(!onGpu)cpuRead();
        Bitmap copy=new Bitmap(width,height,onGpu?new int[pixels.length]:pixels.clone());
        if(onGpu)shiraz.runtime.NativeGpu.copyInto(gpu,copy.shirazGpuTarget());
        copy.config=config;
        if(this.config==Config.ALPHA_8||config==Config.ALPHA_8)for(int i=0;i<copy.pixels.length;i++)copy.pixels[i]&=0xff000000;
        copy.mutable=mutable;copy.density=density;copy.ninePatchChunk=getNinePatchChunk();return copy;
    }
    /** Internal surface-buffer transfer; no conversion or allocation. */
    public void shirazCopyPixelsInto(Bitmap destination) {
        requireLive();
        if(destination==null)throw new NullPointerException("destination");
        if(gpuDirty){destination.requireLive();if(!destination.mutable)throw new IllegalStateException("Bitmap is immutable");if(width!=destination.width||height!=destination.height||config!=destination.config)throw new IllegalArgumentException("Bitmap dimensions");shiraz.runtime.NativeGpu.copyInto(gpu,destination.shirazGpuTarget());return;}
        destination.requireMutable();
        if(width!=destination.width||height!=destination.height||config!=destination.config)
            throw new IllegalArgumentException("Bitmap buffer shape/config mismatch");
        System.arraycopy(pixels,0,destination.pixels,0,pixels.length);
    }
    /** Internal synchronous presentation; never exposes or retains the backing array. */
    public void shirazPostToSurface(shiraz.runtime.NativeSurface surface) {
        requireLive();
        if(config!=Config.ARGB_8888)throw new IllegalArgumentException("Presentation requires ARGB_8888");
        if(surface==null)throw new NullPointerException("surface");
        synchronized(surface){
            if(width!=surface.getWidth()||height!=surface.getHeight())
                throw new IllegalArgumentException("Bitmap and surface dimensions differ");
            if(gpuDirty)shiraz.runtime.NativeGpu.post(gpu);else surface.post(pixels);
        }
    }
    void requireLive() { if (pixels == null) throw new IllegalStateException("Bitmap is recycled"); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Config getConfig() { return config; }
    public boolean isRecycled() { return pixels == null; }
    public void recycle() { if(gpu!=0)releaseGpu();gpu=0;gpuDirty=false;pixels = null; }
    public int getByteCount() { return width * height * (config==Config.ALPHA_8?1:4); }
    public int getRowBytes(){return width*(config==Config.ALPHA_8?1:4);}
    public void copyPixelsToBuffer(java.nio.Buffer buffer) {
        requireLive();
        cpuRead();
        if (buffer == null) throw new NullPointerException("buffer");
        if (!(buffer instanceof java.nio.ByteBuffer)) throw new IllegalArgumentException("Pixel copy requires a byte buffer");
        java.nio.ByteBuffer output = (java.nio.ByteBuffer) buffer;
        int count = getByteCount();
        if (output.remaining() < count) throw new RuntimeException("Buffer not large enough for pixels");
        if (config == Config.ALPHA_8) { for (int color : pixels) output.put((byte)(color >>> 24)); return; }
        for (int color : pixels) { output.put((byte)(color >>> 24)); output.put((byte)(color >>> 16)); output.put((byte)(color >>> 8)); output.put((byte) color); }
    }
    public int getAllocationByteCount(){return (capacity==0?width*height:capacity)*4;}
    private int logicalColor(int color){return config==Config.ALPHA_8?color&0xff000000:color;}
    // Alpha-only bitmaps use bounded int backing; RGB is ignored, including native raster writes.
    int[] colorPixels(int color){requireLive();cpuRead();if(config!=Config.ALPHA_8)return pixels;int[] result=new int[pixels.length];for(int i=0;i<result.length;i++)result[i]=(pixels[i]&0xff000000)|(color&0xffffff);return result;}
    private int index(int x, int y) {
        requireLive();
        if (x < 0 || y < 0 || x >= width || y >= height) throw new IllegalArgumentException("Pixel outside bitmap");
        return y * width + x;
    }
    public int getPixel(int x, int y) { cpuRead(); return logicalColor(pixels[index(x, y)]); }
    public void setPixel(int x, int y, int color) { requireMutable(); pixels[index(x, y)] = logicalColor(color); }
    public void eraseColor(int color) { requireLive();if(!mutable)throw new IllegalStateException("Bitmap is immutable");if(gpuDirty&&gpuThread==Thread.currentThread()&&shiraz.runtime.NativeGpu.valid(gpu)){changed();shiraz.runtime.NativeGpu.clear(gpu,logicalColor(color));}else {cpuWrite();Arrays.fill(pixels,logicalColor(color));} }
    public void setPixels(int[] source,int offset,int stride,int x,int y,int w,int h) {
        requireMutable();if(source==null)throw new NullPointerException("source");
        if(stride<0)throw new UnsupportedOperationException("Negative row strides are not implemented");
        if(x<0||y<0||w<0||h<0||(long)x+w>width||(long)y+h>height||stride<w||offset<0)throw new IllegalArgumentException("Bitmap copy bounds");
        if(w==0||h==0)return;
        if((long)offset+(long)(h-1)*stride+w>source.length)throw new ArrayIndexOutOfBoundsException("Source too small");
        for(int row=0;row<h;row++){System.arraycopy(source,offset+row*stride,pixels,(y+row)*width+x,w);if(config==Config.ALPHA_8)for(int col=0;col<w;col++)pixels[(y+row)*width+x+col]&=0xff000000;}
    }
    public void getPixels(int[] target, int offset, int stride, int x, int y, int w, int h) {
        requireLive();
        cpuRead();
        if (target == null) throw new NullPointerException("target");
        if (stride < 0) throw new UnsupportedOperationException("Negative row strides are not implemented");
        if (x < 0 || y < 0 || w < 0 || h < 0 || (long) x + w > width || (long) y + h > height || stride < w || offset < 0)
            throw new IllegalArgumentException("Bitmap copy bounds");
        if (w == 0 || h == 0) return;
        if ((long) offset + (long) (h - 1) * stride + w > target.length) throw new ArrayIndexOutOfBoundsException("Target too small");
        for (int row = 0; row < h; ++row){System.arraycopy(pixels, (y + row) * width + x, target, offset + row * stride, w);if(config==Config.ALPHA_8)for(int col=0;col<w;col++)target[offset+row*stride+col]&=0xff000000;}
    }
}
