// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class BluetoothA2dp implements BluetoothProfile {
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED";
    public static final String ACTION_PLAYING_STATE_CHANGED = "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED";
    public static final int STATE_PLAYING = 10;
    public static final int STATE_NOT_PLAYING = 11;

    BluetoothA2dp() {}

    public void finalize() {
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

    public boolean isA2dpPlaying(BluetoothDevice device) {
        return false;
    }

    public Collection<BluetoothCodecType> getSupportedCodecTypes() {
        return Collections.emptyList();
    }
}
