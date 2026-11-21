// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class BluetoothGattServer implements BluetoothProfile {
    private BluetoothGattServerCallback mCallback;
    private final List<BluetoothGattService> mServices = new ArrayList<BluetoothGattService>();

    BluetoothGattServer(BluetoothGattServerCallback callback) {
        mCallback = callback;
    }

    public void close() {
        mCallback = null;
        mServices.clear();
    }

    public boolean connect(BluetoothDevice device, boolean autoConnect) {
        return false;
    }

    public void cancelConnection(BluetoothDevice device) {
    }

    public void setPreferredPhy(BluetoothDevice device, int txPhy, int rxPhy, int phyOptions) {
    }

    public void readPhy(BluetoothDevice device) {
    }

    public boolean sendResponse(BluetoothDevice device, int requestId, int status, int offset, byte[] value) {
        return false;
    }

    public boolean notifyCharacteristicChanged(BluetoothDevice device, BluetoothGattCharacteristic characteristic, boolean confirm) {
        return false;
    }

    public int notifyCharacteristicChanged(BluetoothDevice device, BluetoothGattCharacteristic characteristic, boolean confirm, byte[] value) {
        if (device == null || characteristic == null || value == null) {
            throw new NullPointerException("device, characteristic, and value must not be null");
        }
        return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
    }

    public boolean addService(BluetoothGattService service) {
        if (service == null) {
            return false;
        }
        mServices.add(service);
        return true;
    }

    public boolean removeService(BluetoothGattService service) {
        return mServices.remove(service);
    }

    public void clearServices() {
        mServices.clear();
    }

    public List<BluetoothGattService> getServices() {
        return Collections.unmodifiableList(new ArrayList<BluetoothGattService>(mServices));
    }

    public BluetoothGattService getService(UUID uuid) {
        for (BluetoothGattService service : mServices) {
            if (uuid != null && uuid.equals(service.getUuid())) {
                return service;
            }
        }
        return null;
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
