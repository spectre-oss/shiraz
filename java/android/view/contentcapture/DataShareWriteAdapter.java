// SPDX-License-Identifier: GPL-3.0-only

package android.view.contentcapture;

import android.os.ParcelFileDescriptor;

public interface DataShareWriteAdapter {
    void onWrite(ParcelFileDescriptor destination);

    void onRejected();

    default void onError(int errorCode) {
    }
}
