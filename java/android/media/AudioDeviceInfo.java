// SPDX-License-Identifier: GPL-3.0-only
package android.media;
/** Logical host default PCM route; physical device selection belongs to the host. */
public final class AudioDeviceInfo {
 public static final int TYPE_UNKNOWN=0;
 AudioDeviceInfo(){}
 public int getId(){return 1;}public int getType(){return TYPE_UNKNOWN;}public String getAddress(){return "default";}public CharSequence getProductName(){return "Host default audio";}
 public boolean isSource(){return false;}public boolean isSink(){return true;}
 public int[] getChannelCounts(){return new int[]{1,2};}public int[] getEncodings(){return new int[]{2};}public int[] getSampleRates(){return new int[0];}
 public int[] getChannelMasks(){return new int[]{12};}public int[] getChannelIndexMasks(){return new int[0];}
}
