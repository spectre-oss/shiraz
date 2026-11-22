// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedded WebView backed by Shiraz's pure-Java HTML5 DOM tree ({@link ShirazWebDom})
 * and JavaScript engine ({@link ShirazJsEngine}) with {@link JavascriptInterface}
 * bridging, navigation history, and DOM mutation re-rendering.
 */
public class WebView extends FrameLayout {
    public interface FindListener {
        void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting);
    }

    public class PrivateAccess {}

    private static final class HistoryEntry {
        final String url;
        String title;
        final String rawPayload;
        final boolean htmlPayload;

        HistoryEntry(String url, String title, String rawPayload, boolean htmlPayload) {
            this.url = url == null ? "" : url;
            this.title = title;
            this.rawPayload = rawPayload == null ? "" : rawPayload;
            this.htmlPayload = htmlPayload;
        }
    }

    private static final class TrackableJsResult extends JsResult {
        boolean confirmed;

        @Override public void confirm() { confirmed = true; }
        @Override public void cancel() { confirmed = false; }
    }

    private static final class TrackableJsPromptResult extends JsPromptResult {
        boolean confirmed;

        @Override public void confirm() { confirmed = true; }
        @Override public void confirm(String result) {
            confirmed = true;
            super.confirm(result);
        }
        @Override public void cancel() { confirmed = false; }
    }

    private final WebSettings settings = new WebSettings();
    private final PrivateAccess privateAccess = new PrivateAccess();
    private final TextView body;
    private final List<HistoryEntry> history = new ArrayList<HistoryEntry>();
    private int historyIndex = -1;
    private ShirazWebDom dom = ShirazWebDom.parse("");
    private final ShirazJsEngine jsEngine;
    private String url = "";
    private int progress;
    private String title;
    private FindListener findListener;
    private WebViewClient client;
    private WebChromeClient chrome;
    private boolean inPageCallback;
    private boolean destroyed;

    public PrivateAccess shirazPrivateAccess() { return privateAccess; }

    public WebView(Context context) { this(context, null); }
    public WebView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public WebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        body = new TextView(context);
        body.setPadding(24, 24, 24, 24);
        ScrollView scroll = new ScrollView(context);
        scroll.addView(body, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        this.jsEngine = new ShirazJsEngine(new ShirazJsEngine.HostCallbacks() {
            @Override public String getUrl() {
                return WebView.this.url;
            }

            @Override public void onDomMutated() {
                if (!destroyed) {
                    renderCurrentDom();
                }
            }

            @Override public void onTitleChanged(String newTitle) {
                WebView.this.title = newTitle;
                if (historyIndex >= 0 && historyIndex < history.size()) {
                    history.get(historyIndex).title = newTitle;
                }
                if (chrome != null && newTitle != null) {
                    chrome.onReceivedTitle(WebView.this, newTitle);
                }
            }

            @Override public void onConsole(ConsoleMessage.MessageLevel level, String message) {
                if (chrome != null) {
                    chrome.onConsoleMessage(new ConsoleMessage(message, WebView.this.url, 1, level));
                }
            }

            @Override public void onAlert(String message) {
                if (chrome != null) {
                    chrome.onJsAlert(WebView.this, WebView.this.url, message, new TrackableJsResult());
                }
            }

            @Override public boolean onConfirm(String message) {
                if (chrome == null) return false;
                TrackableJsResult res = new TrackableJsResult();
                chrome.onJsConfirm(WebView.this, WebView.this.url, message, res);
                return res.confirmed;
            }

            @Override public String onPrompt(String message, String defaultValue) {
                if (chrome == null) return null;
                TrackableJsPromptResult res = new TrackableJsPromptResult();
                chrome.onJsPrompt(WebView.this, WebView.this.url, message, defaultValue, res);
                return res.confirmed ? res.getStringResult() : null;
            }

            @Override public void onWindowPostMessage(String data, String targetOrigin) {
                jsEngine.dispatchWindowMessage(data, targetOrigin);
            }
        }, dom);
    }

    /** Disabling debugging is valid without a DevTools server; enabling it is unsupported. */
    public static void setWebContentsDebuggingEnabled(boolean enabled) {
        if (enabled) throw new UnsupportedOperationException("WebView remote debugging requires a DevTools server");
    }

    public static WebViewFactoryProvider getFactory() { return WebViewFactory.getProvider(); }
    public WebSettings getSettings() { return settings; }

    public void loadUrl(String url, java.util.Map<String, String> additionalHttpHeaders) {
        if (additionalHttpHeaders != null && !additionalHttpHeaders.isEmpty() && url != null
                && (url.regionMatches(true, 0, "http:", 0, 5) || url.regionMatches(true, 0, "https:", 0, 6))) {
            throw new UnsupportedOperationException("WebView HTTP requests require a web engine");
        }
        loadUrl(url);
    }

    public void loadUrl(String url) {
        if (inPageCallback) {
            final String next = url;
            post(new Runnable() {
                @Override public void run() { loadUrl(next); }
            });
            return;
        }
        if (url != null && url.regionMatches(true, 0, "javascript:", 0, 11)) {
            evaluateJavascript(url.substring(11), null);
            return;
        }
        String targetUrl = url == null ? "" : url;
        FetchedPayload payload = fetchPayload(targetUrl);
        loadDocumentInternal(targetUrl, payload.content, payload.isHtml, true);
    }

    public void loadData(String data, String mimeType, String encoding) {
        loadDataWithBaseURL(null, data, mimeType, encoding, null);
    }

    public void loadDataWithBaseURL(
            String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        String resolvedUrl = historyUrl == null ? (baseUrl == null ? "" : baseUrl) : historyUrl;
        String payload = data == null ? "" : data;
        if ("base64".equalsIgnoreCase(encoding)) {
            try {
                payload = new String(java.util.Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
            } catch (RuntimeException ignored) {
                // Keep original payload if not valid base64.
            }
        }
        boolean isHtml = mimeType == null || mimeType.isEmpty()
                || mimeType.toLowerCase(java.util.Locale.ROOT).contains("html");
        loadDocumentInternal(resolvedUrl, payload, isHtml, true);
    }

    private void loadDocumentInternal(
            String targetUrl, String rawPayload, boolean isHtml, boolean recordHistory) {
        this.url = targetUrl == null ? "" : targetUrl;
        this.progress = 0;
        this.title = null;
        inPageCallback = true;
        try {
            if (client != null) client.onPageStarted(this, this.url, null);
            if (isHtml) {
                dom = ShirazWebDom.parse(rawPayload);
                title = dom.getTitle();
                jsEngine.resetForDocument(dom);
                if (settings.getJavaScriptEnabled()) {
                    for (String script : dom.collectInlineScripts()) {
                        try {
                            jsEngine.evaluate(script);
                        } catch (RuntimeException err) {
                            if (chrome != null) {
                                chrome.onConsoleMessage(new ConsoleMessage(
                                        String.valueOf(err.getMessage()), this.url, 1,
                                        ConsoleMessage.MessageLevel.ERROR));
                            }
                        }
                    }
                    String updatedTitle = dom.getTitle();
                    if (updatedTitle != null) title = updatedTitle;
                }
                renderCurrentDom();
            } else {
                dom = ShirazWebDom.parse("");
                dom.getBody().setTextContent(rawPayload == null ? "" : rawPayload);
                jsEngine.resetForDocument(dom);
                show(rawPayload);
            }

            if (recordHistory) {
                pushHistory(new HistoryEntry(this.url, this.title, rawPayload, isHtml));
            } else if (historyIndex >= 0 && historyIndex < history.size()) {
                history.get(historyIndex).title = this.title;
            }

            progress = 100;
            if (client != null) client.onPageFinished(this, this.url);
            if (chrome != null) {
                chrome.onProgressChanged(this, 100);
                if (title != null) chrome.onReceivedTitle(this, title);
            }
        } finally {
            inPageCallback = false;
        }
    }

    private void pushHistory(HistoryEntry entry) {
        while (history.size() > historyIndex + 1) {
            history.remove(history.size() - 1);
        }
        history.add(entry);
        historyIndex = history.size() - 1;
    }

    private void renderCurrentDom() {
        String rendered = dom.toRenderedText();
        if (rendered.length() > 32000) {
            rendered = rendered.substring(0, 32000) + "\n\u2026";
        }
        show(rendered);
    }

    private void show(CharSequence text) {
        body.setText(text == null ? "" : text);
    }

    private static final class FetchedPayload {
        final String content;
        final boolean isHtml;

        FetchedPayload(String content, boolean isHtml) {
            this.content = content;
            this.isHtml = isHtml;
        }
    }

    private FetchedPayload fetchPayload(String target) {
        if (target == null || target.isEmpty()) return new FetchedPayload("", true);
        String asset = assetPath(target);
        if (asset != null) {
            try {
                byte[] bytes = readAsset(asset);
                return new FetchedPayload(new String(bytes, charsetOf(null)), true);
            } catch (Exception error) {
                System.err.println("SHIRAZ_WEBVIEW_ASSET_FAILED " + asset + " " + error.getMessage());
                return new FetchedPayload(target, false);
            }
        }
        if (target.startsWith("file:")) {
            try {
                java.net.URI uri = new java.net.URI(target);
                if (uri.getAuthority() != null && !uri.getAuthority().isEmpty()) {
                    throw new java.io.IOException("Remote file authority");
                }
                java.io.File file = new java.io.File(uri.getPath()).getCanonicalFile();
                java.io.File files = getContext().getFilesDir().getCanonicalFile();
                java.io.File cache = getContext().getCacheDir().getCanonicalFile();
                if (!within(file, files) && !within(file, cache)) {
                    throw new java.io.IOException("File outside app storage");
                }
                try (InputStream in = new java.io.FileInputStream(file)) {
                    return new FetchedPayload(new String(readContent(in), StandardCharsets.UTF_8), true);
                }
            } catch (Exception error) {
                throw new IllegalArgumentException("Cannot load app-local WebView content", error);
            }
        }
        if (target.startsWith("data:")) {
            int comma = target.indexOf(',');
            if (comma > 5) {
                String meta = target.substring(5, comma);
                String payload = target.substring(comma + 1);
                try {
                    byte[] bytes = meta.toLowerCase(java.util.Locale.ROOT).contains(";base64")
                            ? java.util.Base64.getDecoder().decode(payload)
                            : payload.getBytes(StandardCharsets.UTF_8);
                    return new FetchedPayload(new String(bytes, charsetOf(meta)), true);
                } catch (RuntimeException error) {
                    return new FetchedPayload(target, false);
                }
            }
        }
        return new FetchedPayload(target, false);
    }

    private static boolean within(java.io.File file, java.io.File directory) {
        return file.getPath().startsWith(directory.getPath() + java.io.File.separator);
    }

    private byte[] readAsset(String path) throws Exception {
        try (InputStream in = getContext().getAssets().open(path)) {
            return readContent(in);
        }
    }

    private static byte[] readContent(InputStream in) throws java.io.IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (n > 4 * 1024 * 1024 - out.size()) {
                    throw new java.io.IOException("WebView content too large");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        }
    }

    private static String assetPath(String target) {
        final String prefix = "file:///android_asset/";
        if (!target.startsWith(prefix)) return null;
        String path = target.substring(prefix.length());
        int cut = path.length();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '?' || c == '#') { cut = i; break; }
        }
        path = path.substring(0, cut);
        if (path.isEmpty() || path.charAt(0) == '/' || path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            return null;
        }
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
        }
        return path;
    }

    private static Charset charsetOf(String meta) {
        if (meta != null) {
            String lower = meta.toLowerCase(java.util.Locale.ROOT);
            int at = lower.indexOf("charset=");
            if (at >= 0) {
                String name = meta.substring(at + 8).trim();
                int end = name.length();
                for (int i = 0; i < name.length(); i++) {
                    char c = name.charAt(i);
                    if (c == ';' || c == ' ' || c == ',') { end = i; break; }
                }
                try {
                    return Charset.forName(name.substring(0, end));
                } catch (RuntimeException ignored) {
                    // Fall back to UTF-8.
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** Kept for compatibility with any helper paths that need quick HTML-to-text conversion. */
    static CharSequence plain(String html) {
        if (html == null || html.isEmpty()) return "";
        try {
            CharSequence spanned = Html.fromHtml(html);
            if (spanned.length() <= 32000) return spanned;
        } catch (IllegalArgumentException ignored) {
            // Fall through to DOM rendering.
        }
        String text = ShirazWebDom.parse(html).toRenderedText();
        if (text.length() > 32000) text = text.substring(0, 32000) + "\n\u2026";
        return text;
    }

    public String getTitle() { return title; }
    public int getProgress() { return progress; }
    public String getUrl() { return url; }
    public void stopLoading() {}

    public void reload() {
        if (historyIndex >= 0 && historyIndex < history.size()) {
            HistoryEntry entry = history.get(historyIndex);
            loadDocumentInternal(entry.url, entry.rawPayload, entry.htmlPayload, false);
        }
    }

    public boolean canGoBack() { return historyIndex > 0; }
    public boolean canGoForward() { return historyIndex >= 0 && historyIndex + 1 < history.size(); }

    public void goBack() {
        if (canGoBack()) {
            historyIndex--;
            HistoryEntry entry = history.get(historyIndex);
            loadDocumentInternal(entry.url, entry.rawPayload, entry.htmlPayload, false);
        }
    }

    public void goForward() {
        if (canGoForward()) {
            historyIndex++;
            HistoryEntry entry = history.get(historyIndex);
            loadDocumentInternal(entry.url, entry.rawPayload, entry.htmlPayload, false);
        }
    }

    public void clearCache(boolean includeDiskFiles) {}

    public void clearHistory() {
        if (historyIndex >= 0 && historyIndex < history.size()) {
            HistoryEntry current = history.get(historyIndex);
            history.clear();
            history.add(current);
            historyIndex = 0;
        } else {
            history.clear();
            historyIndex = -1;
        }
    }

    public void clearFormData() {}
    public void setWebViewClient(WebViewClient client) { this.client = client; }
    public void setWebChromeClient(WebChromeClient client) { chrome = client; }

    public void addJavascriptInterface(Object object, String name) {
        jsEngine.addJavascriptInterface(object, name);
    }

    public void removeJavascriptInterface(String name) {
        jsEngine.removeJavascriptInterface(name);
    }

    public void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
        if (destroyed) {
            if (resultCallback != null) resultCallback.onReceiveValue("null");
            return;
        }
        String jsonResult = "null";
        try {
            jsonResult = jsEngine.evaluateToJson(script);
        } catch (RuntimeException err) {
            if (chrome != null) {
                chrome.onConsoleMessage(new ConsoleMessage(
                        String.valueOf(err.getMessage()), this.url, 1,
                        ConsoleMessage.MessageLevel.ERROR));
            }
        }
        if (resultCallback != null) {
            resultCallback.onReceiveValue(jsonResult);
        }
    }

    public void setDownloadListener(DownloadListener listener) {}
    public void setFindListener(FindListener listener) { findListener = listener; }

    public void findAllAsync(String find) {
        int matches = dom.countTextMatches(find);
        if (findListener != null) {
            findListener.onFindResultReceived(0, matches, true);
        }
    }

    public void clearMatches() {}
    public void setNetworkAvailable(boolean networkUp) {}
    public void onPause() {}
    public void onResume() {}
    public void pauseTimers() {}
    public void resumeTimers() {}
    public void freeMemory() {}

    public void destroy() {
        destroyed = true;
        history.clear();
        historyIndex = -1;
        removeAllViews();
    }

    public static class HitTestResult {
        public static final int UNKNOWN_TYPE = 0;
        @Deprecated public static final int ANCHOR_TYPE = 1;
        public static final int PHONE_TYPE = 2;
        public static final int GEO_TYPE = 3;
        public static final int EMAIL_TYPE = 4;
        public static final int IMAGE_TYPE = 5;
        @Deprecated public static final int IMAGE_ANCHOR_TYPE = 6;
        public static final int SRC_ANCHOR_TYPE = 7;
        public static final int SRC_IMAGE_ANCHOR_TYPE = 8;
        public static final int EDIT_TEXT_TYPE = 9;
        private int mType = UNKNOWN_TYPE;
        private String mExtra;

        public HitTestResult() {}
        public void setType(int type) { mType = type; }
        public void setExtra(String extra) { mExtra = extra; }
        public int getType() { return mType; }
        public String getExtra() { return mExtra; }
    }

    @Deprecated
    public interface PictureListener {
        @Deprecated void onNewPicture(WebView view, android.graphics.Picture picture);
    }

    public abstract static class VisualStateCallback {
        public VisualStateCallback() {}
        public abstract void onComplete(long requestId);
    }

    public class WebViewTransport {
        private WebView mWebview;

        public WebViewTransport() {}
        public synchronized void setWebView(WebView webview) { mWebview = webview; }
        public synchronized WebView getWebView() { return mWebview; }
    }

    private WebViewRenderProcessClient renderProcessClient;
    private PictureListener pictureListener;

    public HitTestResult getHitTestResult() { return new HitTestResult(); }

    @Deprecated
    public void setPictureListener(PictureListener listener) { pictureListener = listener; }

    public void postVisualStateCallback(final long requestId, final VisualStateCallback callback) {
        if (callback == null) return;
        post(new Runnable() {
            @Override public void run() { callback.onComplete(requestId); }
        });
    }

    public WebMessagePort[] createWebMessageChannel() {
        final LocalMessagePort p0 = new LocalMessagePort();
        final LocalMessagePort p1 = new LocalMessagePort();
        p0.peer = p1;
        p1.peer = p0;
        return new WebMessagePort[] { p0, p1 };
    }

    public void postWebMessage(WebMessage message, android.net.Uri targetOrigin) {
        if (destroyed || message == null) return;
        String origin = targetOrigin == null ? "" : targetOrigin.toString();
        try {
            jsEngine.dispatchWindowMessage(message.getData(), origin);
        } catch (RuntimeException err) {
            if (chrome != null) {
                chrome.onConsoleMessage(new ConsoleMessage(
                        String.valueOf(err.getMessage()), this.url, 1,
                        ConsoleMessage.MessageLevel.ERROR));
            }
        }
    }

    public WebBackForwardList copyBackForwardList() {
        List<SnapshotHistoryItem> items = new ArrayList<SnapshotHistoryItem>(history.size());
        for (HistoryEntry entry : history) {
            items.add(new SnapshotHistoryItem(entry.url, entry.title));
        }
        return new SnapshotBackForwardList(items, historyIndex);
    }

    public void setWebViewRenderProcessClient(
            java.util.concurrent.Executor executor, WebViewRenderProcessClient client) {
        renderProcessClient = client;
    }

    public void setWebViewRenderProcessClient(WebViewRenderProcessClient client) {
        renderProcessClient = client;
    }

    public WebViewRenderProcessClient getWebViewRenderProcessClient() { return renderProcessClient; }
    public WebViewRenderProcess getWebViewRenderProcess() { return null; }

    private static final class LocalMessagePort extends WebMessagePort {
        LocalMessagePort peer;
        private volatile boolean closed;
        private volatile WebMessageCallback callback;
        private volatile android.os.Handler handler;

        @Override public void postMessage(final WebMessage message) {
            if (closed) throw new IllegalStateException("Port is already closed");
            final LocalMessagePort target = peer;
            if (target == null || target.closed) return;
            final WebMessageCallback cb = target.callback;
            final android.os.Handler h = target.handler;
            if (cb != null) {
                if (h != null) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            if (!target.closed) cb.onMessage(target, message);
                        }
                    });
                } else {
                    cb.onMessage(target, message);
                }
            }
        }

        @Override public void close() { closed = true; callback = null; handler = null; }
        @Override public void setWebMessageCallback(WebMessageCallback cb) { setWebMessageCallback(cb, null); }
        @Override public void setWebMessageCallback(WebMessageCallback cb, android.os.Handler h) {
            if (closed) throw new IllegalStateException("Port is already closed");
            this.callback = cb;
            this.handler = h;
        }
    }

    private static final class SnapshotHistoryItem extends WebHistoryItem {
        private final String itemUrl;
        private final String itemTitle;

        SnapshotHistoryItem(String itemUrl, String itemTitle) {
            this.itemUrl = itemUrl != null ? itemUrl : "";
            this.itemTitle = itemTitle != null ? itemTitle : "";
        }

        @Override public String getUrl() { return itemUrl; }
        @Override public String getOriginalUrl() { return itemUrl; }
        @Override public String getTitle() { return itemTitle; }
        @Override public android.graphics.Bitmap getFavicon() { return null; }
        @Override protected WebHistoryItem clone() { return new SnapshotHistoryItem(itemUrl, itemTitle); }
    }

    private static final class SnapshotBackForwardList extends WebBackForwardList {
        private final List<SnapshotHistoryItem> items;
        private final int currentIndex;

        SnapshotBackForwardList(List<SnapshotHistoryItem> items, int currentIndex) {
            this.items = new ArrayList<SnapshotHistoryItem>(items);
            this.currentIndex = (currentIndex >= 0 && currentIndex < this.items.size()) ? currentIndex : -1;
        }

        @Override public WebHistoryItem getCurrentItem() {
            return currentIndex >= 0 ? items.get(currentIndex) : null;
        }

        @Override public int getCurrentIndex() { return currentIndex; }

        @Override public WebHistoryItem getItemAtIndex(int index) {
            return (index >= 0 && index < items.size()) ? items.get(index) : null;
        }

        @Override public int getSize() { return items.size(); }

        @Override protected WebBackForwardList clone() {
            return new SnapshotBackForwardList(items, currentIndex);
        }
    }
}
