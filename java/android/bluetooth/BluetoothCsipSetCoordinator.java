// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.util.Collections;
import java.util.List;

public final class BluetoothCsipSetCoordinator implements BluetoothProfile, AutoCloseable {
    public static final String ACTION_CSIS_CONNECTION_STATE_CHANGED = "android.bluetooth.action.CSIS_CONNECTION_STATE_CHANGED";

    BluetoothCsipSetCoordinator() {}

    protected void finalize() {
        close();
    }

    public void close() {
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return Collections.emptyList();
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }

    public int getConnectionState(BluetoothDevice device) {
        return STATE_DISCONNECTED;
    }
}
