// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import android.content.Context;
import android.os.Looper;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.*;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.*;
import org.apache.http.protocol.HttpContext;

/** Optional Android API adapter over the separately licensed Apache HTTP client. */
public final class AndroidHttpClient implements HttpClient {
    private final DefaultHttpClient client;
    private volatile boolean closed;
    private volatile Logging logging;
    private static final class Logging {
        final String tag; final int level;
        Logging(String tag, int level) { this.tag = tag; this.level = level; }
    }
    private AndroidHttpClient(String userAgent) {
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 60000);
        HttpConnectionParams.setSoTimeout(params, 60000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpConnectionParams.setStaleCheckingEnabled(params, false);
        HttpProtocolParams.setUserAgent(params, userAgent);
        org.apache.http.client.params.HttpClientParams.setRedirecting(params, false);
        org.apache.http.client.params.HttpClientParams.setCookiePolicy(params, org.apache.http.client.params.CookiePolicy.IGNORE_COOKIES);
        SchemeRegistry schemes = new SchemeRegistry();
        schemes.register(new Scheme("http", 80, PlainSocketFactory.getSocketFactory()));
        schemes.register(new Scheme("https", 443, SSLSocketFactory.getSocketFactory()));
        client = new DefaultHttpClient(new ThreadSafeClientConnManager(params, schemes), params);
        client.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(HttpRequest request, HttpContext context) {
                if (closed) throw new IllegalStateException("HTTP client is closed");
                if (Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper())
                    throw new RuntimeException("HTTP requests are forbidden on the main looper");
                Logging current = logging;
                if (current != null && android.util.Log.isLoggable(current.tag, current.level))
                    android.util.Log.println(current.level, current.tag, "HTTP request (curl URL, headers and body redacted)");
            }
        }, 0);
    }
    public static AndroidHttpClient newInstance(String userAgent) { return new AndroidHttpClient(userAgent); }
    public static AndroidHttpClient newInstance(String userAgent, Context context) { return newInstance(userAgent); }
    private void checkExecution() {
        if (closed) throw new IllegalStateException("HTTP client is closed");
        if (Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper())
            throw new RuntimeException("HTTP requests are forbidden on the main looper");
    }
    public void close() { closed = true; client.getConnectionManager().shutdown(); }
    /** Request diagnostics omit URLs, headers and payloads to keep account data out of logs. */
    public void enableCurlLogging(String tag, int level) {
        if (tag == null) throw new NullPointerException("tag");
        if (level < 2 || level > 7) throw new IllegalArgumentException("Invalid log priority");
        logging = new Logging(tag, level);
    }
    public void disableCurlLogging() { logging = null; }
    public HttpParams getParams() { return client.getParams(); }
    public ClientConnectionManager getConnectionManager() { return client.getConnectionManager(); }
    public static void modifyRequestToAcceptGzipResponse(HttpRequest request) { request.addHeader("Accept-Encoding", "gzip"); }
    public static InputStream getUngzippedContent(HttpEntity entity) throws IOException {
        InputStream stream = entity.getContent();
        Header encoding = entity.getContentEncoding();
        if (stream != null && encoding != null && encoding.getValue() != null && encoding.getValue().contains("gzip"))
            return new GZIPInputStream(stream);
        return stream;
    }
    public static long getMinGzipSize(android.content.ContentResolver resolver) { return 256; }
    public static org.apache.http.entity.AbstractHttpEntity getCompressedEntity(byte[] data, android.content.ContentResolver resolver) throws IOException {
        if (data.length < getMinGzipSize(resolver)) return new org.apache.http.entity.ByteArrayEntity(data);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) { gzip.write(data); }
        org.apache.http.entity.ByteArrayEntity entity = new org.apache.http.entity.ByteArrayEntity(bytes.toByteArray());
        entity.setContentEncoding("gzip");
        return entity;
    }
    public HttpResponse execute(HttpUriRequest request) throws IOException { checkExecution(); return client.execute(request); }
    public HttpResponse execute(HttpUriRequest request, HttpContext context) throws IOException { checkExecution(); return client.execute(request, context); }
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler) throws IOException { checkExecution(); return client.execute(request, handler); }
    public <T> T execute(HttpUriRequest request, ResponseHandler<? extends T> handler, HttpContext context) throws IOException { checkExecution(); return client.execute(request, handler, context); }
    public HttpResponse execute(HttpHost host, HttpRequest request) throws IOException { checkExecution(); return client.execute(host, request); }
    public HttpResponse execute(HttpHost host, HttpRequest request, HttpContext context) throws IOException { checkExecution(); return client.execute(host, request, context); }
    public <T> T execute(HttpHost host, HttpRequest request, ResponseHandler<? extends T> handler) throws IOException { checkExecution(); return client.execute(host, request, handler); }
    public <T> T execute(HttpHost host, HttpRequest request, ResponseHandler<? extends T> handler, HttpContext context) throws IOException { checkExecution(); return client.execute(host, request, handler, context); }
}
