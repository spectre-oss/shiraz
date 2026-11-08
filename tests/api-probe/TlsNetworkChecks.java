// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Disposable build-generated credentials; no global trust or TLS defaults are modified. */
final class TlsNetworkChecks {
  interface Assets { InputStream open(String name) throws IOException; }
  static byte[] bytes(InputStream in) throws IOException {
    try (InputStream input = in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      for (int n; (n = input.read(buffer)) != -1;) {
        if (out.size() + n > 16384) throw new IOException("oversized TLS fixture");
        out.write(buffer, 0, n);
      }
      return out.toByteArray();
    }
  }
  static SSLContext context(KeyManager[] keys, TrustManager[] trust) throws Exception {
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keys, trust, null);
    return context;
  }
  static String run(String id, Assets assets) throws Exception {
    if (!java.util.Arrays.asList("network.tls_verified", "network.tls_untrusted", "network.tls_hostname",
            "network.tls_engine_verified", "network.tls_engine_untrusted", "network.tls_engine_hostname").contains(id))
      throw new IllegalArgumentException("Unknown TLS case: " + id);
    X509Certificate cert;
    try (InputStream in = assets.open("loopback-cert.pem")) {
      cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
    }
    // Check before the rejection assertion: an expired fixture is infrastructure failure.
    cert.checkValidity();
    PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(
        new PKCS8EncodedKeySpec(bytes(assets.open("loopback-key.der"))));
    KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    store.load(null, null);
    char[] password = new char[0];
    store.setKeyEntry("loopback", key, password, new java.security.cert.Certificate[] {cert});
    KeyManagerFactory km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    km.init(store, password);
    KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
    trust.load(null, null);
    trust.setCertificateEntry("loopback", cert);
    TrustManagerFactory tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tm.init(trust);
    SSLContext serverContext = context(km.getKeyManagers(), null);
    boolean untrusted = id.endsWith("_untrusted");
    boolean mismatch = id.endsWith("_hostname");
    SSLContext clientContext = context(null, untrusted ? null : tm.getTrustManagers());
    if (id.startsWith("network.tls_engine_"))
      return TlsEngineChecks.run(clientContext, serverContext, mismatch ? "127.0.0.1" : "localhost", untrusted || mismatch);
    try (SSLServerSocket server = (SSLServerSocket) serverContext.getServerSocketFactory()
             .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(2000);
      java.util.concurrent.atomic.AtomicBoolean receivedHttp = new java.util.concurrent.atomic.AtomicBoolean();
      FutureTask<Void> peer = new FutureTask<>(() -> {
        try (SSLSocket socket = (SSLSocket) server.accept()) {
          socket.setSoTimeout(2000);
          try {
            socket.startHandshake();
            // Some clients finish TLS before rejecting the hostname. They must send no HTTP.
            int first = socket.getInputStream().read();
            if (first < 0) return null;
            receivedHttp.set(true);
            NetworkChecks.require(first == 'G', "TLS request method byte=" + first);
            NetworkChecks.require(NetworkChecks.headers(socket).startsWith("ET /probe HTTP/1."), "TLS request path");
            NetworkChecks.response(socket, "HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nprobe");
          } catch (IOException expected) {
            if (!untrusted && !mismatch) throw expected;
          }
        }
        return null;
      });
      Thread thread = new Thread(peer, "probe-tls-peer");
      thread.setDaemon(true);
      thread.start();
      HttpsURLConnection connection = null;
      try {
        String hostname = mismatch ? "127.0.0.1" : "localhost";
        URL url = new URL("https://" + hostname + ":" + server.getLocalPort() + "/probe");
        connection = (HttpsURLConnection) url.openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(clientContext.getSocketFactory());
        connection.setConnectTimeout(1500);
        connection.setReadTimeout(1500);
        boolean rejected = false;
        try {
          NetworkChecks.require(connection.getResponseCode() == 200, "TLS HTTP status");
          NetworkChecks.require(connection.getCipherSuite() != null, "negotiated cipher");
          NetworkChecks.require(connection.getServerCertificates().length > 0, "peer certificates");
          NetworkChecks.require("probe".equals(NetworkChecks.body(connection.getInputStream())), "TLS response body");
        } catch (SSLException expected) {
          if (!untrusted && !mismatch) throw expected;
          rejected = true;
        }
        NetworkChecks.require(rejected == (untrusted || mismatch), "certificate rejection result");
        peer.get(2500, TimeUnit.MILLISECONDS);
        NetworkChecks.require(receivedHttp.get() == (!untrusted && !mismatch), "HTTP sent before certificate verification");
      } finally {
        if (connection != null) connection.disconnect();
        server.close();
        thread.join(2500);
        NetworkChecks.require(!thread.isAlive(), "TLS peer did not terminate");
      }
    }
    return untrusted ? "Untrusted certificate rejected before HTTP bytes"
        : mismatch ? "Trusted certificate with wrong hostname rejected before HTTP bytes"
        : "Verified TLS handshake returns HTTPS body, cipher and peer certificates";
  }
}
