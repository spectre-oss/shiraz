// SPDX-License-Identifier: GPL-3.0-only
import android.net.http.AndroidHttpClient;
import java.net.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

public class AndroidHttpClientSmoke {
    public static void main(String[] args) throws Exception {
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        server.setSoTimeout(10000);
        Thread responder = new Thread(new Runnable() {
            public void run() {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(10000);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "US-ASCII"));
                    StringBuilder headers = new StringBuilder();
                    for (String line; (line = reader.readLine()) != null && !line.isEmpty();) headers.append(line).append('\n');
                    if (!headers.toString().contains("User-Agent: shiraz-http-test") || !headers.toString().contains("Accept-Encoding: gzip"))
                        throw new AssertionError("Request headers missing");
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write("actual-http".getBytes("UTF-8")); }
                    OutputStream output = socket.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\nContent-Length: " + bytes.size() + "\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
                    output.write(bytes.toByteArray()); output.flush();
                } catch (Throwable error) { failure.set(error); }
            }
        });
        responder.start();
        AndroidHttpClient client = AndroidHttpClient.newInstance("shiraz-http-test");
        try {
            HttpGet request = new HttpGet("http://127.0.0.1:" + server.getLocalPort() + "/probe");
            AndroidHttpClient.modifyRequestToAcceptGzipResponse(request);
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) throw new AssertionError("Response status");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = AndroidHttpClient.getUngzippedContent(response.getEntity())) {
                for (int value; (value = input.read()) != -1;) bytes.write(value);
            }
            if (!"actual-http".equals(bytes.toString("UTF-8"))) throw new AssertionError("Gzip decode");
            byte[] original = new byte[1024]; java.util.Arrays.fill(original, (byte)'x');
            org.apache.http.HttpEntity compressed = AndroidHttpClient.getCompressedEntity(original, null);
            if (compressed.getContentLength() >= original.length) throw new AssertionError("Compression missing");
            try (InputStream input = AndroidHttpClient.getUngzippedContent(compressed)) {
                for (byte value : original) if (input.read() != (value & 255)) throw new AssertionError("Compression roundtrip");
                if (input.read() != -1) throw new AssertionError("Extra bytes");
            }
            if (!"tiny".equals(EntityUtils.toString(AndroidHttpClient.getCompressedEntity("tiny".getBytes("UTF-8"), null), "UTF-8")))
                throw new AssertionError("Small entity");
        } finally { client.close(); server.close(); responder.join(12000); }
        if (responder.isAlive() || failure.get() != null) throw new AssertionError("HTTP server failed", failure.get());
        try { client.execute(new HttpGet("http://127.0.0.1:1/")); throw new AssertionError("Closed client reused"); }
        catch (IllegalStateException expected) { }
        android.os.Looper.prepareMainLooper();
        AndroidHttpClient mainClient = AndroidHttpClient.newInstance("main-test");
        try {
            try { mainClient.execute(new HttpGet("http://127.0.0.1:1/")); throw new AssertionError("Main thread allowed"); }
            catch (RuntimeException expected) {
                if (!expected.getMessage().contains("main looper")) throw expected;
            }
        } finally { mainClient.close(); }
        System.out.println("ANDROID_HTTP_CLIENT_OK");
    }
}
