// SPDX-License-Identifier: GPL-3.0-only
package android.media;
/** No host video-encoding profile is advertised until video recording exists. */
public class CamcorderProfile {
 public static final int QUALITY_LOW=0,QUALITY_HIGH=1,QUALITY_QCIF=2,QUALITY_CIF=3,QUALITY_480P=4,QUALITY_720P=5,QUALITY_1080P=6,QUALITY_QVGA=7,QUALITY_2160P=8,QUALITY_VGA=9;
 public static final int QUALITY_TIME_LAPSE_LOW=1000,QUALITY_TIME_LAPSE_HIGH=1001,QUALITY_HIGH_SPEED_LOW=2000,QUALITY_HIGH_SPEED_HIGH=2001;
 public int duration,quality,fileFormat,videoCodec,videoBitRate,videoFrameRate,videoFrameWidth,videoFrameHeight,audioCodec,audioBitRate,audioSampleRate,audioChannels;
 private CamcorderProfile(){}
 public static boolean hasProfile(int quality){return false;}
 public static boolean hasProfile(int cameraId,int quality){return false;}
 public static CamcorderProfile get(int quality){throw new UnsupportedOperationException("Host video recording profiles unavailable");}
 public static CamcorderProfile get(int cameraId,int quality){return get(quality);}
}
