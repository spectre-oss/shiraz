// SPDX-License-Identifier: GPL-3.0-only
package android.sax;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;

/** Declarative XML element node used with {@link RootElement} for streaming SAX parsing. */
public class Element {
    final String uri;
    final String localName;
    final int depth;
    final Element parent;
    private Map<String, Element> children;
    private ArrayList<Element> requiredChildren;
    boolean visited;
    StartElementListener startElementListener;
    EndElementListener endElementListener;
    EndTextElementListener endTextElementListener;

    Element(Element parent, String uri, String localName, int depth) {
        if (uri == null) throw new NullPointerException("uri == null");
        if (localName == null) throw new NullPointerException("localName == null");
        this.parent = parent;
        this.uri = uri;
        this.localName = localName;
        this.depth = depth;
    }

    Element() {
        this(null, "", "", 0);
    }

    public Element getChild(String localName) {
        return getChild("", localName);
    }

    public Element getChild(String uri, String localName) {
        if (endTextElementListener != null) {
            throw new IllegalStateException(
                    "This element already has an end text element listener. It cannot have children.");
        }
        if (uri == null) throw new NullPointerException("uri == null");
        if (localName == null) throw new NullPointerException("localName == null");
        if (children == null) {
            children = new LinkedHashMap<String, Element>();
        }
        String key = childKey(uri, localName);
        Element child = children.get(key);
        if (child == null) {
            child = new Element(this, uri, localName, depth + 1);
            children.put(key, child);
        }
        return child;
    }

    public Element requireChild(String localName) {
        return requireChild("", localName);
    }

    public Element requireChild(String uri, String localName) {
        Element child = getChild(uri, localName);
        if (requiredChildren == null) {
            requiredChildren = new ArrayList<Element>();
            requiredChildren.add(child);
        } else if (!requiredChildren.contains(child)) {
            requiredChildren.add(child);
        }
        return child;
    }

    public void setElementListener(ElementListener elementListener) {
        setStartElementListener(elementListener);
        setEndElementListener(elementListener);
    }

    public void setTextElementListener(TextElementListener elementListener) {
        setStartElementListener(elementListener);
        setEndTextElementListener(elementListener);
    }

    public void setStartElementListener(StartElementListener startElementListener) {
        if (this.startElementListener != null) {
            throw new IllegalStateException("Start element listener has already been set.");
        }
        this.startElementListener = startElementListener;
    }

    public void setEndElementListener(EndElementListener endElementListener) {
        if (this.endElementListener != null) {
            throw new IllegalStateException("End element listener has already been set.");
        }
        this.endElementListener = endElementListener;
    }

    public void setEndTextElementListener(EndTextElementListener endTextElementListener) {
        if (this.endTextElementListener != null) {
            throw new IllegalStateException("End text element listener has already been set.");
        }
        if (children != null) {
            throw new IllegalStateException(
                    "This element already has children. It cannot have an end text element listener.");
        }
        this.endTextElementListener = endTextElementListener;
    }

    Element lookupChild(String uri, String localName) {
        return children == null ? null : children.get(childKey(uri, localName));
    }

    void resetRequiredChildren() {
        if (requiredChildren != null) {
            for (int i = 0; i < requiredChildren.size(); i++) {
                requiredChildren.get(i).visited = false;
            }
        }
    }

    void checkRequiredChildren(Locator locator) throws SAXParseException {
        if (requiredChildren != null) {
            for (int i = 0; i < requiredChildren.size(); i++) {
                Element child = requiredChildren.get(i);
                if (!child.visited) {
                    throw new SAXParseException(
                            "Element named " + this + " is missing required child element named " + child + ".",
                            locator);
                }
            }
        }
    }

    private static String childKey(String uri, String localName) {
        return uri.isEmpty() ? localName : uri + "\u0000" + localName;
    }

    @Override
    public String toString() {
        return "'" + (uri.isEmpty() ? localName : uri + ":" + localName) + "'";
    }
}
