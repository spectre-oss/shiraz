// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Shared assertion execution. Local timeouts block successors until process restart. */
final class ProbeExecutor {
  static volatile boolean localBlocked;
  static void start(Context context, String id, String token, boolean isolated) {
    AtomicBoolean finished = new AtomicBoolean();
    long begin = SystemClock.elapsedRealtime();
    Thread watchdog = new Thread(() -> {
      try {
        Thread.sleep(ProbeProtocol.WORKER_MS);
      } catch (InterruptedException ignored) {
        return;
      }
      finish(context, finished, token, id, "TIMEOUT", "Test deadline exceeded (8 seconds)", begin,
          isolated);
    }, "probe-watchdog");
    watchdog.setDaemon(true);
    watchdog.start();
    Thread worker = new Thread(() -> {
      String status = "PASS", detail;
      try {
        detail = ProbeChecks.run(context, id);
      } catch (ProbeChecks.Skip e) {
        status = "SKIPPED";
        detail = e.getMessage();
      } catch (UnsupportedOperationException | NoClassDefFoundError | NoSuchMethodError
          | NoSuchFieldError | AbstractMethodError | UnsatisfiedLinkError e) {
        status = "UNSUPPORTED";
        detail = describe(e);
      } catch (Throwable e) {
        status = "FAIL";
        detail = describe(e);
      }
      finish(context, finished, token, id, status, detail, begin, isolated);
    }, "probe-case");
    worker.setDaemon(true);
    worker.start();
  }
  private static String describe(Throwable error) {
    StringBuilder text = new StringBuilder(error.toString());
    StackTraceElement[] stack = error.getStackTrace();
    for (int i = 0; i < Math.min(stack.length, 8); i++) text.append("\n at ").append(stack[i]);
    if (error.getCause() != null)
      text.append("\n caused by ").append(error.getCause());
    return text.toString();
  }
  private static void finish(Context context, AtomicBoolean finished, String token, String id,
      String status, String detail, long begin, boolean isolated) {
    if (!finished.compareAndSet(false, true))
      return;
    if (!isolated && status.equals("TIMEOUT"))
      localBlocked = true;
    try {
      JSONObject result = new JSONObject()
                              .put("id", id)
                              .put("status", status)
                              .put("observed", detail)
                              .put("durationMs", SystemClock.elapsedRealtime() - begin)
                              .put("workerPid", Process.myPid())
                              .put("isolated", isolated)
                              .put("executionMode", isolated ? "isolated_process" : "in_process");
      ProbeFiles.write(ProbeFiles.result(context, token), result);
    } catch (Throwable e) {
      if (!isolated)
        localBlocked = true;
      android.util.Log.e("ShirazApiProbe", "Cannot persist result", e);
    }
    if (isolated)
      Process.killProcess(Process.myPid());
  }
}
