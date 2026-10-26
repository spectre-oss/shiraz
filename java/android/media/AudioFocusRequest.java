// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.os.Handler;
/** Immutable request for cooperative audio focus within this APK process. */
public final class AudioFocusRequest {
 final int gain;final AudioAttributes attributes;final AudioManager.OnAudioFocusChangeListener listener;final Handler handler;final boolean delayed,pause;
 private AudioFocusRequest(Builder b){gain=b.gain;attributes=b.attributes;listener=b.listener;handler=b.handler;delayed=b.delayed;pause=b.pause;}
 public int getFocusGain(){return gain;}public AudioAttributes getAudioAttributes(){return attributes;}public boolean acceptsDelayedFocusGain(){return delayed;}public boolean willPauseWhenDucked(){return pause;}
 public AudioManager.OnAudioFocusChangeListener getOnAudioFocusChangeListener(){return listener;}
 public static final class Builder {
  int gain;AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build();AudioManager.OnAudioFocusChangeListener listener;Handler handler;boolean delayed,pause;
  public Builder(int gain){setFocusGain(gain);}public Builder(AudioFocusRequest source){if(source==null)throw new NullPointerException("request");gain=source.gain;attributes=source.attributes;listener=source.listener;handler=source.handler;delayed=source.delayed;pause=source.pause;}
  public Builder setFocusGain(int value){if(value<1||value>4)throw new IllegalArgumentException("focus gain");gain=value;return this;}
  public Builder setAudioAttributes(AudioAttributes value){if(value==null)throw new NullPointerException("attributes");attributes=value;return this;}
  public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener value){if(value==null)throw new NullPointerException("listener");listener=value;handler=null;return this;}
  public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener value,Handler target){if(target==null)throw new NullPointerException("handler");setOnAudioFocusChangeListener(value);handler=target;return this;}
  public Builder setAcceptsDelayedFocusGain(boolean value){delayed=value;return this;}public Builder setWillPauseWhenDucked(boolean value){pause=value;return this;}
  public AudioFocusRequest build(){if((delayed||pause)&&listener==null)throw new IllegalStateException("focus listener required");return new AudioFocusRequest(this);}
 }
}
