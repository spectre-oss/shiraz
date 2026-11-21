// SPDX-License-Identifier: GPL-3.0-only
package android.net.http;

import android.net.Network;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

public abstract class UrlRequest {
    public static final int REQUEST_PRIORITY_IDLE = 0;
    public static final int REQUEST_PRIORITY_LOWEST = 1;
    public static final int REQUEST_PRIORITY_LOW = 2;
    public static final int REQUEST_PRIORITY_MEDIUM = 3;
    public static final int REQUEST_PRIORITY_HIGHEST = 4;

    UrlRequest() {}

    public abstract String getHttpMethod();

    public abstract HeaderBlock getHeaders();

    public abstract boolean isCacheDisabled();

    public abstract boolean isDirectExecutorAllowed();

    public abstract int getPriority();

    public abstract boolean hasTrafficStatsTag();

    public abstract int getTrafficStatsTag();

    public abstract boolean hasTrafficStatsUid();

    public abstract int getTrafficStatsUid();

    public abstract void start();

    public abstract void followRedirect();

    public abstract void read(ByteBuffer buffer);

    public abstract void cancel();

    public abstract boolean isDone();

    public abstract void getStatus(StatusListener listener);

    public abstract static class Builder {
        Builder() {}

        public abstract Builder setHttpMethod(String method);

        public abstract Builder addHeader(String header, String value);

        public abstract Builder setCacheDisabled(boolean disableCache);

        public abstract Builder setPriority(int priority);

        public abstract Builder setUploadDataProvider(
                UploadDataProvider uploadDataProvider, Executor executor);

        public abstract Builder setDirectExecutorAllowed(boolean allowDirectExecutor);

        public abstract Builder bindToNetwork(Network network);

        public abstract Builder setTrafficStatsTag(int tag);

        public abstract Builder setTrafficStatsUid(int uid);

        public abstract UrlRequest build();
    }

    public interface Callback {
        void onRedirectReceived(
                UrlRequest request, UrlResponseInfo info, String newLocationUrl)
                throws Exception;

        void onResponseStarted(UrlRequest request, UrlResponseInfo info) throws Exception;

        void onReadCompleted(
                UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer)
                throws Exception;

        void onSucceeded(UrlRequest request, UrlResponseInfo info);

        void onFailed(UrlRequest request, UrlResponseInfo info, HttpException error);

        void onCanceled(UrlRequest request, UrlResponseInfo info);
    }

    public static class Status {
        public static final int INVALID = -1;
        public static final int IDLE = 0;
        public static final int WAITING_FOR_STALLED_SOCKET_POOL = 1;
        public static final int WAITING_FOR_AVAILABLE_SOCKET = 2;
        public static final int WAITING_FOR_DELEGATE = 3;
        public static final int WAITING_FOR_CACHE = 4;
        public static final int DOWNLOADING_PAC_FILE = 5;
        public static final int RESOLVING_PROXY_FOR_URL = 6;
        public static final int RESOLVING_HOST_IN_PAC_FILE = 7;
        public static final int ESTABLISHING_PROXY_TUNNEL = 8;
        public static final int RESOLVING_HOST = 9;
        public static final int CONNECTING = 10;
        public static final int SSL_HANDSHAKE = 11;
        public static final int SENDING_REQUEST = 12;
        public static final int WAITING_FOR_RESPONSE = 13;
        public static final int READING_RESPONSE = 14;

        private Status() {}
    }

    public interface StatusListener {
        void onStatus(int status);
    }
}
