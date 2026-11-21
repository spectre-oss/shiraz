// SPDX-License-Identifier: GPL-3.0-only
package android.accessibilityservice;

import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;
import java.io.IOException;
import java.util.concurrent.Executor;

public interface BrailleDisplayController {
    void connect(BluetoothDevice bluetoothDevice, BrailleDisplayCallback callback);

    void connect(
            BluetoothDevice bluetoothDevice,
            Executor callbackExecutor,
            BrailleDisplayCallback callback);

    void connect(UsbDevice usbDevice, BrailleDisplayCallback callback);

    void connect(
            UsbDevice usbDevice,
            Executor callbackExecutor,
            BrailleDisplayCallback callback);

    boolean isConnected();

    void write(byte[] buffer) throws IOException;

    void disconnect();

    public interface BrailleDisplayCallback {
        public static final int FLAG_ERROR_CANNOT_ACCESS = 1 << 0;
        public static final int FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND = 1 << 1;

        void onConnected(byte[] hidDescriptor);

        void onConnectionFailed(int error);

        void onInput(byte[] input);

        void onDisconnected();
    }
}
