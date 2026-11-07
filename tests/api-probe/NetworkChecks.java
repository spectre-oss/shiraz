// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.apiprobe;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Bounded behavioral checks, using loopback peers rather than Internet availability. */
final class NetworkChecks {
  interface Exchange { void run(Socket socket) throws Exception; }
  interface Request { void run(URL url) throws Exception; }
  static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
  static void local(Exchange exchange, Request request) throws Exception {
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(2000);
      FutureTask<Void> peer = new FutureTask<>(() -> {
        try (Socket socket = server.accept()) {
          socket.setSoTimeout(2000);
          exchange.run(socket);
        }
        return null;
      });
      Thread thread = new Thread(peer, "probe-network-peer");
      thread.setDaemon(true);
      thread.start();
      try {
        request.run(new URL("http://127.0.0.1:" + server.getLocalPort() + "/probe"));
        peer.get(2500, TimeUnit.MILLISECONDS);
      } finally {
        server.close();
        thread.join(2500);
        require(!thread.isAlive(), "loopback peer did not terminate");
      }
    }
  }
  // Bound both line size and total headers; never use an unbounded readLine on a peer.
  static String line(InputStream in) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (int i = 0; i < 8192; i++) {
      int b = in.read();
      if (b < 0) throw new EOFException("incomplete request headers");
      if (b == '\n') return bytes.toString("US-ASCII").replace("\r", "");
      bytes.write(b);
    }
    throw new IOException("oversized request line");
  }
  static String headers(Socket socket) throws IOException {
    InputStream in = socket.getInputStream();
    String first = line(in);
    int total = first.length();
    for (;;) {
      String next = line(in);
      total += next.length() + 2;
      if (total > 16384) throw new IOException("oversized headers");
      if (next.isEmpty()) return first;
    }
  }
  static void response(Socket socket, String text) throws IOException {
    socket.getOutputStream().write(text.getBytes("US-ASCII"));
    socket.getOutputStream().flush();
  }
  static String body(InputStream in) throws IOException {
    try (InputStream input = in) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[256];
      for (int n; (n = input.read(buffer)) != -1;) {
        if (out.size() + n > 65536) throw new IOException("oversized body");
        out.write(buffer, 0, n);
      }
      return out.toString("UTF-8");
    }
  }
  static HttpURLConnection connection(URL url) throws IOException {
    HttpURLConnection c = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
    c.setConnectTimeout(1500);
    c.setReadTimeout(500);
    return c;
  }
  static String run(String id) throws Exception {
    switch (id) {
      case "network.dns_localhost":
        InetAddress[] addresses = InetAddress.getAllByName("localhost");
        require(addresses.length > 0, "localhost resolved no addresses");
        for (InetAddress address : addresses)
          require(address.isLoopbackAddress(), "localhost resolved a non-loopback address");
        return "Resolver returns loopback addresses for localhost (no external DNS proof)";
      case "network.tcp_half_close":
        local(socket -> {
          require(socket.getInputStream().read() == 42, "request byte");
          require(socket.getInputStream().read() == -1, "shutdownOutput must send EOF");
          socket.getOutputStream().write(43);
        }, url -> {
          try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), 1500);
            socket.setSoTimeout(1500);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            require(socket.getTcpNoDelay() && socket.getKeepAlive(), "TCP options");
            socket.getOutputStream().write(42);
            socket.shutdownOutput();
            require(socket.isOutputShutdown(), "output state");
            require(socket.getInputStream().read() == 43, "half close lost reply");
            require(socket.getInputStream().read() == -1, "peer EOF");
          }
        });
        return "TCP connect, socket options, half-close and reply EOF succeed";
      case "network.http_chunked":
        local(socket -> {
          require(headers(socket).startsWith("GET /probe HTTP/1."), "GET request");
          response(socket, "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\nX-Probe: yes\r\n\r\n2\r\npr\r\n3\r\nobe\r\n0\r\n\r\n");
        }, url -> {
          HttpURLConnection c = connection(url);
          try {
            require(c.getResponseCode() == 200, "chunked status");
            require("yes".equals(c.getHeaderField("X-Probe")), "response header");
            require("probe".equals(body(c.getInputStream())), "decoded chunked body");
          } finally { c.disconnect(); }
        });
        return "HTTP status, response headers and chunked body decode correctly";
      case "network.http_error_body":
        local(socket -> {
          headers(socket);
          response(socket, "HTTP/1.1 404 Not Found\r\nContent-Length: 7\r\nConnection: close\r\n\r\nmissing");
        }, url -> {
          HttpURLConnection c = connection(url);
          try {
            require(c.getResponseCode() == 404, "error status");
            try { c.getInputStream(); throw new AssertionError("404 accepted as success"); }
            catch (FileNotFoundException expected) { }
            require("missing".equals(body(c.getErrorStream())), "error stream body");
          } finally { c.disconnect(); }
        });
        return "HTTP 404 retains error body and rejects success stream";
      case "network.http_post":
        local(socket -> {
          require(headers(socket).startsWith("POST /probe HTTP/1."), "POST method");
          byte[] expected = "probe".getBytes("US-ASCII");
          for (byte b : expected) require(socket.getInputStream().read() == b, "POST body");
          response(socket, "HTTP/1.1 201 Created\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok");
        }, url -> {
          HttpURLConnection c = connection(url);
          try {
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setFixedLengthStreamingMode(5);
            c.setRequestProperty("Content-Type", "text/plain");
            try (OutputStream out = c.getOutputStream()) { out.write("probe".getBytes("US-ASCII")); }
            require(c.getResponseCode() == 201, "POST response status");
            require("ok".equals(body(c.getInputStream())), "POST response body");
          } finally { c.disconnect(); }
        });
        return "HTTP fixed-length POST reaches peer and returns response body";
      case "network.http_redirect":
        local(socket -> {
          headers(socket);
          response(socket, "HTTP/1.1 302 Found\r\nLocation: /target\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        }, url -> {
          HttpURLConnection c = connection(url);
          try {
            c.setInstanceFollowRedirects(false);
            require(c.getResponseCode() == 302, "redirect disabled status");
            require("/target".equals(c.getHeaderField("Location")), "relative redirect location");
          } finally { c.disconnect(); }
        });
        return "HTTP exposes relative redirect when automatic following is disabled";
      case "network.http_read_timeout":
        CountDownLatch done = new CountDownLatch(1);
        try {
          local(socket -> { headers(socket); done.await(2, TimeUnit.SECONDS); }, url -> {
            HttpURLConnection c = connection(url);
            try {
              try { c.getResponseCode(); throw new AssertionError("silent HTTP peer did not time out"); }
              catch (SocketTimeoutException expected) { }
            } finally { c.disconnect(); done.countDown(); }
          });
        } finally { done.countDown(); }
        return "Silent HTTP peer triggers configured read timeout";
      case "network.tls_socket_factory":
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
          SSLParameters parameters = socket.getSSLParameters();
          parameters.setEndpointIdentificationAlgorithm("HTTPS");
          socket.setSSLParameters(parameters);
          require("HTTPS".equals(socket.getSSLParameters().getEndpointIdentificationAlgorithm()), "endpoint verification setting");
          require(socket.getEnabledProtocols().length > 0 && socket.getEnabledCipherSuites().length > 0, "TLS protocols/ciphers");
        }
        return "TLS context creates socket with protocols, ciphers and HTTPS identification";
      case "network.trust_manager":
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((java.security.KeyStore) null);
        boolean roots = false;
        for (TrustManager manager : factory.getTrustManagers()) {
          if (manager instanceof X509TrustManager)
            roots |= ((X509TrustManager) manager).getAcceptedIssuers().length > 0;
        }
        require(roots, "default trust manager exposes no certificate authorities");
        return "Default X509 trust manager exposes certificate authorities (handshake tested separately)";
      default: throw new IllegalArgumentException("Unknown networking case: " + id);
    }
  }
}
