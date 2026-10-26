// SPDX-License-Identifier: GPL-3.0-only
package android.media;
public final class PlaybackParams {
 private float speed=1,pitch=1;private int fallback;
 public PlaybackParams allowDefaults(){return this;}
 public PlaybackParams setSpeed(float value){if(!Float.isFinite(value)||value<0)throw new IllegalArgumentException("speed");speed=value;return this;}
 public PlaybackParams setPitch(float value){if(!Float.isFinite(value)||value<=0)throw new IllegalArgumentException("pitch");pitch=value;return this;}
 public PlaybackParams setAudioFallbackMode(int value){fallback=value;return this;}
 public float getSpeed(){return speed;}public float getPitch(){return pitch;}public int getAudioFallbackMode(){return fallback;}
}
