// SPDX-License-Identifier: GPL-3.0-only
package android.telecom;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
public abstract class ConnectionService extends Service {
 public static final String SERVICE_INTERFACE="android.telecom.ConnectionService";
 @Override public IBinder onBind(Intent intent){return null;}
}
