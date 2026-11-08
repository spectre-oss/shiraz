// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.util.Arrays;
import org.json.JSONObject;

/** Shared, host-testable validation of the worker's durable result protocol. */
final class ProbeProtocol {
  static final long WORKER_MS = 8000, CONTROLLER_MS = 12000;
  static boolean overdue(long start, long now) {
    return now < start || now - start > CONTROLLER_MS;
  }
  static void validate(JSONObject result, String expectedId) throws Exception {
    if (!expectedId.equals(result.getString("id")))
      throw new IllegalStateException("Result id mismatch");
    String status = result.getString("status");
    if (!Arrays.asList("PASS", "FAIL", "SKIPPED", "UNSUPPORTED", "TIMEOUT").contains(status))
      throw new IllegalStateException("Unknown status");
    if (result.getLong("durationMs") < 0 || result.getInt("workerPid") <= 0)
      throw new IllegalStateException("Invalid duration or process identity");
    if (!result.getBoolean("isolated") && !"in_process".equals(result.optString("executionMode"))
        && !status.equals("FAIL"))
      throw new IllegalStateException("Non-isolated worker may only report infrastructure failure");
    result.getString("observed");
  }
}
