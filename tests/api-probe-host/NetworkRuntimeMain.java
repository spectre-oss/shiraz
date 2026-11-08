// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;

/** Execute the exact APK's network assertions on ART, preserving every failure. */
public final class NetworkRuntimeMain {
  public static void main(String[] args) throws Exception {
    if (args.length > 2 && !args[2].equals("-")) {
      java.lang.reflect.Field fingerprint = Class.forName("org.shiraz.apiprobe.BuildConfig")
          .getDeclaredField("FINGERPRINT");
      fingerprint.setAccessible(true);
      if (!args[2].equals(fingerprint.get(null)))
        throw new IllegalStateException("API probe APK does not match the current suite sources; rebuild it");
      System.out.println("NETWORK_PROBE_APK_FINGERPRINT_VERIFIED " + args[2]);
    }
    Class.forName("shiraz.runtime.HostTlsProvider").getMethod("install").invoke(null);
    String[] cases = {"dns_localhost", "tcp_half_close", "http_chunked", "http_error_body",
        "http_post", "http_redirect", "http_read_timeout", "tls_socket_factory", "trust_manager",
        "tls_verified", "tls_untrusted", "tls_hostname", "tls_handshake",
             "tls_engine_verified", "tls_engine_untrusted", "tls_engine_hostname",
             "local_stream", "local_timeout", "local_rebind", "local_close_read", "local_close_accept",
             "ipv6_tcp", "ipv6_udp", "ipv6_http", "ipv6_tls", "tls_stream_clean", "tls_stream_abrupt", "tls_stream_timeout", "android_trust_chain", "network_identity", "network_dns", "network_socket", "network_http", "network_bind", "network_unknown", "network_https", "network_cleartext_policy"};
    int failed = 0;
    for (String name : cases) {
      String id = "network." + name;
      try {
        String detail;
        if (name.startsWith("ipv6_")) detail=Ipv6Checks.run(id,android.net.ConnectivityManager.shirazForHost(),asset -> new FileInputStream(new File(args[0],asset)));
        else if (name.startsWith("tls_stream_")) detail=TlsStreamChecks.run(id,asset -> new FileInputStream(new File(args[0],asset)));
        else if (name.equals("android_trust_chain")) detail=TrustManagerChecks.run(asset -> new FileInputStream(new File(args[0],asset)));
        else if (name.equals("network_cleartext_policy")) detail = NetworkApiChecks.runPolicy(true);
        else if (name.equals("network_https"))
          detail = NetworkApiChecks.runTls(android.net.ConnectivityManager.shirazForHost(),
              asset -> new FileInputStream(new File(args[0], asset)));
        else if (name.startsWith("network_"))
          detail = NetworkApiChecks.run(id, android.net.ConnectivityManager.shirazForHost());
        else if (name.startsWith("local_")) detail = LocalSocketChecks.run(id);
        else if (name.startsWith("tls_") && !name.equals("tls_socket_factory"))
          detail = TlsNetworkChecks.run(id, asset -> new FileInputStream(new File(args[0], asset)));
        else detail = NetworkChecks.run(id);
        System.out.println("NETWORK_RESULT\t" + id + "\tPASS\t" + detail);
      } catch (Throwable error) {
        failed++;
        System.out.println("NETWORK_RESULT\t" + id + "\tFAIL\t" + error);
        error.printStackTrace(System.err);
      }
    }
    int total=cases.length;
    if(args.length>3&&!args[3].equals("-")){
      total++;
      try{
        android.content.res.Resources resources=android.content.res.Resources.shirazOpen(args[3],360,640);
        String detail;try{detail=NetworkXmlChecks.run(resources,"org.shiraz.apiprobe");}finally{resources.shirazClose();}
        System.out.println("NETWORK_RESULT\tnetwork.network_xml\tPASS\t"+detail);
      }catch(Throwable error){failed++;System.out.println("NETWORK_RESULT\tnetwork.network_xml\tFAIL\t"+error);error.printStackTrace(System.err);}
    }
    System.out.println("NETWORK_SUMMARY " + (total - failed) + "/" + total);
    if (args.length > 1 && !args[1].equals("-")) {
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
