// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.net.ipsec.ike.exceptions.IkeException;

public interface IkeSessionCallback {
    void onOpened(IkeSessionConfiguration sessionConfiguration);

    void onClosed();

    default void onClosedWithException(IkeException exception) {
        onClosed();
    }

    default void onError(IkeException exception) {}
}
