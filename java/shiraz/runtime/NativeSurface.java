// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

/** Internal CPU surface bridge. Not a replacement for android.view.Surface yet. */
public final class NativeSurface implements AutoCloseable {
    static { System.loadLibrary("shiraz-jni"); }
    public static native boolean hasTouchscreen();
    public static native void prepareNativeLooper();
    public static native int pollNativeLooper();
    public static native boolean requestTextInput(boolean visible);
    public static native boolean usesWaylandTextInput();
    public static native boolean setClipboardText(byte[] utf8);
    public static native byte[] getClipboardText();
    /** Own the real SDL window before Application/resources are initialized. */
    public static final class HostDisplay implements AutoCloseable {
        public final int width,height,densityDpi;
        private HostDisplay(int[] geometry){width=geometry[0];height=geometry[1];densityDpi=geometry[2];}
        public void close(){discardPreparedDisplay();}
    }
    public static HostDisplay prepareDisplay(int width,int height){return new HostDisplay(prepareDisplayNative(width,height));}
    private static native int[] prepareDisplayNative(int width,int height);
    private static native void discardPreparedDisplay();
    private long handle;
    private int width;
    private int height;

    public NativeSurface(int width, int height) {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Invalid native surface dimensions");
        this.width = width;
        this.height = height;
        handle = create(width, height);
        if (handle == 0) throw new IllegalStateException("Native surface creation failed");
    }
    private void requireOpen() {
        if (handle == 0) throw new IllegalStateException("Surface is closed");
    }
    public synchronized int getWidth() { return width; }
    public synchronized int getHeight() { return height; }
    public synchronized void requestWindowTitle(String title){requireOpen();String checked=android.view.Window.shirazTitle(title);requestTitle(handle,checked.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    public synchronized void requestWindowSize(int width,int height){requireOpen();if(width<1||height<1||width>8192||height>8192||(long)width*height>16*1024*1024)throw new IllegalArgumentException("Invalid window dimensions");requestSize(handle,width,height);}
    public synchronized void resize(int width,int height) {
        requireOpen();
        if(width<1||height<1||width>8192||height>8192||(long)width*height>16*1024*1024)throw new IllegalArgumentException("Invalid native surface dimensions");
        if(this.width==width&&this.height==height)return;
        resizeBuffer(handle,width,height);this.width=width;this.height=height;
    }
    public synchronized void post(int[] argb) {
        requireOpen();
        if (argb == null) throw new NullPointerException("pixels");
        if (argb.length != width * height) throw new IllegalArgumentException("Pixel array must match surface size");
        postPixels(handle, argb);
    }
    public synchronized int[] snapshot() { requireOpen(); return readPixels(handle); }
    /** Bounded static-frame presentation for bridge diagnostics. */
    public synchronized void present(int frames) {
        requireOpen();
        if (frames < 1 || frames > 600) throw new IllegalArgumentException("frames must be between 1 and 600");
        presentPixels(handle, frames);
    }
    public synchronized void presentUntilClose() { requireOpen(); presentPixels(handle, -1); }
    @Override public synchronized void close() {
        if (handle != 0) { destroy(handle); handle = 0; }
    }
    public interface FrameCallback {
        default void onTestLocation(double latitude,double longitude,float accuracy){throw new UnsupportedOperationException("No test location backend");}
        default void onWindowFocus(boolean focused){}
        default void onWindowVisibility(boolean visible){}
        boolean onFrame();
        void onKey(int action, int key, int scan, int repeat, int meta, int flags, long down, long time);
        void onText(String text, long time);
        default void onScroll(float x,float y,float horizontal,float vertical,long time){}
        default void onTouch(int action,int[] ids,float[] xs,float[] ys,float[] pressures,long time){
            if(ids.length==1)onPointer(action,xs[0],ys[0],0x1002,time);
        }
        void onPointer(int action, float x, float y, int source, long eventTime);
    }
    public interface ResizeCallback extends FrameCallback {
        void onResize(int width,int height);
        default void onDisplayChanged(int width,int height,int densityDpi){onResize(width,height);}
    }
    public synchronized void presentDispatched(int frames, FrameCallback callback) {
        requireOpen();
        if (frames != -1 && (frames < 1 || frames > 600)) throw new IllegalArgumentException("Invalid frame limit");
        if (callback == null) throw new NullPointerException("callback");
        lastPointerIcon=null;
        try{presentDispatch(handle, frames, callback);}finally{lastPointerIcon=null;}
    }
    private android.view.PointerIcon lastPointerIcon;
    private int lastPointerGeneration;
    public synchronized void requestPointerIcon(int type){requireOpen();requestCursor(handle,type);lastPointerIcon=null;}
    public synchronized void requestPointerIcon(android.view.PointerIcon icon){
        requireOpen();
        if(icon==null||icon.getType()!=android.view.PointerIcon.TYPE_CUSTOM){requestPointerIcon(icon==null?1000:icon.getType());return;}
        android.graphics.Bitmap bitmap=icon.shirazBitmap();
        int generation=bitmap.getGenerationId();
        if(icon==lastPointerIcon&&generation==lastPointerGeneration)return;
        int width=bitmap.getWidth(),height=bitmap.getHeight();
        int[] pixels=new int[width*height];bitmap.getPixels(pixels,0,width,0,0,width,height);
        requestColorCursor(handle,pixels,width,height,(int)icon.shirazHotSpotX(),(int)icon.shirazHotSpotY());
        lastPointerIcon=icon;lastPointerGeneration=generation;
    }
    private static native void requestColorCursor(long handle,int[] pixels,int width,int height,int hotX,int hotY);
    private static native void requestCursor(long handle,int type);
    public static native float refreshRate();
    private static native void presentDispatch(long handle, int frames, FrameCallback callback);
    public synchronized void requestKeepScreenOn(boolean value){requireOpen();requestScreenAwake(handle,value);}
    private static native void requestScreenAwake(long handle,boolean value);
    private static native void requestTitle(long handle,byte[] title);
    private static native void requestSize(long handle,int width,int height);
    private static native void resizeBuffer(long handle,int width,int height);
    private static native long create(int width, int height);
    private static native void destroy(long handle);
    private static native void postPixels(long handle, int[] pixels);
    private static native int[] readPixels(long handle);
    private static native void presentPixels(long handle, int frames);
}
