// SPDX-License-Identifier: GPL-3.0-only
package android.os.storage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import shiraz.runtime.HostStorage;

/** Reports Shiraz's existing app-scoped primary volume; no host disk enumeration. */
public class StorageManager {
    /** Android API identifier for default internal storage, not a device identity. */
    public static final java.util.UUID UUID_DEFAULT=java.util.UUID.fromString("41217664-9172-527a-b3d5-edabb50a7d69");
    private final HostStorage storage;
    private final StorageVolume primary;
    private StorageManager(HostStorage storage) {
        if(storage==null)throw new NullPointerException("storage");
        this.storage=storage;primary=new StorageVolume(storage);
    }
    public static StorageManager shirazForStorage(HostStorage storage) { return new StorageManager(storage); }
    public StorageVolume getPrimaryStorageVolume() { return primary; }
    public List<StorageVolume> getStorageVolumes() {
        ArrayList<StorageVolume> result=new ArrayList<StorageVolume>(1);result.add(primary);return result;
    }
    public StorageVolume getStorageVolume(File file) { return storage.onPrimary(file)?primary:null; }
}
