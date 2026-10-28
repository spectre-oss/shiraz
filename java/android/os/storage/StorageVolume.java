// SPDX-License-Identifier: GPL-3.0-only
package android.os.storage;

import android.content.Context;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.File;
import shiraz.runtime.HostStorage;

/** The app's emulated primary volume, backed by its actual Linux data directory. */
public final class StorageVolume implements Parcelable {
    private final HostStorage storage;
    private final File path;
    StorageVolume(HostStorage storage) { this.storage=storage;path=storage.primaryPath(null); }
    public String getDescription(Context context) { return "App storage"; }
    public boolean isPrimary() { return true; }
    public boolean isEmulated() { return true; }
    public boolean isRemovable() { return false; }
    public String getUuid() { return null; }
    public String getState() { return Environment.shirazStorageState(storage); }
    public File getDirectory() {
        String state=getState();return Environment.MEDIA_MOUNTED.equals(state)||Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)?path:null;
    }
    public String getPath() { return path.getPath(); }
    public File getPathFile() { return path; }
    @Override public boolean equals(Object other) { return other instanceof StorageVolume&&path.equals(((StorageVolume)other).path); }
    @Override public int hashCode() { return path.hashCode(); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination,int flags) { throw new UnsupportedOperationException("StorageVolume parcel transport is not implemented"); }
}
