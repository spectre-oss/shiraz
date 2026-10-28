// SPDX-License-Identifier: GPL-3.0-only
package android.media;

/** Type declarations for optional recording modules; recording is unsupported. */
public class MediaRecorder {
 public interface OnErrorListener { void onError(MediaRecorder recorder,int what,int extra); }
 public interface OnInfoListener { void onInfo(MediaRecorder recorder,int what,int extra); }
 public MediaRecorder() { throw new UnsupportedOperationException("MediaRecorder capture is not implemented"); }
}
