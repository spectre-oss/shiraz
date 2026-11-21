// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class BluetoothHidDevice implements BluetoothProfile {
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.hiddevice.profile.action.CONNECTION_STATE_CHANGED";

    public static final byte SUBCLASS1_NONE = 0x00;
    public static final byte SUBCLASS1_KEYBOARD = 0x40;
    public static final byte SUBCLASS1_MOUSE = (byte) 0x80;
    public static final byte SUBCLASS1_COMBO = (byte) 0xC0;

    public static final byte SUBCLASS2_UNCATEGORIZED = 0x00;
    public static final byte SUBCLASS2_JOYSTICK = 0x01;
    public static final byte SUBCLASS2_GAMEPAD = 0x02;
    public static final byte SUBCLASS2_REMOTE_CONTROL = 0x03;
    public static final byte SUBCLASS2_SENSING_DEVICE = 0x04;
    public static final byte SUBCLASS2_DIGITIZER_TABLET = 0x05;
    public static final byte SUBCLASS2_CARD_READER = 0x06;

    public static final byte REPORT_TYPE_INPUT = 1;
    public static final byte REPORT_TYPE_OUTPUT = 2;
    public static final byte REPORT_TYPE_FEATURE = 3;

    public static final byte ERROR_RSP_SUCCESS = 0;
    public static final byte ERROR_RSP_NOT_READY = 1;
    public static final byte ERROR_RSP_INVALID_RPT_ID = 2;
    public static final byte ERROR_RSP_UNSUPPORTED_REQ = 3;
    public static final byte ERROR_RSP_INVALID_PARAM = 4;
    public static final byte ERROR_RSP_UNKNOWN = 14;

    public static final byte PROTOCOL_BOOT_MODE = 0;
    public static final byte PROTOCOL_REPORT_MODE = 1;

    public abstract static class Callback {
        public Callback() {}

        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean registered) {}

        public void onConnectionStateChanged(BluetoothDevice device, int state) {}

        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {}

        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {}

        public void onSetProtocol(BluetoothDevice device, byte protocol) {}

        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {}

        public void onVirtualCableUnplug(BluetoothDevice device) {}
    }

    BluetoothHidDevice() {}

    public List<BluetoothDevice> getConnectedDevices() {
        return Collections.emptyList();
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        return Collections.emptyList();
    }

    public int getConnectionState(BluetoothDevice device) {
        return STATE_DISCONNECTED;
    }

    public boolean registerApp(BluetoothHidDeviceAppSdpSettings sdp, BluetoothHidDeviceAppQosSettings inQos, BluetoothHidDeviceAppQosSettings outQos, Executor executor, Callback callback) {
        if (sdp == null) {
            throw new IllegalArgumentException("sdp parameter cannot be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor parameter cannot be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback parameter cannot be null");
        }
        return false;
    }

    public boolean unregisterApp() {
        return false;
    }

    public boolean sendReport(BluetoothDevice device, int id, byte[] data) {
        return false;
    }

    public boolean replyReport(BluetoothDevice device, byte type, byte id, byte[] data) {
        return false;
    }

    public boolean reportError(BluetoothDevice device, byte error) {
        return false;
    }

    public boolean connect(BluetoothDevice device) {
        return false;
    }

    public boolean disconnect(BluetoothDevice device) {
        return false;
    }
}
