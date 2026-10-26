// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;
import android.os.*;
/** Identity and observed metadata only; this value does not connect to or pair with a radio. */
public final class BluetoothDevice implements Parcelable {
    public static final int DEVICE_TYPE_UNKNOWN=0,DEVICE_TYPE_CLASSIC=1,DEVICE_TYPE_LE=2,DEVICE_TYPE_DUAL=3,PHY_LE_1M=1,PHY_LE_2M=2,PHY_LE_CODED=3,BOND_NONE=10,BOND_BONDING=11,BOND_BONDED=12;
    private final String address,name;private final int type;
    private BluetoothDevice(String address,String name,int type){if(address==null||!address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")||type<0||type>3)throw new IllegalArgumentException("Bluetooth identity");if(name!=null&&name.length()>1024)throw new IllegalArgumentException("Bluetooth name length");this.address=address.toUpperCase(java.util.Locale.ROOT);this.name=name;this.type=type;}
    public static BluetoothDevice shirazObserved(String address,String name,int type){return new BluetoothDevice(address,name,type);}
    public String getAddress(){return address;}
    public String getName(){return name;}
    public int getType(){return type;}
    public int getBondState(){throw new UnsupportedOperationException("Host Bluetooth bond queries are not implemented");}
    public boolean equals(Object other){return other instanceof BluetoothDevice&&address.equals(((BluetoothDevice)other).address);}
    public int hashCode(){return address.hashCode();}
    public String toString(){return address;}
    public int describeContents(){return 0;}
    public void writeToParcel(Parcel out,int flags){throw new UnsupportedOperationException("Bluetooth parcel transport is not implemented");}
    public static final Parcelable.Creator<BluetoothDevice> CREATOR=new Parcelable.Creator<BluetoothDevice>(){public BluetoothDevice createFromParcel(Parcel in){throw new UnsupportedOperationException("Bluetooth parcel transport is not implemented");}public BluetoothDevice[] newArray(int n){return new BluetoothDevice[n];}};
}
