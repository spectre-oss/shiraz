// SPDX-License-Identifier: GPL-3.0-only
package android.app.usage;

import android.os.storage.StorageManager;
import java.io.IOException;
import java.io.File;
import java.util.Objects;
import java.util.UUID;
import shiraz.runtime.HostStorage;

/** Statistics for the single host-backed virtual storage volume.
 * No reclaimable-cache pool or per-UID filesystem quota is configured.
 */
public final class StorageStatsManager {
    private final HostStorage storage;
    private StorageStatsManager(HostStorage storage){this.storage=Objects.requireNonNull(storage);}
    public static StorageStatsManager shirazForStorage(HostStorage storage){return new StorageStatsManager(storage);}
    private File volume(UUID uuid) throws IOException {
        Objects.requireNonNull(uuid,"storageUuid");
        if(!StorageManager.UUID_DEFAULT.equals(uuid))throw new IOException("Unknown storage volume");
        File path=storage.data();
        if(!path.isDirectory()||path.getTotalSpace()<=0)throw new IOException("Storage volume unavailable");
        return path;
    }
    public boolean isQuotaSupported(UUID uuid) throws IOException {volume(uuid);return false;}
    public long getTotalBytes(UUID uuid) throws IOException {return volume(uuid).getTotalSpace();}
    public long getFreeBytes(UUID uuid) throws IOException {return volume(uuid).getUsableSpace();}
}
