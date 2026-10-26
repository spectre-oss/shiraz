// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import java.nio.charset.StandardCharsets;
/** Serialized local speech synthesis. Playback and Android queue semantics are separate. */
public final class NativeSpeech {
 private NativeSpeech(){}
 static {System.loadLibrary("shiraz-speech");}
 public static native int initialize(String dataDirectory);
 /** Alternating installed voice identifier and primary language tag; no external MBROLA voices. */
 public static native String[] installedVoices();
 public static short[] synthesize(String text,String voice,int wordsPerMinute,int pitch){
  if(text==null||voice==null)throw new NullPointerException("speech arguments");
  if(text.length()>4096||text.indexOf('\0')>=0)throw new IllegalArgumentException("Speech text limit");
  if(!voice.matches("[A-Za-z0-9_+/\\-]{1,64}")||voice.startsWith("/")||voice.contains("//"))throw new IllegalArgumentException("Speech voice identifier");
  return synthesizeUtf8(text.getBytes(StandardCharsets.UTF_8),voice,wordsPerMinute,pitch);
 }
 /** Playback returns 0 after drain, 1 when canceled, or -1 on an output failure. */
 public static native long createPlayback();
 public static native boolean isPlaybackRunning(long handle);
 public static native void cancelPlayback(long handle);
 public static native void releasePlayback(long handle);
 public static native int play(long handle,short[] samples,int sampleRate);
 private static native short[] synthesizeUtf8(byte[] text,String voice,int wordsPerMinute,int pitch);
}
