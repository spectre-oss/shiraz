// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Set;

/** Read-only key-value store exposed to an {@link IsolatedService}. */
public interface KeyValueStore {
    byte[] get(String key);

    Set<String> keySet();
}
