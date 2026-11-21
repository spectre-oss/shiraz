// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.util.Collections;
import java.util.List;

public final class BluetoothHearingAid implements BluetoothProfile {
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED";

    BluetoothHearingAid() {}

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
