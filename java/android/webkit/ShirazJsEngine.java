// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java embedded JavaScript interpreter, live DOM binding layer, and
 * {@link JavascriptInterface} reflection bridge for Shiraz {@link WebView}.
 */
final class ShirazJsEngine {
    static final int MAX_STEPS = 200000;

    interface HostCallbacks {
        String getUrl();
        void onDomMutated();
        void onTitleChanged(String newTitle);
        void onConsole(ConsoleMessage.MessageLevel level, String message);
        void onAlert(String message);
        boolean onConfirm(String message);
        String onPrompt(String message, String defaultValue);
        void onWindowPostMessage(String data, String targetOrigin);
    }

    static final class Undefined {
        static final Undefined INSTANCE = new Undefined();
        private Undefined() {}
        @Override public String toString() { return "undefined"; }
    }

    static final class JsRuntimeError extends RuntimeException {
        final Object jsValue;
        JsRuntimeError(String msg) {
            super(msg);
            this.jsValue = msg;
        }
        JsRuntimeError(Object value) {
            super(toJsString(value));
            this.jsValue = value;
        }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object value) { this.value = value; }
    }

    private static final class BreakSignal extends RuntimeException {}
    private static final class ContinueSignal extends RuntimeException {}

    interface JsCallable {
        Object call(Object thisArg, List<Object> args);
    }

    static class JsObject {
        final LinkedHashMap<String, Object> props = new LinkedHashMap<String, Object>();

        Object getProp(String name) {
            return props.containsKey(name) ? props.get(name) : Undefined.INSTANCE;
        }

        void setProp(String name, Object value) {
            props.put(name, value);
        }

        boolean hasProp(String name) {
            return props.containsKey(name);
        }

        boolean deleteProp(String name) {
            props.remove(name);
            return true;
        }

        List<String> ownKeys() {
            return new ArrayList<String>(props.keySet());
        }
    }

    static final class JsArray extends JsObject {
        final List<Object> items = new ArrayList<Object>();

        JsArray() {}

        JsArray(List<Object> initial) {
            items.addAll(initial);
        }

        @Override Object getProp(String name) {
            if ("length".equals(name)) return Double.valueOf(items.size());
            int idx = parseArrayIndex(name);
            if (idx >= 0) {
                return idx < items.size() ? items.get(idx) : Undefined.INSTANCE;
            }
            return super.getProp(name);
        }

        @Override void setProp(String name, Object value) {
            if ("length".equals(name)) {
                int newLen = Math.max(0, (int) toJsNumber(value));
                while (items.size() > newLen) items.remove(items.size() - 1);
                while (items.size() < newLen) items.add(Undefined.INSTANCE);
                return;
            }
            int idx = parseArrayIndex(name);
            if (idx >= 0) {
                while (items.size() <= idx) items.add(Undefined.INSTANCE);
                items.set(idx, value);
                return;
            }
            super.setProp(name, value);
        }

        @Override List<String> ownKeys() {
            List<String> keys = new ArrayList<String>(items.size());
            for (int i = 0; i < items.size(); i++) keys.add(Integer.toString(i));
            return keys;
        }
    }

    private static int parseArrayIndex(String name) {
        if (name == null || name.isEmpty()) return -1;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < '0' || c > '9') return -1;
        }
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static final class Env {
        final Env parent;
        final Map<String, Object> bindings = new HashMap<String, Object>();

        Env(Env parent) {
            this.parent = parent;
        }

        void define(String name, Object value) {
            bindings.put(name, value);
        }

        boolean has(String name) {
            if (bindings.containsKey(name)) return true;
            return parent != null && parent.has(name);
        }

        Object get(String name) {
            if (bindings.containsKey(name)) return bindings.get(name);
            if (parent != null) return parent.get(name);
            return Undefined.INSTANCE;
        }

        void set(String name, Object value) {
            if (bindings.containsKey(name)) {
                bindings.put(name, value);
                return;
            }
            if (parent != null && parent.has(name)) {
                parent.set(name, value);
                return;
            }
            // Global assignment fallback (non-strict JS)
            Env root = this;
            while (root.parent != null) root = root.parent;
            root.bindings.put(name, value);
        }
    }

    private final HostCallbacks host;
    private ShirazWebDom dom;
    private final Map<String, Object> javaInterfaces = new LinkedHashMap<String, Object>();
    private final IdentityHashMap<ShirazWebDom.DomNode, DomElementProxy> nodeProxies =
            new IdentityHashMap<ShirazWebDom.DomNode, DomElementProxy>();
    private final IdentityHashMap<ShirazWebDom.DomNode, Map<String, List<JsCallable>>> domListeners =
            new IdentityHashMap<ShirazWebDom.DomNode, Map<String, List<JsCallable>>>();
    private final Map<String, List<JsCallable>> windowListeners = new HashMap<String, List<JsCallable>>();
    private JsObject windowObj;
    private JsObject documentObj;
    private Env globalEnv;
    private int steps;

    ShirazJsEngine(HostCallbacks host, ShirazWebDom initialDom) {
        this.host = host;
        resetForDocument(initialDom);
    }

    void addJavascriptInterface(Object target, String name) {
        if (target == null || name == null || name.isEmpty()) return;
        javaInterfaces.put(name, target);
        if (globalEnv != null && windowObj != null) {
            JavaBridgeObject bridge = new JavaBridgeObject(target);
            globalEnv.define(name, bridge);
            windowObj.setProp(name, bridge);
        }
    }

    void removeJavascriptInterface(String name) {
        if (name == null) return;
        javaInterfaces.remove(name);
        if (globalEnv != null) globalEnv.bindings.remove(name);
        if (windowObj != null) windowObj.deleteProp(name);
    }

    void resetForDocument(ShirazWebDom newDom) {
        this.dom = newDom != null ? newDom : ShirazWebDom.parse("");
        this.nodeProxies.clear();
        this.domListeners.clear();
        this.windowListeners.clear();
        this.globalEnv = new Env(null);
        this.windowObj = new JsObject();
        this.documentObj = buildDocumentObject();

        installBuiltins();
        for (Map.Entry<String, Object> entry : javaInterfaces.entrySet()) {
            JavaBridgeObject bridge = new JavaBridgeObject(entry.getValue());
            globalEnv.define(entry.getKey(), bridge);
            windowObj.setProp(entry.getKey(), bridge);
        }
    }

    Object evaluate(String source) {
        if (source == null || source.trim().isEmpty()) return Undefined.INSTANCE;
        steps = 0;
        List<Token> tokens = new Lexer(source).tokenize();
        List<Stmt> program = new Parser(tokens).parseProgram();
        Object last = Undefined.INSTANCE;
        for (Stmt stmt : program) {
            last = execStmt(stmt, globalEnv, windowObj);
        }
        return last;
    }

    String evaluateToJson(String source) {
        Object result = evaluate(source);
        return toJsonResult(result);
    }

    void dispatchWindowMessage(String data, String origin) {
        JsObject event = new JsObject();
        event.setProp("type", "message");
        event.setProp("data", data == null ? "" : data);
        event.setProp("origin", origin == null ? "" : origin);
        List<Object> args = Collections.<Object>singletonList(event);

        Object onMsg = windowObj.getProp("onmessage");
        if (onMsg instanceof JsCallable) {
            steps = 0;
            ((JsCallable) onMsg).call(windowObj, args);
        }
        List<JsCallable> listeners = windowListeners.get("message");
        if (listeners != null) {
            List<JsCallable> copy = new ArrayList<JsCallable>(listeners);
            for (JsCallable fn : copy) {
                steps = 0;
                fn.call(windowObj, args);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Builtins, Window, Document, DOM Proxy & @JavascriptInterface Bridge
    // -------------------------------------------------------------------------

    private void defineGlobal(String name, Object val) {
        globalEnv.define(name, val);
        windowObj.setProp(name, val);
    }

    private void installBuiltins() {
        defineGlobal("window", windowObj);
        defineGlobal("self", windowObj);
        defineGlobal("globalThis", windowObj);
        defineGlobal("document", documentObj);
        defineGlobal("undefined", Undefined.INSTANCE);
        defineGlobal("NaN", Double.valueOf(Double.NaN));
        defineGlobal("Infinity", Double.valueOf(Double.POSITIVE_INFINITY));

        JsObject location = new JsObject() {
            @Override Object getProp(String name) {
                if ("href".equals(name)) {
                    String u = host.getUrl();
                    return u == null ? "" : u;
                }
                return super.getProp(name);
            }
        };
        defineGlobal("location", location);
        documentObj.setProp("location", location);

        windowObj.setProp("addEventListener", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.size() >= 2 && args.get(1) instanceof JsCallable) {
                    String type = toJsString(args.get(0));
                    List<JsCallable> list = windowListeners.get(type);
                    if (list == null) {
                        list = new ArrayList<JsCallable>();
                        windowListeners.put(type, list);
                    }
                    list.add((JsCallable) args.get(1));
                }
                return Undefined.INSTANCE;
            }
        });
        windowObj.setProp("removeEventListener", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.size() >= 2 && args.get(1) instanceof JsCallable) {
                    String type = toJsString(args.get(0));
                    List<JsCallable> list = windowListeners.get(type);
                    if (list != null) list.remove(args.get(1));
                }
                return Undefined.INSTANCE;
            }
        });
        windowObj.setProp("postMessage", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                String data = args.size() >= 1 ? toJsString(args.get(0)) : "";
                String targetOrigin = args.size() >= 2 ? toJsString(args.get(1)) : "*";
                host.onWindowPostMessage(data, targetOrigin);
                return Undefined.INSTANCE;
            }
        });
        defineGlobal("addEventListener", windowObj.getProp("addEventListener"));
        defineGlobal("removeEventListener", windowObj.getProp("removeEventListener"));
        defineGlobal("postMessage", windowObj.getProp("postMessage"));

        // console
        JsObject console = new JsObject();
        console.setProp("log", makeConsoleFn(ConsoleMessage.MessageLevel.LOG));
        console.setProp("info", makeConsoleFn(ConsoleMessage.MessageLevel.LOG));
        console.setProp("warn", makeConsoleFn(ConsoleMessage.MessageLevel.WARNING));
        console.setProp("error", makeConsoleFn(ConsoleMessage.MessageLevel.ERROR));
        console.setProp("debug", makeConsoleFn(ConsoleMessage.MessageLevel.DEBUG));
        defineGlobal("console", console);

        // Dialogs
        defineGlobal("alert", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                host.onAlert(args.isEmpty() ? "" : toJsString(args.get(0)));
                return Undefined.INSTANCE;
            }
        });
        defineGlobal("confirm", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                return Boolean.valueOf(host.onConfirm(args.isEmpty() ? "" : toJsString(args.get(0))));
            }
        });
        defineGlobal("prompt", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                String msg = args.size() >= 1 ? toJsString(args.get(0)) : "";
                String def = args.size() >= 2 ? toJsString(args.get(1)) : "";
                return host.onPrompt(msg, def);
            }
        });

        // JSON
        JsObject json = new JsObject();
        json.setProp("stringify", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty() || args.get(0) == Undefined.INSTANCE) return Undefined.INSTANCE;
                return toJsonResult(args.get(0));
            }
        });
        json.setProp("parse", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty()) throw new JsRuntimeError("JSON.parse requires a string");
                return new JsonParser(toJsString(args.get(0))).parseValue();
            }
        });
        defineGlobal("JSON", json);

        // Math
        JsObject math = new JsObject();
        math.setProp("PI", Double.valueOf(Math.PI));
        math.setProp("E", Double.valueOf(Math.E));
        math.setProp("abs", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Double.valueOf(Math.abs(a.isEmpty() ? Double.NaN : toJsNumber(a.get(0))));
            }
        });
        math.setProp("floor", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Double.valueOf(Math.floor(a.isEmpty() ? Double.NaN : toJsNumber(a.get(0))));
            }
        });
        math.setProp("ceil", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Double.valueOf(Math.ceil(a.isEmpty() ? Double.NaN : toJsNumber(a.get(0))));
            }
        });
        math.setProp("round", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                double d = a.isEmpty() ? Double.NaN : toJsNumber(a.get(0));
                return Double.isNaN(d) ? Double.valueOf(Double.NaN) : Double.valueOf((double) Math.round(d));
            }
        });
        math.setProp("sqrt", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Double.valueOf(Math.sqrt(a.isEmpty() ? Double.NaN : toJsNumber(a.get(0))));
            }
        });
        math.setProp("pow", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                double x = a.size() >= 1 ? toJsNumber(a.get(0)) : Double.NaN;
                double y = a.size() >= 2 ? toJsNumber(a.get(1)) : Double.NaN;
                return Double.valueOf(Math.pow(x, y));
            }
        });
        math.setProp("min", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                if (a.isEmpty()) return Double.valueOf(Double.POSITIVE_INFINITY);
                double m = Double.POSITIVE_INFINITY;
                for (Object v : a) {
                    double d = toJsNumber(v);
                    if (Double.isNaN(d)) return Double.valueOf(Double.NaN);
                    if (d < m) m = d;
                }
                return Double.valueOf(m);
            }
        });
        math.setProp("max", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                if (a.isEmpty()) return Double.valueOf(Double.NEGATIVE_INFINITY);
                double m = Double.NEGATIVE_INFINITY;
                for (Object v : a) {
                    double d = toJsNumber(v);
                    if (Double.isNaN(d)) return Double.valueOf(Double.NaN);
                    if (d > m) m = d;
                }
                return Double.valueOf(m);
            }
        });
        math.setProp("trunc", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                double d = a.isEmpty() ? Double.NaN : toJsNumber(a.get(0));
                if (Double.isNaN(d) || Double.isInfinite(d)) return Double.valueOf(d);
                return Double.valueOf(d < 0 ? Math.ceil(d) : Math.floor(d));
            }
        });
        defineGlobal("Math", math);

        // Conversion & utility functions
        defineGlobal("parseInt", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                if (a.isEmpty()) return Double.valueOf(Double.NaN);
                String s = toJsString(a.get(0)).trim();
                int radix = a.size() >= 2 ? (int) toJsNumber(a.get(1)) : 0;
                if (s.isEmpty()) return Double.valueOf(Double.NaN);
                int sign = 1;
                int pos = 0;
                if (s.charAt(0) == '-') { sign = -1; pos = 1; }
                else if (s.charAt(0) == '+') { pos = 1; }
                if (radix == 0) {
                    if (s.regionMatches(true, pos, "0x", 0, 2)) {
                        radix = 16;
                        pos += 2;
                    } else {
                        radix = 10;
                    }
                } else if (radix == 16 && s.regionMatches(true, pos, "0x", 0, 2)) {
                    pos += 2;
                }
                if (radix < 2 || radix > 36) return Double.valueOf(Double.NaN);
                long acc = 0;
                int digits = 0;
                while (pos < s.length()) {
                    int d = Character.digit(s.charAt(pos), radix);
                    if (d < 0) break;
                    acc = acc * radix + d;
                    digits++;
                    pos++;
                }
                return digits == 0 ? Double.valueOf(Double.NaN) : Double.valueOf((double) (sign * acc));
            }
        });
        defineGlobal("parseFloat", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                if (a.isEmpty()) return Double.valueOf(Double.NaN);
                String s = toJsString(a.get(0)).trim();
                int end = 0;
                while (end < s.length()) {
                    char c = s.charAt(end);
                    if ((c >= '0' && c <= '9') || c == '.' || c == '+' || c == '-' || c == 'e' || c == 'E') {
                        end++;
                    } else {
                        break;
                    }
                }
                while (end > 0) {
                    try {
                        return Double.valueOf(Double.parseDouble(s.substring(0, end)));
                    } catch (NumberFormatException e) {
                        end--;
                    }
                }
                return Double.valueOf(Double.NaN);
            }
        });
        defineGlobal("Number", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Double.valueOf(a.isEmpty() ? 0.0 : toJsNumber(a.get(0)));
            }
        });
        defineGlobal("String", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return a.isEmpty() ? "" : toJsString(a.get(0));
            }
        });
        defineGlobal("Boolean", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Boolean.valueOf(!a.isEmpty() && toJsBoolean(a.get(0)));
            }
        });
        defineGlobal("isNaN", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Boolean.valueOf(a.isEmpty() || Double.isNaN(toJsNumber(a.get(0))));
            }
        });
        defineGlobal("isFinite", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                if (a.isEmpty()) return Boolean.FALSE;
                double d = toJsNumber(a.get(0));
                return Boolean.valueOf(!Double.isNaN(d) && !Double.isInfinite(d));
            }
        });
        defineGlobal("encodeURIComponent", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                String s = a.isEmpty() ? "undefined" : toJsString(a.get(0));
                try {
                    return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20");
                } catch (Exception e) {
                    return s;
                }
            }
        });
        defineGlobal("decodeURIComponent", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                String s = a.isEmpty() ? "undefined" : toJsString(a.get(0));
                try {
                    return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
                } catch (Exception e) {
                    return s;
                }
            }
        });

        JsObject arrayCtor = new JsObject();
        arrayCtor.setProp("isArray", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                return Boolean.valueOf(!a.isEmpty() && a.get(0) instanceof JsArray);
            }
        });
        defineGlobal("Array", arrayCtor);

        JsObject objectCtor = new JsObject();
        objectCtor.setProp("keys", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                JsArray out = new JsArray();
                if (!a.isEmpty() && a.get(0) instanceof JsObject) {
                    for (String k : ((JsObject) a.get(0)).ownKeys()) out.items.add(k);
                }
                return out;
            }
        });
        objectCtor.setProp("values", new JsCallable() {
            @Override public Object call(Object t, List<Object> a) {
                JsArray out = new JsArray();
                if (!a.isEmpty() && a.get(0) instanceof JsObject) {
                    JsObject obj = (JsObject) a.get(0);
                    for (String k : obj.ownKeys()) out.items.add(obj.getProp(k));
                }
                return out;
            }
        });
        defineGlobal("Object", objectCtor);
    }

    private JsCallable makeConsoleFn(final ConsoleMessage.MessageLevel level) {
        return new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(toJsString(args.get(i)));
                }
                host.onConsole(level, sb.toString());
                return Undefined.INSTANCE;
            }
        };
    }

    private DomElementProxy wrapNode(ShirazWebDom.DomNode node) {
        if (node == null) return null;
        DomElementProxy existing = nodeProxies.get(node);
        if (existing != null) return existing;
        DomElementProxy proxy = new DomElementProxy(node);
        nodeProxies.put(node, proxy);
        return proxy;
    }

    private JsArray wrapNodeList(List<ShirazWebDom.DomNode> nodes) {
        JsArray arr = new JsArray();
        for (ShirazWebDom.DomNode n : nodes) {
            arr.items.add(wrapNode(n));
        }
        return arr;
    }

    private JsObject buildDocumentObject() {
        JsObject doc = new JsObject() {
            @Override Object getProp(String name) {
                if ("title".equals(name)) {
                    String t = dom.getTitle();
                    return t == null ? "" : t;
                }
                if ("URL".equals(name)) {
                    String u = host.getUrl();
                    return u == null ? "" : u;
                }
                if ("body".equals(name)) return wrapNode(dom.getBody());
                if ("head".equals(name)) return wrapNode(dom.getHead());
                if ("documentElement".equals(name)) return wrapNode(dom.getDocumentElement());
                if ("readyState".equals(name)) return "complete";
                return super.getProp(name);
            }

            @Override void setProp(String name, Object value) {
                if ("title".equals(name)) {
                    String newTitle = toJsString(value);
                    dom.setTitle(newTitle);
                    host.onTitleChanged(newTitle);
                    return;
                }
                super.setProp(name, value);
            }
        };

        doc.setProp("getElementById", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty()) return null;
                return wrapNode(dom.getElementById(toJsString(args.get(0))));
            }
        });
        doc.setProp("querySelector", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty()) return null;
                return wrapNode(dom.querySelector(dom.document, toJsString(args.get(0))));
            }
        });
        doc.setProp("querySelectorAll", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty()) return new JsArray();
                return wrapNodeList(dom.querySelectorAll(dom.document, toJsString(args.get(0))));
            }
        });
        doc.setProp("getElementsByTagName", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty()) return new JsArray();
                return wrapNodeList(dom.getElementsByTagName(dom.document, toJsString(args.get(0))));
            }
        });
        doc.setProp("getElementsByClassName", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                if (args.isEmpty()) return new JsArray();
                return wrapNodeList(dom.getElementsByClassName(dom.document, toJsString(args.get(0))));
            }
        });
        doc.setProp("createElement", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                String tag = args.isEmpty() ? "div" : toJsString(args.get(0));
                return wrapNode(dom.createElement(tag));
            }
        });
        doc.setProp("createTextNode", new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                String txt = args.isEmpty() ? "" : toJsString(args.get(0));
                return wrapNode(dom.createTextNode(txt));
            }
        });
        return doc;
    }

    private final class DomElementProxy extends JsObject {
        final ShirazWebDom.DomNode node;

        DomElementProxy(ShirazWebDom.DomNode node) {
            this.node = node;
            initMethods();
        }

        private void initMethods() {
            props.put("getAttribute", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return a.isEmpty() ? null : node.getAttribute(toJsString(a.get(0)));
                }
            });
            props.put("setAttribute", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.size() >= 2) {
                        node.setAttribute(toJsString(a.get(0)), toJsString(a.get(1)));
                        host.onDomMutated();
                    }
                    return Undefined.INSTANCE;
                }
            });
            props.put("removeAttribute", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (!a.isEmpty()) {
                        node.removeAttribute(toJsString(a.get(0)));
                        host.onDomMutated();
                    }
                    return Undefined.INSTANCE;
                }
            });
            props.put("hasAttribute", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return Boolean.valueOf(!a.isEmpty() && node.hasAttribute(toJsString(a.get(0))));
                }
            });
            props.put("appendChild", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (!a.isEmpty() && a.get(0) instanceof DomElementProxy) {
                        DomElementProxy child = (DomElementProxy) a.get(0);
                        node.appendChild(child.node);
                        host.onDomMutated();
                        return child;
                    }
                    return null;
                }
            });
            props.put("removeChild", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (!a.isEmpty() && a.get(0) instanceof DomElementProxy) {
                        DomElementProxy child = (DomElementProxy) a.get(0);
                        if (node.removeChild(child.node)) {
                            host.onDomMutated();
                            return child;
                        }
                    }
                    return null;
                }
            });
            props.put("querySelector", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.isEmpty()) return null;
                    return wrapNode(dom.querySelector(node, toJsString(a.get(0))));
                }
            });
            props.put("querySelectorAll", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.isEmpty()) return new JsArray();
                    return wrapNodeList(dom.querySelectorAll(node, toJsString(a.get(0))));
                }
            });
            props.put("getElementsByTagName", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.isEmpty()) return new JsArray();
                    return wrapNodeList(dom.getElementsByTagName(node, toJsString(a.get(0))));
                }
            });
            props.put("getElementsByClassName", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.isEmpty()) return new JsArray();
                    return wrapNodeList(dom.getElementsByClassName(node, toJsString(a.get(0))));
                }
            });
            props.put("addEventListener", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.size() >= 2 && a.get(1) instanceof JsCallable) {
                        String type = toJsString(a.get(0));
                        Map<String, List<JsCallable>> byType = domListeners.get(node);
                        if (byType == null) {
                            byType = new HashMap<String, List<JsCallable>>();
                            domListeners.put(node, byType);
                        }
                        List<JsCallable> list = byType.get(type);
                        if (list == null) {
                            list = new ArrayList<JsCallable>();
                            byType.put(type, list);
                        }
                        list.add((JsCallable) a.get(1));
                    }
                    return Undefined.INSTANCE;
                }
            });
            props.put("removeEventListener", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.size() >= 2 && a.get(1) instanceof JsCallable) {
                        Map<String, List<JsCallable>> byType = domListeners.get(node);
                        if (byType != null) {
                            List<JsCallable> list = byType.get(toJsString(a.get(0)));
                            if (list != null) list.remove(a.get(1));
                        }
                    }
                    return Undefined.INSTANCE;
                }
            });
            props.put("dispatchEvent", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.isEmpty()) return Boolean.FALSE;
                    Object evt = a.get(0);
                    String type = evt instanceof JsObject ? toJsString(((JsObject) evt).getProp("type")) : toJsString(evt);
                    return Boolean.valueOf(fireEvent(type, evt instanceof JsObject ? (JsObject) evt : null));
                }
            });
            props.put("click", new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    JsObject evt = new JsObject();
                    evt.setProp("type", "click");
                    evt.setProp("target", DomElementProxy.this);
                    fireEvent("click", evt);
                    return Undefined.INSTANCE;
                }
            });
        }

        private boolean fireEvent(String type, JsObject evt) {
            if (evt == null) {
                evt = new JsObject();
                evt.setProp("type", type);
                evt.setProp("target", this);
            }
            List<Object> args = Collections.<Object>singletonList(evt);
            Object inlineHandler = props.get("on" + type);
            if (inlineHandler instanceof JsCallable) {
                ((JsCallable) inlineHandler).call(this, args);
            } else {
                String attrHandler = node.getAttribute("on" + type);
                if (attrHandler != null && !attrHandler.trim().isEmpty()) {
                    evaluate(attrHandler);
                }
            }
            Map<String, List<JsCallable>> byType = domListeners.get(node);
            if (byType != null) {
                List<JsCallable> list = byType.get(type);
                if (list != null) {
                    for (JsCallable fn : new ArrayList<JsCallable>(list)) {
                        fn.call(this, args);
                    }
                }
            }
            return true;
        }

        @Override Object getProp(String name) {
            if ("tagName".equals(name) || "nodeName".equals(name)) {
                return node.nodeType == ShirazWebDom.NODE_ELEMENT
                        ? node.tagName.toUpperCase(Locale.ROOT)
                        : node.tagName;
            }
            if ("nodeType".equals(name)) return Double.valueOf(node.nodeType);
            if ("id".equals(name)) return node.getId();
            if ("className".equals(name)) return node.getClassName();
            if ("textContent".equals(name) || "innerText".equals(name)) return node.getTextContent();
            if ("innerHTML".equals(name)) return node.getInnerHtml();
            if ("value".equals(name)) {
                String v = node.getAttribute("value");
                return v == null ? "" : v;
            }
            if ("href".equals(name)) {
                String v = node.getAttribute("href");
                return v == null ? "" : v;
            }
            if ("src".equals(name)) {
                String v = node.getAttribute("src");
                return v == null ? "" : v;
            }
            if ("children".equals(name)) return wrapNodeList(node.getChildElements());
            if ("childNodes".equals(name)) return wrapNodeList(node.children);
            if ("parentNode".equals(name)) return wrapNode(node.parent);
            if ("firstChild".equals(name)) {
                return node.children.isEmpty() ? null : wrapNode(node.children.get(0));
            }
            if ("lastChild".equals(name)) {
                return node.children.isEmpty() ? null : wrapNode(node.children.get(node.children.size() - 1));
            }
            return super.getProp(name);
        }

        @Override void setProp(String name, Object value) {
            if ("id".equals(name)) {
                node.setAttribute("id", toJsString(value));
                host.onDomMutated();
                return;
            }
            if ("className".equals(name)) {
                node.setAttribute("class", toJsString(value));
                host.onDomMutated();
                return;
            }
            if ("textContent".equals(name) || "innerText".equals(name)) {
                node.setTextContent(toJsString(value));
                host.onDomMutated();
                return;
            }
            if ("innerHTML".equals(name)) {
                node.setInnerHtml(toJsString(value));
                host.onDomMutated();
                return;
            }
            if ("value".equals(name)) {
                node.setAttribute("value", toJsString(value));
                host.onDomMutated();
                return;
            }
            if ("href".equals(name)) {
                node.setAttribute("href", toJsString(value));
                host.onDomMutated();
                return;
            }
            if ("src".equals(name)) {
                node.setAttribute("src", toJsString(value));
                host.onDomMutated();
                return;
            }
            super.setProp(name, value);
        }
    }

    /**
     * Exposes ONLY public methods annotated with {@link JavascriptInterface}
     * on the bound Java object (matching Android API >= 17 security rules).
     */
    private static final class JavaBridgeObject extends JsObject {
        private final Object target;
        private final Map<String, List<Method>> exposed = new HashMap<String, List<Method>>();

        JavaBridgeObject(Object target) {
            this.target = target;
            for (Method m : target.getClass().getMethods()) {
                if (!Modifier.isPublic(m.getModifiers())) continue;
                if (!m.isAnnotationPresent(JavascriptInterface.class)) continue;
                List<Method> overloads = exposed.get(m.getName());
                if (overloads == null) {
                    overloads = new ArrayList<Method>();
                    exposed.put(m.getName(), overloads);
                }
                overloads.add(m);
            }
        }

        @Override Object getProp(final String name) {
            final List<Method> methods = exposed.get(name);
            if (methods == null) return Undefined.INSTANCE;
            return new JsCallable() {
                @Override public Object call(Object thisArg, List<Object> args) {
                    return invokeBridgeMethod(target, name, methods, args);
                }
            };
        }

        @Override boolean hasProp(String name) {
            return exposed.containsKey(name);
        }

        @Override List<String> ownKeys() {
            return new ArrayList<String>(exposed.keySet());
        }
    }

    private static Object invokeBridgeMethod(
            Object target, String name, List<Method> candidates, List<Object> args) {
        Method chosen = null;
        for (Method m : candidates) {
            if (m.getParameterTypes().length == args.size()) {
                chosen = m;
                break;
            }
        }
        if (chosen == null) {
            throw new JsRuntimeError("No matching @JavascriptInterface overload for " + name + "(" + args.size() + ")");
        }
        Class<?>[] types = chosen.getParameterTypes();
        Object[] converted = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            converted[i] = coerceToJava(args.get(i), types[i]);
        }
        try {
            chosen.setAccessible(true);
            Object ret = chosen.invoke(target, converted);
            if (chosen.getReturnType() == void.class) return Undefined.INSTANCE;
            return coerceFromJava(ret);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new JsRuntimeError(cause != null ? cause.getMessage() : e.getMessage());
        } catch (Exception e) {
            throw new JsRuntimeError("Bridge call failed: " + e.getMessage());
        }
    }

    private static Object coerceToJava(Object val, Class<?> type) {
        if (type == String.class) {
            return (val == null || val == Undefined.INSTANCE) ? null : toJsString(val);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(toJsBoolean(val));
        }
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf((int) toJsNumber(val));
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf((long) toJsNumber(val));
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(toJsNumber(val));
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf((float) toJsNumber(val));
        }
        if (type == short.class || type == Short.class) {
            return Short.valueOf((short) toJsNumber(val));
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.valueOf((byte) toJsNumber(val));
        }
        return val == Undefined.INSTANCE ? null : val;
    }

    private static Object coerceFromJava(Object val) {
        if (val == null) return null;
        if (val instanceof String || val instanceof Boolean || val instanceof Double) return val;
        if (val instanceof Number) return Double.valueOf(((Number) val).doubleValue());
        return String.valueOf(val);
    }

    // -------------------------------------------------------------------------
    // Conversions & JSON Serialization
    // -------------------------------------------------------------------------

    static boolean toJsBoolean(Object v) {
        if (v == null || v == Undefined.INSTANCE) return false;
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
        if (v instanceof Double) {
            double d = ((Double) v).doubleValue();
            return d != 0.0 && !Double.isNaN(d);
        }
        if (v instanceof String) return !((String) v).isEmpty();
        return true;
    }

    static double toJsNumber(Object v) {
        if (v == null) return 0.0;
        if (v == Undefined.INSTANCE) return Double.NaN;
        if (v instanceof Double) return ((Double) v).doubleValue();
        if (v instanceof Boolean) return ((Boolean) v).booleanValue() ? 1.0 : 0.0;
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return 0.0;
            if (s.startsWith("0x") || s.startsWith("0X")) {
                try {
                    return (double) Long.parseLong(s.substring(2), 16);
                } catch (NumberFormatException e) {
                    return Double.NaN;
                }
            }
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    static String toJsString(Object v) {
        if (v == null) return "null";
        if (v == Undefined.INSTANCE) return "undefined";
        if (v instanceof String) return (String) v;
        if (v instanceof Boolean) return ((Boolean) v).booleanValue() ? "true" : "false";
        if (v instanceof Double) {
            double d = ((Double) v).doubleValue();
            if (Double.isNaN(d)) return "NaN";
            if (Double.isInfinite(d)) return d > 0 ? "Infinity" : "-Infinity";
            if (d == 0.0) return "0";
            long asLong = (long) d;
            if ((double) asLong == d) return Long.toString(asLong);
            return Double.toString(d);
        }
        if (v instanceof JsArray) {
            JsArray arr = (JsArray) v;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.items.size(); i++) {
                if (i > 0) sb.append(',');
                Object item = arr.items.get(i);
                if (item != null && item != Undefined.INSTANCE) sb.append(toJsString(item));
            }
            return sb.toString();
        }
        if (v instanceof JsCallable) return "[Function]";
        return "[object Object]";
    }

    static String toJsonResult(Object v) {
        StringBuilder sb = new StringBuilder();
        writeJson(v, sb, 0);
        return sb.toString();
    }

    private static void writeJson(Object v, StringBuilder sb, int depth) {
        if (depth > 32 || v == null || v == Undefined.INSTANCE) {
            sb.append("null");
            return;
        }
        if (v instanceof Boolean) {
            sb.append(((Boolean) v).booleanValue() ? "true" : "false");
            return;
        }
        if (v instanceof Double) {
            double d = ((Double) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(toJsString(v));
            }
            return;
        }
        if (v instanceof String) {
            writeJsonString((String) v, sb);
            return;
        }
        if (v instanceof JsArray) {
            JsArray arr = (JsArray) v;
            sb.append('[');
            for (int i = 0; i < arr.items.size(); i++) {
                if (i > 0) sb.append(',');
                writeJson(arr.items.get(i), sb, depth + 1);
            }
            sb.append(']');
            return;
        }
        if (v instanceof JsObject) {
            JsObject obj = (JsObject) v;
            sb.append('{');
            boolean first = true;
            for (String key : obj.ownKeys()) {
                Object prop = obj.getProp(key);
                if (prop == Undefined.INSTANCE || prop instanceof JsCallable) continue;
                if (!first) sb.append(',');
                first = false;
                writeJsonString(key, sb);
                sb.append(':');
                writeJson(prop, sb, depth + 1);
            }
            sb.append('}');
            return;
        }
        sb.append("null");
    }

    private static void writeJsonString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", Integer.valueOf(c)));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // -------------------------------------------------------------------------
    // Minimal JSON Parser (for JSON.parse)
    // -------------------------------------------------------------------------

    private static final class JsonParser {
        private final String s;
        private int pos;

        JsonParser(String s) { this.s = s == null ? "" : s; }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) throw new JsRuntimeError("Unexpected end of JSON");
            char c = s.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' && s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (c == 'f' && s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (c == 'n' && s.startsWith("null", pos)) { pos += 4; return null; }
            return parseNumber();
        }

        private JsObject parseObject() {
            pos++; // '{'
            JsObject obj = new JsObject();
            skipWs();
            if (pos < s.length() && s.charAt(pos) == '}') { pos++; return obj; }
            while (pos < s.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                if (pos >= s.length() || s.charAt(pos) != ':') throw new JsRuntimeError("Expected ':' in JSON");
                pos++;
                Object val = parseValue();
                obj.setProp(key, val);
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                if (pos < s.length() && s.charAt(pos) == '}') { pos++; return obj; }
                throw new JsRuntimeError("Expected ',' or '}' in JSON");
            }
            throw new JsRuntimeError("Unterminated JSON object");
        }

        private JsArray parseArray() {
            pos++; // '['
            JsArray arr = new JsArray();
            skipWs();
            if (pos < s.length() && s.charAt(pos) == ']') { pos++; return arr; }
            while (pos < s.length()) {
                arr.items.add(parseValue());
                skipWs();
                if (pos < s.length() && s.charAt(pos) == ',') { pos++; continue; }
                if (pos < s.length() && s.charAt(pos) == ']') { pos++; return arr; }
                throw new JsRuntimeError("Expected ',' or ']' in JSON");
            }
            throw new JsRuntimeError("Unterminated JSON array");
        }

        private String parseString() {
            if (pos >= s.length() || s.charAt(pos) != '"') throw new JsRuntimeError("Expected JSON string");
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\' && pos < s.length()) {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 <= s.length()) {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                                pos += 4;
                            }
                            break;
                        default: sb.append(esc); break;
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsRuntimeError("Unterminated JSON string");
        }

        private Double parseNumber() {
            int start = pos;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            try {
                return Double.valueOf(Double.parseDouble(s.substring(start, pos)));
            } catch (NumberFormatException e) {
                throw new JsRuntimeError("Invalid JSON number");
            }
        }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }

    // -------------------------------------------------------------------------
    // Lexer
    // -------------------------------------------------------------------------

    private enum TokType {
        EOF, NUMBER, STRING, IDENT,
        LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
        DOT, COMMA, SEMI, COLON, QUESTION, ARROW,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        PLUS_PLUS, MINUS_MINUS,
        ASSIGN, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, PERCENT_EQ,
        EQ, NEQ, STRICT_EQ, STRICT_NEQ,
        LT, LTE, GT, GTE,
        AND, OR, NULLISH, BANG, TILDE, BIT_AND, BIT_OR, BIT_XOR
    }

    private static final class Token {
        final TokType type;
        final String text;
        final double numVal;

        Token(TokType type, String text) {
            this.type = type;
            this.text = text;
            this.numVal = 0.0;
        }

        Token(double numVal, String text) {
            this.type = TokType.NUMBER;
            this.text = text;
            this.numVal = numVal;
        }
    }

    private static final class Lexer {
        private final String src;
        private int pos;

        Lexer(String src) { this.src = src; }

        List<Token> tokenize() {
            List<Token> out = new ArrayList<Token>();
            int n = src.length();
            while (pos < n) {
                char c = src.charAt(pos);
                if (Character.isWhitespace(c)) {
                    pos++;
                    continue;
                }
                if (c == '/' && pos + 1 < n && src.charAt(pos + 1) == '/') {
                    pos += 2;
                    while (pos < n && src.charAt(pos) != '\n') pos++;
                    continue;
                }
                if (c == '/' && pos + 1 < n && src.charAt(pos + 1) == '*') {
                    pos += 2;
                    while (pos + 1 < n && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) pos++;
                    if (pos + 1 < n) pos += 2;
                    continue;
                }
                if (c == '\'' || c == '"') {
                    out.add(readString(c));
                    continue;
                }
                if (c == '`') {
                    readTemplateLiteral(out);
                    continue;
                }
                if ((c >= '0' && c <= '9') || (c == '.' && pos + 1 < n && src.charAt(pos + 1) >= '0' && src.charAt(pos + 1) <= '9')) {
                    out.add(readNumber());
                    continue;
                }
                if (isIdentStart(c)) {
                    int start = pos++;
                    while (pos < n && isIdentPart(src.charAt(pos))) pos++;
                    out.add(new Token(TokType.IDENT, src.substring(start, pos)));
                    continue;
                }
                // Multi-char operators
                if (match("===")) { out.add(new Token(TokType.STRICT_EQ, "===")); continue; }
                if (match("!==")) { out.add(new Token(TokType.STRICT_NEQ, "!==")); continue; }
                if (match("==")) { out.add(new Token(TokType.EQ, "==")); continue; }
                if (match("!=")) { out.add(new Token(TokType.NEQ, "!=")); continue; }
                if (match("<=")) { out.add(new Token(TokType.LTE, "<=")); continue; }
                if (match(">=")) { out.add(new Token(TokType.GTE, ">=")); continue; }
                if (match("=>")) { out.add(new Token(TokType.ARROW, "=>")); continue; }
                if (match("&&")) { out.add(new Token(TokType.AND, "&&")); continue; }
                if (match("||")) { out.add(new Token(TokType.OR, "||")); continue; }
                if (match("??")) { out.add(new Token(TokType.NULLISH, "??")); continue; }
                if (match("++")) { out.add(new Token(TokType.PLUS_PLUS, "++")); continue; }
                if (match("--")) { out.add(new Token(TokType.MINUS_MINUS, "--")); continue; }
                if (match("+=")) { out.add(new Token(TokType.PLUS_EQ, "+=")); continue; }
                if (match("-=")) { out.add(new Token(TokType.MINUS_EQ, "-=")); continue; }
                if (match("*=")) { out.add(new Token(TokType.STAR_EQ, "*=")); continue; }
                if (match("/=")) { out.add(new Token(TokType.SLASH_EQ, "/=")); continue; }
                if (match("%=")) { out.add(new Token(TokType.PERCENT_EQ, "%=")); continue; }

                pos++;
                switch (c) {
                    case '(': out.add(new Token(TokType.LPAREN, "(")); break;
                    case ')': out.add(new Token(TokType.RPAREN, ")")); break;
                    case '{': out.add(new Token(TokType.LBRACE, "{")); break;
                    case '}': out.add(new Token(TokType.RBRACE, "}")); break;
                    case '[': out.add(new Token(TokType.LBRACKET, "[")); break;
                    case ']': out.add(new Token(TokType.RBRACKET, "]")); break;
                    case '.': out.add(new Token(TokType.DOT, ".")); break;
                    case ',': out.add(new Token(TokType.COMMA, ",")); break;
                    case ';': out.add(new Token(TokType.SEMI, ";")); break;
                    case ':': out.add(new Token(TokType.COLON, ":")); break;
                    case '?': out.add(new Token(TokType.QUESTION, "?")); break;
                    case '+': out.add(new Token(TokType.PLUS, "+")); break;
                    case '-': out.add(new Token(TokType.MINUS, "-")); break;
                    case '*': out.add(new Token(TokType.STAR, "*")); break;
                    case '/': out.add(new Token(TokType.SLASH, "/")); break;
                    case '%': out.add(new Token(TokType.PERCENT, "%")); break;
                    case '=': out.add(new Token(TokType.ASSIGN, "=")); break;
                    case '<': out.add(new Token(TokType.LT, "<")); break;
                    case '>': out.add(new Token(TokType.GT, ">")); break;
                    case '!': out.add(new Token(TokType.BANG, "!")); break;
                    case '~': out.add(new Token(TokType.TILDE, "~")); break;
                    case '&': out.add(new Token(TokType.BIT_AND, "&")); break;
                    case '|': out.add(new Token(TokType.BIT_OR, "|")); break;
                    case '^': out.add(new Token(TokType.BIT_XOR, "^")); break;
                    default: throw new JsRuntimeError("Unexpected character in JS: " + c);
                }
            }
            out.add(new Token(TokType.EOF, ""));
            return out;
        }

        private boolean match(String lit) {
            if (src.startsWith(lit, pos)) {
                pos += lit.length();
                return true;
            }
            return false;
        }

        private Token readString(char quote) {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == quote) return new Token(TokType.STRING, sb.toString());
                if (c == '\\' && pos < src.length()) {
                    sb.append(readEscape());
                } else {
                    sb.append(c);
                }
            }
            throw new JsRuntimeError("Unterminated string literal");
        }

        private void readTemplateLiteral(List<Token> out) {
            pos++; // skip opening backtick
            out.add(new Token(TokType.LPAREN, "("));
            StringBuilder seg = new StringBuilder();
            boolean first = true;
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '`') {
                    if (!first) out.add(new Token(TokType.PLUS, "+"));
                    out.add(new Token(TokType.STRING, seg.toString()));
                    out.add(new Token(TokType.RPAREN, ")"));
                    return;
                }
                if (c == '$' && pos < src.length() && src.charAt(pos) == '{') {
                    pos++; // skip '{'
                    if (!first) out.add(new Token(TokType.PLUS, "+"));
                    out.add(new Token(TokType.STRING, seg.toString()));
                    seg.setLength(0);
                    first = false;
                    // Collect balanced expression up to matching '}'
                    int depth = 1;
                    int exprStart = pos;
                    while (pos < src.length() && depth > 0) {
                        char ec = src.charAt(pos++);
                        if (ec == '{') depth++;
                        else if (ec == '}') depth--;
                        else if (ec == '\'' || ec == '"') {
                            while (pos < src.length()) {
                                char sc = src.charAt(pos++);
                                if (sc == '\\' && pos < src.length()) pos++;
                                else if (sc == ec) break;
                            }
                        }
                    }
                    String exprSrc = src.substring(exprStart, pos - 1);
                    out.add(new Token(TokType.PLUS, "+"));
                    out.add(new Token(TokType.LPAREN, "("));
                    List<Token> sub = new Lexer(exprSrc).tokenize();
                    for (int i = 0; i < sub.size() - 1; i++) out.add(sub.get(i));
                    out.add(new Token(TokType.RPAREN, ")"));
                } else if (c == '\\' && pos < src.length()) {
                    seg.append(readEscape());
                } else {
                    seg.append(c);
                }
            }
            throw new JsRuntimeError("Unterminated template literal");
        }

        private char readEscape() {
            char esc = src.charAt(pos++);
            switch (esc) {
                case 'n': return '\n';
                case 'r': return '\r';
                case 't': return '\t';
                case 'b': return '\b';
                case 'f': return '\f';
                case '0': return '\0';
                case 'u':
                    if (pos + 4 <= src.length()) {
                        char ch = (char) Integer.parseInt(src.substring(pos, pos + 4), 16);
                        pos += 4;
                        return ch;
                    }
                    return 'u';
                case 'x':
                    if (pos + 2 <= src.length()) {
                        char ch = (char) Integer.parseInt(src.substring(pos, pos + 2), 16);
                        pos += 2;
                        return ch;
                    }
                    return 'x';
                default: return esc;
            }
        }

        private Token readNumber() {
            int start = pos;
            if (src.charAt(pos) == '0' && pos + 1 < src.length()
                    && (src.charAt(pos + 1) == 'x' || src.charAt(pos + 1) == 'X')) {
                pos += 2;
                while (pos < src.length() && Character.digit(src.charAt(pos), 16) >= 0) pos++;
                String raw = src.substring(start, pos);
                return new Token((double) Long.parseLong(raw.substring(2), 16), raw);
            }
            while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && src.charAt(pos) >= '0' && src.charAt(pos) <= '9') pos++;
            }
            String raw = src.substring(start, pos);
            return new Token(Double.parseDouble(raw), raw);
        }

        private static boolean isIdentStart(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '$';
        }

        private static boolean isIdentPart(char c) {
            return isIdentStart(c) || (c >= '0' && c <= '9');
        }
    }

    // -------------------------------------------------------------------------
    // AST Nodes
    // -------------------------------------------------------------------------

    private interface Stmt {}
    private interface Expr {}

    private static final class BlockStmt implements Stmt {
        final List<Stmt> stmts;
        BlockStmt(List<Stmt> stmts) { this.stmts = stmts; }
    }

    private static final class ExprStmt implements Stmt {
        final Expr expr;
        ExprStmt(Expr expr) { this.expr = expr; }
    }

    private static final class VarDeclStmt implements Stmt {
        final List<String> names;
        final List<Expr> inits;
        VarDeclStmt(List<String> names, List<Expr> inits) { this.names = names; this.inits = inits; }
    }

    private static final class FuncDeclStmt implements Stmt {
        final String name;
        final List<String> params;
        final List<Stmt> body;
        FuncDeclStmt(String name, List<String> params, List<Stmt> body) {
            this.name = name; this.params = params; this.body = body;
        }
    }

    private static final class IfStmt implements Stmt {
        final Expr cond; final Stmt thenBranch; final Stmt elseBranch;
        IfStmt(Expr cond, Stmt thenBranch, Stmt elseBranch) {
            this.cond = cond; this.thenBranch = thenBranch; this.elseBranch = elseBranch;
        }
    }

    private static final class WhileStmt implements Stmt {
        final Expr cond; final Stmt body;
        WhileStmt(Expr cond, Stmt body) { this.cond = cond; this.body = body; }
    }

    private static final class ForStmt implements Stmt {
        final Stmt init; final Expr cond; final Expr update; final Stmt body;
        ForStmt(Stmt init, Expr cond, Expr update, Stmt body) {
            this.init = init; this.cond = cond; this.update = update; this.body = body;
        }
    }

    private static final class ForEachStmt implements Stmt {
        final String varName; final boolean isOf; final Expr iterable; final Stmt body;
        ForEachStmt(String varName, boolean isOf, Expr iterable, Stmt body) {
            this.varName = varName; this.isOf = isOf; this.iterable = iterable; this.body = body;
        }
    }

    private static final class ReturnStmt implements Stmt {
        final Expr expr;
        ReturnStmt(Expr expr) { this.expr = expr; }
    }

    private static final class BreakStmt implements Stmt {}
    private static final class ContinueStmt implements Stmt {}

    private static final class ThrowStmt implements Stmt {
        final Expr expr;
        ThrowStmt(Expr expr) { this.expr = expr; }
    }

    private static final class TryStmt implements Stmt {
        final List<Stmt> tryBody; final String catchVar; final List<Stmt> catchBody; final List<Stmt> finallyBody;
        TryStmt(List<Stmt> tryBody, String catchVar, List<Stmt> catchBody, List<Stmt> finallyBody) {
            this.tryBody = tryBody; this.catchVar = catchVar; this.catchBody = catchBody; this.finallyBody = finallyBody;
        }
    }

    private static final class LitExpr implements Expr {
        final Object value;
        LitExpr(Object value) { this.value = value; }
    }

    private static final class IdentExpr implements Expr {
        final String name;
        IdentExpr(String name) { this.name = name; }
    }

    private static final class ThisExpr implements Expr {}

    private static final class ArrayExpr implements Expr {
        final List<Expr> elements;
        ArrayExpr(List<Expr> elements) { this.elements = elements; }
    }

    private static final class ObjectExpr implements Expr {
        final List<String> keys; final List<Expr> values;
        ObjectExpr(List<String> keys, List<Expr> values) { this.keys = keys; this.values = values; }
    }

    private static final class FuncExpr implements Expr {
        final List<String> params; final List<Stmt> body;
        FuncExpr(List<String> params, List<Stmt> body) { this.params = params; this.body = body; }
    }

    private static final class UnaryExpr implements Expr {
        final String op; final Expr arg;
        UnaryExpr(String op, Expr arg) { this.op = op; this.arg = arg; }
    }

    private static final class UpdateExpr implements Expr {
        final String op; final Expr target; final boolean prefix;
        UpdateExpr(String op, Expr target, boolean prefix) {
            this.op = op; this.target = target; this.prefix = prefix;
        }
    }

    private static final class BinaryExpr implements Expr {
        final String op; final Expr left; final Expr right;
        BinaryExpr(String op, Expr left, Expr right) { this.op = op; this.left = left; this.right = right; }
    }

    private static final class TernaryExpr implements Expr {
        final Expr cond; final Expr thenExpr; final Expr elseExpr;
        TernaryExpr(Expr cond, Expr thenExpr, Expr elseExpr) {
            this.cond = cond; this.thenExpr = thenExpr; this.elseExpr = elseExpr;
        }
    }

    private static final class AssignExpr implements Expr {
        final String op; final Expr target; final Expr value;
        AssignExpr(String op, Expr target, Expr value) { this.op = op; this.target = target; this.value = value; }
    }

    private static final class MemberExpr implements Expr {
        final Expr object; final Expr property; final boolean computed;
        MemberExpr(Expr object, Expr property, boolean computed) {
            this.object = object; this.property = property; this.computed = computed;
        }
    }

    private static final class CallExpr implements Expr {
        final Expr callee; final List<Expr> args;
        CallExpr(Expr callee, List<Expr> args) { this.callee = callee; this.args = args; }
    }

    // -------------------------------------------------------------------------
    // Recursive-Descent Parser
    // -------------------------------------------------------------------------

    private static final class Parser {
        private final List<Token> tokens;
        private int pos;

        Parser(List<Token> tokens) { this.tokens = tokens; }

        List<Stmt> parseProgram() {
            List<Stmt> stmts = new ArrayList<Stmt>();
            while (!at(TokType.EOF)) {
                Stmt s = parseStmt();
                if (s != null) stmts.add(s);
            }
            return stmts;
        }

        private Token peek() { return tokens.get(Math.min(pos, tokens.size() - 1)); }
        private Token peekAt(int offset) { return tokens.get(Math.min(pos + offset, tokens.size() - 1)); }
        private boolean at(TokType t) { return peek().type == t; }
        private boolean atKw(String kw) { return peek().type == TokType.IDENT && kw.equals(peek().text); }

        private Token consume(TokType t) {
            Token tok = peek();
            if (tok.type != t) {
                throw new JsRuntimeError("Expected " + t + " but found " + tok.type + " (" + tok.text + ")");
            }
            pos++;
            return tok;
        }

        private boolean match(TokType t) {
            if (at(t)) { pos++; return true; }
            return false;
        }

        private boolean matchKw(String kw) {
            if (atKw(kw)) { pos++; return true; }
            return false;
        }

        private Stmt parseStmt() {
            if (match(TokType.SEMI)) return null;
            if (at(TokType.LBRACE)) return new BlockStmt(parseBlock());
            if (atKw("var") || atKw("let") || atKw("const")) return parseVarDecl(true);
            if (atKw("function")) return parseFuncDecl();
            if (atKw("if")) return parseIf();
            if (atKw("while")) return parseWhile();
            if (atKw("for")) return parseFor();
            if (atKw("return")) return parseReturn();
            if (matchKw("break")) { match(TokType.SEMI); return new BreakStmt(); }
            if (matchKw("continue")) { match(TokType.SEMI); return new ContinueStmt(); }
            if (atKw("throw")) {
                pos++;
                Expr e = parseExpr();
                match(TokType.SEMI);
                return new ThrowStmt(e);
            }
            if (atKw("try")) return parseTry();
            Expr e = parseExpr();
            match(TokType.SEMI);
            return new ExprStmt(e);
        }

        private List<Stmt> parseBlock() {
            consume(TokType.LBRACE);
            List<Stmt> stmts = new ArrayList<Stmt>();
            while (!at(TokType.RBRACE) && !at(TokType.EOF)) {
                Stmt s = parseStmt();
                if (s != null) stmts.add(s);
            }
            consume(TokType.RBRACE);
            return stmts;
        }

        private VarDeclStmt parseVarDecl(boolean consumeSemi) {
            pos++; // var/let/const
            List<String> names = new ArrayList<String>();
            List<Expr> inits = new ArrayList<Expr>();
            do {
                String name = consume(TokType.IDENT).text;
                Expr init = null;
                if (match(TokType.ASSIGN)) {
                    init = parseAssignment();
                }
                names.add(name);
                inits.add(init);
            } while (match(TokType.COMMA));
            if (consumeSemi) match(TokType.SEMI);
            return new VarDeclStmt(names, inits);
        }

        private FuncDeclStmt parseFuncDecl() {
            pos++; // function
            String name = consume(TokType.IDENT).text;
            List<String> params = parseParamList();
            List<Stmt> body = parseBlock();
            return new FuncDeclStmt(name, params, body);
        }

        private List<String> parseParamList() {
            consume(TokType.LPAREN);
            List<String> params = new ArrayList<String>();
            if (!at(TokType.RPAREN)) {
                do {
                    params.add(consume(TokType.IDENT).text);
                } while (match(TokType.COMMA));
            }
            consume(TokType.RPAREN);
            return params;
        }

        private IfStmt parseIf() {
            pos++; // if
            consume(TokType.LPAREN);
            Expr cond = parseExpr();
            consume(TokType.RPAREN);
            Stmt thenB = parseStmt();
            Stmt elseB = matchKw("else") ? parseStmt() : null;
            return new IfStmt(cond, thenB, elseB);
        }

        private WhileStmt parseWhile() {
            pos++; // while
            consume(TokType.LPAREN);
            Expr cond = parseExpr();
            consume(TokType.RPAREN);
            Stmt body = parseStmt();
            return new WhileStmt(cond, body);
        }

        private Stmt parseFor() {
            pos++; // for
            consume(TokType.LPAREN);
            // Detect for-of / for-in
            int saved = pos;
            if (atKw("var") || atKw("let") || atKw("const")) {
                pos++;
                if (at(TokType.IDENT) && (peekAt(1).type == TokType.IDENT
                        && ("of".equals(peekAt(1).text) || "in".equals(peekAt(1).text)))) {
                    String varName = consume(TokType.IDENT).text;
                    boolean isOf = "of".equals(consume(TokType.IDENT).text);
                    Expr iterable = parseExpr();
                    consume(TokType.RPAREN);
                    Stmt body = parseStmt();
                    return new ForEachStmt(varName, isOf, iterable, body);
                }
                pos = saved;
            }
            Stmt init = null;
            if (!at(TokType.SEMI)) {
                if (atKw("var") || atKw("let") || atKw("const")) {
                    init = parseVarDecl(false);
                } else {
                    init = new ExprStmt(parseExpr());
                }
            }
            consume(TokType.SEMI);
            Expr cond = at(TokType.SEMI) ? null : parseExpr();
            consume(TokType.SEMI);
            Expr update = at(TokType.RPAREN) ? null : parseExpr();
            consume(TokType.RPAREN);
            Stmt body = parseStmt();
            return new ForStmt(init, cond, update, body);
        }

        private ReturnStmt parseReturn() {
            pos++; // return
            if (at(TokType.SEMI) || at(TokType.RBRACE) || at(TokType.EOF)) {
                match(TokType.SEMI);
                return new ReturnStmt(null);
            }
            Expr e = parseExpr();
            match(TokType.SEMI);
            return new ReturnStmt(e);
        }

        private TryStmt parseTry() {
            pos++; // try
            List<Stmt> tryBody = parseBlock();
            String catchVar = null;
            List<Stmt> catchBody = null;
            List<Stmt> finallyBody = null;
            if (matchKw("catch")) {
                if (match(TokType.LPAREN)) {
                    catchVar = consume(TokType.IDENT).text;
                    consume(TokType.RPAREN);
                }
                catchBody = parseBlock();
            }
            if (matchKw("finally")) {
                finallyBody = parseBlock();
            }
            return new TryStmt(tryBody, catchVar, catchBody, finallyBody);
        }

        private Expr parseExpr() {
            return parseAssignment();
        }

        private Expr parseAssignment() {
            // Arrow function: single ident => ...
            if (at(TokType.IDENT) && peekAt(1).type == TokType.ARROW) {
                String p = consume(TokType.IDENT).text;
                consume(TokType.ARROW);
                return parseArrowBody(Collections.singletonList(p));
            }
            // Arrow function: (a, b) => ...
            if (at(TokType.LPAREN)) {
                int saved = pos;
                List<String> arrowParams = tryParseArrowParams();
                if (arrowParams != null) {
                    return parseArrowBody(arrowParams);
                }
                pos = saved;
            }
            Expr left = parseTernary();
            TokType t = peek().type;
            if (t == TokType.ASSIGN || t == TokType.PLUS_EQ || t == TokType.MINUS_EQ
                    || t == TokType.STAR_EQ || t == TokType.SLASH_EQ || t == TokType.PERCENT_EQ) {
                String op = peek().text;
                pos++;
                Expr right = parseAssignment();
                return new AssignExpr(op, left, right);
            }
            return left;
        }

        private List<String> tryParseArrowParams() {
            consume(TokType.LPAREN);
            List<String> params = new ArrayList<String>();
            if (!at(TokType.RPAREN)) {
                while (true) {
                    if (!at(TokType.IDENT)) return null;
                    params.add(consume(TokType.IDENT).text);
                    if (match(TokType.COMMA)) continue;
                    break;
                }
            }
            if (!match(TokType.RPAREN)) return null;
            if (!match(TokType.ARROW)) return null;
            return params;
        }

        private FuncExpr parseArrowBody(List<String> params) {
            if (at(TokType.LBRACE)) {
                return new FuncExpr(params, parseBlock());
            }
            Expr expr = parseAssignment();
            return new FuncExpr(params, Collections.<Stmt>singletonList(new ReturnStmt(expr)));
        }

        private Expr parseTernary() {
            Expr cond = parseNullish();
            if (match(TokType.QUESTION)) {
                Expr t = parseAssignment();
                consume(TokType.COLON);
                Expr f = parseAssignment();
                return new TernaryExpr(cond, t, f);
            }
            return cond;
        }

        private Expr parseNullish() {
            Expr left = parseLogicalOr();
            while (match(TokType.NULLISH)) {
                left = new BinaryExpr("??", left, parseLogicalOr());
            }
            return left;
        }

        private Expr parseLogicalOr() {
            Expr left = parseLogicalAnd();
            while (match(TokType.OR)) {
                left = new BinaryExpr("||", left, parseLogicalAnd());
            }
            return left;
        }

        private Expr parseLogicalAnd() {
            Expr left = parseEquality();
            while (match(TokType.AND)) {
                left = new BinaryExpr("&&", left, parseEquality());
            }
            return left;
        }

        private Expr parseEquality() {
            Expr left = parseRelational();
            while (at(TokType.EQ) || at(TokType.NEQ) || at(TokType.STRICT_EQ) || at(TokType.STRICT_NEQ)) {
                String op = peek().text;
                pos++;
                left = new BinaryExpr(op, left, parseRelational());
            }
            return left;
        }

        private Expr parseRelational() {
            Expr left = parseAdditive();
            while (at(TokType.LT) || at(TokType.LTE) || at(TokType.GT) || at(TokType.GTE) || atKw("in")) {
                String op = peek().text;
                pos++;
                left = new BinaryExpr(op, left, parseAdditive());
            }
            return left;
        }

        private Expr parseAdditive() {
            Expr left = parseMultiplicative();
            while (at(TokType.PLUS) || at(TokType.MINUS)) {
                String op = peek().text;
                pos++;
                left = new BinaryExpr(op, left, parseMultiplicative());
            }
            return left;
        }

        private Expr parseMultiplicative() {
            Expr left = parseUnary();
            while (at(TokType.STAR) || at(TokType.SLASH) || at(TokType.PERCENT)) {
                String op = peek().text;
                pos++;
                left = new BinaryExpr(op, left, parseUnary());
            }
            return left;
        }

        private Expr parseUnary() {
            if (at(TokType.BANG) || at(TokType.MINUS) || at(TokType.PLUS) || at(TokType.TILDE)) {
                String op = peek().text;
                pos++;
                return new UnaryExpr(op, parseUnary());
            }
            if (atKw("typeof") || atKw("void") || atKw("delete")) {
                String op = peek().text;
                pos++;
                return new UnaryExpr(op, parseUnary());
            }
            if (at(TokType.PLUS_PLUS) || at(TokType.MINUS_MINUS)) {
                String op = peek().text;
                pos++;
                return new UpdateExpr(op, parseUnary(), true);
            }
            if (matchKw("new")) {
                // Support `new Error("msg")` / `new Object()` / `new Array(...)`
                Expr target = parsePostfix();
                return target;
            }
            return parsePostfix();
        }

        private Expr parsePostfix() {
            Expr expr = parseCallAndMember();
            if (at(TokType.PLUS_PLUS) || at(TokType.MINUS_MINUS)) {
                String op = peek().text;
                pos++;
                return new UpdateExpr(op, expr, false);
            }
            return expr;
        }

        private Expr parseCallAndMember() {
            Expr expr = parsePrimary();
            while (true) {
                if (match(TokType.DOT)) {
                    String prop = consume(TokType.IDENT).text;
                    expr = new MemberExpr(expr, new LitExpr(prop), false);
                } else if (match(TokType.LBRACKET)) {
                    Expr index = parseExpr();
                    consume(TokType.RBRACKET);
                    expr = new MemberExpr(expr, index, true);
                } else if (match(TokType.LPAREN)) {
                    List<Expr> args = new ArrayList<Expr>();
                    if (!at(TokType.RPAREN)) {
                        do {
                            args.add(parseAssignment());
                        } while (match(TokType.COMMA));
                    }
                    consume(TokType.RPAREN);
                    expr = new CallExpr(expr, args);
                } else {
                    break;
                }
            }
            return expr;
        }

        private Expr parsePrimary() {
            Token tok = peek();
            if (tok.type == TokType.NUMBER) {
                pos++;
                return new LitExpr(Double.valueOf(tok.numVal));
            }
            if (tok.type == TokType.STRING) {
                pos++;
                return new LitExpr(tok.text);
            }
            if (matchKw("true")) return new LitExpr(Boolean.TRUE);
            if (matchKw("false")) return new LitExpr(Boolean.FALSE);
            if (matchKw("null")) return new LitExpr(null);
            if (matchKw("this")) return new ThisExpr();
            if (matchKw("function")) {
                if (at(TokType.IDENT)) pos++; // optional name
                List<String> params = parseParamList();
                List<Stmt> body = parseBlock();
                return new FuncExpr(params, body);
            }
            if (tok.type == TokType.IDENT) {
                pos++;
                return new IdentExpr(tok.text);
            }
            if (match(TokType.LPAREN)) {
                Expr inner = parseExpr();
                consume(TokType.RPAREN);
                return inner;
            }
            if (match(TokType.LBRACKET)) {
                List<Expr> elements = new ArrayList<Expr>();
                if (!at(TokType.RBRACKET)) {
                    do {
                        if (at(TokType.RBRACKET)) break;
                        elements.add(parseAssignment());
                    } while (match(TokType.COMMA));
                }
                consume(TokType.RBRACKET);
                return new ArrayExpr(elements);
            }
            if (match(TokType.LBRACE)) {
                List<String> keys = new ArrayList<String>();
                List<Expr> values = new ArrayList<Expr>();
                if (!at(TokType.RBRACE)) {
                    do {
                        if (at(TokType.RBRACE)) break;
                        Token k = peek();
                        String key;
                        if (k.type == TokType.IDENT || k.type == TokType.STRING || k.type == TokType.NUMBER) {
                            key = k.text;
                            pos++;
                        } else {
                            throw new JsRuntimeError("Expected property name in object literal");
                        }
                        if (match(TokType.COLON)) {
                            keys.add(key);
                            values.add(parseAssignment());
                        } else {
                            // Shorthand { x }
                            keys.add(key);
                            values.add(new IdentExpr(key));
                        }
                    } while (match(TokType.COMMA));
                }
                consume(TokType.RBRACE);
                return new ObjectExpr(keys, values);
            }
            throw new JsRuntimeError("Unexpected token: " + tok.type + " (" + tok.text + ")");
        }
    }

    // -------------------------------------------------------------------------
    // Evaluator
    // -------------------------------------------------------------------------

    private void tick() {
        if (++steps > MAX_STEPS) {
            throw new JsRuntimeError("Script execution step limit exceeded");
        }
    }

    private Object execBlock(List<Stmt> stmts, Env env, Object thisVal) {
        Object last = Undefined.INSTANCE;
        for (Stmt s : stmts) {
            last = execStmt(s, env, thisVal);
        }
        return last;
    }

    private Object execStmt(Stmt stmt, Env env, Object thisVal) {
        tick();
        if (stmt == null) return Undefined.INSTANCE;
        if (stmt instanceof BlockStmt) {
            return execBlock(((BlockStmt) stmt).stmts, new Env(env), thisVal);
        }
        if (stmt instanceof ExprStmt) {
            return evalExpr(((ExprStmt) stmt).expr, env, thisVal);
        }
        if (stmt instanceof VarDeclStmt) {
            VarDeclStmt v = (VarDeclStmt) stmt;
            for (int i = 0; i < v.names.size(); i++) {
                Object val = v.inits.get(i) != null
                        ? evalExpr(v.inits.get(i), env, thisVal)
                        : Undefined.INSTANCE;
                env.define(v.names.get(i), val);
                if (env == globalEnv) windowObj.setProp(v.names.get(i), val);
            }
            return Undefined.INSTANCE;
        }
        if (stmt instanceof FuncDeclStmt) {
            FuncDeclStmt f = (FuncDeclStmt) stmt;
            JsCallable fn = makeUserFunction(f.params, f.body, env);
            env.define(f.name, fn);
            if (env == globalEnv) windowObj.setProp(f.name, fn);
            return Undefined.INSTANCE;
        }
        if (stmt instanceof IfStmt) {
            IfStmt i = (IfStmt) stmt;
            if (toJsBoolean(evalExpr(i.cond, env, thisVal))) {
                return execStmt(i.thenBranch, env, thisVal);
            }
            if (i.elseBranch != null) {
                return execStmt(i.elseBranch, env, thisVal);
            }
            return Undefined.INSTANCE;
        }
        if (stmt instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) stmt;
            while (toJsBoolean(evalExpr(w.cond, env, thisVal))) {
                tick();
                try {
                    execStmt(w.body, env, thisVal);
                } catch (BreakSignal b) {
                    break;
                } catch (ContinueSignal c) {
                    // continue loop
                }
            }
            return Undefined.INSTANCE;
        }
        if (stmt instanceof ForStmt) {
            ForStmt f = (ForStmt) stmt;
            Env loopEnv = new Env(env);
            if (f.init != null) execStmt(f.init, loopEnv, thisVal);
            while (f.cond == null || toJsBoolean(evalExpr(f.cond, loopEnv, thisVal))) {
                tick();
                try {
                    execStmt(f.body, loopEnv, thisVal);
                } catch (BreakSignal b) {
                    break;
                } catch (ContinueSignal c) {
                    // run update then continue
                }
                if (f.update != null) evalExpr(f.update, loopEnv, thisVal);
            }
            return Undefined.INSTANCE;
        }
        if (stmt instanceof ForEachStmt) {
            ForEachStmt fe = (ForEachStmt) stmt;
            Object target = evalExpr(fe.iterable, env, thisVal);
            List<Object> seq = new ArrayList<Object>();
            if (fe.isOf) {
                if (target instanceof JsArray) {
                    seq.addAll(((JsArray) target).items);
                } else if (target instanceof String) {
                    String s = (String) target;
                    for (int idx = 0; idx < s.length(); idx++) seq.add(String.valueOf(s.charAt(idx)));
                }
            } else {
                if (target instanceof JsObject) {
                    seq.addAll(((JsObject) target).ownKeys());
                }
            }
            for (Object item : seq) {
                tick();
                Env iterEnv = new Env(env);
                iterEnv.define(fe.varName, item);
                try {
                    execStmt(fe.body, iterEnv, thisVal);
                } catch (BreakSignal b) {
                    break;
                } catch (ContinueSignal c) {
                    // next item
                }
            }
            return Undefined.INSTANCE;
        }
        if (stmt instanceof ReturnStmt) {
            ReturnStmt r = (ReturnStmt) stmt;
            Object val = r.expr != null ? evalExpr(r.expr, env, thisVal) : Undefined.INSTANCE;
            throw new ReturnSignal(val);
        }
        if (stmt instanceof BreakStmt) throw new BreakSignal();
        if (stmt instanceof ContinueStmt) throw new ContinueSignal();
        if (stmt instanceof ThrowStmt) {
            throw new JsRuntimeError(evalExpr(((ThrowStmt) stmt).expr, env, thisVal));
        }
        if (stmt instanceof TryStmt) {
            TryStmt t = (TryStmt) stmt;
            try {
                execBlock(t.tryBody, new Env(env), thisVal);
            } catch (JsRuntimeError err) {
                if (t.catchBody != null) {
                    Env catchEnv = new Env(env);
                    if (t.catchVar != null) catchEnv.define(t.catchVar, err.jsValue);
                    execBlock(t.catchBody, catchEnv, thisVal);
                } else {
                    throw err;
                }
            } finally {
                if (t.finallyBody != null) {
                    execBlock(t.finallyBody, new Env(env), thisVal);
                }
            }
            return Undefined.INSTANCE;
        }
        return Undefined.INSTANCE;
    }

    private JsCallable makeUserFunction(final List<String> params, final List<Stmt> body, final Env closure) {
        return new JsCallable() {
            @Override public Object call(Object thisArg, List<Object> args) {
                Env fnEnv = new Env(closure);
                for (int i = 0; i < params.size(); i++) {
                    fnEnv.define(params.get(i), i < args.size() ? args.get(i) : Undefined.INSTANCE);
                }
                fnEnv.define("arguments", new JsArray(args));
                try {
                    execBlock(body, fnEnv, thisArg != null ? thisArg : windowObj);
                } catch (ReturnSignal ret) {
                    return ret.value;
                }
                return Undefined.INSTANCE;
            }
        };
    }

    private Object evalExpr(Expr expr, Env env, Object thisVal) {
        tick();
        if (expr instanceof LitExpr) return ((LitExpr) expr).value;
        if (expr instanceof ThisExpr) return thisVal;
        if (expr instanceof IdentExpr) {
            String name = ((IdentExpr) expr).name;
            if (env.has(name)) return env.get(name);
            return windowObj.getProp(name);
        }
        if (expr instanceof ArrayExpr) {
            JsArray arr = new JsArray();
            for (Expr e : ((ArrayExpr) expr).elements) arr.items.add(evalExpr(e, env, thisVal));
            return arr;
        }
        if (expr instanceof ObjectExpr) {
            ObjectExpr oe = (ObjectExpr) expr;
            JsObject obj = new JsObject();
            for (int i = 0; i < oe.keys.size(); i++) {
                obj.setProp(oe.keys.get(i), evalExpr(oe.values.get(i), env, thisVal));
            }
            return obj;
        }
        if (expr instanceof FuncExpr) {
            FuncExpr fe = (FuncExpr) expr;
            return makeUserFunction(fe.params, fe.body, env);
        }
        if (expr instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) expr;
            if ("typeof".equals(u.op)) {
                Object val = evalExpr(u.arg, env, thisVal);
                return jsTypeof(val);
            }
            if ("delete".equals(u.op)) {
                if (u.arg instanceof MemberExpr) {
                    MemberExpr m = (MemberExpr) u.arg;
                    Object obj = evalExpr(m.object, env, thisVal);
                    String prop = toJsString(evalExpr(m.property, env, thisVal));
                    if (obj instanceof JsObject) return Boolean.valueOf(((JsObject) obj).deleteProp(prop));
                }
                return Boolean.TRUE;
            }
            Object val = evalExpr(u.arg, env, thisVal);
            if ("!".equals(u.op)) return Boolean.valueOf(!toJsBoolean(val));
            if ("-".equals(u.op)) return Double.valueOf(-toJsNumber(val));
            if ("+".equals(u.op)) return Double.valueOf(toJsNumber(val));
            if ("~".equals(u.op)) return Double.valueOf((double) (~((int) toJsNumber(val))));
            if ("void".equals(u.op)) return Undefined.INSTANCE;
            return Undefined.INSTANCE;
        }
        if (expr instanceof UpdateExpr) {
            UpdateExpr u = (UpdateExpr) expr;
            double oldVal = toJsNumber(evalExpr(u.target, env, thisVal));
            double newVal = "++".equals(u.op) ? oldVal + 1.0 : oldVal - 1.0;
            assignTarget(u.target, Double.valueOf(newVal), env, thisVal);
            return Double.valueOf(u.prefix ? newVal : oldVal);
        }
        if (expr instanceof TernaryExpr) {
            TernaryExpr t = (TernaryExpr) expr;
            return toJsBoolean(evalExpr(t.cond, env, thisVal))
                    ? evalExpr(t.thenExpr, env, thisVal)
                    : evalExpr(t.elseExpr, env, thisVal);
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) expr;
            if ("&&".equals(b.op)) {
                Object l = evalExpr(b.left, env, thisVal);
                return toJsBoolean(l) ? evalExpr(b.right, env, thisVal) : l;
            }
            if ("||".equals(b.op)) {
                Object l = evalExpr(b.left, env, thisVal);
                return toJsBoolean(l) ? l : evalExpr(b.right, env, thisVal);
            }
            if ("??".equals(b.op)) {
                Object l = evalExpr(b.left, env, thisVal);
                return (l != null && l != Undefined.INSTANCE) ? l : evalExpr(b.right, env, thisVal);
            }
            Object l = evalExpr(b.left, env, thisVal);
            Object r = evalExpr(b.right, env, thisVal);
            return evalBinaryOp(b.op, l, r);
        }
        if (expr instanceof AssignExpr) {
            AssignExpr a = (AssignExpr) expr;
            Object rhs = evalExpr(a.value, env, thisVal);
            Object finalVal = rhs;
            if (!"=".equals(a.op)) {
                Object cur = evalExpr(a.target, env, thisVal);
                String baseOp = a.op.substring(0, a.op.length() - 1);
                finalVal = evalBinaryOp(baseOp, cur, rhs);
            }
            assignTarget(a.target, finalVal, env, thisVal);
            return finalVal;
        }
        if (expr instanceof MemberExpr) {
            MemberExpr m = (MemberExpr) expr;
            Object obj = evalExpr(m.object, env, thisVal);
            String prop = toJsString(evalExpr(m.property, env, thisVal));
            return getProperty(obj, prop);
        }
        if (expr instanceof CallExpr) {
            CallExpr c = (CallExpr) expr;
            Object callThis = windowObj;
            Object calleeVal;
            if (c.callee instanceof MemberExpr) {
                MemberExpr m = (MemberExpr) c.callee;
                callThis = evalExpr(m.object, env, thisVal);
                String prop = toJsString(evalExpr(m.property, env, thisVal));
                calleeVal = getProperty(callThis, prop);
            } else {
                calleeVal = evalExpr(c.callee, env, thisVal);
            }
            List<Object> argVals = new ArrayList<Object>(c.args.size());
            for (Expr a : c.args) argVals.add(evalExpr(a, env, thisVal));
            if (!(calleeVal instanceof JsCallable)) {
                throw new JsRuntimeError("Value is not a function: " + toJsString(calleeVal));
            }
            return ((JsCallable) calleeVal).call(callThis, argVals);
        }
        return Undefined.INSTANCE;
    }

    private void assignTarget(Expr target, Object val, Env env, Object thisVal) {
        if (target instanceof IdentExpr) {
            String name = ((IdentExpr) target).name;
            env.set(name, val);
            if (!env.bindings.containsKey(name) || env == globalEnv) {
                if (windowObj.hasProp(name) || env == globalEnv) {
                    windowObj.setProp(name, val);
                }
            }
            return;
        }
        if (target instanceof MemberExpr) {
            MemberExpr m = (MemberExpr) target;
            Object obj = evalExpr(m.object, env, thisVal);
            String prop = toJsString(evalExpr(m.property, env, thisVal));
            if (obj instanceof JsObject) {
                ((JsObject) obj).setProp(prop, val);
                if (obj == windowObj) globalEnv.define(prop, val);
            }
            return;
        }
        throw new JsRuntimeError("Invalid assignment target");
    }

    private static String jsTypeof(Object val) {
        if (val == Undefined.INSTANCE) return "undefined";
        if (val == null) return "object";
        if (val instanceof Boolean) return "boolean";
        if (val instanceof Double) return "number";
        if (val instanceof String) return "string";
        if (val instanceof JsCallable) return "function";
        return "object";
    }

    private static Object evalBinaryOp(String op, Object l, Object r) {
        if ("+".equals(op)) {
            if (l instanceof String || r instanceof String) {
                return toJsString(l) + toJsString(r);
            }
            return Double.valueOf(toJsNumber(l) + toJsNumber(r));
        }
        if ("-".equals(op)) return Double.valueOf(toJsNumber(l) - toJsNumber(r));
        if ("*".equals(op)) return Double.valueOf(toJsNumber(l) * toJsNumber(r));
        if ("/".equals(op)) return Double.valueOf(toJsNumber(l) / toJsNumber(r));
        if ("%".equals(op)) return Double.valueOf(toJsNumber(l) % toJsNumber(r));
        if ("===".equals(op)) return Boolean.valueOf(strictEquals(l, r));
        if ("!==".equals(op)) return Boolean.valueOf(!strictEquals(l, r));
        if ("==".equals(op)) return Boolean.valueOf(looseEquals(l, r));
        if ("!=".equals(op)) return Boolean.valueOf(!looseEquals(l, r));
        if ("<".equals(op)) return Boolean.valueOf(compareRel(l, r) < 0);
        if ("<=".equals(op)) return Boolean.valueOf(compareRel(l, r) <= 0);
        if (">".equals(op)) return Boolean.valueOf(compareRel(l, r) > 0);
        if (">=".equals(op)) return Boolean.valueOf(compareRel(l, r) >= 0);
        if ("in".equals(op)) {
            if (r instanceof JsObject) return Boolean.valueOf(((JsObject) r).hasProp(toJsString(l)));
            return Boolean.FALSE;
        }
        return Undefined.INSTANCE;
    }

    private static boolean strictEquals(Object a, Object b) {
        if (a == b) {
            if (a instanceof Double && Double.isNaN(((Double) a).doubleValue())) return false;
            return true;
        }
        if (a == null || b == null) return false;
        if (a instanceof Double && b instanceof Double) {
            return ((Double) a).doubleValue() == ((Double) b).doubleValue();
        }
        if (a instanceof String && b instanceof String) return a.equals(b);
        if (a instanceof Boolean && b instanceof Boolean) return a.equals(b);
        return false;
    }

    private static boolean looseEquals(Object a, Object b) {
        if ((a == null || a == Undefined.INSTANCE) && (b == null || b == Undefined.INSTANCE)) return true;
        if (a == null || b == null || a == Undefined.INSTANCE || b == Undefined.INSTANCE) return false;
        if (a.getClass() == b.getClass()) return strictEquals(a, b);
        if (a instanceof Double || b instanceof Double || a instanceof Boolean || b instanceof Boolean) {
            return toJsNumber(a) == toJsNumber(b);
        }
        return toJsString(a).equals(toJsString(b));
    }

    private static double compareRel(Object a, Object b) {
        if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        }
        double da = toJsNumber(a);
        double db = toJsNumber(b);
        if (Double.isNaN(da) || Double.isNaN(db)) return Double.NaN;
        return Double.compare(da, db);
    }

    // -------------------------------------------------------------------------
    // Property & Built-in Method Dispatch (String & Array)
    // -------------------------------------------------------------------------

    private Object getProperty(Object obj, String prop) {
        if (obj == null || obj == Undefined.INSTANCE) {
            throw new JsRuntimeError("Cannot read property '" + prop + "' of " + toJsString(obj));
        }
        if (obj instanceof String) {
            return getStringProperty((String) obj, prop);
        }
        if (obj instanceof JsArray) {
            Object method = getArrayMethod((JsArray) obj, prop);
            if (method != null) return method;
            return ((JsArray) obj).getProp(prop);
        }
        if (obj instanceof JsObject) {
            return ((JsObject) obj).getProp(prop);
        }
        return Undefined.INSTANCE;
    }

    private Object getStringProperty(final String s, String prop) {
        if ("length".equals(prop)) return Double.valueOf(s.length());
        int idx = parseArrayIndex(prop);
        if (idx >= 0) return idx < s.length() ? String.valueOf(s.charAt(idx)) : Undefined.INSTANCE;
        if ("toLowerCase".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) { return s.toLowerCase(Locale.ROOT); }
            };
        }
        if ("toUpperCase".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) { return s.toUpperCase(Locale.ROOT); }
            };
        }
        if ("trim".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) { return s.trim(); }
            };
        }
        if ("indexOf".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    String needle = a.isEmpty() ? "undefined" : toJsString(a.get(0));
                    int from = a.size() >= 2 ? (int) toJsNumber(a.get(1)) : 0;
                    return Double.valueOf(s.indexOf(needle, from));
                }
            };
        }
        if ("includes".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return Boolean.valueOf(s.contains(a.isEmpty() ? "undefined" : toJsString(a.get(0))));
                }
            };
        }
        if ("startsWith".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return Boolean.valueOf(s.startsWith(a.isEmpty() ? "undefined" : toJsString(a.get(0))));
                }
            };
        }
        if ("endsWith".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return Boolean.valueOf(s.endsWith(a.isEmpty() ? "undefined" : toJsString(a.get(0))));
                }
            };
        }
        if ("slice".equals(prop) || "substring".equals(prop)) {
            final boolean isSlice = "slice".equals(prop);
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    int len = s.length();
                    int start = a.isEmpty() ? 0 : (int) toJsNumber(a.get(0));
                    int end = a.size() >= 2 ? (int) toJsNumber(a.get(1)) : len;
                    if (isSlice) {
                        if (start < 0) start = Math.max(0, len + start);
                        if (end < 0) end = Math.max(0, len + end);
                    }
                    start = Math.max(0, Math.min(len, start));
                    end = Math.max(start, Math.min(len, end));
                    return s.substring(start, end);
                }
            };
        }
        if ("split".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    JsArray arr = new JsArray();
                    if (a.isEmpty()) {
                        arr.items.add(s);
                        return arr;
                    }
                    String sep = toJsString(a.get(0));
                    if (sep.isEmpty()) {
                        for (int i = 0; i < s.length(); i++) arr.items.add(String.valueOf(s.charAt(i)));
                        return arr;
                    }
                    int start = 0;
                    while (true) {
                        int idx2 = s.indexOf(sep, start);
                        if (idx2 < 0) {
                            arr.items.add(s.substring(start));
                            break;
                        }
                        arr.items.add(s.substring(start, idx2));
                        start = idx2 + sep.length();
                    }
                    return arr;
                }
            };
        }
        if ("replace".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.size() < 2) return s;
                    String target = toJsString(a.get(0));
                    String repl = toJsString(a.get(1));
                    int idx2 = s.indexOf(target);
                    if (idx2 < 0) return s;
                    return s.substring(0, idx2) + repl + s.substring(idx2 + target.length());
                }
            };
        }
        if ("charAt".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    int i = a.isEmpty() ? 0 : (int) toJsNumber(a.get(0));
                    return (i >= 0 && i < s.length()) ? String.valueOf(s.charAt(i)) : "";
                }
            };
        }
        return Undefined.INSTANCE;
    }

    private Object getArrayMethod(final JsArray arr, String prop) {
        if ("push".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    arr.items.addAll(a);
                    return Double.valueOf(arr.items.size());
                }
            };
        }
        if ("pop".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return arr.items.isEmpty() ? Undefined.INSTANCE : arr.items.remove(arr.items.size() - 1);
                }
            };
        }
        if ("shift".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    return arr.items.isEmpty() ? Undefined.INSTANCE : arr.items.remove(0);
                }
            };
        }
        if ("unshift".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    arr.items.addAll(0, a);
                    return Double.valueOf(arr.items.size());
                }
            };
        }
        if ("join".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    String sep = (a.isEmpty() || a.get(0) == Undefined.INSTANCE) ? "," : toJsString(a.get(0));
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < arr.items.size(); i++) {
                        if (i > 0) sb.append(sep);
                        Object v = arr.items.get(i);
                        if (v != null && v != Undefined.INSTANCE) sb.append(toJsString(v));
                    }
                    return sb.toString();
                }
            };
        }
        if ("slice".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    int len = arr.items.size();
                    int start = a.isEmpty() ? 0 : (int) toJsNumber(a.get(0));
                    int end = a.size() >= 2 ? (int) toJsNumber(a.get(1)) : len;
                    if (start < 0) start = Math.max(0, len + start);
                    if (end < 0) end = Math.max(0, len + end);
                    start = Math.max(0, Math.min(len, start));
                    end = Math.max(start, Math.min(len, end));
                    return new JsArray(arr.items.subList(start, end));
                }
            };
        }
        if ("indexOf".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    Object needle = a.isEmpty() ? Undefined.INSTANCE : a.get(0);
                    for (int i = 0; i < arr.items.size(); i++) {
                        if (strictEquals(arr.items.get(i), needle)) return Double.valueOf(i);
                    }
                    return Double.valueOf(-1);
                }
            };
        }
        if ("includes".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    Object needle = a.isEmpty() ? Undefined.INSTANCE : a.get(0);
                    for (int i = 0; i < arr.items.size(); i++) {
                        if (strictEquals(arr.items.get(i), needle)) return Boolean.TRUE;
                    }
                    return Boolean.FALSE;
                }
            };
        }
        if ("map".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    JsArray out = new JsArray();
                    if (!a.isEmpty() && a.get(0) instanceof JsCallable) {
                        JsCallable fn = (JsCallable) a.get(0);
                        for (int i = 0; i < arr.items.size(); i++) {
                            List<Object> cbArgs = new ArrayList<Object>(3);
                            cbArgs.add(arr.items.get(i));
                            cbArgs.add(Double.valueOf(i));
                            cbArgs.add(arr);
                            out.items.add(fn.call(windowObj, cbArgs));
                        }
                    }
                    return out;
                }
            };
        }
        if ("filter".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    JsArray out = new JsArray();
                    if (!a.isEmpty() && a.get(0) instanceof JsCallable) {
                        JsCallable fn = (JsCallable) a.get(0);
                        for (int i = 0; i < arr.items.size(); i++) {
                            List<Object> cbArgs = new ArrayList<Object>(3);
                            cbArgs.add(arr.items.get(i));
                            cbArgs.add(Double.valueOf(i));
                            cbArgs.add(arr);
                            if (toJsBoolean(fn.call(windowObj, cbArgs))) out.items.add(arr.items.get(i));
                        }
                    }
                    return out;
                }
            };
        }
        if ("forEach".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (!a.isEmpty() && a.get(0) instanceof JsCallable) {
                        JsCallable fn = (JsCallable) a.get(0);
                        for (int i = 0; i < arr.items.size(); i++) {
                            List<Object> cbArgs = new ArrayList<Object>(3);
                            cbArgs.add(arr.items.get(i));
                            cbArgs.add(Double.valueOf(i));
                            cbArgs.add(arr);
                            fn.call(windowObj, cbArgs);
                        }
                    }
                    return Undefined.INSTANCE;
                }
            };
        }
        if ("reduce".equals(prop)) {
            return new JsCallable() {
                @Override public Object call(Object t, List<Object> a) {
                    if (a.isEmpty() || !(a.get(0) instanceof JsCallable)) return Undefined.INSTANCE;
                    JsCallable fn = (JsCallable) a.get(0);
                    int idx = 0;
                    Object acc;
                    if (a.size() >= 2) {
                        acc = a.get(1);
                    } else {
                        if (arr.items.isEmpty()) throw new JsRuntimeError("Reduce of empty array");
                        acc = arr.items.get(0);
                        idx = 1;
                    }
                    for (; idx < arr.items.size(); idx++) {
                        List<Object> cbArgs = new ArrayList<Object>(4);
                        cbArgs.add(acc);
                        cbArgs.add(arr.items.get(idx));
                        cbArgs.add(Double.valueOf(idx));
                        cbArgs.add(arr);
                        acc = fn.call(windowObj, cbArgs);
                    }
                    return acc;
                }
            };
        }
        return null;
    }
}
