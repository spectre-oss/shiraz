// SPDX-License-Identifier: GPL-3.0-only
package android.media;
public final class AudioFormat {
 public static final int ENCODING_INVALID=0,ENCODING_DEFAULT=1,ENCODING_PCM_16BIT=2,ENCODING_PCM_8BIT=3,ENCODING_PCM_FLOAT=4;
 public static final int CHANNEL_IN_DEFAULT=1,CHANNEL_IN_MONO=16,CHANNEL_IN_STEREO=12,CHANNEL_OUT_MONO=4,CHANNEL_OUT_STEREO=12;
 private final int rate,mask,encoding;
 private AudioFormat(Builder b){rate=b.rate;mask=b.mask;encoding=b.encoding;}
 public int getSampleRate(){return rate;}public int getChannelMask(){return mask;}public int getChannelIndexMask(){return 0;}public int getEncoding(){return encoding;}public int getChannelCount(){return mask==CHANNEL_IN_MONO||mask==CHANNEL_OUT_MONO?1:mask==12?2:0;}
 public static final class Builder {
  private int rate,mask,encoding=ENCODING_PCM_16BIT;
  public Builder(){}public Builder(AudioFormat f){rate=f.rate;mask=f.mask;encoding=f.encoding;}
  public Builder setSampleRate(int value){if(value<8000||value>192000)throw new IllegalArgumentException("sample rate");rate=value;return this;}
  public Builder setChannelMask(int value){if(value!=CHANNEL_IN_MONO&&value!=CHANNEL_IN_STEREO&&value!=CHANNEL_OUT_MONO)throw new IllegalArgumentException("channel mask");mask=value;return this;}
  public Builder setEncoding(int value){if(value!=ENCODING_PCM_16BIT)throw new UnsupportedOperationException("Capture supports PCM16");encoding=value;return this;}
  public AudioFormat build(){return new AudioFormat(this);}
 }
}
