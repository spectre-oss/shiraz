// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import android.content.Context;
import android.net.Network;
import android.net.TrafficStats;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class HttpEngine {
    HttpEngine() {}

    public static String getVersionString() {
        return "ShirazHttpEngine/1.0 (API 35)";
    }

    public abstract void shutdown();

    public void bindToNetwork(Network network) {}

    public abstract URLConnection openConnection(URL url) throws IOException;

    public abstract URLStreamHandlerFactory createUrlStreamHandlerFactory();

    public abstract UrlRequest.Builder newUrlRequestBuilder(
            String url, Executor executor, UrlRequest.Callback callback);

    public abstract BidirectionalStream.Builder newBidirectionalStreamBuilder(
            String url, Executor executor, BidirectionalStream.Callback callback);

    public static class Builder {
        public static final int HTTP_CACHE_DISABLED = 0;
        public static final int HTTP_CACHE_IN_MEMORY = 1;
        public static final int HTTP_CACHE_DISK_NO_HTTP = 2;
        public static final int HTTP_CACHE_DISK = 3;

        private final Context mContext;
        private String mUserAgent;
        private String mStoragePath;
        private boolean mEnableQuic = true;
        private boolean mEnableHttp2 = true;
        private boolean mEnableBrotli = false;
        private int mHttpCacheMode = HTTP_CACHE_DISABLED;
        private long mHttpCacheMaxSize;
        private boolean mEnablePublicKeyPinningBypass = true;
        private QuicOptions mQuicOptions;
        private DnsOptions mDnsOptions;
        private ConnectionMigrationOptions mConnectionMigrationOptions;

        public Builder(Context context) {
            this.mContext = Objects.requireNonNull(context, "context");
        }

        public String getDefaultUserAgent() {
            String pkg = null;
            try {
                pkg = mContext.getPackageName();
            } catch (RuntimeException ignored) {
                pkg = null;
            }
            if (pkg == null || pkg.isEmpty()) {
                pkg = "android";
            }
            return pkg + " (Linux; U; Android 15) " + getVersionString();
        }

        public Builder setUserAgent(String userAgent) {
            this.mUserAgent = userAgent;
            return this;
        }

        public Builder setStoragePath(String value) {
            this.mStoragePath = value;
            return this;
        }

        public Builder setEnableQuic(boolean value) {
            this.mEnableQuic = value;
            return this;
        }

        public Builder setEnableHttp2(boolean value) {
            this.mEnableHttp2 = value;
            return this;
        }

        public Builder setEnableBrotli(boolean value) {
            this.mEnableBrotli = value;
            return this;
        }

        public Builder setEnableHttpCache(int cacheMode, long maxSize) {
            if (cacheMode < HTTP_CACHE_DISABLED || cacheMode > HTTP_CACHE_DISK) {
                throw new IllegalArgumentException("Invalid cache mode: " + cacheMode);
            }
            if (maxSize < 0) {
                throw new IllegalArgumentException("Cache maxSize must be non-negative");
            }
            if ((cacheMode == HTTP_CACHE_DISK || cacheMode == HTTP_CACHE_DISK_NO_HTTP)
                    && (mStoragePath == null || mStoragePath.isEmpty())) {
                throw new IllegalArgumentException("Storage path must be set for disk cache");
            }
            this.mHttpCacheMode = cacheMode;
            this.mHttpCacheMaxSize = maxSize;
            return this;
        }

        public Builder addQuicHint(String host, int port, int alternatePort) {
            Objects.requireNonNull(host, "host");
            if (port <= 0 || port > 65535 || alternatePort <= 0 || alternatePort > 65535) {
                throw new IllegalArgumentException("Invalid QUIC port");
            }
            return this;
        }

        public Builder addPublicKeyPins(
                String hostName,
                Set<byte[]> pinsSha256,
                boolean includeSubdomains,
                Instant expirationInstant) {
            Objects.requireNonNull(hostName, "hostName");
            Objects.requireNonNull(pinsSha256, "pinsSha256");
            Objects.requireNonNull(expirationInstant, "expirationInstant");
            return this;
        }

        public Builder setEnablePublicKeyPinningBypassForLocalTrustAnchors(boolean value) {
            this.mEnablePublicKeyPinningBypass = value;
            return this;
        }

        public Builder setQuicOptions(QuicOptions quicOptions) {
            this.mQuicOptions = Objects.requireNonNull(quicOptions, "quicOptions");
            return this;
        }

        public Builder setDnsOptions(DnsOptions dnsOptions) {
            this.mDnsOptions = Objects.requireNonNull(dnsOptions, "dnsOptions");
            return this;
        }

        public Builder setConnectionMigrationOptions(
                ConnectionMigrationOptions connectionMigrationOptions) {
            this.mConnectionMigrationOptions =
                    Objects.requireNonNull(
                            connectionMigrationOptions, "connectionMigrationOptions");
            return this;
        }

        public HttpEngine build() {
            String ua = mUserAgent != null ? mUserAgent : getDefaultUserAgent();
            return new HostHttpEngine(ua, mHttpCacheMode == HTTP_CACHE_DISABLED);
        }
    }

    static final class SimpleHeaderBlock extends HeaderBlock {
        private final List<Map.Entry<String, String>> mList;
        private final Map<String, List<String>> mMap;

        SimpleHeaderBlock(List<Map.Entry<String, String>> entries) {
            List<Map.Entry<String, String>> copy =
                    new ArrayList<Map.Entry<String, String>>(entries.size());
            Map<String, List<String>> map =
                    new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<String, String> e = entries.get(i);
                if (e.getKey() == null) {
                    continue;
                }
                Map.Entry<String, String> immutableEntry =
                        new AbstractMap.SimpleImmutableEntry<String, String>(
                                e.getKey(), e.getValue());
                copy.add(immutableEntry);
                List<String> values = map.get(e.getKey());
                if (values == null) {
                    values = new ArrayList<String>();
                    map.put(e.getKey(), values);
                }
                values.add(e.getValue());
            }
            Map<String, List<String>> frozen = new LinkedHashMap<String, List<String>>();
            for (Map.Entry<String, List<String>> me : map.entrySet()) {
                frozen.put(me.getKey(), Collections.unmodifiableList(me.getValue()));
            }
            this.mList = Collections.unmodifiableList(copy);
            this.mMap = Collections.unmodifiableMap(frozen);
        }

        @Override
        public List<Map.Entry<String, String>> getAsList() {
            return mList;
        }

        @Override
        public Map<String, List<String>> getAsMap() {
            return mMap;
        }
    }

    static final class SimpleUrlResponseInfo extends UrlResponseInfo {
        private final List<String> mUrlChain;
        private final int mStatusCode;
        private final String mStatusText;
        private final HeaderBlock mHeaders;
        private final boolean mWasCached;
        private final String mProtocol;
        private final AtomicLong mReceivedBytes;

        SimpleUrlResponseInfo(
                List<String> urlChain,
                int statusCode,
                String statusText,
                HeaderBlock headers,
                boolean wasCached,
                String protocol,
                AtomicLong receivedBytes) {
            this.mUrlChain = Collections.unmodifiableList(new ArrayList<String>(urlChain));
            this.mStatusCode = statusCode;
            this.mStatusText = statusText == null ? "" : statusText;
            this.mHeaders = headers;
            this.mWasCached = wasCached;
            this.mProtocol = protocol == null ? "http/1.1" : protocol;
            this.mReceivedBytes = receivedBytes;
        }

        @Override
        public String getUrl() {
            return mUrlChain.get(mUrlChain.size() - 1);
        }

        @Override
        public List<String> getUrlChain() {
            return mUrlChain;
        }

        @Override
        public int getHttpStatusCode() {
            return mStatusCode;
        }

        @Override
        public String getHttpStatusText() {
            return mStatusText;
        }

        @Override
        public HeaderBlock getHeaders() {
            return mHeaders;
        }

        @Override
        public boolean wasCached() {
            return mWasCached;
        }

        @Override
        public String getNegotiatedProtocol() {
            return mProtocol;
        }

        @Override
        public long getReceivedByteCount() {
            return mReceivedBytes.get();
        }
    }

    static final class SimpleNetworkException extends NetworkException {
        private final int mErrorCode;
        private final boolean mRetryable;

        SimpleNetworkException(
                String message, Throwable cause, int errorCode, boolean retryable) {
            super(message, cause);
            this.mErrorCode = errorCode;
            this.mRetryable = retryable;
        }

        @Override
        public int getErrorCode() {
            return mErrorCode;
        }

        @Override
        public boolean isImmediatelyRetryable() {
            return mRetryable;
        }
    }

    static final class SimpleCallbackException extends CallbackException {
        SimpleCallbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class HostHttpEngine extends HttpEngine {
        private static final AtomicInteger THREAD_ID = new AtomicInteger(1);

        private final String mDefaultUserAgent;
        private final boolean mEngineCacheDisabled;
        private final ExecutorService mIoPool;
        private final AtomicBoolean mShutdown = new AtomicBoolean(false);
        private volatile Network mBoundNetwork;

        HostHttpEngine(String defaultUserAgent, boolean engineCacheDisabled) {
            this.mDefaultUserAgent = defaultUserAgent;
            this.mEngineCacheDisabled = engineCacheDisabled;
            this.mIoPool =
                    Executors.newCachedThreadPool(
                            new ThreadFactory() {
                                @Override
                                public Thread newThread(Runnable r) {
                                    Thread t =
                                            new Thread(
                                                    r,
                                                    "ShirazHttpEngine-"
                                                            + THREAD_ID.getAndIncrement());
                                    t.setDaemon(true);
                                    return t;
                                }
                            });
        }

        @Override
        public void shutdown() {
            if (mShutdown.compareAndSet(false, true)) {
                mIoPool.shutdown();
            }
        }

        @Override
        public void bindToNetwork(Network network) {
            this.mBoundNetwork = network;
        }

        @Override
        public URLConnection openConnection(URL url) throws IOException {
            Objects.requireNonNull(url, "url");
            ensureActive();
            Network net = mBoundNetwork;
            URLConnection conn = net != null ? net.openConnection(url) : url.openConnection();
            if (mDefaultUserAgent != null && conn.getRequestProperty("User-Agent") == null) {
                conn.setRequestProperty("User-Agent", mDefaultUserAgent);
            }
            return conn;
        }

        @Override
        public URLStreamHandlerFactory createUrlStreamHandlerFactory() {
            ensureActive();
            return new URLStreamHandlerFactory() {
                @Override
                public URLStreamHandler createURLStreamHandler(final String protocol) {
                    if (!"http".equalsIgnoreCase(protocol)
                            && !"https".equalsIgnoreCase(protocol)) {
                        return null;
                    }
                    final int defaultPort = "https".equalsIgnoreCase(protocol) ? 443 : 80;
                    return new URLStreamHandler() {
                        @Override
                        protected int getDefaultPort() {
                            return defaultPort;
                        }

                        @Override
                        protected URLConnection openConnection(URL u) throws IOException {
                            URL target =
                                    new URL(
                                            u.getProtocol(),
                                            u.getHost(),
                                            u.getPort(),
                                            u.getFile());
                            return HostHttpEngine.this.openConnection(target);
                        }
                    };
                }
            };
        }

        @Override
        public UrlRequest.Builder newUrlRequestBuilder(
                String url, Executor executor, UrlRequest.Callback callback) {
            ensureActive();
            return new HostUrlRequestBuilder(
                    this,
                    Objects.requireNonNull(url, "url"),
                    Objects.requireNonNull(executor, "executor"),
                    Objects.requireNonNull(callback, "callback"));
        }

        @Override
        public BidirectionalStream.Builder newBidirectionalStreamBuilder(
                String url, Executor executor, BidirectionalStream.Callback callback) {
            ensureActive();
            return new HostBidirectionalStreamBuilder(
                    this,
                    Objects.requireNonNull(url, "url"),
                    Objects.requireNonNull(executor, "executor"),
                    Objects.requireNonNull(callback, "callback"));
        }

        void ensureActive() {
            if (mShutdown.get()) {
                throw new IllegalStateException("HttpEngine has been shut down");
            }
        }

        ExecutorService ioPool() {
            return mIoPool;
        }

        String defaultUserAgent() {
            return mDefaultUserAgent;
        }

        boolean isEngineCacheDisabled() {
            return mEngineCacheDisabled;
        }

        Network boundNetwork() {
            return mBoundNetwork;
        }
    }

    private static final class HostUrlRequestBuilder extends UrlRequest.Builder {
        private final HostHttpEngine mEngine;
        private final String mUrl;
        private final Executor mExecutor;
        private final UrlRequest.Callback mCallback;
        private String mMethod;
        private final List<Map.Entry<String, String>> mHeaders =
                new ArrayList<Map.Entry<String, String>>();
        private boolean mCacheDisabled;
        private int mPriority = UrlRequest.REQUEST_PRIORITY_MEDIUM;
        private UploadDataProvider mUploadProvider;
        private Executor mUploadExecutor;
        private boolean mDirectExecutorAllowed;
        private Network mNetwork;
        private boolean mHasTag;
        private int mTag;
        private boolean mHasUid;
        private int mUid;

        HostUrlRequestBuilder(
                HostHttpEngine engine,
                String url,
                Executor executor,
                UrlRequest.Callback callback) {
            this.mEngine = engine;
            this.mUrl = url;
            this.mExecutor = executor;
            this.mCallback = callback;
            this.mCacheDisabled = engine.isEngineCacheDisabled();
            this.mNetwork = engine.boundNetwork();
        }

        @Override
        public UrlRequest.Builder setHttpMethod(String method) {
            this.mMethod = Objects.requireNonNull(method, "method");
            return this;
        }

        @Override
        public UrlRequest.Builder addHeader(String header, String value) {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(value, "value");
            mHeaders.add(new AbstractMap.SimpleImmutableEntry<String, String>(header, value));
            return this;
        }

        @Override
        public UrlRequest.Builder setCacheDisabled(boolean disableCache) {
            this.mCacheDisabled = disableCache;
            return this;
        }

        @Override
        public UrlRequest.Builder setPriority(int priority) {
            this.mPriority = priority;
            return this;
        }

        @Override
        public UrlRequest.Builder setUploadDataProvider(
                UploadDataProvider uploadDataProvider, Executor executor) {
            this.mUploadProvider =
                    Objects.requireNonNull(uploadDataProvider, "uploadDataProvider");
            this.mUploadExecutor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        @Override
        public UrlRequest.Builder setDirectExecutorAllowed(boolean allowDirectExecutor) {
            this.mDirectExecutorAllowed = allowDirectExecutor;
            return this;
        }

        @Override
        public UrlRequest.Builder bindToNetwork(Network network) {
            this.mNetwork = network;
            return this;
        }

        @Override
        public UrlRequest.Builder setTrafficStatsTag(int tag) {
            this.mHasTag = true;
            this.mTag = tag;
            return this;
        }

        @Override
        public UrlRequest.Builder setTrafficStatsUid(int uid) {
            this.mHasUid = true;
            this.mUid = uid;
            return this;
        }

        @Override
        public UrlRequest build() {
            String effectiveMethod =
                    mMethod != null ? mMethod : (mUploadProvider != null ? "POST" : "GET");
            return new HostUrlRequest(
                    mEngine,
                    mUrl,
                    mExecutor,
                    mCallback,
                    effectiveMethod,
                    new SimpleHeaderBlock(mHeaders),
                    mCacheDisabled,
                    mPriority,
                    mUploadProvider,
                    mUploadExecutor,
                    mDirectExecutorAllowed,
                    mNetwork,
                    mHasTag,
                    mTag,
                    mHasUid,
                    mUid);
        }
    }

    private static final class HostUrlRequest extends UrlRequest {
        private final HostHttpEngine mEngine;
        private final Executor mExecutor;
        private final Callback mCallback;
        private final String mInitialMethod;
        private final HeaderBlock mHeaders;
        private final boolean mCacheDisabled;
        private final int mPriority;
        private final UploadDataProvider mUploadProvider;
        private final Executor mUploadExecutor;
        private final boolean mDirectExecutorAllowed;
        private final Network mNetwork;
        private final boolean mHasTag;
        private final int mTag;
        private final boolean mHasUid;
        private final int mUid;

        private final List<String> mUrlChain = new ArrayList<String>();
        private final AtomicLong mReceivedBytes = new AtomicLong(0L);
        private final AtomicBoolean mStarted = new AtomicBoolean(false);
        private final AtomicBoolean mCanceled = new AtomicBoolean(false);
        private final AtomicBoolean mDone = new AtomicBoolean(false);
        private volatile int mStatus = Status.IDLE;

        private volatile String mCurrentMethod;
        private volatile boolean mSendUpload;
        private volatile String mPendingRedirectUrl;
        private volatile boolean mAwaitingRedirect;
        private volatile boolean mAwaitingRead;
        private volatile HttpURLConnection mActiveConnection;
        private volatile InputStream mResponseStream;
        private volatile UrlResponseInfo mResponseInfo;

        HostUrlRequest(
                HostHttpEngine engine,
                String initialUrl,
                Executor executor,
                Callback callback,
                String method,
                HeaderBlock headers,
                boolean cacheDisabled,
                int priority,
                UploadDataProvider uploadProvider,
                Executor uploadExecutor,
                boolean directExecutorAllowed,
                Network network,
                boolean hasTag,
                int tag,
                boolean hasUid,
                int uid) {
            this.mEngine = engine;
            this.mExecutor = executor;
            this.mCallback = callback;
            this.mInitialMethod = method;
            this.mCurrentMethod = method;
            this.mSendUpload = uploadProvider != null;
            this.mHeaders = headers;
            this.mCacheDisabled = cacheDisabled;
            this.mPriority = priority;
            this.mUploadProvider = uploadProvider;
            this.mUploadExecutor = uploadExecutor;
            this.mDirectExecutorAllowed = directExecutorAllowed;
            this.mNetwork = network;
            this.mHasTag = hasTag;
            this.mTag = tag;
            this.mHasUid = hasUid;
            this.mUid = uid;
            this.mUrlChain.add(initialUrl);
        }

        @Override
        public String getHttpMethod() {
            return mInitialMethod;
        }

        @Override
        public HeaderBlock getHeaders() {
            return mHeaders;
        }

        @Override
        public boolean isCacheDisabled() {
            return mCacheDisabled;
        }

        @Override
        public boolean isDirectExecutorAllowed() {
            return mDirectExecutorAllowed;
        }

        @Override
        public int getPriority() {
            return mPriority;
        }

        @Override
        public boolean hasTrafficStatsTag() {
            return mHasTag;
        }

        @Override
        public int getTrafficStatsTag() {
            if (!mHasTag) {
                throw new IllegalStateException("TrafficStatsTag is not set");
            }
            return mTag;
        }

        @Override
        public boolean hasTrafficStatsUid() {
            return mHasUid;
        }

        @Override
        public int getTrafficStatsUid() {
            if (!mHasUid) {
                throw new IllegalStateException("TrafficStatsUid is not set");
            }
            return mUid;
        }

        @Override
        public void start() {
            if (!mStarted.compareAndSet(false, true)) {
                throw new IllegalStateException("Request already started");
            }
            if (mCanceled.get()) {
                finishCanceled();
                return;
            }
            mEngine.ioPool().execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            executeSingleHop(false);
                        }
                    });
        }

        @Override
        public void followRedirect() {
            final String nextUrl;
            synchronized (this) {
                if (!mAwaitingRedirect || mPendingRedirectUrl == null) {
                    throw new IllegalStateException("No redirect pending");
                }
                mAwaitingRedirect = false;
                nextUrl = mPendingRedirectUrl;
                mPendingRedirectUrl = null;
                mUrlChain.add(nextUrl);
            }
            if (mCanceled.get()) {
                finishCanceled();
                return;
            }
            mEngine.ioPool().execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            executeSingleHop(true);
                        }
                    });
        }

        @Override
        public void read(final ByteBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer");
            if (!buffer.hasRemaining()) {
                throw new IllegalArgumentException("Pre-filled buffer has no remaining capacity");
            }
            synchronized (this) {
                if (!mAwaitingRead) {
                    throw new IllegalStateException("Not awaiting read()");
                }
                mAwaitingRead = false;
            }
            if (mCanceled.get()) {
                finishCanceled();
                return;
            }
            mEngine.ioPool().execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            performRead(buffer);
                        }
                    });
        }

        @Override
        public void cancel() {
            if (mDone.get()) {
                return;
            }
            if (mCanceled.compareAndSet(false, true)) {
                if (!mStarted.get()) {
                    mDone.set(true);
                    mStatus = Status.INVALID;
                    closeQuietly();
                    return;
                }
                mEngine.ioPool().execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                finishCanceled();
                            }
                        });
            }
        }

        @Override
        public boolean isDone() {
            return mDone.get();
        }

        @Override
        public void getStatus(final StatusListener listener) {
            Objects.requireNonNull(listener, "listener");
            final int snapshot = mDone.get() ? Status.INVALID : mStatus;
            dispatchToExecutor(
                    mExecutor,
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onStatus(snapshot);
                        }
                    });
        }

        private void executeSingleHop(boolean isRedirectFollow) {
            if (mCanceled.get()) {
                finishCanceled();
                return;
            }
            if (mHasTag) {
                TrafficStats.setThreadStatsTag(mTag);
            }
            if (mHasUid) {
                TrafficStats.setThreadStatsUid(mUid);
            }
            try {
                String currentUrl;
                synchronized (this) {
                    currentUrl = mUrlChain.get(mUrlChain.size() - 1);
                }
                mStatus = Status.CONNECTING;
                URL url = new URL(currentUrl);
                URLConnection rawConn =
                        mNetwork != null ? mNetwork.openConnection(url) : url.openConnection();
                if (!(rawConn instanceof HttpURLConnection)) {
                    throw new IOException("Unsupported non-HTTP URL: " + currentUrl);
                }
                HttpURLConnection conn = (HttpURLConnection) rawConn;
                mActiveConnection = conn;
                conn.setInstanceFollowRedirects(false);
                conn.setUseCaches(!mCacheDisabled);
                conn.setRequestMethod(mCurrentMethod);

                boolean hasUserAgent = false;
                List<Map.Entry<String, String>> reqHeaders = mHeaders.getAsList();
                for (int i = 0; i < reqHeaders.size(); i++) {
                    Map.Entry<String, String> entry = reqHeaders.get(i);
                    if ("User-Agent".equalsIgnoreCase(entry.getKey())) {
                        hasUserAgent = true;
                    }
                    conn.addRequestProperty(entry.getKey(), entry.getValue());
                }
                if (!hasUserAgent && mEngine.defaultUserAgent() != null) {
                    conn.setRequestProperty("User-Agent", mEngine.defaultUserAgent());
                }

                if (mSendUpload && mUploadProvider != null) {
                    mStatus = Status.SENDING_REQUEST;
                    if (isRedirectFollow) {
                        rewindUploadProvider();
                    }
                    byte[] payload = readAllUploadBytes();
                    conn.setDoOutput(true);
                    conn.setFixedLengthStreamingMode(payload.length);
                    OutputStream out = conn.getOutputStream();
                    try {
                        out.write(payload);
                        out.flush();
                    } finally {
                        out.close();
                    }
                }

                if (mCanceled.get()) {
                    finishCanceled();
                    return;
                }

                mStatus = Status.WAITING_FOR_RESPONSE;
                int statusCode = conn.getResponseCode();
                String statusMsg = conn.getResponseMessage();
                List<Map.Entry<String, String>> respHeaders =
                        new ArrayList<Map.Entry<String, String>>();
                for (int i = 0; ; i++) {
                    String k = conn.getHeaderFieldKey(i);
                    String v = conn.getHeaderField(i);
                    if (k == null && v == null) {
                        break;
                    }
                    if (k != null && v != null) {
                        respHeaders.add(new AbstractMap.SimpleImmutableEntry<String, String>(k, v));
                    }
                }
                final SimpleUrlResponseInfo info;
                synchronized (this) {
                    info =
                            new SimpleUrlResponseInfo(
                                    mUrlChain,
                                    statusCode,
                                    statusMsg,
                                    new SimpleHeaderBlock(respHeaders),
                                    false,
                                    "https".equalsIgnoreCase(url.getProtocol())
                                            ? "http/1.1"
                                            : "http/1.1",
                                    mReceivedBytes);
                }
                mResponseInfo = info;

                if (isRedirectStatus(statusCode)) {
                    String location = conn.getHeaderField("Location");
                    if (location != null && !location.isEmpty()) {
                        final String resolved = new URL(url, location).toExternalForm();
                        if (statusCode == 301 || statusCode == 302 || statusCode == 303) {
                            if (!"GET".equalsIgnoreCase(mCurrentMethod)
                                    && !"HEAD".equalsIgnoreCase(mCurrentMethod)) {
                                mCurrentMethod = "GET";
                                mSendUpload = false;
                            }
                        }
                        closeCurrentStreamAndDisconnect();
                        synchronized (this) {
                            mPendingRedirectUrl = resolved;
                            mAwaitingRedirect = true;
                        }
                        mStatus = Status.IDLE;
                        dispatchCallback(
                                new CallbackAction() {
                                    @Override
                                    public void run() throws Exception {
                                        mCallback.onRedirectReceived(
                                                HostUrlRequest.this, info, resolved);
                                    }
                                });
                        return;
                    }
                }

                InputStream stream = null;
                try {
                    stream = conn.getInputStream();
                } catch (IOException ioe) {
                    stream = conn.getErrorStream();
                }
                mResponseStream = stream;
                synchronized (this) {
                    mAwaitingRead = true;
                }
                mStatus = Status.READING_RESPONSE;
                dispatchCallback(
                        new CallbackAction() {
                            @Override
                            public void run() throws Exception {
                                mCallback.onResponseStarted(HostUrlRequest.this, info);
                            }
                        });
            } catch (HttpException he) {
                failWith(he);
            } catch (IOException ioe) {
                failWith(mapIoException(ioe));
            } catch (RuntimeException re) {
                failWith(new SimpleNetworkException("Request failed", re, NetworkException.ERROR_OTHER, false));
            } finally {
                if (mHasTag) {
                    TrafficStats.clearThreadStatsTag();
                }
                if (mHasUid) {
                    TrafficStats.clearThreadStatsUid();
                }
            }
        }

        private void performRead(final ByteBuffer buffer) {
            if (mCanceled.get()) {
                finishCanceled();
                return;
            }
            try {
                InputStream in = mResponseStream;
                if (in == null) {
                    finishSucceeded();
                    return;
                }
                int maxRead = Math.min(buffer.remaining(), 16384);
                byte[] scratch = new byte[maxRead];
                int n = in.read(scratch, 0, maxRead);
                if (n <= 0) {
                    finishSucceeded();
                    return;
                }
                buffer.put(scratch, 0, n);
                mReceivedBytes.addAndGet(n);
                synchronized (this) {
                    mAwaitingRead = true;
                }
                final UrlResponseInfo info = mResponseInfo;
                dispatchCallback(
                        new CallbackAction() {
                            @Override
                            public void run() throws Exception {
                                mCallback.onReadCompleted(HostUrlRequest.this, info, buffer);
                            }
                        });
            } catch (IOException ioe) {
                failWith(mapIoException(ioe));
            }
        }

        private void rewindUploadProvider() throws HttpException {
            final CountDownLatch latch = new CountDownLatch(1);
            final Exception[] errorHolder = new Exception[1];
            final UploadDataSink sink =
                    new UploadDataSink() {
                        @Override
                        public void onReadSucceeded(boolean finalChunk) {}

                        @Override
                        public void onReadError(Exception exception) {}

                        @Override
                        public void onRewindSucceeded() {
                            latch.countDown();
                        }

                        @Override
                        public void onRewindError(Exception exception) {
                            errorHolder[0] =
                                    exception != null
                                            ? exception
                                            : new IOException("Upload rewind error");
                            latch.countDown();
                        }
                    };
            dispatchUploadTask(
                    new UploadAction() {
                        @Override
                        public void run() throws Exception {
                            mUploadProvider.rewind(sink);
                        }
                    },
                    errorHolder,
                    latch);
            awaitLatch(latch, errorHolder);
        }

        private byte[] readAllUploadBytes() throws HttpException {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final long declaredLen;
            try {
                declaredLen = mUploadProvider.getLength();
            } catch (Exception e) {
                throw new SimpleCallbackException("Exception thrown from UploadDataProvider.getLength()", e);
            }
            long remaining = declaredLen;
            while (true) {
                if (declaredLen >= 0 && remaining == 0) {
                    break;
                }
                int chunkCap =
                        declaredLen >= 0
                                ? (int) Math.min(remaining, 16384L)
                                : 16384;
                final ByteBuffer buf = ByteBuffer.allocate(Math.max(chunkCap, 1));
                final CountDownLatch latch = new CountDownLatch(1);
                final boolean[] finalChunkHolder = new boolean[1];
                final Exception[] errorHolder = new Exception[1];
                final UploadDataSink sink =
                        new UploadDataSink() {
                            @Override
                            public void onReadSucceeded(boolean finalChunk) {
                                finalChunkHolder[0] = finalChunk;
                                latch.countDown();
                            }

                            @Override
                            public void onReadError(Exception exception) {
                                errorHolder[0] =
                                        exception != null
                                                ? exception
                                                : new IOException("Upload read error");
                                latch.countDown();
                            }

                            @Override
                            public void onRewindSucceeded() {}

                            @Override
                            public void onRewindError(Exception exception) {}
                        };
                dispatchUploadTask(
                        new UploadAction() {
                            @Override
                            public void run() throws Exception {
                                mUploadProvider.read(sink, buf);
                            }
                        },
                        errorHolder,
                        latch);
                awaitLatch(latch, errorHolder);
                buf.flip();
                int readBytes = buf.remaining();
                byte[] tmp = new byte[readBytes];
                buf.get(tmp);
                baos.write(tmp, 0, readBytes);
                if (declaredLen >= 0) {
                    remaining -= readBytes;
                    if (remaining <= 0) {
                        break;
                    }
                } else if (finalChunkHolder[0]) {
                    break;
                }
            }
            return baos.toByteArray();
        }

        private void dispatchUploadTask(
                final UploadAction action,
                final Exception[] errorHolder,
                final CountDownLatch latch)
                throws HttpException {
            Executor exec = mUploadExecutor != null ? mUploadExecutor : mExecutor;
            try {
                dispatchToExecutor(
                        exec,
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    action.run();
                                } catch (Exception e) {
                                    errorHolder[0] = e;
                                    latch.countDown();
                                }
                            }
                        });
            } catch (InlineExecutionProhibitedException iepe) {
                throw new SimpleCallbackException("Inline execution prohibited", iepe);
            }
        }

        private static void awaitLatch(CountDownLatch latch, Exception[] errorHolder)
                throws HttpException {
            try {
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SimpleNetworkException(
                        "Interrupted waiting for upload data",
                        ie,
                        NetworkException.ERROR_OTHER,
                        false);
            }
            if (errorHolder[0] != null) {
                throw new SimpleCallbackException(
                        "Exception reported by UploadDataProvider", errorHolder[0]);
            }
        }

        private void dispatchCallback(final CallbackAction action) {
            try {
                dispatchToExecutor(
                        mExecutor,
                        new Runnable() {
                            @Override
                            public void run() {
                                if (mCanceled.get()) {
                                    finishCanceled();
                                    return;
                                }
                                try {
                                    action.run();
                                } catch (Exception callbackEx) {
                                    failWith(
                                            new SimpleCallbackException(
                                                    "Exception received from UrlRequest.Callback",
                                                    callbackEx));
                                }
                            }
                        });
            } catch (InlineExecutionProhibitedException iepe) {
                failWith(new SimpleCallbackException("Inline execution prohibited", iepe));
            } catch (RuntimeException re) {
                failWith(new SimpleCallbackException("Executor rejected task", re));
            }
        }

        private void dispatchToExecutor(Executor executor, final Runnable task) {
            if (mDirectExecutorAllowed) {
                executor.execute(task);
                return;
            }
            final Thread caller = Thread.currentThread();
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (Thread.currentThread() == caller) {
                                throw new InlineExecutionProhibitedException();
                            }
                            task.run();
                        }
                    });
        }

        private void finishSucceeded() {
            if (!mDone.compareAndSet(false, true)) {
                return;
            }
            mStatus = Status.INVALID;
            closeQuietly();
            final UrlResponseInfo info = mResponseInfo;
            try {
                dispatchToExecutor(
                        mExecutor,
                        new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onSucceeded(HostUrlRequest.this, info);
                            }
                        });
            } catch (RuntimeException ignored) {
            }
        }

        private void failWith(final HttpException error) {
            if (!mDone.compareAndSet(false, true)) {
                return;
            }
            mStatus = Status.INVALID;
            closeQuietly();
            final UrlResponseInfo info = mResponseInfo;
            try {
                mExecutor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onFailed(HostUrlRequest.this, info, error);
                            }
                        });
            } catch (RuntimeException ignored) {
            }
        }

        private void finishCanceled() {
            if (!mDone.compareAndSet(false, true)) {
                return;
            }
            mStatus = Status.INVALID;
            closeQuietly();
            final UrlResponseInfo info = mResponseInfo;
            try {
                mExecutor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onCanceled(HostUrlRequest.this, info);
                            }
                        });
            } catch (RuntimeException ignored) {
            }
        }

        private void closeCurrentStreamAndDisconnect() {
            InputStream in = mResponseStream;
            mResponseStream = null;
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            HttpURLConnection conn = mActiveConnection;
            mActiveConnection = null;
            if (conn != null) {
                conn.disconnect();
            }
        }

        private void closeQuietly() {
            closeCurrentStreamAndDisconnect();
            if (mUploadProvider != null) {
                try {
                    mUploadProvider.close();
                } catch (IOException ignored) {
                }
            }
        }

        private static boolean isRedirectStatus(int code) {
            return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
        }

        private static NetworkException mapIoException(IOException ioe) {
            if (ioe instanceof UnknownHostException) {
                return new SimpleNetworkException(
                        ioe.getMessage(), ioe, NetworkException.ERROR_HOSTNAME_NOT_RESOLVED, false);
            }
            if (ioe instanceof ConnectException) {
                return new SimpleNetworkException(
                        ioe.getMessage(), ioe, NetworkException.ERROR_CONNECTION_REFUSED, false);
            }
            if (ioe instanceof SocketTimeoutException) {
                return new SimpleNetworkException(
                        ioe.getMessage(), ioe, NetworkException.ERROR_TIMED_OUT, true);
            }
            if (ioe instanceof NoRouteToHostException) {
                return new SimpleNetworkException(
                        ioe.getMessage(), ioe, NetworkException.ERROR_ADDRESS_UNREACHABLE, false);
            }
            return new SimpleNetworkException(
                    ioe.getMessage(), ioe, NetworkException.ERROR_OTHER, false);
        }
    }

    private interface CallbackAction {
        void run() throws Exception;
    }

    private interface UploadAction {
        void run() throws Exception;
    }

    private static final class HostBidirectionalStreamBuilder extends BidirectionalStream.Builder {
        private final HostHttpEngine mEngine;
        private final String mUrl;
        private final Executor mExecutor;
        private final BidirectionalStream.Callback mCallback;
        private String mMethod = "POST";
        private final List<Map.Entry<String, String>> mHeaders =
                new ArrayList<Map.Entry<String, String>>();
        private int mPriority = BidirectionalStream.STREAM_PRIORITY_MEDIUM;
        private boolean mDelayHeaders;
        private boolean mHasTag;
        private int mTag;
        private boolean mHasUid;
        private int mUid;

        HostBidirectionalStreamBuilder(
                HostHttpEngine engine,
                String url,
                Executor executor,
                BidirectionalStream.Callback callback) {
            this.mEngine = engine;
            this.mUrl = url;
            this.mExecutor = executor;
            this.mCallback = callback;
        }

        @Override
        public BidirectionalStream.Builder setHttpMethod(String method) {
            this.mMethod = Objects.requireNonNull(method, "method");
            return this;
        }

        @Override
        public BidirectionalStream.Builder addHeader(String header, String value) {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(value, "value");
            mHeaders.add(new AbstractMap.SimpleImmutableEntry<String, String>(header, value));
            return this;
        }

        @Override
        public BidirectionalStream.Builder setPriority(int priority) {
            this.mPriority = priority;
            return this;
        }

        @Override
        public BidirectionalStream.Builder setDelayRequestHeadersUntilFirstFlushEnabled(
                boolean delayRequestHeadersUntilFirstFlush) {
            this.mDelayHeaders = delayRequestHeadersUntilFirstFlush;
            return this;
        }

        @Override
        public BidirectionalStream.Builder setTrafficStatsTag(int tag) {
            this.mHasTag = true;
            this.mTag = tag;
            return this;
        }

        @Override
        public BidirectionalStream.Builder setTrafficStatsUid(int uid) {
            this.mHasUid = true;
            this.mUid = uid;
            return this;
        }

        @Override
        public BidirectionalStream build() {
            return new HostBidirectionalStream(
                    mEngine,
                    mUrl,
                    mExecutor,
                    mCallback,
                    mMethod,
                    new SimpleHeaderBlock(mHeaders),
                    mPriority,
                    mDelayHeaders,
                    mHasTag,
                    mTag,
                    mHasUid,
                    mUid);
        }
    }

    private static final class HostBidirectionalStream extends BidirectionalStream {
        private final HostHttpEngine mEngine;
        private final String mUrl;
        private final Executor mExecutor;
        private final Callback mCallback;
        private final String mMethod;
        private final HeaderBlock mHeaders;
        private final int mPriority;
        private final boolean mDelayHeaders;
        private final boolean mHasTag;
        private final int mTag;
        private final boolean mHasUid;
        private final int mUid;
        private final ByteArrayOutputStream mWriteBuffer = new ByteArrayOutputStream();
        private final List<ByteBuffer> mPendingWrites = new ArrayList<ByteBuffer>();
        private final List<Boolean> mPendingEnds = new ArrayList<Boolean>();
        private final AtomicBoolean mStarted = new AtomicBoolean(false);
        private final AtomicBoolean mCanceled = new AtomicBoolean(false);
        private final AtomicBoolean mDone = new AtomicBoolean(false);
        private volatile boolean mWriteStreamEnded;
        private volatile InputStream mResponseStream;
        private volatile UrlResponseInfo mResponseInfo;
        private final AtomicLong mReceivedBytes = new AtomicLong(0L);

        HostBidirectionalStream(
                HostHttpEngine engine,
                String url,
                Executor executor,
                Callback callback,
                String method,
                HeaderBlock headers,
                int priority,
                boolean delayHeaders,
                boolean hasTag,
                int tag,
                boolean hasUid,
                int uid) {
            this.mEngine = engine;
            this.mUrl = url;
            this.mExecutor = executor;
            this.mCallback = callback;
            this.mMethod = method;
            this.mHeaders = headers;
            this.mPriority = priority;
            this.mDelayHeaders = delayHeaders;
            this.mHasTag = hasTag;
            this.mTag = tag;
            this.mHasUid = hasUid;
            this.mUid = uid;
        }

        @Override
        public String getHttpMethod() {
            return mMethod;
        }

        @Override
        public boolean hasTrafficStatsTag() {
            return mHasTag;
        }

        @Override
        public int getTrafficStatsTag() {
            if (!mHasTag) {
                throw new IllegalStateException("TrafficStatsTag is not set");
            }
            return mTag;
        }

        @Override
        public boolean hasTrafficStatsUid() {
            return mHasUid;
        }

        @Override
        public int getTrafficStatsUid() {
            if (!mHasUid) {
                throw new IllegalStateException("TrafficStatsUid is not set");
            }
            return mUid;
        }

        @Override
        public HeaderBlock getHeaders() {
            return mHeaders;
        }

        @Override
        public int getPriority() {
            return mPriority;
        }

        @Override
        public boolean isDelayRequestHeadersUntilFirstFlushEnabled() {
            return mDelayHeaders;
        }

        @Override
        public void start() {
            if (!mStarted.compareAndSet(false, true)) {
                throw new IllegalStateException("Stream already started");
            }
            final boolean noBodyMethod =
                    "GET".equalsIgnoreCase(mMethod) || "HEAD".equalsIgnoreCase(mMethod);
            if (noBodyMethod) {
                mWriteStreamEnded = true;
            }
            mExecutor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (mCanceled.get()) {
                                finishCanceled();
                                return;
                            }
                            mCallback.onStreamReady(HostBidirectionalStream.this);
                            if (noBodyMethod) {
                                openAndReceiveResponse();
                            }
                        }
                    });
        }

        @Override
        public synchronized void write(ByteBuffer buffer, boolean endOfStream) {
            Objects.requireNonNull(buffer, "buffer");
            mPendingWrites.add(buffer);
            mPendingEnds.add(Boolean.valueOf(endOfStream));
        }

        @Override
        public void flush() {
            final List<ByteBuffer> buffers;
            final List<Boolean> ends;
            synchronized (this) {
                buffers = new ArrayList<ByteBuffer>(mPendingWrites);
                ends = new ArrayList<Boolean>(mPendingEnds);
                mPendingWrites.clear();
                mPendingEnds.clear();
            }
            mEngine.ioPool().execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            boolean reachedEnd = false;
                            for (int i = 0; i < buffers.size(); i++) {
                                ByteBuffer b = buffers.get(i);
                                final boolean endFlag = ends.get(i).booleanValue();
                                byte[] chunk = new byte[b.remaining()];
                                b.get(chunk);
                                synchronized (mWriteBuffer) {
                                    mWriteBuffer.write(chunk, 0, chunk.length);
                                }
                                if (endFlag) {
                                    reachedEnd = true;
                                    mWriteStreamEnded = true;
                                }
                                final ByteBuffer completedBuf = b;
                                mExecutor.execute(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                mCallback.onWriteCompleted(
                                                        HostBidirectionalStream.this,
                                                        mResponseInfo,
                                                        completedBuf,
                                                        endFlag);
                                            }
                                        });
                            }
                            if (reachedEnd) {
                                openAndReceiveResponse();
                            }
                        }
                    });
        }

        @Override
        public void read(final ByteBuffer buffer) {
            Objects.requireNonNull(buffer, "buffer");
            mEngine.ioPool().execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (mCanceled.get()) {
                                finishCanceled();
                                return;
                            }
                            try {
                                InputStream in = mResponseStream;
                                int max = Math.min(buffer.remaining(), 16384);
                                byte[] tmp = new byte[max];
                                int n = in != null ? in.read(tmp, 0, max) : -1;
                                final boolean eof = n < 0;
                                if (n > 0) {
                                    buffer.put(tmp, 0, n);
                                    mReceivedBytes.addAndGet(n);
                                }
                                if (eof && in != null) {
                                    in.close();
                                    mResponseStream = null;
                                }
                                final UrlResponseInfo info = mResponseInfo;
                                mExecutor.execute(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                mCallback.onReadCompleted(
                                                        HostBidirectionalStream.this,
                                                        info,
                                                        buffer,
                                                        eof);
                                                if (eof && mDone.compareAndSet(false, true)) {
                                                    mCallback.onSucceeded(
                                                            HostBidirectionalStream.this, info);
                                                }
                                            }
                                        });
                            } catch (final IOException ioe) {
                                if (mDone.compareAndSet(false, true)) {
                                    mExecutor.execute(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallback.onFailed(
                                                            HostBidirectionalStream.this,
                                                            mResponseInfo,
                                                            new SimpleNetworkException(
                                                                    ioe.getMessage(),
                                                                    ioe,
                                                                    NetworkException.ERROR_OTHER,
                                                                    false));
                                                }
                                            });
                                }
                            }
                        }
                    });
        }

        @Override
        public void cancel() {
            if (mCanceled.compareAndSet(false, true)) {
                finishCanceled();
            }
        }

        @Override
        public boolean isDone() {
            return mDone.get();
        }

        private void openAndReceiveResponse() {
            mEngine.ioPool().execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                URL url = new URL(mUrl);
                                HttpURLConnection conn =
                                        (HttpURLConnection) mEngine.openConnection(url);
                                conn.setRequestMethod(mMethod);
                                for (Map.Entry<String, String> e : mHeaders.getAsList()) {
                                    conn.addRequestProperty(e.getKey(), e.getValue());
                                }
                                byte[] body;
                                synchronized (mWriteBuffer) {
                                    body = mWriteBuffer.toByteArray();
                                }
                                if (body.length > 0) {
                                    conn.setDoOutput(true);
                                    conn.setFixedLengthStreamingMode(body.length);
                                    OutputStream out = conn.getOutputStream();
                                    try {
                                        out.write(body);
                                    } finally {
                                        out.close();
                                    }
                                }
                                int code = conn.getResponseCode();
                                String msg = conn.getResponseMessage();
                                List<Map.Entry<String, String>> hdrs =
                                        new ArrayList<Map.Entry<String, String>>();
                                for (int i = 0; ; i++) {
                                    String k = conn.getHeaderFieldKey(i);
                                    String v = conn.getHeaderField(i);
                                    if (k == null && v == null) {
                                        break;
                                    }
                                    if (k != null && v != null) {
                                        hdrs.add(
                                                new AbstractMap.SimpleImmutableEntry<
                                                        String, String>(k, v));
                                    }
                                }
                                InputStream in;
                                try {
                                    in = conn.getInputStream();
                                } catch (IOException ioe) {
                                    in = conn.getErrorStream();
                                }
                                mResponseStream = in;
                                final UrlResponseInfo info =
                                        new SimpleUrlResponseInfo(
                                                Collections.singletonList(mUrl),
                                                code,
                                                msg,
                                                new SimpleHeaderBlock(hdrs),
                                                false,
                                                "http/1.1",
                                                mReceivedBytes);
                                mResponseInfo = info;
                                mExecutor.execute(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                mCallback.onResponseHeadersReceived(
                                                        HostBidirectionalStream.this, info);
                                            }
                                        });
                            } catch (final IOException ioe) {
                                if (mDone.compareAndSet(false, true)) {
                                    mExecutor.execute(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    mCallback.onFailed(
                                                            HostBidirectionalStream.this,
                                                            mResponseInfo,
                                                            new SimpleNetworkException(
                                                                    ioe.getMessage(),
                                                                    ioe,
                                                                    NetworkException.ERROR_OTHER,
                                                                    false));
                                                }
                                            });
                                }
                            }
                        }
                    });
        }

        private void finishCanceled() {
            if (mDone.compareAndSet(false, true)) {
                InputStream in = mResponseStream;
                mResponseStream = null;
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                mExecutor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                mCallback.onCanceled(HostBidirectionalStream.this, mResponseInfo);
                            }
                        });
            }
        }
    }
}
