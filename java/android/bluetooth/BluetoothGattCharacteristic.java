// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothGattCharacteristic implements Parcelable {
    public static final int PROPERTY_BROADCAST = 0x01;
    public static final int PROPERTY_READ = 0x02;
    public static final int PROPERTY_WRITE_NO_RESPONSE = 0x04;
    public static final int PROPERTY_WRITE = 0x08;
    public static final int PROPERTY_NOTIFY = 0x10;
    public static final int PROPERTY_INDICATE = 0x20;
    public static final int PROPERTY_SIGNED_WRITE = 0x40;
    public static final int PROPERTY_EXTENDED_PROPS = 0x80;

    public static final int PERMISSION_READ = 0x01;
    public static final int PERMISSION_READ_ENCRYPTED = 0x02;
    public static final int PERMISSION_READ_ENCRYPTED_MITM = 0x04;
    public static final int PERMISSION_WRITE = 0x10;
    public static final int PERMISSION_WRITE_ENCRYPTED = 0x20;
    public static final int PERMISSION_WRITE_ENCRYPTED_MITM = 0x40;
    public static final int PERMISSION_WRITE_SIGNED = 0x80;
    public static final int PERMISSION_WRITE_SIGNED_MITM = 0x100;

    public static final int WRITE_TYPE_DEFAULT = 0x02;
    public static final int WRITE_TYPE_NO_RESPONSE = 0x01;
    public static final int WRITE_TYPE_SIGNED = 0x04;

    public static final int FORMAT_UINT8 = 0x11;
    public static final int FORMAT_UINT16 = 0x12;
    public static final int FORMAT_UINT32 = 0x14;
    public static final int FORMAT_SINT8 = 0x21;
    public static final int FORMAT_SINT16 = 0x22;
    public static final int FORMAT_SINT32 = 0x24;
    public static final int FORMAT_SFLOAT = 0x32;
    public static final int FORMAT_FLOAT = 0x34;

    protected List<BluetoothGattDescriptor> mDescriptors;

    private final UUID mUuid;
    private int mInstance;
    private final int mProperties;
    private final int mPermissions;
    private int mWriteType;
    private BluetoothGattService mService;
    private byte[] mValue;

    public BluetoothGattCharacteristic(UUID uuid, int properties, int permissions) {
        mUuid = uuid;
        mProperties = properties;
        mPermissions = permissions;
        mDescriptors = new ArrayList<BluetoothGattDescriptor>();
        if ((mProperties & PROPERTY_WRITE_NO_RESPONSE) != 0) {
            mWriteType = WRITE_TYPE_NO_RESPONSE;
        } else {
            mWriteType = WRITE_TYPE_DEFAULT;
        }
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        if (mUuid != null) {
            out.writeInt(1);
            out.writeLong(mUuid.getMostSignificantBits());
            out.writeLong(mUuid.getLeastSignificantBits());
        } else {
            out.writeInt(0);
        }
        out.writeInt(mInstance);
        out.writeInt(mProperties);
        out.writeInt(mPermissions);
        out.writeInt(mWriteType);
        out.writeByteArray(mValue);
        out.writeTypedList(mDescriptors);
    }

    public static final Parcelable.Creator<BluetoothGattCharacteristic> CREATOR = new Parcelable.Creator<BluetoothGattCharacteristic>() {
        public BluetoothGattCharacteristic createFromParcel(Parcel in) {
            UUID uuid = in.readInt() != 0 ? new UUID(in.readLong(), in.readLong()) : null;
            int instance = in.readInt();
            int props = in.readInt();
            int perms = in.readInt();
            int writeType = in.readInt();
            byte[] value = in.createByteArray();
            ArrayList<BluetoothGattDescriptor> descs = in.createTypedArrayList(BluetoothGattDescriptor.CREATOR);
            BluetoothGattCharacteristic ch = new BluetoothGattCharacteristic(uuid, props, perms);
            ch.mInstance = instance;
            ch.mWriteType = writeType;
            ch.mValue = value;
            if (descs != null) {
                for (BluetoothGattDescriptor d : descs) {
                    if (d != null) {
                        ch.addDescriptor(d);
                    }
                }
            }
            return ch;
        }

        public BluetoothGattCharacteristic[] newArray(int size) {
            return new BluetoothGattCharacteristic[size];
        }
    };

    public boolean addDescriptor(BluetoothGattDescriptor descriptor) {
        if (descriptor == null) {
            return false;
        }
        mDescriptors.add(descriptor);
        descriptor.setCharacteristic(this);
        return true;
    }

    public BluetoothGattService getService() {
        return mService;
    }

    void setService(BluetoothGattService service) {
        mService = service;
    }

    public UUID getUuid() {
        return mUuid;
    }

    public int getInstanceId() {
        return mInstance;
    }

    public int getProperties() {
        return mProperties;
    }

    public int getPermissions() {
        return mPermissions;
    }

    public int getWriteType() {
        return mWriteType;
    }

    public void setWriteType(int writeType) {
        mWriteType = writeType;
    }

    public List<BluetoothGattDescriptor> getDescriptors() {
        return mDescriptors;
    }

    public BluetoothGattDescriptor getDescriptor(UUID uuid) {
        for (BluetoothGattDescriptor descriptor : mDescriptors) {
            if (uuid != null && uuid.equals(descriptor.getUuid())) {
                return descriptor;
            }
        }
        return null;
    }

    public byte[] getValue() {
        return mValue;
    }

    private static int getTypeLen(int formatType) {
        return formatType & 0x0F;
    }

    private static int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

    private static int unsignedBytesToInt(byte b0, byte b1) {
        return unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8);
    }

    private static int unsignedBytesToInt(byte b0, byte b1, byte b2, byte b3) {
        return unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8)
                + (unsignedByteToInt(b2) << 16) + (unsignedByteToInt(b3) << 24);
    }

    private static int unsignedToSigned(int unsigned, int size) {
        if ((unsigned & (1 << (size - 1))) != 0) {
            unsigned = -1 * ((1 << (size - 1)) - (unsigned & ((1 << (size - 1)) - 1)));
        }
        return unsigned;
    }

    private static int intToSignedBits(int i, int size) {
        if (i < 0) {
            i = (1 << (size - 1)) + (i & ((1 << (size - 1)) - 1));
        }
        return i;
    }

    public Integer getIntValue(int formatType, int offset) {
        if (mValue == null || offset < 0 || (offset + getTypeLen(formatType)) > mValue.length) {
            return null;
        }
        switch (formatType) {
            case FORMAT_UINT8:
                return unsignedByteToInt(mValue[offset]);
            case FORMAT_UINT16:
                return unsignedBytesToInt(mValue[offset], mValue[offset + 1]);
            case FORMAT_UINT32:
                return unsignedBytesToInt(mValue[offset], mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]);
            case FORMAT_SINT8:
                return unsignedToSigned(unsignedByteToInt(mValue[offset]), 8);
            case FORMAT_SINT16:
                return unsignedToSigned(unsignedBytesToInt(mValue[offset], mValue[offset + 1]), 16);
            case FORMAT_SINT32:
                return unsignedBytesToInt(mValue[offset], mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]);
            default:
                return null;
        }
    }

    public Float getFloatValue(int formatType, int offset) {
        if (mValue == null || offset < 0 || (offset + getTypeLen(formatType)) > mValue.length) {
            return null;
        }
        switch (formatType) {
            case FORMAT_SFLOAT: {
                int raw = unsignedBytesToInt(mValue[offset], mValue[offset + 1]);
                int mantissaUnsigned = raw & 0x0FFF;
                int exponentUnsigned = (raw >> 12) & 0x0F;
                if (exponentUnsigned == 0) {
                    if (mantissaUnsigned == 0x07FF || mantissaUnsigned == 0x0800
                            || mantissaUnsigned == 0x07FD || mantissaUnsigned == 0x0801) {
                        return Float.NaN;
                    }
                    if (mantissaUnsigned == 0x07FE) {
                        return Float.POSITIVE_INFINITY;
                    }
                    if (mantissaUnsigned == 0x0802) {
                        return Float.NEGATIVE_INFINITY;
                    }
                }
                int mantissa = unsignedToSigned(mantissaUnsigned, 12);
                int exponent = unsignedToSigned(exponentUnsigned, 4);
                return (float) (mantissa * Math.pow(10, exponent));
            }
            case FORMAT_FLOAT: {
                int raw = unsignedBytesToInt(mValue[offset], mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]);
                int mantissaUnsigned = raw & 0x00FFFFFF;
                int exponentUnsigned = (raw >>> 24) & 0xFF;
                if (exponentUnsigned == 0) {
                    if (mantissaUnsigned == 0x007FFFFF || mantissaUnsigned == 0x00800000
                            || mantissaUnsigned == 0x007FFFFD || mantissaUnsigned == 0x00800001) {
                        return Float.NaN;
                    }
                    if (mantissaUnsigned == 0x007FFFFE) {
                        return Float.POSITIVE_INFINITY;
                    }
                    if (mantissaUnsigned == 0x00800002) {
                        return Float.NEGATIVE_INFINITY;
                    }
                }
                int mantissa = unsignedToSigned(mantissaUnsigned, 24);
                int exponent = mValue[offset + 3];
                return (float) (mantissa * Math.pow(10, exponent));
            }
            default:
                return null;
        }
    }

    public String getStringValue(int offset) {
        if (mValue == null || offset < 0 || offset > mValue.length) {
            return null;
        }
        return new String(mValue, offset, mValue.length - offset, StandardCharsets.UTF_8);
    }

    public boolean setValue(byte[] value) {
        mValue = value;
        return true;
    }

    public boolean setValue(int value, int formatType, int offset) {
        if (offset < 0) {
            return false;
        }
        int len = offset + getTypeLen(formatType);
        if (mValue == null) {
            mValue = new byte[len];
        }
        if (len > mValue.length) {
            return false;
        }
        switch (formatType) {
            case FORMAT_SINT8:
                value = intToSignedBits(value, 8);
                // fall through
            case FORMAT_UINT8:
                mValue[offset] = (byte) (value & 0xFF);
                break;
            case FORMAT_SINT16:
                value = intToSignedBits(value, 16);
                // fall through
            case FORMAT_UINT16:
                mValue[offset] = (byte) (value & 0xFF);
                mValue[offset + 1] = (byte) ((value >> 8) & 0xFF);
                break;
            case FORMAT_SINT32:
            case FORMAT_UINT32:
                mValue[offset] = (byte) (value & 0xFF);
                mValue[offset + 1] = (byte) ((value >> 8) & 0xFF);
                mValue[offset + 2] = (byte) ((value >> 16) & 0xFF);
                mValue[offset + 3] = (byte) ((value >> 24) & 0xFF);
                break;
            default:
                return false;
        }
        return true;
    }

    public boolean setValue(int mantissa, int exponent, int formatType, int offset) {
        if (offset < 0) {
            return false;
        }
        int len = offset + getTypeLen(formatType);
        if (mValue == null) {
            mValue = new byte[len];
        }
        if (len > mValue.length) {
            return false;
        }
        switch (formatType) {
            case FORMAT_SFLOAT: {
                int m = intToSignedBits(mantissa, 12) & 0x0FFF;
                int e = intToSignedBits(exponent, 4) & 0x0F;
                mValue[offset] = (byte) (m & 0xFF);
                mValue[offset + 1] = (byte) (((m >> 8) & 0x0F) | (e << 4));
                break;
            }
            case FORMAT_FLOAT: {
                int m = intToSignedBits(mantissa, 24) & 0x00FFFFFF;
                int e = intToSignedBits(exponent, 8) & 0xFF;
                mValue[offset] = (byte) (m & 0xFF);
                mValue[offset + 1] = (byte) ((m >> 8) & 0xFF);
                mValue[offset + 2] = (byte) ((m >> 16) & 0xFF);
                mValue[offset + 3] = (byte) e;
                break;
            }
            default:
                return false;
        }
        return true;
    }

    public boolean setValue(String value) {
        if (value == null) {
            return false;
        }
        mValue = value.getBytes(StandardCharsets.UTF_8);
        return true;
    }
}
