// SPDX-License-Identifier: GPL-3.0-only
package android.location;
/** Callback contract only: the simulated provider has no satellite receiver. */
public final class GnssStatus {
 private GnssStatus(){}
 public abstract static class Callback {
  public void onStarted(){}
  public void onStopped(){}
  public void onFirstFix(int ttffMillis){}
  public void onSatelliteStatusChanged(GnssStatus status){}
 }
 public int getSatelliteCount(){return 0;}
 public boolean usedInFix(int satelliteIndex){throw new IndexOutOfBoundsException();}
}
