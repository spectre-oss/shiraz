// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Published SDL direct-touch capability, refreshed on the presentation thread. */
public final class HostInput {
 private HostInput(){}
 private static volatile boolean touchscreen;
 public static boolean hasTouchscreen(){return DeviceProfile.selected()?Boolean.parseBoolean(DeviceProfile.get("input.touchscreen","false")):touchscreen;}
 public static void updateTouchscreen(boolean value){touchscreen=value;}
}
