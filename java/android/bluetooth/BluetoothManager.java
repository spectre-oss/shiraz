// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.content.Context;
import java.util.Collections;
import java.util.List;

public final class BluetoothManager {
    private final BluetoothAdapter mAdapter;

    BluetoothManager() {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public BluetoothAdapter getAdapter() {
        return mAdapter;
    }

    public int getConnectionState(BluetoothDevice device, int profile) {
        if (profile != BluetoothProfile.GATT && profile != BluetoothProfile.GATT_SERVER) {
            throw new IllegalArgumentException("Profile not supported: " + profile);
        }
        return BluetoothProfile.STATE_DISCONNECTED;
    }

    public List<BluetoothDevice> getConnectedDevices(int profile) {
        if (profile != BluetoothProfile.GATT && profile != BluetoothProfile.GATT_SERVER) {
            throw new IllegalArgumentException("Profile not supported: " + profile);
        }
        return Collections.emptyList();
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int profile, int[] states) {
        if (profile != BluetoothProfile.GATT && profile != BluetoothProfile.GATT_SERVER) {
            throw new IllegalArgumentException("Profile not supported: " + profile);
        }
        return Collections.emptyList();
    }

    public BluetoothGattServer openGattServer(Context context, BluetoothGattServerCallback callback) {
        if (context == null || callback == null) {
            throw new IllegalArgumentException("null parameter: " + context + " " + callback);
        }
        return new BluetoothGattServer(callback);
    }
}
