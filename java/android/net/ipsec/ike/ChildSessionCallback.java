// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.net.IpSecTransform;
import android.net.ipsec.ike.exceptions.IkeException;

public interface ChildSessionCallback {
    void onOpened(ChildSessionConfiguration sessionConfig);

    void onClosed();

    default void onClosedWithException(IkeException exception) {
        onClosed();
    }

    void onIpSecTransformCreated(IpSecTransform ipSecTransform, int direction);

    void onIpSecTransformDeleted(IpSecTransform ipSecTransform, int direction);
}
