// SPDX-License-Identifier: GPL-3.0-only
package android.sax;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Root XML element that supplies a streaming {@link ContentHandler} for {@link org.xml.sax.XMLReader}. */
public class RootElement extends Element {
    private final Handler handler = new Handler();

    public RootElement(String uri, String localName) {
        super(null, uri, localName, 0);
    }

    public RootElement(String localName) {
        this("", localName);
    }

    public ContentHandler getContentHandler() {
        return handler;
    }

    private final class Handler extends DefaultHandler {
        private Locator locator;
        private int depth = -1;
        private Element current;
        private StringBuilder bodyBuilder;

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startDocument() {
            depth = -1;
            current = null;
            bodyBuilder = null;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            int nextDepth = ++depth;
            String resolvedUri = uri == null ? "" : uri;
            String resolvedName = (localName == null || localName.isEmpty()) ? qName : localName;
            if (resolvedName == null) resolvedName = "";

            if (nextDepth == 0) {
                if (!RootElement.this.uri.equals(resolvedUri)
                        || !RootElement.this.localName.equals(resolvedName)) {
                    throw new SAXParseException(
                            "Root element name does not match. Expected: "
                                    + RootElement.this
                                    + ", Got: '"
                                    + (resolvedUri.isEmpty() ? resolvedName : resolvedUri + ":" + resolvedName)
                                    + "'",
                            locator);
                }
                enter(RootElement.this, attributes);
                return;
            }

            if (bodyBuilder != null) {
                throw new SAXParseException(
                        "Encountered mixed content within text element named " + current + ".",
                        locator);
            }

            if (current != null && nextDepth == current.depth + 1) {
                Element child = current.lookupChild(resolvedUri, resolvedName);
                if (child != null) {
                    enter(child, attributes);
                }
            }
        }

        private void enter(Element element, Attributes attributes) {
            current = element;
            element.visited = true;
            element.resetRequiredChildren();
            if (element.startElementListener != null) {
                element.startElementListener.start(attributes);
            }
            if (element.endTextElementListener != null) {
                bodyBuilder = new StringBuilder();
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (bodyBuilder != null) {
                bodyBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            Element active = current;
            if (active != null && depth == active.depth) {
                active.checkRequiredChildren(locator);
                if (active.endElementListener != null) {
                    active.endElementListener.end();
                }
                if (bodyBuilder != null) {
                    String body = bodyBuilder.toString();
                    bodyBuilder = null;
                    active.endTextElementListener.end(body);
                }
                current = active.parent;
            }
            depth--;
        }
    }
}
