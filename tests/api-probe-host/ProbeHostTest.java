// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.File;
import java.io.RandomAccessFile;
import org.json.JSONObject;

/** Host JVM checks are runner/helper evidence, never Android compatibility evidence. */
public final class ProbeHostTest {
  public static void main(String[] args) throws Exception {
    File report = new File(args[0], "report.json");
    ProbeFiles.write(report, new JSONObject().put("n", 42));
    if (ProbeFiles.read(report).getInt("n") != 42)
      throw new AssertionError("report round trip");
    ProbeFiles.write(report, new JSONObject().put("n", 43));
    if (ProbeFiles.read(report).getInt("n") != 43)
      throw new AssertionError("atomic replacement");
    try (RandomAccessFile file = new RandomAccessFile(report, "rw")) {
      file.setLength(2 * 1024 * 1024 + 1);
    }
    boolean rejected = false;
    try {
      ProbeFiles.read(report);
    } catch (java.io.IOException expected) {
      rejected = true;
    }
    if (!rejected)
      throw new AssertionError("oversized report accepted");
    rejected = false;
    try {
      ProbeFiles.result(null, "../../escape");
    } catch (IllegalArgumentException expected) {
      rejected = true;
    }
    if (!rejected)
      throw new AssertionError("unsafe token accepted");
    File directory = new File(args[0], "directory");
    directory.mkdir();
    new File(directory, "occupied").createNewFile();
    rejected = false;
    try {
      ProbeFiles.write(directory, new JSONObject());
    } catch (java.io.IOException expected) {
      rejected = true;
    }
    if (!rejected)
      throw new AssertionError("failed rename accepted");
    for (String id : new String[] {"runtime.reflection", "runtime.exceptions", "runtime.thread",
             "runtime.executor", "runtime.bytebuffer", "runtime.utf8", "runtime.zip",
             "runtime.regex", "runtime.json", "runtime.serialization", "runtime.gzip",
             "runtime.crc32", "runtime.array_reflection", "runtime.thread_local", "runtime.monitor",
             "runtime.scheduled_executor", "runtime.semaphore", "runtime.interrupt",
             "runtime.locale_format", "runtime.timezone", "network.inet_address",
             "network.loopback", "network.udp", "network.socket_timeout", "network.url_encode",
             "network.cookie", "crypto.sha256", "crypto.aes", "crypto.random", "crypto.hmac",
             "crypto.rsa_signature"}) {
      try {
        ProbeChecks.run(null, id);
      } catch (Throwable e) {
        throw new AssertionError("Host assertion failed: " + id, e);
      }
    }
    if (!ProbeProtocol.overdue(100, 12101) || ProbeProtocol.overdue(100, 12099)
        || !ProbeProtocol.overdue(100, 99))
      throw new AssertionError("deadline boundary or restart detection");
    JSONObject result = new JSONObject()
                            .put("id", "test")
                            .put("status", "PASS")
                            .put("durationMs", 1)
                            .put("workerPid", 42)
                            .put("isolated", true)
                            .put("observed", "passed");
    ProbeProtocol.validate(result, "test");
    for (String mutation : new String[] {"stale", "status", "isolation", "duration"}) {
      JSONObject invalid = new JSONObject(result.toString());
      if (mutation.equals("status"))
        invalid.put("status", "UNKNOWN");
      if (mutation.equals("isolation"))
        invalid.put("isolated", false);
      if (mutation.equals("duration"))
        invalid.put("durationMs", -1);
      rejected = false;
      try {
        ProbeProtocol.validate(invalid, mutation.equals("stale") ? "other" : "test");
      } catch (IllegalStateException expected) {
        rejected = true;
      }
      if (!rejected)
        throw new AssertionError("invalid protocol accepted: " + mutation);
    }
    result.put("isolated", false).put("executionMode", "in_process");
    ProbeProtocol.validate(result, "test");
    System.out.println("HOST_HELPERS_PASS (not Android execution)");
  }
}
