// SPDX-License-Identifier: GPL-3.0-only
package android.os;
/** Lookup boundary for Android's system Binder services.
 * Shiraz exposes its implemented services through Context; there is no Android
 * system service manager or privileged system Binder endpoint on the host.
 */
public final class ServiceManager {
 private ServiceManager(){}
 public static IBinder getService(String name){return null;}
 public static IBinder checkService(String name){return null;}
 public static String[] listServices(){return new String[0];}
}
