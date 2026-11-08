// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import org.json.JSONObject;

/** Requests real process isolation; never kills or tests in a misassigned process. */
public final class ProbeService extends Service {
  private boolean started;
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (started || intent == null)
      return START_NOT_STICKY;
    started = true;
    String id = intent.getStringExtra("id"), token = intent.getStringExtra("token");
    if (Process.myPid() == intent.getIntExtra("controllerPid", Process.myPid())) {
      try {
        ProbeFiles.write(ProbeFiles.result(this, token),
            new JSONObject()
                .put("id", id)
                .put("status", "FAIL")
                .put("observed", "Runtime ignored service process isolation; test not executed")
                .put("durationMs", 0)
                .put("workerPid", Process.myPid())
                .put("isolated", false));
      } catch (Throwable e) {
        android.util.Log.e("ShirazApiProbe", "Cannot persist infrastructure failure", e);
      }
      stopSelf();
    } else
      ProbeExecutor.start(this, id, token, true);
    return START_NOT_STICKY;
  }
}
