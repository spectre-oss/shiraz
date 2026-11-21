// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;
import android.content.Context;
import android.os.*;
import java.io.IOException;
import java.util.UUID;

/** Identity and observed metadata only; this value does not connect to or pair with a radio. */
public final class BluetoothDevice implements Parcelable {
    public static final String ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED";
    public static final String ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED";
    public static final String ACTION_ACL_DISCONNECT_REQUESTED = "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED";
    public static final String ACTION_ALIAS_CHANGED = "android.bluetooth.device.action.ALIAS_CHANGED";
    public static final String ACTION_BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED";
    public static final String ACTION_CLASS_CHANGED = "android.bluetooth.device.action.CLASS_CHANGED";
    public static final String ACTION_FOUND = "android.bluetooth.device.action.FOUND";
    public static final String ACTION_NAME_CHANGED = "android.bluetooth.device.action.NAME_CHANGED";
    public static final String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
    public static final String ACTION_UUID = "android.bluetooth.device.action.UUID";

    public static final int ADDRESS_TYPE_PUBLIC = 0;
    public static final int ADDRESS_TYPE_RANDOM = 1;
    public static final int ADDRESS_TYPE_ANONYMOUS = 255;
    public static final int ADDRESS_TYPE_UNKNOWN = 65535;

    public static final int BOND_NONE = 10;
    public static final int BOND_BONDING = 11;
    public static final int BOND_BONDED = 12;

    public static final int DEVICE_TYPE_UNKNOWN = 0;
    public static final int DEVICE_TYPE_CLASSIC = 1;
    public static final int DEVICE_TYPE_LE = 2;
    public static final int DEVICE_TYPE_DUAL = 3;

    public static final int ERROR = -2147483648;

    public static final String EXTRA_BOND_STATE = "android.bluetooth.device.extra.BOND_STATE";
    public static final String EXTRA_CLASS = "android.bluetooth.device.extra.CLASS";
    public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
    public static final String EXTRA_IS_COORDINATED_SET_MEMBER = "android.bluetooth.extra.IS_COORDINATED_SET_MEMBER";
    public static final String EXTRA_NAME = "android.bluetooth.device.extra.NAME";
    public static final String EXTRA_PAIRING_KEY = "android.bluetooth.device.extra.PAIRING_KEY";
    public static final String EXTRA_PAIRING_VARIANT = "android.bluetooth.device.extra.PAIRING_VARIANT";
    public static final String EXTRA_PREVIOUS_BOND_STATE = "android.bluetooth.device.extra.PREVIOUS_BOND_STATE";
    public static final String EXTRA_RSSI = "android.bluetooth.device.extra.RSSI";
    public static final String EXTRA_TRANSPORT = "android.bluetooth.device.extra.TRANSPORT";
    public static final String EXTRA_UUID = "android.bluetooth.device.extra.UUID";

    public static final int PAIRING_VARIANT_PIN = 0;
    public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;

    public static final int PHY_LE_1M = 1;
    public static final int PHY_LE_2M = 2;
    public static final int PHY_LE_CODED = 3;
    public static final int PHY_LE_1M_MASK = 1;
    public static final int PHY_LE_2M_MASK = 2;
    public static final int PHY_LE_CODED_MASK = 4;

    public static final int PHY_OPTION_NO_PREFERRED = 0;
    public static final int PHY_OPTION_S2 = 1;
    public static final int PHY_OPTION_S8 = 2;

    public static final int TRANSPORT_AUTO = 0;
    public static final int TRANSPORT_BREDR = 1;
    public static final int TRANSPORT_LE = 2;

    private final String address;
    private final String name;
    private final int type;
    private final int addressType;
    private String alias;

    private BluetoothDevice(String address, String name, int type) {
        this(address, name, type, ADDRESS_TYPE_PUBLIC);
    }

    BluetoothDevice(String address, String name, int type, int addressType) {
        if (address == null || !address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}") || type < 0 || type > 3) {
            throw new IllegalArgumentException("Bluetooth identity");
        }
        if (name != null && name.length() > 1024) {
            throw new IllegalArgumentException("Bluetooth name length");
        }
        this.address = address.toUpperCase(java.util.Locale.ROOT);
        this.name = name;
        this.type = type;
        this.addressType = addressType;
    }

    public static BluetoothDevice shirazObserved(String address, String name, int type) {
        return new BluetoothDevice(address, name, type);
    }

    public String getAddress() {
        return address;
    }

    public int getAddressType() {
        return addressType;
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public String getAlias() {
        return alias != null ? alias : name;
    }

    public int setAlias(String alias) {
        if (alias != null && alias.isEmpty()) {
            throw new IllegalArgumentException("alias cannot be the empty string");
        }
        this.alias = alias;
        return BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED;
    }

    public boolean createBond() {
        return false;
    }

    public int getBondState() {
        throw new UnsupportedOperationException("Host Bluetooth bond queries are not implemented");
    }

    public BluetoothClass getBluetoothClass() {
        return null;
    }

    public ParcelUuid[] getUuids() {
        return null;
    }

    public boolean fetchUuidsWithSdp() {
        return false;
    }

    public boolean setPin(byte[] pin) {
        return false;
    }

    public boolean setPairingConfirmation(boolean confirm) {
        return false;
    }

    public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        if (uuid == null) {
            throw new NullPointerException("uuid is null");
        }
        return new BluetoothSocket(BluetoothSocket.TYPE_RFCOMM, true, true, this, -1, new ParcelUuid(uuid));
    }

    public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
        if (uuid == null) {
            throw new NullPointerException("uuid is null");
        }
        return new BluetoothSocket(BluetoothSocket.TYPE_RFCOMM, false, false, this, -1, new ParcelUuid(uuid));
    }

    public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback) {
        return connectGatt(context, autoConnect, callback, TRANSPORT_AUTO);
    }

    public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport) {
        return connectGatt(context, autoConnect, callback, transport, PHY_LE_1M_MASK);
    }

    public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport, int phy) {
        return connectGatt(context, autoConnect, callback, transport, phy, null);
    }

    public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport, int phy, Handler handler) {
        if (callback == null) {
            throw new NullPointerException("callback is null");
        }
        return new BluetoothGatt(this, callback, autoConnect, transport, phy, handler);
    }

    public BluetoothSocket createL2capChannel(int psm) throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_L2CAP, true, true, this, psm, null);
    }

    public BluetoothSocket createInsecureL2capChannel(int psm) throws IOException {
        return new BluetoothSocket(BluetoothSocket.TYPE_L2CAP, false, false, this, psm, null);
    }

    public boolean equals(Object other) {
        return other instanceof BluetoothDevice && address.equals(((BluetoothDevice) other).address);
    }

    public int hashCode() {
        return address.hashCode();
    }

    public String toString() {
        return address;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(address);
        out.writeString(name);
        out.writeInt(type);
        out.writeInt(addressType);
        out.writeString(alias);
    }

    public static final Parcelable.Creator<BluetoothDevice> CREATOR = new Parcelable.Creator<BluetoothDevice>() {
        public BluetoothDevice createFromParcel(Parcel in) {
            String addr = in.readString();
            String nm = in.readString();
            int tp = in.readInt();
            int addrTp = in.readInt();
            String al = in.readString();
            BluetoothDevice d = new BluetoothDevice(addr, nm, tp, addrTp);
            d.alias = al;
            return d;
        }

        public BluetoothDevice[] newArray(int n) {
            return new BluetoothDevice[n];
        }
    };
}
