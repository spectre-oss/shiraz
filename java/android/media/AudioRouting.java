// SPDX-License-Identifier: GPL-3.0-only
package android.media;
public interface AudioRouting {
 interface OnRoutingChangedListener {void onRoutingChanged(AudioRouting router);}
 boolean setPreferredDevice(AudioDeviceInfo device);
 AudioDeviceInfo getPreferredDevice();AudioDeviceInfo getRoutedDevice();
 void addOnRoutingChangedListener(OnRoutingChangedListener listener,android.os.Handler handler);
 void removeOnRoutingChangedListener(OnRoutingChangedListener listener);
}
