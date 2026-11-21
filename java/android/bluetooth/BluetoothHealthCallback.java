// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.ParcelFileDescriptor;

public abstract class BluetoothHealthCallback {
    public BluetoothHealthCallback() {}

    public void onHealthAppConfigurationStatusChange(BluetoothHealthAppConfiguration config, int status) {}

    public void onHealthChannelStateChange(BluetoothHealthAppConfiguration config, BluetoothDevice device, int prevState, int newState, ParcelFileDescriptor fd, int channelId) {}
}
