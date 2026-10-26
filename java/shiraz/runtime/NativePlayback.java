// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
public final class NativePlayback {
 private NativePlayback(){}
 static{System.loadLibrary("shiraz-playback");}
 public static native byte[] decodeFile(byte[] data,int[] format);
 public static native long decoderOpen(byte[] config);
 public static native int[] decoderFormat(long handle);
 public static native byte[] decode(long handle,byte[] packet);
 public static native long trackOpen(int rate,int channels,int bytes);
 public static native int write(long handle,byte[] pcm,int offset,int length,boolean blocking,float gain);
 public static native long position(long handle);
 public static native void control(long handle,int action);
 public static native void close(long handle);
}
