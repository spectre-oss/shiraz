// SPDX-License-Identifier: GPL-3.0-only
package android.net;

/** Android local-socket name and namespace. */
public class LocalSocketAddress {
    public enum Namespace { ABSTRACT, RESERVED, FILESYSTEM }
    private final String name;
    private final Namespace namespace;
    public LocalSocketAddress(String name) { this(name, Namespace.ABSTRACT); }
    public LocalSocketAddress(String name, Namespace namespace) {
        if (name == null || namespace == null) throw new NullPointerException("local socket address");
        if (name.isEmpty() || name.indexOf('\0') >= 0) throw new IllegalArgumentException("Invalid local socket name");
        this.name = name; this.namespace = namespace;
    }
    public String getName() { return name; }
    public Namespace getNamespace() { return namespace; }
}
