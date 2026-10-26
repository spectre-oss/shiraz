// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Optional OS GStreamer decoder bridge. No codec plugins are bundled. */
public final class NativeVideo {
 private static final boolean loaded;
 static {boolean ok=false;try{System.loadLibrary("shiraz-video");ok=true;}catch(UnsatisfiedLinkError unavailable){System.err.println("SHIRAZ_VIDEO unavailable: OS GStreamer backend not found");}loaded=ok;}
 private NativeVideo(){}
 private static native String[] nativeCodecs();
 public static String[] codecs(){return loaded?nativeCodecs():new String[0];}
 public static native long opusOpen(byte[] config);
 public static native byte[] opusDecode(long handle,byte[] packet);
 public static native void opusClose(long handle);
 public static native long open(String mime,int width,int height,byte[] config);
 public static native boolean canQueue(long handle);
 public static native void queue(long handle,byte[] bytes,long timeUs,boolean eos);
 /** width, height, timestamp microseconds, output slot; null until output is ready. */
 public static native long[] poll(long handle);
 public static native void releaseFrame(long handle,int index,long window,boolean render);
 public static native void close(long handle);
}
