// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.File;
import java.io.FileInputStream;

/** Checks the probe's assertions on a host JVM; this is not APK runtime proof. */
public final class NetworkHostTest {
  public static void main(String[] args) throws Exception {
    for (String id : new String[] {"dns_localhost", "tcp_half_close", "http_chunked",
             "http_error_body", "http_post", "http_redirect", "http_read_timeout",
             "tls_socket_factory", "trust_manager"}) {
      System.out.println("HOST network." + id + ": " + NetworkChecks.run("network." + id));
    }
    for (String id : new String[] {"tls_verified", "tls_untrusted", "tls_hostname"}) {
      System.out.println("HOST network." + id + ": " + TlsNetworkChecks.run("network." + id,
          name -> new FileInputStream(new File(args[0], name))));
    }
    System.out.println("NETWORK_PROBE_HOST_PASS (not Android execution)");
  }
}
