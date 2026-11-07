// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;

/** Execute the exact APK's network assertions on ART, preserving every failure. */
public final class NetworkRuntimeMain {
  public static void main(String[] args) throws Exception {
    Class.forName("shiraz.runtime.HostTlsProvider").getMethod("install").invoke(null);
    String[] cases = {"dns_localhost", "tcp_half_close", "http_chunked", "http_error_body",
        "http_post", "http_redirect", "http_read_timeout", "tls_socket_factory", "trust_manager",
        "tls_verified", "tls_untrusted", "tls_hostname"};
    int failed = 0;
    for (String name : cases) {
      String id = "network." + name;
      try {
        String detail = name.startsWith("tls_") && !name.equals("tls_socket_factory")
            ? TlsNetworkChecks.run(id, asset -> new FileInputStream(new File(args[0], asset)))
            : NetworkChecks.run(id);
        System.out.println("NETWORK_RESULT\t" + id + "\tPASS\t" + detail);
      } catch (Throwable error) {
        failed++;
        System.out.println("NETWORK_RESULT\t" + id + "\tFAIL\t" + error);
        error.printStackTrace(System.err);
      }
    }
    System.out.println("NETWORK_SUMMARY " + (cases.length - failed) + "/" + cases.length);
    if (args.length > 1) {
      javax.net.ssl.HttpsURLConnection c = (javax.net.ssl.HttpsURLConnection)
          new java.net.URL(args[1]).openConnection(java.net.Proxy.NO_PROXY);
      c.setConnectTimeout(10000); c.setReadTimeout(10000);
      try {
        if (c.getResponseCode() != 200) throw new AssertionError("Internet HTTPS status " + c.getResponseCode());
        String cipher = c.getCipherSuite();
        int bytes = 0;
        try (InputStream in = c.getInputStream()) {
          byte[] buffer = new byte[4096];
          for (int n; bytes < 65536 && (n = in.read(buffer)) != -1;) bytes += n;
        }
        if (bytes == 0) throw new AssertionError("Empty Internet HTTPS body");
        System.out.println("NETWORK_INTERNET_PASS " + args[1] + " bytes=" + bytes + " cipher=" + cipher);
      } finally { c.disconnect(); }
    }
    if (failed != 0) System.exit(1);
  }
}
