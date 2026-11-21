// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.Handler;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public final class MidiManager {
    public static final int TRANSPORT_MIDI_BYTE_STREAM = 1;
    public static final int TRANSPORT_UNIVERSAL_MIDI_PACKETS = 2;

    public static class DeviceCallback {
        public DeviceCallback() {
        }

        public void onDeviceAdded(MidiDeviceInfo device) {
        }

        public void onDeviceRemoved(MidiDeviceInfo device) {
        }

        public void onDeviceStatusChanged(MidiDeviceStatus status) {
        }
    }

    public interface OnDeviceOpenedListener {
        void onDeviceOpened(MidiDevice device);
    }

    private static final class CallbackRecord {
        final DeviceCallback callback;
        final Handler handler;
        final Executor executor;
        final int transport;

        CallbackRecord(DeviceCallback callback, Handler handler, Executor executor, int transport) {
            this.callback = callback;
            this.handler = handler;
            this.executor = executor;
            this.transport = transport;
        }

        void post(Runnable action) {
            if (executor != null) {
                executor.execute(action);
            } else if (handler != null) {
                handler.post(action);
            } else {
                action.run();
            }
        }
    }

    private static final AtomicInteger NEXT_DEVICE_ID = new AtomicInteger(1);

    private final Map<DeviceCallback, CallbackRecord> mCallbacks =
            new LinkedHashMap<DeviceCallback, CallbackRecord>();
    private final Set<MidiDeviceInfo> mDevices = new LinkedHashSet<MidiDeviceInfo>();

    public MidiManager() {
    }

    public void registerDeviceCallback(DeviceCallback callback, Handler handler) {
        if (callback == null) {
            throw new NullPointerException("callback must not be null");
        }
        synchronized (mCallbacks) {
            mCallbacks.put(callback,
                    new CallbackRecord(callback, handler, null, TRANSPORT_MIDI_BYTE_STREAM));
        }
    }

    public void registerDeviceCallback(
            int transport, Executor executor, DeviceCallback callback) {
        if (callback == null) {
            throw new NullPointerException("callback must not be null");
        }
        if (executor == null) {
            throw new NullPointerException("executor must not be null");
        }
        if (transport != TRANSPORT_MIDI_BYTE_STREAM
                && transport != TRANSPORT_UNIVERSAL_MIDI_PACKETS) {
            throw new IllegalArgumentException("Invalid transport: " + transport);
        }
        synchronized (mCallbacks) {
            mCallbacks.put(callback, new CallbackRecord(callback, null, executor, transport));
        }
    }

    public void unregisterDeviceCallback(DeviceCallback callback) {
        synchronized (mCallbacks) {
            mCallbacks.remove(callback);
        }
    }

    public MidiDeviceInfo[] getDevices() {
        synchronized (mDevices) {
            Set<MidiDeviceInfo> byteStream = new LinkedHashSet<MidiDeviceInfo>();
            for (MidiDeviceInfo info : mDevices) {
                if (info.getDefaultProtocol() == MidiDeviceInfo.PROTOCOL_UNKNOWN) {
                    byteStream.add(info);
                }
            }
            return byteStream.toArray(new MidiDeviceInfo[0]);
        }
    }

    public Set<MidiDeviceInfo> getDevicesForTransport(int transport) {
        if (transport != TRANSPORT_MIDI_BYTE_STREAM
                && transport != TRANSPORT_UNIVERSAL_MIDI_PACKETS) {
            throw new IllegalArgumentException("Invalid transport: " + transport);
        }
        synchronized (mDevices) {
            Set<MidiDeviceInfo> result = new LinkedHashSet<MidiDeviceInfo>();
            for (MidiDeviceInfo info : mDevices) {
                boolean isUmp = info.getDefaultProtocol() != MidiDeviceInfo.PROTOCOL_UNKNOWN;
                if ((transport == TRANSPORT_UNIVERSAL_MIDI_PACKETS) == isUmp) {
                    result.add(info);
                }
            }
            return Collections.unmodifiableSet(result);
        }
    }

    public void openDevice(
            MidiDeviceInfo deviceInfo,
            final OnDeviceOpenedListener listener,
            Handler handler) {
        if (deviceInfo == null) {
            throw new NullPointerException("deviceInfo must not be null");
        }
        if (listener == null) {
            throw new NullPointerException("listener must not be null");
        }
        final MidiDevice device = new MidiDevice(deviceInfo, this);
        deliverOpenedDevice(device, listener, handler);
    }

    public void openBluetoothDevice(
            BluetoothDevice bluetoothDevice,
            final OnDeviceOpenedListener listener,
            Handler handler) {
        if (bluetoothDevice == null) {
            throw new NullPointerException("bluetoothDevice must not be null");
        }
        if (listener == null) {
            throw new NullPointerException("listener must not be null");
        }
        Bundle props = new Bundle();
        props.putParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, bluetoothDevice);
        String name = bluetoothDevice.getName();
        props.putString(MidiDeviceInfo.PROPERTY_NAME, name != null ? name : "Bluetooth MIDI");
        MidiDeviceInfo info = new MidiDeviceInfo(
                MidiDeviceInfo.TYPE_BLUETOOTH,
                NEXT_DEVICE_ID.getAndIncrement(),
                1,
                1,
                new String[]{"input"},
                new String[]{"output"},
                props,
                false,
                MidiDeviceInfo.PROTOCOL_UNKNOWN);
        final MidiDevice device = new MidiDevice(info, this);
        deliverOpenedDevice(device, listener, handler);
    }

    private void deliverOpenedDevice(
            final MidiDevice device,
            final OnDeviceOpenedListener listener,
            Handler handler) {
        Runnable action = new Runnable() {
            @Override
            public void run() {
                listener.onDeviceOpened(device);
            }
        };
        if (handler != null) {
            handler.post(action);
        } else {
            action.run();
        }
    }

    void notifyDeviceStatusChanged(final MidiDeviceStatus status) {
        CallbackRecord[] records;
        synchronized (mCallbacks) {
            records = mCallbacks.values().toArray(new CallbackRecord[0]);
        }
        for (int i = 0; i < records.length; i++) {
            final CallbackRecord rec = records[i];
            rec.post(new Runnable() {
                @Override
                public void run() {
                    rec.callback.onDeviceStatusChanged(status);
                }
            });
        }
    }
}
