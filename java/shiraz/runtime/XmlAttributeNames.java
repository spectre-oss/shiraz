// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/** Reuse immutable allowlists without retaining resource nodes or theme state. */
public final class XmlAttributeNames {
    private XmlAttributeNames() {}
    private static final LinkedHashMap<String,Set<String>> CACHE =
        new LinkedHashMap<String,Set<String>>(16, .75f, true);

    public static Set<String> compile(String names) {
        if (names == null) throw new NullPointerException("attribute names");
        if (names.length() > 2048) throw new IllegalArgumentException("Attribute allowlist limit");
        synchronized (CACHE) {
            Set<String> result = CACHE.get(names);
            if (result != null) return result;
            HashSet<String> parsed = new HashSet<String>();
            for (String name : names.split(" ")) if (!name.isEmpty()) parsed.add(name);
            result = Collections.unmodifiableSet(parsed);
            if (CACHE.size() >= 64) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(names, result);
            return result;
        }
    }
}
