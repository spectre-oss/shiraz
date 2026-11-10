// SPDX-License-Identifier: GPL-3.0-only
package android.media;
/** Logical host default PCM route; physical device selection belongs to the host. */
public final class AudioDeviceInfo {
 public static final int TYPE_UNKNOWN=0;
 private final boolean source;
 AudioDeviceInfo(){this(false);}AudioDeviceInfo(boolean source){this.source=source;}
 public int getId(){return source?2:1;}public int getType(){return TYPE_UNKNOWN;}public String getAddress(){return "default";}public CharSequence getProductName(){return source?"Host default microphone":"Host default audio";}
 public boolean isSource(){return source;}public boolean isSink(){return !source;}
 public int[] getChannelCounts(){return new int[]{1,2};}public int[] getEncodings(){return new int[]{2};}public int[] getSampleRates(){return new int[0];}
 public int[] getChannelMasks(){return source?new int[]{16,12}:new int[]{12};}public int[] getChannelIndexMasks(){return new int[0];}
}
