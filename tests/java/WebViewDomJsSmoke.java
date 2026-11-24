// SPDX-License-Identifier: GPL-3.0-only
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Looper;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebMessage;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class WebViewDomJsSmoke {
    public static final class NativeBridge {
        String lastPing;

        @JavascriptInterface
        public String greet(String name, int times) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < times; i++) {
                if (i > 0) sb.append('!');
                sb.append("Hi ").append(name);
            }
            return sb.toString();
        }

        @JavascriptInterface
        public void ping(String msg) {
            lastPing = msg;
        }

        // Intentionally NOT annotated with @JavascriptInterface: must be inaccessible from JS.
        public String unexposedSecret() {
            return "TOP_SECRET";
        }
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertEquals(Object expected, Object actual, String msg) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(msg + ": expected=<" + expected + "> actual=<" + actual + ">");
        }
    }

    private static String evalSync(WebView view, String js) {
        final String[] out = new String[1];
        view.evaluateJavascript(js, new ValueCallback<String>() {
            @Override public void onReceiveValue(String value) {
                out[0] = value;
            }
        });
        return out[0];
    }

    private static String renderedText(WebView view) {
        ScrollView scroll = (ScrollView) view.getChildAt(0);
        TextView tv = (TextView) scroll.getChildAt(0);
        return tv.getText().toString();
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        final java.nio.file.Path appDir = java.nio.file.Files.createTempDirectory("shiraz-webview-js-");
        Context context = new ContextWrapper(null) {
            private final Resources.Theme theme = Resources.getSystem().newTheme();
            @Override public File getFilesDir() { return appDir.toFile(); }
            @Override public File getCacheDir() { return appDir.toFile(); }
            @Override public Resources getResources() { return Resources.getSystem(); }
            @Override public Resources.Theme getTheme() { return theme; }
        };

        WebView view = new WebView(context);
        final List<String> consoleLogs = new ArrayList<String>();
        final List<String> titles = new ArrayList<String>();
        final List<String> alerts = new ArrayList<String>();

        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView v, String title) {
                titles.add(title);
            }
            @Override public boolean onConsoleMessage(ConsoleMessage cm) {
                consoleLogs.add(cm.messageLevel() + ":" + cm.message());
                return true;
            }
            @Override public boolean onJsAlert(WebView v, String url, String message, JsResult result) {
                alerts.add(message);
                result.confirm();
                return true;
            }
            @Override public boolean onJsConfirm(WebView v, String url, String message, JsResult result) {
                if ("accept?".equals(message)) result.confirm();
                else result.cancel();
                return true;
            }
            @Override public boolean onJsPrompt(
                    WebView v, String url, String message, String def, JsPromptResult result) {
                result.confirm(def + "-ok");
                return true;
            }
        });

        // 1. Register @JavascriptInterface bridge
        NativeBridge bridge = new NativeBridge();
        view.addJavascriptInterface(bridge, "Android");

        // 2. Load HTML with inline script, DOM elements, attributes, classes
        String page1 = "<!doctype html><html><head><title>Initial</title></head><body>"
                + "<h1 id='heading' class='hero primary'>Welcome</h1>"
                + "<ul id='list'><li class='item active' data-k='1'>Alpha</li>"
                + "<li class='item' data-k='2'>Beta</li></ul>"
                + "<button id='btn'>Go</button>"
                + "<script>"
                + "document.title = 'Dynamic Title';"
                + "var el = document.getElementById('heading');"
                + "el.textContent = Android.greet('Shiraz', 2);"
                + "Android.ping('booted');"
                + "console.log('ready', 42);"
                + "</script></body></html>";

        view.loadDataWithBaseURL("https://app.local/p1", page1, "text/html", "UTF-8", null);

        assertEquals("Dynamic Title", view.getTitle(), "script-updated document.title");
        assertTrue(titles.contains("Dynamic Title"), "onReceivedTitle fired for dynamic title");
        assertEquals("booted", bridge.lastPing, "@JavascriptInterface void call");
        assertTrue(consoleLogs.contains("LOG:ready 42"), "console.log forwarded to WebChromeClient");
        assertTrue(renderedText(view).contains("Hi Shiraz!Hi Shiraz"), "DOM mutation reflected in TextView");

        // 3. Verify unannotated methods are NOT exposed on bridge
        assertEquals("\"undefined\"", evalSync(view, "typeof Android.unexposedSecret"),
                "unannotated method must be blocked");
        assertEquals("\"undefined\"", evalSync(view, "typeof Android.getClass"),
                "Object.getClass must be blocked");

        // 4. CSS selectors, DOM manipulation, events, JSON, arrays, arrow functions
        assertEquals("\"Alpha\"", evalSync(view, "document.querySelector('ul#list > li.active').textContent"),
                "child combinator + class selector");
        assertEquals("\"Beta\"", evalSync(view, "document.querySelector('[data-k=\"2\"]').textContent"),
                "attribute equals selector");
        assertEquals("2", evalSync(view, "document.querySelectorAll('.item').length"), "querySelectorAll count");
        assertEquals("[2,4,6]", evalSync(view, "[1,2,3].map(x => x * 2)"), "arrow function + Array.map");
        assertEquals("{\"sum\":15}", evalSync(view, "({sum: [1,2,3,4,5].reduce((a,b) => a + b, 0)})"),
                "Array.reduce + object JSON serialization");

        // DOM createElement + appendChild + click event listener
        evalSync(view, "var clicks = 0;"
                + "var b = document.getElementById('btn');"
                + "b.addEventListener('click', function(e) { clicks++; e.target.textContent = 'Clicked ' + clicks; });"
                + "b.click(); b.click();");
        assertEquals("2", evalSync(view, "clicks"), "DOM event listener count");
        assertTrue(renderedText(view).contains("Clicked 2"), "click DOM mutation rendered");

        // 5. javascript: URL execution (no history push)
        view.loadUrl("javascript:var viaUrl = 99;");
        assertEquals("99", evalSync(view, "viaUrl"), "javascript: URL executed in current document");
        assertEquals(1, view.copyBackForwardList().getSize(), "javascript: URL must not add history entry");

        // 6. postWebMessage -> window message listener
        evalSync(view, "var received = ''; window.addEventListener('message', e => { received = e.origin + ':' + e.data; });");
        view.postWebMessage(new WebMessage("payload-123"), Uri.parse("https://host.local"));
        assertEquals("\"https://host.local:payload-123\"", evalSync(view, "received"),
                "postWebMessage delivered to window listener");

        // 7. alert / confirm / prompt hooks
        evalSync(view, "alert('hello-alert');");
        assertTrue(alerts.contains("hello-alert"), "alert forwarded");
        assertEquals("true", evalSync(view, "confirm('accept?')"), "confirm true");
        assertEquals("false", evalSync(view, "confirm('reject?')"), "confirm false");
        assertEquals("\"seed-ok\"", evalSync(view, "prompt('name?', 'seed')"), "prompt result");

        // 8. findAllAsync
        final int[] found = new int[] { -1 };
        view.setFindListener(new WebView.FindListener() {
            @Override public void onFindResultReceived(int active, int count, boolean done) {
                if (done) found[0] = count;
            }
        });
        view.findAllAsync("Shiraz");
        assertEquals(2, found[0], "findAllAsync match count");

        // 9. Multi-entry back/forward navigation history & reload
        view.loadDataWithBaseURL("https://app.local/p2", "<title>Page 2</title><p>Second</p>",
                "text/html", "UTF-8", null);
        assertTrue(view.canGoBack(), "canGoBack after second page");
        assertTrue(!view.canGoForward(), "cannot goForward at tip");
        WebBackForwardList list = view.copyBackForwardList();
        assertEquals(2, list.getSize(), "history size");
        assertEquals(1, list.getCurrentIndex(), "history index");
        assertEquals("Page 2", list.getCurrentItem().getTitle(), "history current title");

        view.goBack();
        assertEquals("https://app.local/p1", view.getUrl(), "goBack URL");
        assertTrue(view.canGoForward(), "canGoForward after goBack");
        view.goForward();
        assertEquals("https://app.local/p2", view.getUrl(), "goForward URL");
        view.reload();
        assertEquals("Page 2", view.getTitle(), "reload preserves title");
        view.clearHistory();
        assertTrue(!view.canGoBack(), "clearHistory drops previous entries");
        assertEquals(1, view.copyBackForwardList().getSize(), "clearHistory retains current page");

        // 10. Step budget guard prevents infinite loop hang
        int errorsBefore = consoleLogs.size();
        assertEquals("null", evalSync(view, "while (true) {}"), "infinite loop returns null");
        assertTrue(consoleLogs.size() > errorsBefore, "infinite loop logs console error");

        // 11. removeJavascriptInterface revokes access
        view.removeJavascriptInterface("Android");
        assertEquals("\"undefined\"", evalSync(view, "typeof Android"), "removed JS interface");

        // 12. Disabled JavaScript skips inline <script>
        view.getSettings().setJavaScriptEnabled(false);
        view.loadData("<title>Static</title><script>document.title='Mutated';</script><p>Safe</p>",
                "text/html", "UTF-8");
        assertEquals("Static", view.getTitle(), "inline script skipped when JS disabled");

        // 13. Rich DOM/JS contentEditable editor (Joplin/CodeMirror-style round-trip)
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        final List<String> rnMessages = new ArrayList<String>();
        view.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void postMessage(String msg) {
                rnMessages.add(msg);
            }
        }, "ReactNativeWebView");

        String editorHtml = "<!doctype html><html><head><title>Note Editor</title></head><body>"
                + "<div id='ed' class='cm-content' contenteditable='true' data-note-id='n42'>Initial body</div>"
                + "<script>"
                + "var ed = document.getElementById('ed');"
                + "var observed = 0;"
                + "var docInputBubbled = 0;"
                + "var selChanges = 0;"
                + "var readyFired = false;"
                + "document.addEventListener('DOMContentLoaded', () => { readyFired = true; });"
                + "document.addEventListener('selectionchange', () => { selChanges++; });"
                + "document.addEventListener('input', () => { docInputBubbled++; });"
                + "var mo = new MutationObserver((records) => { observed += records.length; });"
                + "mo.observe(ed, { childList: true, characterData: true, attributes: true, subtree: true });"
                + "ed.addEventListener('input', (e) => {"
                + "  ReactNativeWebView.postMessage(JSON.stringify({ kind: 'change', body: ed.textContent, id: ed.dataset.noteId }));"
                + "});"
                + "ed.classList.add('focused'); ed.style.backgroundColor = '#fff';"
                + "localStorage.setItem('draft', 'saved-draft');"
                + "</script></body></html>";

        view.loadDataWithBaseURL("file:///app/MarkdownEditor.html", editorHtml, "text/html", "UTF-8", null);
        assertEquals("true", evalSync(view, "readyFired"), "DOMContentLoaded fired");
        assertEquals("true", evalSync(view, "ed.isContentEditable"), "contentEditable detected");
        assertEquals("true", evalSync(view, "ed.classList.contains('focused')"), "classList.add");
        assertEquals("\"#fff\"", evalSync(view, "ed.style.backgroundColor"), "style camelCase");
        assertEquals("\"n42\"", evalSync(view, "ed.dataset.noteId"), "dataset camelCase");
        assertEquals("\"saved-draft\"", evalSync(view, "localStorage.getItem('draft')"), "localStorage");

        // User edits directly through WebView's interactive EditText
        ScrollView editorScroll = (ScrollView) view.getChildAt(0);
        EditText editorInput = (EditText) editorScroll.getChildAt(0);
        assertTrue(editorInput.isFocusableInTouchMode(), "contentEditable enables interactive EditText");
        assertEquals("Initial body", editorInput.getText().toString(), "editor seeded with DOM content");
        editorInput.setText("Updated Joplin note body");
        assertEquals("\"Updated Joplin note body\"", evalSync(view, "ed.textContent"),
                "interactive EditText synced back into DOM");
        assertTrue(rnMessages.size() >= 1 && rnMessages.get(rnMessages.size() - 1).contains("Updated Joplin note body"),
                "ReactNativeWebView.postMessage fired from contentEditable input");
        assertTrue(Double.parseDouble(evalSync(view, "docInputBubbled")) >= 1.0, "input event bubbled to document");
        assertTrue(Double.parseDouble(evalSync(view, "selChanges")) >= 1.0, "selectionchange fired");
        assertTrue(Double.parseDouble(evalSync(view, "observed")) >= 1.0, "MutationObserver notified");

        // Selection, Range, execCommand, DOM tree mutations & CustomEvent
        assertEquals("true", evalSync(view, "document.execCommand('selectAll')"), "execCommand selectAll");
        assertEquals("\"Updated Joplin note body\"", evalSync(view, "window.getSelection().toString()"),
                "getSelection returns selected text");
        assertEquals("true", evalSync(view, "document.execCommand('insertText', false, 'Hello Markdown')"),
                "execCommand insertText");
        assertEquals("Hello Markdown", editorInput.getText().toString(), "execCommand synced to EditText");
        assertEquals("true", evalSync(view, "document.execCommand('selectAll') && document.execCommand('bold')"),
                "execCommand bold");
        assertEquals("\"<strong>Hello Markdown</strong>\"", evalSync(view, "ed.innerHTML"),
                "bold wrapped selection");
        assertEquals("\"<span>A</span><span>B</span>\"",
                evalSync(view, "var box = document.createElement('div');"
                        + "var b = document.createElement('span'); b.textContent = 'B';"
                        + "var a = document.createElement('span'); a.textContent = 'A';"
                        + "box.appendChild(b); box.insertBefore(a, b); box.innerHTML"),
                "insertBefore order");
        assertEquals("\"custom-ok\"",
                evalSync(view, "var got = '';"
                        + "ed.addEventListener('ping', (e) => { got = e.detail; e.preventDefault(); });"
                        + "var ce = new CustomEvent('ping', { detail: 'custom-ok', cancelable: true, bubbles: true });"
                        + "ed.dispatchEvent(ce); got"),
                "CustomEvent dispatch");

        // setTimeout & clearTimeout scheduling
        evalSync(view, "var timerFired = 0; var t1 = setTimeout(() => { timerFired += 1; }, 0);"
                + "var t2 = setTimeout(() => { timerFired += 100; }, 0); clearTimeout(t2);");
        new android.os.Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                Looper.shirazRequestMainExit();
            }
        }, 5);
        Looper.loop();
        assertEquals("1", evalSync(view, "timerFired"), "setTimeout fired and clearTimeout cancelled");

        view.destroy();
        java.nio.file.Files.deleteIfExists(appDir);
        System.out.println("WEBVIEW_DOM_JS_SMOKE_OK");
    }
}
