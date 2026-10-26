// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Desktop endpoint has no vibration hardware. */
public class Vibrator {
 public boolean hasVibrator(){return false;}public boolean hasAmplitudeControl(){return false;}
 public void vibrate(long milliseconds){if(milliseconds<0)throw new IllegalArgumentException("duration");}
 public void vibrate(long[] pattern,int repeat){if(pattern==null||repeat< -1||repeat>=pattern.length)throw new IllegalArgumentException("pattern");}
 public void cancel(){}
}
