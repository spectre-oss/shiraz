// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

/** Mutable key-value store exposed to an {@link IsolatedService} for local data. */
public interface MutableKeyValueStore extends KeyValueStore {
    byte[] put(String key, byte[] value);

    byte[] remove(String key);
}
