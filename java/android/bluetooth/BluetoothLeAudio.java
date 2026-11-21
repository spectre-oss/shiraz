// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.util.Collections;
import java.util.List;

public final class BluetoothLeAudio implements BluetoothProfile, AutoCloseable {
    public static final String ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED = "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED";
    public static final int GROUP_ID_INVALID = -1;

    BluetoothLeAudio() {}

    public void close() {
    }

    protected void finalize() {
        close();
    }

    public BluetoothDevice getConnectedGroupLeadDevice(int groupId) {
        return null;
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

    public int getGroupId(BluetoothDevice device) {
        return GROUP_ID_INVALID;
    }
}
