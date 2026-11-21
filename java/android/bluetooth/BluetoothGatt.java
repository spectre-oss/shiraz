// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Handler;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BluetoothGatt implements BluetoothProfile {
    public static final int GATT_SUCCESS = 0;
    public static final int GATT_READ_NOT_PERMITTED = 0x2;
    public static final int GATT_WRITE_NOT_PERMITTED = 0x3;
    public static final int GATT_INSUFFICIENT_AUTHENTICATION = 0x5;
    public static final int GATT_REQUEST_NOT_SUPPORTED = 0x6;
    public static final int GATT_INVALID_OFFSET = 0x7;
    public static final int GATT_INSUFFICIENT_AUTHORIZATION = 0x8;
    public static final int GATT_INVALID_ATTRIBUTE_LENGTH = 0xd;
    public static final int GATT_INSUFFICIENT_ENCRYPTION = 0xf;
    public static final int GATT_CONNECTION_CONGESTED = 0x8f;
    public static final int GATT_CONNECTION_TIMEOUT = 0x93;
    public static final int GATT_FAILURE = 0x101;

    public static final int CONNECTION_PRIORITY_BALANCED = 0;
    public static final int CONNECTION_PRIORITY_HIGH = 1;
    public static final int CONNECTION_PRIORITY_LOW_POWER = 2;
    public static final int CONNECTION_PRIORITY_DCK = 3;

    private final BluetoothDevice mDevice;
    private BluetoothGattCallback mCallback;

    BluetoothGatt(BluetoothDevice device, BluetoothGattCallback callback, boolean autoConnect, int transport, int phy, Handler handler) {
        mDevice = device;
        mCallback = callback;
    }

    public void close() {
        mCallback = null;
    }

    public void disconnect() {
    }

    public boolean connect() {
        return false;
    }

    public void setPreferredPhy(int txPhy, int rxPhy, int phyOptions) {
    }

    public void readPhy() {
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public boolean discoverServices() {
        return false;
    }

    public List<BluetoothGattService> getServices() {
        return Collections.emptyList();
    }

    public BluetoothGattService getService(UUID uuid) {
        return null;
    }

    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        return false;
    }

    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
        return false;
    }

    public int writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value, int writeType) {
        if (characteristic == null || value == null) {
            throw new IllegalArgumentException("characteristic and value must not be null");
        }
        return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
    }

    public boolean readDescriptor(BluetoothGattDescriptor descriptor) {
        return false;
    }

    public boolean writeDescriptor(BluetoothGattDescriptor descriptor) {
        return false;
    }

    public int writeDescriptor(BluetoothGattDescriptor descriptor, byte[] value) {
        if (descriptor == null || value == null) {
            throw new IllegalArgumentException("descriptor and value must not be null");
        }
        return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
    }

    public boolean beginReliableWrite() {
        return false;
    }

    public boolean executeReliableWrite() {
        return false;
    }

    public void abortReliableWrite() {
    }

    public void abortReliableWrite(BluetoothDevice mDevice) {
    }

    public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        return false;
    }

    public boolean readRemoteRssi() {
        return false;
    }

    public boolean requestMtu(int mtu) {
        return false;
    }

    public boolean requestConnectionPriority(int connectionPriority) {
        if (connectionPriority < CONNECTION_PRIORITY_BALANCED || connectionPriority > CONNECTION_PRIORITY_DCK) {
            throw new IllegalArgumentException("connectionPriority not within valid range");
        }
        return false;
    }

    public int getConnectionState(BluetoothDevice device) {
        throw new UnsupportedOperationException("Use BluetoothManager#getConnectionState instead.");
    }

    public List<BluetoothDevice> getConnectedDevices() {
        throw new UnsupportedOperationException("Use BluetoothManager#getConnectedDevices instead.");
    }

    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        throw new UnsupportedOperationException("Use BluetoothManager#getDevicesMatchingConnectionStates instead.");
    }
}
