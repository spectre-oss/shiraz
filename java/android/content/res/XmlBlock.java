// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;

import java.util.Arrays;
import shiraz.runtime.CompiledXmlParser;

/** Owned binary XML document; each parser has its own cursor and lifetime. */
final class XmlBlock implements AutoCloseable {
    private CompiledXmlParser document;

    public XmlBlock(byte[] data) {
        this(data, 0, data.length);
    }

    public XmlBlock(byte[] data, int offset, int size) {
        this(data, offset, size, 0);
    }

    XmlBlock(byte[] data, int resource) {
        this(data, 0, data.length, resource);
    }

    private XmlBlock(byte[] data, int offset, int size, int resource) {
        if (data == null) throw new NullPointerException("compiled XML");
        if (offset < 0 || size < 0 || offset > data.length - size) {
            throw new IndexOutOfBoundsException("compiled XML slice");
        }
        if (size > 4 * 1024 * 1024) throw new IllegalArgumentException("Compiled XML length");
        document = new CompiledXmlParser(Arrays.copyOfRange(data, offset, offset + size), resource);
    }

    public synchronized XmlResourceParser newParser() {
        return document == null ? null : document.shirazFork();
    }

    @Override public synchronized void close() {
        if (document != null) {
            document.close();
            document = null;
        }
    }
}
