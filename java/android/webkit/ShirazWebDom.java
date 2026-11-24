// SPDX-License-Identifier: GPL-3.0-only
package android.webkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure-Java HTML5-style DOM tree, CSS selector matcher, and serializer for Shiraz WebView. */
final class ShirazWebDom {
    static final int NODE_DOCUMENT = 9;
    static final int NODE_ELEMENT = 1;
    static final int NODE_TEXT = 3;
    static final int NODE_COMMENT = 8;

    private static final Set<String> VOID_TAGS = new HashSet<String>(Arrays.asList(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"));

    private static final Set<String> BLOCK_TAGS = new HashSet<String>(Arrays.asList(
            "address", "article", "aside", "blockquote", "div", "dl", "dt", "dd",
            "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
            "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p",
            "pre", "section", "table", "tr", "ul"));

    static final class DomNode {
        final int nodeType;
        final String tagName; // lowercase for elements, "#document"/"#text"/"#comment" otherwise
        String textData;
        DomNode parent;
        final LinkedHashMap<String, String> attributes = new LinkedHashMap<String, String>();
        final List<DomNode> children = new ArrayList<DomNode>();

        DomNode(int nodeType, String tagName, String textData) {
            this.nodeType = nodeType;
            this.tagName = tagName;
            this.textData = textData;
        }

        void appendChild(DomNode child) {
            if (child == null) return;
            if (child.parent != null) {
                child.parent.children.remove(child);
            }
            child.parent = this;
            children.add(child);
        }

        boolean removeChild(DomNode child) {
            if (child != null && children.remove(child)) {
                child.parent = null;
                return true;
            }
            return false;
        }

        DomNode insertBefore(DomNode newNode, DomNode refNode) {
            if (newNode == null) return null;
            if (refNode == null) {
                appendChild(newNode);
                return newNode;
            }
            int idx = children.indexOf(refNode);
            if (idx < 0) return null;
            if (newNode.parent != null) {
                int oldIdx = newNode.parent.children.indexOf(newNode);
                newNode.parent.children.remove(newNode);
                if (newNode.parent == this && oldIdx >= 0 && oldIdx < idx) {
                    idx--;
                }
            }
            newNode.parent = this;
            children.add(idx, newNode);
            return newNode;
        }

        DomNode replaceChild(DomNode newNode, DomNode oldNode) {
            if (newNode == null || oldNode == null) return null;
            int idx = children.indexOf(oldNode);
            if (idx < 0) return null;
            if (newNode == oldNode) return oldNode;
            if (newNode.parent != null) {
                int oldIdx = newNode.parent.children.indexOf(newNode);
                newNode.parent.children.remove(newNode);
                if (newNode.parent == this && oldIdx >= 0 && oldIdx < idx) {
                    idx--;
                }
            }
            oldNode.parent = null;
            newNode.parent = this;
            children.set(idx, newNode);
            return oldNode;
        }

        DomNode cloneNode(boolean deep) {
            DomNode copy = new DomNode(nodeType, tagName, textData);
            copy.attributes.putAll(attributes);
            if (deep) {
                for (DomNode c : children) {
                    copy.appendChild(c.cloneNode(true));
                }
            }
            return copy;
        }

        DomNode nextSibling() {
            if (parent == null) return null;
            int idx = parent.children.indexOf(this);
            return (idx >= 0 && idx + 1 < parent.children.size()) ? parent.children.get(idx + 1) : null;
        }

        DomNode previousSibling() {
            if (parent == null) return null;
            int idx = parent.children.indexOf(this);
            return idx > 0 ? parent.children.get(idx - 1) : null;
        }

        boolean contains(DomNode other) {
            DomNode cur = other;
            while (cur != null) {
                if (cur == this) return true;
                cur = cur.parent;
            }
            return false;
        }

        boolean isContentEditable() {
            DomNode cur = this;
            while (cur != null) {
                if (cur.nodeType == NODE_ELEMENT && cur.hasAttribute("contenteditable")) {
                    String val = cur.getAttribute("contenteditable");
                    if ("false".equalsIgnoreCase(val)) return false;
                    return true;
                }
                cur = cur.parent;
            }
            return false;
        }

        boolean isEditableControl() {
            if (nodeType != NODE_ELEMENT) return false;
            if ("textarea".equals(tagName)) return true;
            if ("input".equals(tagName)) {
                String type = getAttribute("type");
                if (type == null || type.isEmpty()) return true;
                String t = type.toLowerCase(Locale.ROOT);
                return "text".equals(t) || "search".equals(t) || "email".equals(t)
                        || "url".equals(t) || "tel".equals(t) || "password".equals(t)
                        || "number".equals(t);
            }
            return hasAttribute("contenteditable") && !"false".equalsIgnoreCase(getAttribute("contenteditable"));
        }

        String getEditableValue() {
            if ("input".equals(tagName)) {
                String v = getAttribute("value");
                return v == null ? "" : v;
            }
            if ("textarea".equals(tagName)) {
                String v = getAttribute("value");
                return v != null ? v : getTextContent();
            }
            return getTextContent();
        }

        void setEditableValue(String value) {
            String safe = value == null ? "" : value;
            if ("input".equals(tagName)) {
                setAttribute("value", safe);
            } else if ("textarea".equals(tagName)) {
                setAttribute("value", safe);
                setTextContent(safe);
            } else {
                setTextContent(safe);
            }
        }

        String getOuterHtml() {
            StringBuilder sb = new StringBuilder();
            serializeNode(this, sb);
            return sb.toString();
        }

        String getAttribute(String name) {
            if (name == null) return null;
            return attributes.get(name.toLowerCase(Locale.ROOT));
        }

        void setAttribute(String name, String value) {
            if (name == null || name.isEmpty()) return;
            attributes.put(name.toLowerCase(Locale.ROOT), value == null ? "" : value);
        }

        void removeAttribute(String name) {
            if (name == null) return;
            attributes.remove(name.toLowerCase(Locale.ROOT));
        }

        boolean hasAttribute(String name) {
            return name != null && attributes.containsKey(name.toLowerCase(Locale.ROOT));
        }

        String getId() {
            String v = attributes.get("id");
            return v == null ? "" : v;
        }

        String getClassName() {
            String v = attributes.get("class");
            return v == null ? "" : v;
        }

        List<DomNode> getChildElements() {
            List<DomNode> out = new ArrayList<DomNode>();
            for (DomNode c : children) {
                if (c.nodeType == NODE_ELEMENT) out.add(c);
            }
            return out;
        }

        String getTextContent() {
            if (nodeType == NODE_TEXT || nodeType == NODE_COMMENT) {
                return textData == null ? "" : textData;
            }
            StringBuilder sb = new StringBuilder();
            collectTextContent(this, sb);
            return sb.toString();
        }

        private static void collectTextContent(DomNode node, StringBuilder sb) {
            for (DomNode c : node.children) {
                if (c.nodeType == NODE_TEXT) {
                    if (c.textData != null) sb.append(c.textData);
                } else if (c.nodeType == NODE_ELEMENT) {
                    collectTextContent(c, sb);
                }
            }
        }

        void setTextContent(String text) {
            for (DomNode c : children) c.parent = null;
            children.clear();
            if (text != null && !text.isEmpty()) {
                appendChild(new DomNode(NODE_TEXT, "#text", text));
            }
        }

        String getInnerHtml() {
            StringBuilder sb = new StringBuilder();
            for (DomNode c : children) {
                serializeNode(c, sb);
            }
            return sb.toString();
        }

        void setInnerHtml(String html) {
            for (DomNode c : children) c.parent = null;
            children.clear();
            if (html == null || html.isEmpty()) return;
            DomNode fragment = parseFragment(html);
            List<DomNode> copy = new ArrayList<DomNode>(fragment.children);
            for (DomNode c : copy) {
                appendChild(c);
            }
        }

        private static void serializeNode(DomNode node, StringBuilder sb) {
            if (node.nodeType == NODE_TEXT) {
                sb.append(escapeHtml(node.textData == null ? "" : node.textData));
                return;
            }
            if (node.nodeType == NODE_COMMENT) {
                sb.append("<!--").append(node.textData == null ? "" : node.textData).append("-->");
                return;
            }
            if (node.nodeType != NODE_ELEMENT) {
                for (DomNode c : node.children) serializeNode(c, sb);
                return;
            }
            sb.append('<').append(node.tagName);
            for (Map.Entry<String, String> entry : node.attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append("=\"")
                        .append(escapeAttr(entry.getValue())).append('"');
            }
            sb.append('>');
            if (VOID_TAGS.contains(node.tagName)) return;
            for (DomNode c : node.children) {
                if (("script".equals(node.tagName) || "style".equals(node.tagName))
                        && c.nodeType == NODE_TEXT) {
                    sb.append(c.textData == null ? "" : c.textData);
                } else {
                    serializeNode(c, sb);
                }
            }
            sb.append("</").append(node.tagName).append('>');
        }
    }

    final DomNode document;

    private ShirazWebDom(DomNode document) {
        this.document = document;
    }

    static ShirazWebDom parse(String html) {
        DomNode doc = new DomNode(NODE_DOCUMENT, "#document", null);
        if (html != null && !html.isEmpty()) {
            parseInto(html, doc);
        }
        return new ShirazWebDom(doc);
    }

    static DomNode parseFragment(String html) {
        DomNode root = new DomNode(NODE_DOCUMENT, "#fragment", null);
        if (html != null && !html.isEmpty()) {
            parseInto(html, root);
        }
        return root;
    }

    DomNode createElement(String tagName) {
        String norm = tagName == null ? "div" : tagName.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty()) norm = "div";
        return new DomNode(NODE_ELEMENT, norm, null);
    }

    DomNode createTextNode(String text) {
        return new DomNode(NODE_TEXT, "#text", text == null ? "" : text);
    }

    DomNode getDocumentElement() {
        for (DomNode c : document.children) {
            if (c.nodeType == NODE_ELEMENT && "html".equals(c.tagName)) return c;
        }
        return document;
    }

    DomNode getHead() {
        DomNode h = findFirstByTag(document, "head");
        return h != null ? h : document;
    }

    DomNode getBody() {
        DomNode b = findFirstByTag(document, "body");
        return b != null ? b : document;
    }

    String getTitle() {
        DomNode titleEl = findFirstByTag(document, "title");
        if (titleEl == null) return null;
        return titleEl.getTextContent().replaceAll("[\\t\\n\\r\\f ]+", " ").trim();
    }

    void setTitle(String newTitle) {
        DomNode titleEl = findFirstByTag(document, "title");
        if (titleEl == null) {
            titleEl = createElement("title");
            getHead().appendChild(titleEl);
        }
        titleEl.setTextContent(newTitle == null ? "" : newTitle);
    }

    List<String> collectInlineScripts() {
        List<String> scripts = new ArrayList<String>();
        collectScriptsRecursive(document, scripts);
        return scripts;
    }

    private static void collectScriptsRecursive(DomNode node, List<String> out) {
        for (DomNode c : node.children) {
            if (c.nodeType == NODE_ELEMENT) {
                if ("script".equals(c.tagName)) {
                    if (!c.hasAttribute("src")) {
                        String code = c.getTextContent();
                        if (!code.trim().isEmpty()) out.add(code);
                    }
                } else {
                    collectScriptsRecursive(c, out);
                }
            }
        }
    }

    DomNode getElementById(String id) {
        if (id == null || id.isEmpty()) return null;
        return findById(document, id);
    }

    DomNode findPrimaryEditableNode() {
        return findFirstEditable(getBody());
    }

    private static DomNode findFirstEditable(DomNode node) {
        if (node == null) return null;
        if (node.isEditableControl()) return node;
        for (DomNode c : node.children) {
            if (c.nodeType == NODE_ELEMENT) {
                DomNode found = findFirstEditable(c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static DomNode findById(DomNode node, String id) {
        for (DomNode c : node.children) {
            if (c.nodeType == NODE_ELEMENT) {
                if (id.equals(c.getId())) return c;
                DomNode nested = findById(c, id);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    List<DomNode> getElementsByTagName(DomNode root, String tag) {
        List<DomNode> out = new ArrayList<DomNode>();
        if (root == null || tag == null) return out;
        String target = tag.trim().toLowerCase(Locale.ROOT);
        boolean wildcard = "*".equals(target);
        collectByTag(root, target, wildcard, out);
        return out;
    }

    private static void collectByTag(DomNode node, String target, boolean wildcard, List<DomNode> out) {
        for (DomNode c : node.children) {
            if (c.nodeType == NODE_ELEMENT) {
                if (wildcard || target.equals(c.tagName)) out.add(c);
                collectByTag(c, target, wildcard, out);
            }
        }
    }

    List<DomNode> getElementsByClassName(DomNode root, String classNames) {
        List<DomNode> out = new ArrayList<DomNode>();
        if (root == null || classNames == null || classNames.trim().isEmpty()) return out;
        String[] required = classNames.trim().split("\\s+");
        collectByClass(root, required, out);
        return out;
    }

    private static void collectByClass(DomNode node, String[] required, List<DomNode> out) {
        for (DomNode c : node.children) {
            if (c.nodeType == NODE_ELEMENT) {
                if (hasAllClasses(c, required)) out.add(c);
                collectByClass(c, required, out);
            }
        }
    }

    DomNode querySelector(DomNode root, String selector) {
        List<DomNode> all = querySelectorAll(root, selector);
        return all.isEmpty() ? null : all.get(0);
    }

    List<DomNode> querySelectorAll(DomNode root, String selector) {
        List<DomNode> out = new ArrayList<DomNode>();
        if (root == null || selector == null || selector.trim().isEmpty()) return out;
        String[] groups = selector.split(",");
        List<DomNode> allElements = getElementsByTagName(root, "*");
        for (DomNode candidate : allElements) {
            for (String group : groups) {
                String trimmed = group.trim();
                if (!trimmed.isEmpty() && matchesSelectorChain(candidate, root, trimmed)) {
                    out.add(candidate);
                    break;
                }
            }
        }
        return out;
    }

    private static boolean matchesSelectorChain(DomNode node, DomNode scopeRoot, String chain) {
        // Tokenize combinators ('>' and whitespace)
        List<String> parts = new ArrayList<String>();
        List<Boolean> directChild = new ArrayList<Boolean>();
        int i = 0;
        int len = chain.length();
        boolean nextDirect = false;
        while (i < len) {
            while (i < len && Character.isWhitespace(chain.charAt(i))) i++;
            if (i >= len) break;
            if (chain.charAt(i) == '>') {
                nextDirect = true;
                i++;
                continue;
            }
            int start = i;
            boolean inBracket = false;
            while (i < len) {
                char ch = chain.charAt(i);
                if (ch == '[') inBracket = true;
                else if (ch == ']') inBracket = false;
                else if (!inBracket && (Character.isWhitespace(ch) || ch == '>')) break;
                i++;
            }
            if (i > start) {
                parts.add(chain.substring(start, i));
                directChild.add(nextDirect);
                nextDirect = false;
            }
        }
        if (parts.isEmpty()) return false;
        return matchChainRecursive(node, scopeRoot, parts, directChild, parts.size() - 1);
    }

    private static boolean matchChainRecursive(
            DomNode node, DomNode scopeRoot, List<String> parts, List<Boolean> directChild, int idx) {
        if (node == null || node == scopeRoot || node.nodeType != NODE_ELEMENT) return false;
        if (!matchesSimpleSelector(node, parts.get(idx))) return false;
        if (idx == 0) return true;
        boolean mustBeDirect = directChild.get(idx);
        if (mustBeDirect) {
            return matchChainRecursive(node.parent, scopeRoot, parts, directChild, idx - 1);
        }
        DomNode ancestor = node.parent;
        while (ancestor != null && ancestor != scopeRoot) {
            if (matchChainRecursive(ancestor, scopeRoot, parts, directChild, idx - 1)) return true;
            ancestor = ancestor.parent;
        }
        return false;
    }

    private static boolean matchesSimpleSelector(DomNode node, String simple) {
        int i = 0;
        int len = simple.length();
        while (i < len) {
            char ch = simple.charAt(i);
            if (ch == '#') {
                int start = ++i;
                while (i < len && !isSelectorDelimiter(simple.charAt(i))) i++;
                String id = simple.substring(start, i);
                if (!id.equals(node.getId())) return false;
            } else if (ch == '.') {
                int start = ++i;
                while (i < len && !isSelectorDelimiter(simple.charAt(i))) i++;
                String cls = simple.substring(start, i);
                if (!hasAllClasses(node, new String[] { cls })) return false;
            } else if (ch == '[') {
                int close = simple.indexOf(']', i + 1);
                if (close < 0) return false;
                String expr = simple.substring(i + 1, close).trim();
                int eq = expr.indexOf('=');
                if (eq < 0) {
                    if (!node.hasAttribute(expr)) return false;
                } else {
                    String k = expr.substring(0, eq).trim();
                    String v = expr.substring(eq + 1).trim();
                    if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                            || (v.startsWith("'") && v.endsWith("'")))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    String actual = node.getAttribute(k);
                    if (actual == null || !actual.equals(v)) return false;
                }
                i = close + 1;
            } else if (ch == '*') {
                i++;
            } else {
                int start = i;
                while (i < len && !isSelectorDelimiter(simple.charAt(i))) i++;
                String tag = simple.substring(start, i).toLowerCase(Locale.ROOT);
                if (!tag.equals(node.tagName)) return false;
            }
        }
        return true;
    }

    private static boolean isSelectorDelimiter(char ch) {
        return ch == '#' || ch == '.' || ch == '[' || ch == ']';
    }

    private static boolean hasAllClasses(DomNode node, String[] required) {
        String cls = node.getClassName();
        if (cls.isEmpty()) return false;
        String[] actual = cls.trim().split("\\s+");
        for (String req : required) {
            boolean found = false;
            for (String a : actual) {
                if (a.equals(req)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static DomNode findFirstByTag(DomNode root, String tag) {
        for (DomNode c : root.children) {
            if (c.nodeType == NODE_ELEMENT) {
                if (tag.equals(c.tagName)) return c;
                DomNode nested = findFirstByTag(c, tag);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    String toRenderedText() {
        StringBuilder out = new StringBuilder();
        renderNode(getBody(), out);
        String text = out.toString().replace('\u00a0', ' ').trim();
        if (text.length() > 32000) {
            text = text.substring(0, 32000) + "\n\u2026";
        }
        return text;
    }

    int countTextMatches(String query) {
        if (query == null || query.isEmpty()) return 0;
        String hay = toRenderedText().toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int count = 0;
        int idx = 0;
        while ((idx = hay.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static void renderNode(DomNode node, StringBuilder out) {
        if (out.length() > 32000) return;
        if (node.nodeType == NODE_TEXT) {
            String t = node.textData == null ? "" : node.textData;
            String collapsed = t.replaceAll("[\\t\\n\\r\\f ]+", " ");
            if (!collapsed.isEmpty()) {
                if (collapsed.equals(" ") && (out.length() == 0
                        || out.charAt(out.length() - 1) == '\n'
                        || out.charAt(out.length() - 1) == ' ')) {
                    return;
                }
                out.append(collapsed);
            }
            return;
        }
        if (node.nodeType == NODE_COMMENT) return;
        if (node.nodeType == NODE_ELEMENT) {
            String tag = node.tagName;
            if ("script".equals(tag) || "style".equals(tag) || "head".equals(tag)
                    || "title".equals(tag) || "noscript".equals(tag)) {
                return;
            }
            String style = node.getAttribute("style");
            if (style != null && style.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                    .contains("display:none")) {
                return;
            }
            if ("br".equals(tag)) {
                out.append('\n');
                return;
            }
            if ("hr".equals(tag)) {
                ensureNewline(out);
                out.append("────────\n");
                return;
            }
            if ("input".equals(tag)) {
                String val = node.getAttribute("value");
                if (val == null) val = node.getAttribute("placeholder");
                if (val != null && !val.isEmpty()) out.append('[').append(val).append(']');
                return;
            }
            if ("textarea".equals(tag)) {
                ensureNewline(out);
                String val = node.getEditableValue();
                if (!val.isEmpty()) out.append(val);
                ensureNewline(out);
                return;
            }
            boolean isBlock = BLOCK_TAGS.contains(tag);
            if (isBlock) ensureNewline(out);
            if ("li".equals(tag)) out.append("\u2022 ");
            for (DomNode c : node.children) renderNode(c, out);
            if (isBlock) ensureNewline(out);
            return;
        }
        for (DomNode c : node.children) renderNode(c, out);
    }

    private static void ensureNewline(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
    }

    private static void parseInto(String html, DomNode root) {
        List<DomNode> stack = new ArrayList<DomNode>();
        stack.add(root);
        int i = 0;
        int len = html.length();
        while (i < len) {
            int lt = html.indexOf('<', i);
            if (lt < 0) {
                appendText(stack.get(stack.size() - 1), decodeEntities(html.substring(i)));
                break;
            }
            if (lt > i) {
                appendText(stack.get(stack.size() - 1), decodeEntities(html.substring(i, lt)));
            }
            // HTML comment
            if (html.startsWith("<!--", lt)) {
                int end = html.indexOf("-->", lt + 4);
                if (end < 0) {
                    stack.get(stack.size() - 1).appendChild(
                            new DomNode(NODE_COMMENT, "#comment", html.substring(lt + 4)));
                    break;
                }
                stack.get(stack.size() - 1).appendChild(
                        new DomNode(NODE_COMMENT, "#comment", html.substring(lt + 4, end)));
                i = end + 3;
                continue;
            }
            // DOCTYPE / processing instruction
            if (lt + 1 < len && (html.charAt(lt + 1) == '!' || html.charAt(lt + 1) == '?')) {
                int gt = html.indexOf('>', lt + 2);
                i = gt < 0 ? len : gt + 1;
                continue;
            }
            int gt = findTagClose(html, lt + 1);
            if (gt < 0) {
                appendText(stack.get(stack.size() - 1), decodeEntities(html.substring(lt)));
                break;
            }
            String raw = html.substring(lt + 1, gt).trim();
            i = gt + 1;
            if (raw.isEmpty()) continue;

            if (raw.charAt(0) == '/') {
                String closeTag = extractTagName(raw.substring(1).trim());
                for (int s = stack.size() - 1; s >= 1; s--) {
                    if (stack.get(s).tagName.equals(closeTag)) {
                        while (stack.size() > s) stack.remove(stack.size() - 1);
                        break;
                    }
                }
                continue;
            }

            boolean selfClosing = raw.endsWith("/");
            if (selfClosing) raw = raw.substring(0, raw.length() - 1).trim();
            int nameEnd = 0;
            while (nameEnd < raw.length() && !Character.isWhitespace(raw.charAt(nameEnd))) {
                nameEnd++;
            }
            String tag = raw.substring(0, nameEnd).toLowerCase(Locale.ROOT);
            if (tag.isEmpty()) continue;
            DomNode el = new DomNode(NODE_ELEMENT, tag, null);
            if (nameEnd < raw.length()) {
                parseAttributes(raw.substring(nameEnd), el);
            }
            stack.get(stack.size() - 1).appendChild(el);

            if ("script".equals(tag) || "style".equals(tag)) {
                if (!selfClosing) {
                    int closeStart = findRawCloseTag(html, i, tag);
                    if (closeStart < 0) {
                        el.appendChild(new DomNode(NODE_TEXT, "#text", html.substring(i)));
                        i = len;
                    } else {
                        if (closeStart > i) {
                            el.appendChild(new DomNode(NODE_TEXT, "#text", html.substring(i, closeStart)));
                        }
                        int closeGt = html.indexOf('>', closeStart + 2);
                        i = closeGt < 0 ? len : closeGt + 1;
                    }
                }
                continue;
            }

            if (!selfClosing && !VOID_TAGS.contains(tag)) {
                stack.add(el);
            }
        }
    }

    private static int findTagClose(String html, int from) {
        char quote = 0;
        for (int i = from; i < html.length(); i++) {
            char c = html.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static int findRawCloseTag(String html, int from, String tag) {
        String lower = html.toLowerCase(Locale.ROOT);
        String needle = "</" + tag;
        int idx = from;
        while (idx < lower.length()) {
            int found = lower.indexOf(needle, idx);
            if (found < 0) return -1;
            int after = found + needle.length();
            if (after >= lower.length()) return found;
            char next = lower.charAt(after);
            if (next == '>' || Character.isWhitespace(next)) return found;
            idx = after;
        }
        return -1;
    }

    private static String extractTagName(String text) {
        int i = 0;
        while (i < text.length() && !Character.isWhitespace(text.charAt(i))) i++;
        return text.substring(0, i).toLowerCase(Locale.ROOT);
    }

    private static void parseAttributes(String attrText, DomNode el) {
        int i = 0;
        int len = attrText.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(attrText.charAt(i))) i++;
            if (i >= len) break;
            int nameStart = i;
            while (i < len && !Character.isWhitespace(attrText.charAt(i)) && attrText.charAt(i) != '=') {
                i++;
            }
            String name = attrText.substring(nameStart, i).toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                i++;
                continue;
            }
            while (i < len && Character.isWhitespace(attrText.charAt(i))) i++;
            String value = "";
            if (i < len && attrText.charAt(i) == '=') {
                i++;
                while (i < len && Character.isWhitespace(attrText.charAt(i))) i++;
                if (i < len && (attrText.charAt(i) == '"' || attrText.charAt(i) == '\'')) {
                    char q = attrText.charAt(i++);
                    int valStart = i;
                    while (i < len && attrText.charAt(i) != q) i++;
                    value = decodeEntities(attrText.substring(valStart, i));
                    if (i < len) i++;
                } else {
                    int valStart = i;
                    while (i < len && !Character.isWhitespace(attrText.charAt(i))) i++;
                    value = decodeEntities(attrText.substring(valStart, i));
                }
            }
            el.setAttribute(name, value);
        }
    }

    private static void appendText(DomNode parent, String text) {
        if (text == null || text.isEmpty()) return;
        parent.appendChild(new DomNode(NODE_TEXT, "#text", text));
    }

    static String decodeEntities(String input) {
        if (input == null || input.indexOf('&') < 0) return input == null ? "" : input;
        StringBuilder out = new StringBuilder(input.length());
        int i = 0;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (c == '&') {
                int semi = input.indexOf(';', i + 1);
                if (semi > i + 1 && semi - i <= 16) {
                    String name = input.substring(i + 1, semi);
                    String decoded = decodeEntity(name);
                    if (decoded != null) {
                        out.append(decoded);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static String decodeEntity(String name) {
        if ("amp".equals(name) || "#38".equals(name)) return "&";
        if ("lt".equals(name) || "#60".equals(name)) return "<";
        if ("gt".equals(name) || "#62".equals(name)) return ">";
        if ("quot".equals(name) || "#34".equals(name)) return "\"";
        if ("apos".equals(name) || "#39".equals(name)) return "'";
        if ("nbsp".equals(name) || "#160".equals(name)) return " ";
        if (name.startsWith("#x") || name.startsWith("#X")) {
            try {
                return new String(Character.toChars(Integer.parseInt(name.substring(2), 16)));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (name.startsWith("#")) {
            try {
                return new String(Character.toChars(Integer.parseInt(name.substring(1))));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeAttr(String text) {
        return escapeHtml(text).replace("\"", "&quot;");
    }
}
