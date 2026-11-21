// SPDX-License-Identifier: GPL-3.0-only
package android.content;

/** Callback notified when sync status changes. */
public interface SyncStatusObserver {
    void onStatusChanged(int which);
}
