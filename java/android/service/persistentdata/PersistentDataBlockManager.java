// SPDX-License-Identifier: GPL-3.0-only
package android.service.persistentdata;

/** API surface for an optional device service. The desktop host has no FRP
 * partition and returns null from Context.getSystemService for this type.
 * No instance or successful hardware operation is provided without a backend.
 */
public final class PersistentDataBlockManager {
    public static final int FLASH_LOCK_UNKNOWN = -1, FLASH_LOCK_UNLOCKED = 0, FLASH_LOCK_LOCKED = 1;
    private PersistentDataBlockManager() {}
    private UnsupportedOperationException unavailable() {
        return new UnsupportedOperationException("No persistent data block device backend");
    }
    public int write(byte[] data) { throw unavailable(); }
    public byte[] read() { throw unavailable(); }
    public int getDataBlockSize() { throw unavailable(); }
    public long getMaximumDataBlockSize() { throw unavailable(); }
    public void wipe() { throw unavailable(); }
    public void setOemUnlockEnabled(boolean enabled) { throw unavailable(); }
    public boolean getOemUnlockEnabled() { throw unavailable(); }
    public int getFlashLockState() { throw unavailable(); }
}
