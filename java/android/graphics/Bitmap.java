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
    private void cpuRead(){if(gpu!=0&&gpuThread!=Thread.currentThread()){if(gpuDirty)throw new IllegalStateException("GPU screen bitmap used outside its render thread");releaseGpu();gpu=0;}if(gpu!=0&&!shiraz.runtime.NativeGpu.valid(gpu)){releaseGpu();gpu=0;gpuDirty=false;}if(gpuDirty){shiraz.runtime.NativeGpu.read(gpu,pixels);}normalizeOpacity();}
    private void cpuWrite(){cpuRead();changed();if(gpu!=0){releaseGpu();gpu=0;gpuDirty=false;}}
    long shirazGpuImage(){requireLive();if(!hasAlpha){cpuRead();if(gpu!=0){releaseGpu();gpu=0;gpuDirty=false;}}if(gpu!=0&&!shiraz.runtime.NativeGpu.valid(gpu)){releaseGpu();gpu=0;gpuDirty=false;}if(gpu==0){gpu=shiraz.runtime.NativeGpu.create(width,height,pixels);gpuLease=new GpuLease(gpu);gpuThread=Thread.currentThread();}return gpu;}
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


    private long allocationBytes;
    private boolean mutable=true;
    private boolean hasAlpha=true;
    private void normalizeOpacity(){if(!hasAlpha&&config!=Config.ALPHA_8)for(int i=0;i<pixels.length;i++)pixels[i]|=0xff000000;}
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
    public static Bitmap createBitmap(int[] colors,int width,int height,Config config){return createBitmap(null,colors,0,width,width,height,config);}
    public static Bitmap createBitmap(int[] colors,int offset,int stride,int width,int height,Config config){return createBitmap(null,colors,offset,stride,width,height,config);}
    public static Bitmap createBitmap(android.util.DisplayMetrics metrics,int[] colors,int width,int height,Config config){return createBitmap(metrics,colors,0,width,width,height,config);}
    public static Bitmap createBitmap(android.util.DisplayMetrics metrics,int[] colors,int offset,int stride,int width,int height,Config config){
        if(colors==null||config==null)throw new NullPointerException("Bitmap colors/config");
        if(width<=0||height<=0||Math.abs((long)stride)<width)throw new IllegalArgumentException("Bitmap color geometry");
        long last=(long)offset+(long)(height-1)*stride;
        if(offset<0||last<0||(long)offset+width>colors.length||last+width>colors.length)throw new ArrayIndexOutOfBoundsException("Bitmap color bounds");
        Bitmap result=createBitmap(width,height,config);
        for(int row=0;row<height;row++){
            int start=(int)((long)offset+(long)row*stride);
            for(int col=0;col<width;col++)result.pixels[row*width+col]=result.logicalColor(colors[start+col]);
        }
        if(metrics!=null)result.setDensity(metrics.densityDpi);
        result.mutable=false;
        return result;
    }
    public static Bitmap createBitmap(int width, int height, Config config) {
        Config storage=storageConfig(config);
        if(storage==Config.HARDWARE)throw new IllegalArgumentException("can't create mutable bitmap with Config.HARDWARE");
        Bitmap result=new Bitmap(width,height);result.config=storage;if(storage==Config.RGB_565)result.hasAlpha=false;return result;
    }
    /** Pixel storage stays int ARGB; RGB_565 is opaque and quantized on every logical read/write. */
    private static Config storageConfig(Config config){
        if(config==null)throw new NullPointerException("Bitmap config");
        if(config==Config.RGBA_F16)throw new UnsupportedOperationException("RGBA_F16 bitmaps are not implemented");
        // Android 4.4+ creates ARGB_8888 whenever ARGB_4444 is requested.
        return config==Config.ARGB_4444?Config.ARGB_8888:config;
    }
    private static int bytesPerPixel(Config config){return config==Config.ALPHA_8?1:config==Config.RGB_565?2:4;}
    private static int quantize565(int color){
        int r=(color>>>19)&31,g=(color>>>10)&63,b=(color>>>3)&31;
        return 0xff000000|(r<<3|r>>>2)<<16|(g<<2|g>>>4)<<8|(b<<3|b>>>2);
    }
    public static Bitmap createBitmap(int width,int height,Config config,boolean hasAlpha){return createBitmap(null,width,height,config,hasAlpha,ColorSpace.get(ColorSpace.Named.SRGB));}
    public static Bitmap createBitmap(int width,int height,Config config,boolean hasAlpha,ColorSpace colorSpace){return createBitmap(null,width,height,config,hasAlpha,colorSpace);}
    public static Bitmap createBitmap(android.util.DisplayMetrics metrics,int width,int height,Config config,boolean hasAlpha,ColorSpace colorSpace){
        if(colorSpace==null)throw new IllegalArgumentException("Bitmap color space is null");
        if(!colorSpace.isSrgb())throw new UnsupportedOperationException("Bitmap color space must be sRGB");

        Bitmap result=createBitmap(width,height,config);result.setHasAlpha(hasAlpha);if(metrics!=null)result.setDensity(metrics.densityDpi);return result;
    }
    public ColorSpace getColorSpace(){requireLive();return config==Config.ALPHA_8?null:ColorSpace.get(ColorSpace.Named.SRGB);}
    public void prepareToDraw(){requireLive();}
    public boolean hasAlpha(){requireLive();return hasAlpha;}
    public void setHasAlpha(boolean value){requireLive();if(config==Config.RGB_565||value==hasAlpha)return;cpuWrite();hasAlpha=value;normalizeOpacity();}
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
        Config storage=storageConfig(newConfig);
        if(storage==Config.HARDWARE)throw new IllegalArgumentException("Bitmap config");
        if(newWidth<=0||newHeight<=0||newWidth>8192||newHeight>8192||(long)newWidth*newHeight>MAX_RENDER_PIXELS)throw new IllegalArgumentException("Bitmap dimensions");
        long allocated=getAllocationByteCount();
        if((long)newWidth*newHeight*bytesPerPixel(storage)>allocated)throw new IllegalArgumentException("New bitmap size is larger than the allocation");
        allocationBytes=allocated;
        if(newWidth!=width||newHeight!=height)pixels=new int[newWidth*newHeight];
        // Android keeps an opaque request across reconfigure, except that RGB_565 is always opaque.
        if(storage==Config.RGB_565)hasAlpha=false;else if(config==Config.RGB_565)hasAlpha=true;
        width=newWidth;height=newHeight;config=storage;normalizeOpacity();
    }
    public void setPremultiplied(boolean value){requireLive();if(!value)throw new UnsupportedOperationException("Unpremultiplied bitmap storage is not implemented");}
    public boolean sameAs(Bitmap other){
        requireLive();cpuRead();if(other==null)return false;other.requireLive();other.cpuRead();
        return other==this||other.width==width&&other.height==height&&other.config==config&&Arrays.equals(other.pixels,pixels);
    }
    public void copyPixelsFromBuffer(java.nio.Buffer source){
        requireMutable();if(source==null)throw new NullPointerException("source");
        byte[] memory=new byte[getByteCount()];
        java.nio.ByteBuffer view=java.nio.ByteBuffer.wrap(memory).order(java.nio.ByteOrder.nativeOrder());
        if(source instanceof java.nio.ByteBuffer){java.nio.ByteBuffer bytes=(java.nio.ByteBuffer)source;if(bytes.remaining()<memory.length)throw new RuntimeException("Buffer not large enough for pixels");bytes.get(memory);}
        else if(source instanceof java.nio.IntBuffer){java.nio.IntBuffer ints=(java.nio.IntBuffer)source;if((long)ints.remaining()*4<memory.length)throw new RuntimeException("Buffer not large enough for pixels");int[] words=new int[(memory.length+3)/4];ints.get(words,0,memory.length/4);view.asIntBuffer().put(words,0,memory.length/4);}
        else if(source instanceof java.nio.ShortBuffer){java.nio.ShortBuffer shorts=(java.nio.ShortBuffer)source;if((long)shorts.remaining()*2<memory.length)throw new RuntimeException("Buffer not large enough for pixels");short[] words=new short[memory.length/2];shorts.get(words);view.asShortBuffer().put(words);}
        else throw new UnsupportedOperationException("Only byte, short and int pixel buffers are implemented");
        decodeMemory(memory);
    }
    // Android pixel memory: ALPHA_8 bytes, native-order RGB_565 halfwords, premultiplied R,G,B,A bytes.
    private byte[] encodeMemory(){
        byte[] memory=new byte[getByteCount()];int count=width*height;
        if(config==Config.ALPHA_8){for(int i=0;i<count;i++)memory[i]=(byte)(pixels[i]>>>24);return memory;}
        boolean little=java.nio.ByteOrder.nativeOrder()==java.nio.ByteOrder.LITTLE_ENDIAN;
        for(int i=0;i<count;i++){
            int color=logicalColor(pixels[i]);
            if(config==Config.RGB_565){
                int value=((color>>>19)&31)<<11|((color>>>10)&63)<<5|((color>>>3)&31);
                memory[2*i+(little?0:1)]=(byte)value;memory[2*i+(little?1:0)]=(byte)(value>>>8);continue;
            }
            int a=color>>>24;
            memory[4*i]=(byte)premultiply((color>>>16)&255,a);memory[4*i+1]=(byte)premultiply((color>>>8)&255,a);
            memory[4*i+2]=(byte)premultiply(color&255,a);memory[4*i+3]=(byte)a;
        }
        return memory;
    }
    private void decodeMemory(byte[] memory){
        int count=width*height;
        if(config==Config.ALPHA_8){for(int i=0;i<count;i++)pixels[i]=(memory[i]&255)<<24;return;}
        boolean little=java.nio.ByteOrder.nativeOrder()==java.nio.ByteOrder.LITTLE_ENDIAN;
        for(int i=0;i<count;i++){
            if(config==Config.RGB_565){
                int value=(memory[2*i+(little?0:1)]&255)|(memory[2*i+(little?1:0)]&255)<<8;
                int r=value>>>11,g=(value>>>5)&63,b=value&31;
                pixels[i]=0xff000000|(r<<3|r>>>2)<<16|(g<<2|g>>>4)<<8|(b<<3|b>>>2);continue;
            }
            int a=memory[4*i+3]&255;
            pixels[i]=logicalColor(a<<24|unpremultiply(memory[4*i]&255,a)<<16|unpremultiply(memory[4*i+1]&255,a)<<8|unpremultiply(memory[4*i+2]&255,a));
        }
    }
    // Same rounding as the AndroidBitmap JNI provider (native/android_bitmap.cpp).
    private static int premultiply(int value,int alpha){return (value*alpha+127)/255;}
    private static int unpremultiply(int value,int alpha){return alpha==0?0:Math.min(255,(value*255+alpha/2)/alpha);}
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
        requireLive();if(config==Config.HARDWARE)throw new UnsupportedOperationException("Hardware bitmap copies are not implemented");config=storageConfig(config);
        boolean onGpu=gpuDirty&&config==this.config&&config==Config.ARGB_8888;
        if(!onGpu)cpuRead();
        Bitmap copy=new Bitmap(width,height,onGpu?new int[pixels.length]:pixels.clone());
        if(onGpu)shiraz.runtime.NativeGpu.copyInto(gpu,copy.shirazGpuTarget());
        copy.config=config;copy.hasAlpha=config!=Config.RGB_565&&hasAlpha;
        if(this.config==Config.ALPHA_8||config==Config.ALPHA_8)for(int i=0;i<copy.pixels.length;i++)copy.pixels[i]&=0xff000000;
        else if(this.config==Config.RGB_565||config==Config.RGB_565)for(int i=0;i<copy.pixels.length;i++)copy.pixels[i]=copy.logicalColor(logicalColor(copy.pixels[i]));
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
    public int getByteCount() { return width * height * bytesPerPixel(config); }
    public int getRowBytes(){return width*bytesPerPixel(config);}
    public void copyPixelsToBuffer(java.nio.Buffer buffer) {
        requireLive();
        cpuRead();
        if (buffer == null) throw new NullPointerException("buffer");
        byte[] memory=encodeMemory();
        java.nio.ByteBuffer view=java.nio.ByteBuffer.wrap(memory).order(java.nio.ByteOrder.nativeOrder());
        if(buffer instanceof java.nio.ByteBuffer){java.nio.ByteBuffer output=(java.nio.ByteBuffer)buffer;if(output.remaining()<memory.length)throw new RuntimeException("Buffer not large enough for pixels");output.put(memory);}
        else if(buffer instanceof java.nio.IntBuffer){java.nio.IntBuffer output=(java.nio.IntBuffer)buffer;if((long)output.remaining()*4<memory.length)throw new RuntimeException("Buffer not large enough for pixels");int[] words=new int[memory.length/4];view.asIntBuffer().get(words);output.put(words);}
        else if(buffer instanceof java.nio.ShortBuffer){java.nio.ShortBuffer output=(java.nio.ShortBuffer)buffer;if((long)output.remaining()*2<memory.length)throw new RuntimeException("Buffer not large enough for pixels");short[] words=new short[memory.length/2];view.asShortBuffer().get(words);output.put(words);}
        else throw new UnsupportedOperationException("Only byte, short and int pixel buffers are implemented");
    }
    public int getAllocationByteCount(){return allocationBytes==0?getByteCount():(int)allocationBytes;}
    private int logicalColor(int color){return config==Config.ALPHA_8?color&0xff000000:config==Config.RGB_565?quantize565(color):hasAlpha?color:color|0xff000000;}
    // Alpha-only bitmaps use bounded int backing; RGB is ignored, including native raster writes.
    int[] colorPixels(int color){requireLive();cpuRead();if(config!=Config.ALPHA_8)return pixels;int[] result=new int[pixels.length];for(int i=0;i<result.length;i++)result[i]=(pixels[i]&0xff000000)|(color&0xffffff);return result;}
    private int index(int x, int y) {
        requireLive();
        if (x < 0 || y < 0 || x >= width || y >= height) throw new IllegalArgumentException("Pixel outside bitmap");
        return y * width + x;
    }
    public int getPixel(int x, int y) { cpuRead(); return logicalColor(pixels[index(x, y)]); }
    public void setPixel(int x, int y, int color) { requireMutable(); pixels[index(x, y)] = logicalColor(color); }
    /** Shiraz surface post: force opaque pixels (optionally RGB_565-quantized) in one native pass. */
    public void shirazOpaque(boolean rgb565){requireLive();if(!mutable)throw new IllegalStateException("Bitmap is immutable");cpuWrite();shiraz.runtime.NativeBitmap.opaque(pixels,rgb565);}
    public void eraseColor(int color) { requireLive();if(!mutable)throw new IllegalStateException("Bitmap is immutable");if(gpuDirty&&gpuThread==Thread.currentThread()&&shiraz.runtime.NativeGpu.valid(gpu)){changed();shiraz.runtime.NativeGpu.clear(gpu,logicalColor(color));}else {cpuWrite();shiraz.runtime.NativeBitmap.clear(pixels,logicalColor(color));} }
    public void setPixels(int[] source,int offset,int stride,int x,int y,int w,int h) {
        requireMutable();if(source==null)throw new NullPointerException("source");
        if(stride<0)throw new UnsupportedOperationException("Negative row strides are not implemented");
        if(x<0||y<0||w<0||h<0||(long)x+w>width||(long)y+h>height||stride<w||offset<0)throw new IllegalArgumentException("Bitmap copy bounds");
        if(w==0||h==0)return;
        if((long)offset+(long)(h-1)*stride+w>source.length)throw new ArrayIndexOutOfBoundsException("Source too small");
        for(int row=0;row<h;row++){System.arraycopy(source,offset+row*stride,pixels,(y+row)*width+x,w);if(config==Config.ALPHA_8||config==Config.RGB_565)for(int col=0;col<w;col++)pixels[(y+row)*width+x+col]=logicalColor(pixels[(y+row)*width+x+col]);}
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
        for (int row = 0; row < h; ++row){System.arraycopy(pixels, (y + row) * width + x, target, offset + row * stride, w);if(config==Config.ALPHA_8||config==Config.RGB_565)for(int col=0;col<w;col++)target[offset+row*stride+col]=logicalColor(target[offset+row*stride+col]);}
    }
}
