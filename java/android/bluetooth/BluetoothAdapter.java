// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class BluetoothAdapter {
    public static final int ERROR = -2147483648;

    public static final String ACTION_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED";
    public static final String EXTRA_STATE = "android.bluetooth.adapter.extra.STATE";
    public static final String EXTRA_PREVIOUS_STATE = "android.bluetooth.adapter.extra.PREVIOUS_STATE";

    public static final int STATE_OFF = 10;
    public static final int STATE_TURNING_ON = 11;
    public static final int STATE_ON = 12;
    public static final int STATE_TURNING_OFF = 13;

    public static final String ACTION_REQUEST_DISCOVERABLE = "android.bluetooth.adapter.action.REQUEST_DISCOVERABLE";
    public static final String EXTRA_DISCOVERABLE_DURATION = "android.bluetooth.adapter.extra.DISCOVERABLE_DURATION";
    public static final String ACTION_REQUEST_ENABLE = "android.bluetooth.adapter.action.REQUEST_ENABLE";
    public static final String ACTION_SCAN_MODE_CHANGED = "android.bluetooth.adapter.action.SCAN_MODE_CHANGED";
    public static final String EXTRA_SCAN_MODE = "android.bluetooth.adapter.extra.SCAN_MODE";
    public static final String EXTRA_PREVIOUS_SCAN_MODE = "android.bluetooth.adapter.extra.PREVIOUS_SCAN_MODE";

    public static final int SCAN_MODE_NONE = 20;
    public static final int SCAN_MODE_CONNECTABLE = 21;
    public static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 23;

    public static final String ACTION_DISCOVERY_STARTED = "android.bluetooth.adapter.action.DISCOVERY_STARTED";
    public static final String ACTION_DISCOVERY_FINISHED = "android.bluetooth.adapter.action.DISCOVERY_FINISHED";
    public static final String ACTION_LOCAL_NAME_CHANGED = "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED";
    public static final String EXTRA_LOCAL_NAME = "android.bluetooth.adapter.extra.LOCAL_NAME";
    public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED";
    public static final String EXTRA_CONNECTION_STATE = "android.bluetooth.adapter.extra.CONNECTION_STATE";
    public static final String EXTRA_PREVIOUS_CONNECTION_STATE = "android.bluetooth.adapter.extra.PREVIOUS_CONNECTION_STATE";

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_DISCONNECTING = 3;

    private static final int ADDRESS_LENGTH = 17;
    private static BluetoothAdapter sAdapter;

    private String mLocalName = "Shiraz Host";

    BluetoothAdapter() {}

    public static synchronized BluetoothAdapter getDefaultAdapter() {
        if (sAdapter == null) {
            sAdapter = new BluetoothAdapter();
        }
        return sAdapter;
    }

    public BluetoothDevice getRemoteDevice(String address) {
        if (address == null) {
            throw new IllegalArgumentException("address cannot be null");
        }
        if (!checkBluetoothAddress(address)) {
            throw new IllegalArgumentException(address + " is not a valid Bluetooth address");
        }
        return new BluetoothDevice(address, null, BluetoothDevice.DEVICE_TYPE_UNKNOWN, BluetoothDevice.ADDRESS_TYPE_PUBLIC);
    }

    public BluetoothDevice getRemoteLeDevice(String address, int addressType) {
        if (address == null) {
            throw new IllegalArgumentException("address cannot be null");
        }
        if (!checkBluetoothAddress(address)) {
            throw new IllegalArgumentException(address + " is not a valid Bluetooth address");
        }
        if (addressType != BluetoothDevice.ADDRESS_TYPE_PUBLIC
                && addressType != BluetoothDevice.ADDRESS_TYPE_RANDOM
                && addressType != BluetoothDevice.ADDRESS_TYPE_ANONYMOUS) {
            throw new IllegalArgumentException("Invalid address type: " + addressType);
        }
        return new BluetoothDevice(address, null, BluetoothDevice.DEVICE_TYPE_LE, addressType);
    }

    public BluetoothDevice getRemoteDevice(byte[] address) {
        if (address == null || address.length != 6) {
            throw new IllegalArgumentException("Bluetooth address must have 6 bytes");
        }
        return getRemoteDevice(String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                address[0], address[1], address[2], address[3], address[4], address[5]));
    }

    public BluetoothLeAdvertiser getBluetoothLeAdvertiser() {
        return null;
    }

    public BluetoothLeScanner getBluetoothLeScanner() {
        return null;
    }

    public boolean isEnabled() {
        return false;
    }

    public int getState() {
        return STATE_OFF;
    }

    public boolean enable() {
        return false;
    }

    public boolean disable() {
        return false;
    }

    public String getAddress() {
        return "02:00:00:00:00:00";
    }

    public String getName() {
        return mLocalName;
    }

    public boolean setName(String name) {
        if (name == null) {
            return false;
        }
        mLocalName = name;
        return false;
    }

    public int getScanMode() {
        return SCAN_MODE_NONE;
    }

    public Duration getDiscoverableTimeout() {
        return null;
    }

    public boolean startDiscovery() {
        return false;
    }

    public boolean cancelDiscovery() {
        return false;
    }

    public boolean isDiscovering() {
        return false;
    }

    public boolean isMultipleAdvertisementSupported() {
        return false;
    }

    public boolean isOffloadedFilteringSupported() {
        return false;
    }

    public boolean isOffloadedScanBatchingSupported() {
        return false;
    }

    public boolean isLe2MPhySupported() {
        return false;
    }

    public boolean isLeCodedPhySupported() {
        return false;
    }

    public boolean isLeExtendedAdvertisingSupported() {
        return false;
    }

    public boolean isLePeriodicAdvertisingSupported() {
        return false;
    }

    public int isLeAudioSupported() {
        return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
    }

    public int isLeAudioBroadcastSourceSupported() {
        return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
    }

    public int isLeAudioBroadcastAssistantSupported() {
        return BluetoothStatusCodes.FEATURE_NOT_SUPPORTED;
    }

    public int getLeMaximumAdvertisingDataLength() {
        return 31;
    }

    public int getMaxConnectedAudioDevices() {
        return 1;
    }

    public Set<BluetoothDevice> getBondedDevices() {
        return Collections.emptySet();
    }

    public int getProfileConnectionState(int profile) {
        return STATE_DISCONNECTED;
    }

    public BluetoothServerSocket listenUsingRfcommWithServiceRecord(String name, UUID uuid) throws IOException {
        throw new BluetoothSocketException(BluetoothSocketException.BLUETOOTH_OFF_FAILURE, "Bluetooth is off");
    }

    public BluetoothServerSocket listenUsingInsecureRfcommWithServiceRecord(String name, UUID uuid) throws IOException {
        throw new BluetoothSocketException(BluetoothSocketException.BLUETOOTH_OFF_FAILURE, "Bluetooth is off");
    }

    public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener, int profile) {
        return false;
    }

    public void closeProfileProxy(int profile, BluetoothProfile proxy) {
    }

    protected void finalize() throws Throwable {
        super.finalize();
    }

    public static boolean checkBluetoothAddress(String address) {
        if (address == null || address.length() != ADDRESS_LENGTH) {
            return false;
        }
        for (int i = 0; i < ADDRESS_LENGTH; i++) {
            char c = address.charAt(i);
            switch (i % 3) {
                case 0:
                case 1:
                    if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
                        break;
                    }
                    return false;
                case 2:
                    if (c == ':') {
                        break;
                    }
                    return false;
            }
        }
        return true;
    }

    public interface LeScanCallback {
        void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord);
    }

    public boolean startLeScan(LeScanCallback callback) {
        return false;
    }

    public boolean startLeScan(UUID[] serviceUuids, LeScanCallback callback) {
        return false;
    }

    public void stopLeScan(LeScanCallback callback) {
    }

    public BluetoothServerSocket listenUsingL2capChannel() throws IOException {
        throw new BluetoothSocketException(BluetoothSocketException.BLUETOOTH_OFF_FAILURE, "Bluetooth is off");
    }

    public BluetoothServerSocket listenUsingInsecureL2capChannel() throws IOException {
        throw new BluetoothSocketException(BluetoothSocketException.BLUETOOTH_OFF_FAILURE, "Bluetooth is off");
    }
}
