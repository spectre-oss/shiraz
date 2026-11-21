// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.usb;

import android.app.PendingIntent;
import android.os.ParcelFileDescriptor;
import java.util.HashMap;

public class UsbManager {
    public static final String ACTION_USB_DEVICE_ATTACHED =
            "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DEVICE_DETACHED =
            "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_ACCESSORY_ATTACHED =
            "android.hardware.usb.action.USB_ACCESSORY_ATTACHED";
    public static final String ACTION_USB_ACCESSORY_DETACHED =
            "android.hardware.usb.action.USB_ACCESSORY_DETACHED";

    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_ACCESSORY = "accessory";
    public static final String EXTRA_PERMISSION_GRANTED = "permission";

    /** @hide */
    public UsbManager() {
    }

    public HashMap<String, UsbDevice> getDeviceList() {
        return new HashMap<String, UsbDevice>();
    }

    public UsbDeviceConnection openDevice(UsbDevice device) {
        if (device == null) {
            throw new NullPointerException("device");
        }
        return null;
    }

    public UsbAccessory[] getAccessoryList() {
        return null;
    }

    public ParcelFileDescriptor openAccessory(UsbAccessory accessory) {
        if (accessory == null) {
            throw new NullPointerException("accessory");
        }
        return null;
    }

    public boolean hasPermission(UsbDevice device) {
        if (device == null) {
            throw new NullPointerException("device");
        }
        return false;
    }

    public boolean hasPermission(UsbAccessory accessory) {
        if (accessory == null) {
            throw new NullPointerException("accessory");
        }
        return false;
    }

    public void requestPermission(UsbDevice device, PendingIntent pi) {
        if (device == null) {
            throw new NullPointerException("device");
        }
    }

    public void requestPermission(UsbAccessory accessory, PendingIntent pi) {
        if (accessory == null) {
            throw new NullPointerException("accessory");
        }
    }
}
