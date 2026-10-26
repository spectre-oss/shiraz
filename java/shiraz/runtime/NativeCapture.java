// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Native handles remain private to consent-enforcing Java capture owners. */
public final class NativeCapture {
 private NativeCapture(){}
 static {System.loadLibrary("shiraz-capture");}
 public static native long microphoneOpen(int sampleRate,int channels);
 public static native int microphoneRead(long handle,byte[] bytes,int offset,int length);
 public static native long cameraOpen();
 public static native int[] cameraFrame(long handle);
 public static native int[] cameraSize(long handle);
 public static native long aacOpen(int sampleRate,int channels,int bitrate);
 public static native byte[] aacEncode(long handle,byte[] pcm);
 public static native void close(long handle);
}
