// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.ParcelFileDescriptor;
import java.util.Collections;
import java.util.List;

public final class BluetoothHealth implements BluetoothProfile {
    public static final int SOURCE_ROLE = 1;
    public static final int SINK_ROLE = 2;

    public static final int CHANNEL_TYPE_RELIABLE = 10;
    public static final int CHANNEL_TYPE_STREAMING = 11;

    public static final int STATE_CHANNEL_DISCONNECTED = 0;
    public static final int STATE_CHANNEL_CONNECTING = 1;
    public static final int STATE_CHANNEL_CONNECTED = 2;
    public static final int STATE_CHANNEL_DISCONNECTING = 3;

    public static final int APP_CONFIG_REGISTRATION_SUCCESS = 0;
    public static final int APP_CONFIG_REGISTRATION_FAILURE = 1;
    public static final int APP_CONFIG_UNREGISTRATION_SUCCESS = 2;
    public static final int APP_CONFIG_UNREGISTRATION_FAILURE = 3;

    BluetoothHealth() {}

    public boolean registerSinkAppConfiguration(String name, int dataType, BluetoothHealthCallback callback) {
        return false;
    }

    public boolean unregisterAppConfiguration(BluetoothHealthAppConfiguration config) {
        return false;
    }

    public boolean connectChannelToSource(BluetoothDevice device, BluetoothHealthAppConfiguration config) {
        return false;
    }

    public boolean disconnectChannel(BluetoothDevice device, BluetoothHealthAppConfiguration config, int channelId) {
        return false;
    }

    public ParcelFileDescriptor getMainChannelFd(BluetoothDevice device, BluetoothHealthAppConfiguration config) {
        return null;
    }

    public int getConnectionState(BluetoothDevice device) {
        return STATE_DISCONNECTED;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return Collections.emptyList();
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }
}
