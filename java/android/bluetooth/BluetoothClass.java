// SPDX-License-Identifier: GPL-3.0-only
package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

public final class BluetoothClass implements Parcelable {
    public static final int PROFILE_HEADSET = 0;
    public static final int PROFILE_A2DP = 1;
    public static final int PROFILE_HID = 3;

    private static final int SERVICE_BITMASK = 0xFFE000;
    private static final int DEVICE_MAJOR_BITMASK = 0x1F00;
    private static final int DEVICE_BITMASK = 0x1FFC;

    private final int mClass;

    BluetoothClass(int classInt) {
        mClass = classInt;
    }

    public boolean equals(Object o) {
        return (o instanceof BluetoothClass) && mClass == ((BluetoothClass) o).mClass;
    }

    public int hashCode() {
        return mClass;
    }

    public String toString() {
        return Integer.toHexString(mClass);
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(mClass);
    }

    public static final Parcelable.Creator<BluetoothClass> CREATOR = new Parcelable.Creator<BluetoothClass>() {
        public BluetoothClass createFromParcel(Parcel in) {
            return new BluetoothClass(in.readInt());
        }

        public BluetoothClass[] newArray(int size) {
            return new BluetoothClass[size];
        }
    };

    public static final class Service {
        public static final int LIMITED_DISCOVERABILITY = 0x002000;
        public static final int LE_AUDIO = 0x004000;
        public static final int POSITIONING = 0x010000;
        public static final int NETWORKING = 0x020000;
        public static final int RENDER = 0x040000;
        public static final int CAPTURE = 0x080000;
        public static final int OBJECT_TRANSFER = 0x100000;
        public static final int AUDIO = 0x200000;
        public static final int TELEPHONY = 0x400000;
        public static final int INFORMATION = 0x800000;

        public Service() {}
    }

    public boolean hasService(int service) {
        return (mClass & SERVICE_BITMASK & service) != 0;
    }

    public static class Device {
        public static class Major {
            public static final int MISC = 0x0000;
            public static final int COMPUTER = 0x0100;
            public static final int PHONE = 0x0200;
            public static final int NETWORKING = 0x0300;
            public static final int AUDIO_VIDEO = 0x0400;
            public static final int PERIPHERAL = 0x0500;
            public static final int IMAGING = 0x0600;
            public static final int WEARABLE = 0x0700;
            public static final int TOY = 0x0800;
            public static final int HEALTH = 0x0900;
            public static final int UNCATEGORIZED = 0x1F00;

            public Major() {}
        }

        public static final int COMPUTER_UNCATEGORIZED = 0x0100;
        public static final int COMPUTER_DESKTOP = 0x0104;
        public static final int COMPUTER_SERVER = 0x0108;
        public static final int COMPUTER_LAPTOP = 0x010C;
        public static final int COMPUTER_HANDHELD_PC_PDA = 0x0110;
        public static final int COMPUTER_PALM_SIZE_PC_PDA = 0x0114;
        public static final int COMPUTER_WEARABLE = 0x0118;

        public static final int PHONE_UNCATEGORIZED = 0x0200;
        public static final int PHONE_CELLULAR = 0x0204;
        public static final int PHONE_CORDLESS = 0x0208;
        public static final int PHONE_SMART = 0x020C;
        public static final int PHONE_MODEM_OR_GATEWAY = 0x0210;
        public static final int PHONE_ISDN = 0x0214;

        public static final int AUDIO_VIDEO_UNCATEGORIZED = 0x0400;
        public static final int AUDIO_VIDEO_WEARABLE_HEADSET = 0x0404;
        public static final int AUDIO_VIDEO_HANDSFREE = 0x0408;
        public static final int AUDIO_VIDEO_MICROPHONE = 0x0410;
        public static final int AUDIO_VIDEO_LOUDSPEAKER = 0x0414;
        public static final int AUDIO_VIDEO_HEADPHONES = 0x0418;
        public static final int AUDIO_VIDEO_PORTABLE_AUDIO = 0x041C;
        public static final int AUDIO_VIDEO_CAR_AUDIO = 0x0420;
        public static final int AUDIO_VIDEO_SET_TOP_BOX = 0x0424;
        public static final int AUDIO_VIDEO_HIFI_AUDIO = 0x0428;
        public static final int AUDIO_VIDEO_VCR = 0x042C;
        public static final int AUDIO_VIDEO_VIDEO_CAMERA = 0x0430;
        public static final int AUDIO_VIDEO_CAMCORDER = 0x0434;
        public static final int AUDIO_VIDEO_VIDEO_MONITOR = 0x0438;
        public static final int AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER = 0x043C;
        public static final int AUDIO_VIDEO_VIDEO_CONFERENCING = 0x0440;
        public static final int AUDIO_VIDEO_VIDEO_GAMING_TOY = 0x0448;

        public static final int PERIPHERAL_NON_KEYBOARD_NON_POINTING = 0x0500;
        public static final int PERIPHERAL_KEYBOARD = 0x0540;
        public static final int PERIPHERAL_POINTING = 0x0580;
        public static final int PERIPHERAL_KEYBOARD_POINTING = 0x05C0;

        public static final int WEARABLE_UNCATEGORIZED = 0x0700;
        public static final int WEARABLE_WRIST_WATCH = 0x0704;
        public static final int WEARABLE_PAGER = 0x0708;
        public static final int WEARABLE_JACKET = 0x070C;
        public static final int WEARABLE_HELMET = 0x0710;
        public static final int WEARABLE_GLASSES = 0x0714;

        public static final int TOY_UNCATEGORIZED = 0x0800;
        public static final int TOY_ROBOT = 0x0804;
        public static final int TOY_VEHICLE = 0x0808;
        public static final int TOY_DOLL_ACTION_FIGURE = 0x080C;
        public static final int TOY_CONTROLLER = 0x0810;
        public static final int TOY_GAME = 0x0814;

        public static final int HEALTH_UNCATEGORIZED = 0x0900;
        public static final int HEALTH_BLOOD_PRESSURE = 0x0904;
        public static final int HEALTH_THERMOMETER = 0x0908;
        public static final int HEALTH_WEIGHING = 0x090C;
        public static final int HEALTH_GLUCOSE = 0x0910;
        public static final int HEALTH_PULSE_OXIMETER = 0x0914;
        public static final int HEALTH_PULSE_RATE = 0x0918;
        public static final int HEALTH_DATA_DISPLAY = 0x091C;

        public Device() {}
    }

    public int getMajorDeviceClass() {
        return mClass & DEVICE_MAJOR_BITMASK;
    }

    public int getDeviceClass() {
        return mClass & DEVICE_BITMASK;
    }

    public boolean doesClassMatch(int profile) {
        if (profile == PROFILE_A2DP) {
            if (hasService(Service.RENDER)) {
                return true;
            }
            switch (getDeviceClass()) {
                case Device.AUDIO_VIDEO_LOUDSPEAKER:
                case Device.AUDIO_VIDEO_HEADPHONES:
                case Device.AUDIO_VIDEO_CAR_AUDIO:
                case Device.AUDIO_VIDEO_HIFI_AUDIO:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_HEADSET) {
            if (hasService(Service.RENDER)) {
                return true;
            }
            switch (getDeviceClass()) {
                case Device.AUDIO_VIDEO_HANDSFREE:
                case Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                case Device.AUDIO_VIDEO_CAR_AUDIO:
                    return true;
                default:
                    return false;
            }
        } else if (profile == PROFILE_HID) {
            return getMajorDeviceClass() == Device.Major.PERIPHERAL;
        }
        return false;
    }
}
